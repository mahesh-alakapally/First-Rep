package com.firstrep.entitlement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firstrep.audit.AuditService;
import com.firstrep.common.AppException;
import com.firstrep.common.Ids;
import com.firstrep.common.Kinds.CheckoutStatus;
import com.firstrep.common.Kinds.SlotStatus;
import com.firstrep.common.Kinds.SubscriptionStatus;
import com.firstrep.config.AppProperties;
import com.firstrep.enrollment.FreeSlotRepository;
import com.firstrep.identity.UserAccount;
import com.firstrep.identity.UserAccountRepository;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingService {
    private final PlanRepository plans;
    private final SubscriptionRepository subscriptions;
    private final PaymentRepository payments;
    private final WebhookEventRepository webhooks;
    private final CheckoutSessionRepository checkouts;
    private final UserAccountRepository users;
    private final FreeSlotRepository slots;
    private final AuditService audit;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public BillingService(PlanRepository plans, SubscriptionRepository subscriptions, PaymentRepository payments,
                          WebhookEventRepository webhooks, CheckoutSessionRepository checkouts, UserAccountRepository users,
                          FreeSlotRepository slots, AuditService audit, AppProperties properties, ObjectMapper objectMapper) {
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.payments = payments;
        this.webhooks = webhooks;
        this.checkouts = checkouts;
        this.users = users;
        this.slots = slots;
        this.audit = audit;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<PlanView> listPlans() {
        return plans.findByActiveTrueOrderByPriceCentsAsc().stream()
                .map(plan -> new PlanView(plan.getCode(), plan.getName(), plan.getBillingInterval(), plan.getPriceCents(), plan.getCurrency()))
                .toList();
    }

    @Transactional
    public Entitlements entitlements(UserAccount user) {
        expireIfNeeded(user);
        int allowance = properties.getFreeAllowance();
        var userSlots = slots.findByStudentIdOrderBySlotIndexAsc(user.getId());
        int remaining = (int) userSlots.stream().filter(slot -> slot.getStatus() == SlotStatus.AVAILABLE).count();
        int used = (int) userSlots.stream().filter(slot -> slot.getStatus() == SlotStatus.CONSUMED).count();
        Subscription current = activeSubscription(user);
        return new Entitlements(allowance, remaining, used, current != null, current == null ? null : toView(current));
    }

    @Transactional
    public boolean hasActivePremium(UserAccount user) {
        expireIfNeeded(user);
        return activeSubscription(user) != null;
    }

    @Transactional
    public Map<String, Object> checkout(UserAccount user, String planCode) {
        Plan plan = plans.findByCode(planCode).filter(Plan::isActive)
                .orElseThrow(() -> AppException.bad("UNKNOWN_PLAN", "Choose a listed plan."));
        CheckoutSession session = new CheckoutSession();
        session.setPublicId(Ids.uuid());
        session.setUser(user);
        session.setPlan(plan);
        session.setStatus(CheckoutStatus.PENDING);
        session.setProvider(properties.simulatorBilling() ? "SIMULATOR" : "STRIPE");
        session.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        checkouts.save(session);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("checkoutId", session.getPublicId());
        body.put("mode", properties.simulatorBilling() ? "simulator" : "stripe");
        if (properties.simulatorBilling()) {
            body.put("message", "Local billing simulator. No card is charged and this is not Stripe.");
            return body;
        }
        String secret = properties.getBilling().getStripeSecretKey();
        if (secret == null || secret.isBlank()) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "STRIPE_NOT_CONFIGURED", "Stripe is not configured.");
        }
        String interval = "YEAR".equals(plan.getBillingInterval()) ? "year" : "month";
        String base = properties.getAppBaseUrl().replaceAll("/$", "");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("mode", "subscription");
        fields.put("success_url", base + "/app/billing?checkout=return");
        fields.put("cancel_url", base + "/app/billing?checkout=cancel");
        fields.put("client_reference_id", session.getPublicId());
        fields.put("metadata[userPublicId]", user.getPublicId());
        fields.put("metadata[planCode]", plan.getCode());
        fields.put("metadata[checkoutPublicId]", session.getPublicId());
        fields.put("line_items[0][quantity]", "1");
        fields.put("line_items[0][price_data][currency]", plan.getCurrency().toLowerCase());
        fields.put("line_items[0][price_data][unit_amount]", Integer.toString(plan.getPriceCents()));
        fields.put("line_items[0][price_data][recurring][interval]", interval);
        fields.put("line_items[0][price_data][product_data][name]", plan.getName());
        String form = form(fields);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getBilling().getStripeApiBase().replaceAll("/$", "") + "/v1/checkout/sessions"))
                    .header("Authorization", "Bearer " + secret)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new AppException(HttpStatus.BAD_GATEWAY, "STRIPE_ERROR", "Stripe did not open a checkout session.");
            }
            JsonNode node = objectMapper.readTree(response.body());
            session.setProviderSessionId(node.path("id").asText());
            body.put("url", node.path("url").asText());
            return body;
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "STRIPE_ERROR", "Stripe could not be reached.");
        }
    }

    @Transactional
    public Entitlements confirmSimulator(UserAccount user, String checkoutId) {
        if (!properties.simulatorBilling()) {
            throw AppException.notFound("Checkout not found.");
        }
        CheckoutSession session = ownedCheckout(user, checkoutId);
        apply("SIMULATOR", "sim_" + session.getPublicId(), "checkout.session.completed", user.getPublicId(),
                session.getPlan().getCode(), "sim_cus_" + user.getPublicId(), "sim_sub_" + session.getPublicId(), session.getPublicId());
        return entitlements(user);
    }

    @Transactional
    public Map<String, String> portal(UserAccount user) {
        if (properties.simulatorBilling()) {
            return Map.of("mode", "simulator", "url", properties.getAppBaseUrl().replaceAll("/$", "") + "/app/billing");
        }
        throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "STRIPE_NOT_CONFIGURED", "Customer portal needs Stripe configuration.");
    }

    @Transactional
    public Entitlements cancel(UserAccount user) {
        Subscription subscription = subscriptions.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(item -> item.getStatus() == SubscriptionStatus.ACTIVE || item.getStatus() == SubscriptionStatus.CANCEL_AT_PERIOD_END)
                .findFirst()
                .orElseThrow(() -> AppException.bad("NO_SUBSCRIPTION", "There is no active subscription to cancel."));
        subscription.setStatus(SubscriptionStatus.CANCEL_AT_PERIOD_END);
        subscription.setCancelAtPeriodEnd(true);
        audit.record(user, "SUBSCRIPTION_CANCEL_SCHEDULED", "SUBSCRIPTION", subscription.getPublicId(), null, "Cancels at period end");
        return entitlements(user);
    }

    @Transactional(readOnly = true)
    public List<PaymentView> history(UserAccount user) {
        return payments.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(payment -> new PaymentView(payment.getPublicId(), payment.getAmountCents(), payment.getCurrency(),
                        payment.getStatus(), payment.getProvider(), payment.getCreatedAt().toString()))
                .toList();
    }

    @Transactional
    public void handleStripe(String payload, String signature) {
        StripeSignatures.verify(payload, signature, properties.getBilling().getStripeWebhookSecret(), Duration.ofMinutes(5));
        try {
            JsonNode event = objectMapper.readTree(payload);
            String eventId = event.path("id").asText();
            String type = event.path("type").asText();
            JsonNode object = event.path("data").path("object");
            String userPublicId = text(object, "metadata", "userPublicId");
            String planCode = text(object, "metadata", "planCode");
            String checkoutId = text(object, "metadata", "checkoutPublicId");
            if (userPublicId.isBlank() && object.has("client_reference_id")) {
                CheckoutSession session = checkouts.findByPublicId(object.path("client_reference_id").asText()).orElse(null);
                if (session != null) {
                    userPublicId = session.getUser().getPublicId();
                    planCode = session.getPlan().getCode();
                    checkoutId = session.getPublicId();
                }
            }
            apply("STRIPE", eventId, type, userPublicId, planCode, object.path("customer").asText(""),
                    object.path("subscription").asText(object.path("id").asText("")), checkoutId);
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw AppException.bad("STRIPE_EVENT", "The Stripe event could not be read.");
        }
    }

    @Transactional
    public void apply(String provider, String eventId, String type, String userPublicId, String planCode,
                      String customerId, String subscriptionId, String checkoutPublicId) {
        if (eventId == null || eventId.isBlank() || webhooks.existsByProviderAndEventId(provider, eventId)) {
            return;
        }
        WebhookEvent event = new WebhookEvent();
        event.setProvider(provider);
        event.setEventId(eventId);
        event.setEventType(type);
        event.setPayloadHash(Ids.sha256(provider + ":" + eventId + ":" + type));
        event.setProcessedAt(LocalDateTime.now(ZoneOffset.UTC));
        webhooks.saveAndFlush(event);
        if (userPublicId == null || userPublicId.isBlank()) {
            return;
        }
        UserAccount user = users.findByPublicId(userPublicId).orElse(null);
        if (user == null) {
            return;
        }
        if ("checkout.session.completed".equals(type) || "customer.subscription.updated".equals(type)) {
            activate(user, planCode, provider, customerId, subscriptionId, checkoutPublicId, eventId);
        } else if ("invoice.payment_failed".equals(type)) {
            subscriptions.findByUserIdOrderByCreatedAtDesc(user.getId()).stream().findFirst().ifPresent(sub -> sub.setStatus(SubscriptionStatus.PAST_DUE));
            audit.record(user, "SUBSCRIPTION_PAST_DUE", "SUBSCRIPTION", eventId, null, "Payment failed");
        } else if ("customer.subscription.deleted".equals(type)) {
            subscriptions.findByUserIdOrderByCreatedAtDesc(user.getId()).stream().findFirst().ifPresent(sub -> {
                sub.setStatus(SubscriptionStatus.CANCELED);
                sub.setCancelAtPeriodEnd(false);
            });
            audit.record(user, "SUBSCRIPTION_CANCELED", "SUBSCRIPTION", eventId, null, "Subscription ended");
        }
    }

    private void activate(UserAccount user, String planCode, String provider, String customerId, String subscriptionId,
                          String checkoutPublicId, String eventId) {
        Plan plan = plans.findByCode(planCode).orElse(null);
        if (plan == null) {
            return;
        }
        LocalDateTime start = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime end = "YEAR".equals(plan.getBillingInterval()) ? start.plusYears(1) : start.plusMonths(1);
        Subscription subscription = subscriptions.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(item -> item.getStatus() == SubscriptionStatus.ACTIVE || item.getStatus() == SubscriptionStatus.INCOMPLETE
                        || item.getStatus() == SubscriptionStatus.PAST_DUE || item.getStatus() == SubscriptionStatus.CANCEL_AT_PERIOD_END)
                .findFirst()
                .orElseGet(() -> {
                    Subscription created = new Subscription();
                    created.setPublicId(Ids.uuid());
                    created.setUser(user);
                    return created;
                });
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setProvider(provider);
        subscription.setProviderCustomerId(blankToNull(customerId));
        subscription.setProviderSubscriptionId(blankToNull(subscriptionId));
        subscription.setCurrentPeriodStart(start);
        subscription.setCurrentPeriodEnd(end);
        subscription.setCancelAtPeriodEnd(false);
        subscriptions.save(subscription);
        PaymentRecord payment = new PaymentRecord();
        payment.setPublicId(Ids.uuid());
        payment.setSubscription(subscription);
        payment.setUser(user);
        payment.setAmountCents(plan.getPriceCents());
        payment.setCurrency(plan.getCurrency());
        payment.setStatus("PAID");
        payment.setProvider(provider);
        payment.setProviderEventId(eventId);
        payment.setCreatedAt(start);
        payments.save(payment);
        if (checkoutPublicId != null) {
            checkouts.findByPublicId(checkoutPublicId).ifPresent(session -> {
                session.setStatus(CheckoutStatus.COMPLETED);
                session.setCompletedAt(start);
            });
        }
        audit.record(user, "SUBSCRIPTION_ACTIVATED", "SUBSCRIPTION", subscription.getPublicId(), null, plan.getCode() + " via " + provider);
    }

    private CheckoutSession ownedCheckout(UserAccount user, String checkoutId) {
        CheckoutSession session = checkouts.findByPublicId(checkoutId).orElseThrow(() -> AppException.notFound("Checkout not found."));
        if (!session.getUser().getId().equals(user.getId())) {
            throw AppException.notFound("Checkout not found.");
        }
        return session;
    }

    private void expireIfNeeded(UserAccount user) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        for (Subscription subscription : subscriptions.findByUserIdOrderByCreatedAtDesc(user.getId())) {
            if (subscription.getCurrentPeriodEnd() != null && subscription.getCurrentPeriodEnd().isBefore(now)
                    && (subscription.getStatus() == SubscriptionStatus.ACTIVE || subscription.getStatus() == SubscriptionStatus.CANCEL_AT_PERIOD_END
                    || subscription.getStatus() == SubscriptionStatus.PAST_DUE)) {
                subscription.setStatus(subscription.isCancelAtPeriodEnd() || subscription.getStatus() == SubscriptionStatus.CANCEL_AT_PERIOD_END
                        ? SubscriptionStatus.CANCELED : SubscriptionStatus.EXPIRED);
            }
        }
    }

    private Subscription activeSubscription(UserAccount user) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return subscriptions.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(item -> (item.getStatus() == SubscriptionStatus.ACTIVE || item.getStatus() == SubscriptionStatus.CANCEL_AT_PERIOD_END)
                        && item.getCurrentPeriodEnd() != null && item.getCurrentPeriodEnd().isAfter(now))
                .findFirst()
                .orElse(null);
    }

    private SubscriptionView toView(Subscription subscription) {
        return new SubscriptionView(subscription.getPublicId(), subscription.getPlan().getCode(), subscription.getStatus().name(),
                subscription.getProvider(), subscription.getCurrentPeriodEnd() == null ? null : subscription.getCurrentPeriodEnd().toString(),
                subscription.isCancelAtPeriodEnd());
    }

    private static String text(JsonNode object, String parent, String field) {
        return object.path(parent).path(field).asText("");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String form(Map<String, String> fields) {
        StringBuilder body = new StringBuilder();
        fields.forEach((key, value) -> {
            if (!body.isEmpty()) {
                body.append('&');
            }
            body.append(URLEncoder.encode(key, StandardCharsets.UTF_8));
            body.append('=');
            body.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });
        return body.toString();
    }

    public record PlanView(String code, String name, String interval, int priceCents, String currency) {}
    public record SubscriptionView(String id, String planCode, String status, String provider, String currentPeriodEnd, boolean cancelAtPeriodEnd) {}
    public record Entitlements(int freeAllowance, int freeRemaining, int freeUsed, boolean premiumActive, SubscriptionView subscription) {}
    public record PaymentView(String id, int amountCents, String currency, String status, String provider, String createdAt) {}
    public record CheckoutRequest(String planCode) {}
    public record ConfirmRequest(String checkoutId) {}
}
