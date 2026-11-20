package com.firstrep.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "firstrep")
@Getter
@Setter
public class AppProperties {
    private int freeAllowance = 2;
    private String appBaseUrl = "http://localhost:5173";
    private String corsOrigins = "http://localhost:5173";
    private Security security = new Security();
    private Billing billing = new Billing();
    private Ai ai = new Ai();
    private Storage storage = new Storage();
    private Mail mail = new Mail();
    private Dev dev = new Dev();
    private Seed seed = new Seed();
    private Outbox outbox = new Outbox();
    private Worker worker = new Worker();

    @Getter @Setter
    public static class Security {
        private String jwtSecret;
        private Duration accessTtl = Duration.ofMinutes(15);
        private Duration refreshTtl = Duration.ofDays(7);
        private boolean cookieSecure;
        private int authRateLimit = 30;
    }

    @Getter @Setter
    public static class Billing {
        private String mode = "simulator";
        private String stripeSecretKey = "";
        private String stripeWebhookSecret = "";
        private String stripeApiBase = "https://api.stripe.com";
    }

    @Getter @Setter
    public static class Ai {
        private String provider = "mock";
        private String openaiApiKey = "";
        private String openaiBaseUrl = "https://api.openai.com/v1";
        private String model = "";
        private String promptVersion = "rubric-v1";
        private Duration timeout = Duration.ofSeconds(30);
        private int maxEvidenceChars = 12000;
    }

    @Getter @Setter
    public static class Storage {
        private String mode = "s3";
        private String endpoint = "http://localhost:9000";
        private String accessKey = "firstrep";
        private String secretKey = "firstrep-secret";
        private String bucket = "firstrep-private";
        private String region = "us-east-1";
    }

    @Getter @Setter
    public static class Mail {
        private String from = "noreply@firstrep.local";
    }

    @Getter @Setter
    public static class Dev {
        private boolean exposeEmailTokens;
    }

    @Getter @Setter
    public static class Seed {
        private boolean enabled;
    }

    @Getter @Setter
    public static class Outbox {
        private boolean publish = true;
    }

    @Getter @Setter
    public static class Worker {
        private boolean consume;
        private int maxAttempts = 3;
    }

    public boolean simulatorBilling() {
        return "simulator".equalsIgnoreCase(billing.mode);
    }
}
