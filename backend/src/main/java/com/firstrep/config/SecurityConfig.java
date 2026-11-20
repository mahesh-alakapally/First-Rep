package com.firstrep.config;

import com.firstrep.common.Ids;
import com.firstrep.identity.UserAccount;
import com.firstrep.identity.UserAccountRepository;
import com.firstrep.common.Kinds.AccountStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    public static final String ACCESS = "fr_access";
    public static final String REFRESH = "fr_refresh";
    public static final String CSRF = "fr_csrf";

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecretKey jwtKey(AppProperties properties) {
        byte[] bytes = properties.getSecurity().getJwtSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    @Bean
    TokenService tokenService(SecretKey key, AppProperties properties) {
        return new TokenService(key, properties);
    }

    @Bean
    CookieWriter cookieWriter(AppProperties properties) {
        return new CookieWriter(properties);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AppProperties properties, TokenService tokens,
                                            CookieWriter cookies, UserAccountRepository users) throws Exception {
        var rate = new RateLimitFilter(properties);
        var jwt = new JwtFilter(tokens, users);
        var csrf = new CsrfFilter();
        http.csrf(csrfSpec -> csrfSpec.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> write(res, 401, "AUTH_REQUIRED", "Sign in required."))
                        .accessDeniedHandler((req, res, ex) -> write(res, 403, "FORBIDDEN", "You do not have access to that.")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/projects", "/api/v1/projects/*", "/api/v1/skills", "/api/v1/companies/public", "/api/v1/billing/plans").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/certificates/*/verify").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/pilot-interest", "/api/v1/webhooks/stripe").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(rate, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(csrf, JwtFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(properties.getCorsOrigins().split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    static void write(HttpServletResponse res, int status, String code, String detail) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + HttpStatus.valueOf(status).getReasonPhrase()
                + "\",\"status\":" + status + ",\"detail\":\"" + detail + "\",\"code\":\"" + code + "\"}");
    }

    public static final class TokenService {
        private final SecretKey key;
        private final AppProperties properties;

        TokenService(SecretKey key, AppProperties properties) {
            this.key = key;
            this.properties = properties;
        }

        public String access(String publicId) {
            Instant now = Instant.now();
            return Jwts.builder()
                    .subject(publicId)
                    .issuedAt(Date.from(now))
                    .expiration(Date.from(now.plus(properties.getSecurity().getAccessTtl())))
                    .signWith(key)
                    .compact();
        }

        public String subject(String token) {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return claims.getSubject();
        }
    }

    public static final class CookieWriter {
        private final AppProperties properties;

        CookieWriter(AppProperties properties) {
            this.properties = properties;
        }

        public void access(HttpServletResponse res, String token) {
            set(res, ACCESS, token, properties.getSecurity().getAccessTtl().getSeconds(), "/", true);
        }

        public void refresh(HttpServletResponse res, String token) {
            set(res, REFRESH, token, properties.getSecurity().getRefreshTtl().getSeconds(), "/api/v1/auth", true);
        }

        public String ensureCsrf(HttpServletRequest req, HttpServletResponse res) {
            String current = read(req, CSRF);
            if (current != null && !current.isBlank()) {
                return current;
            }
            String token = Ids.randomToken();
            set(res, CSRF, token, properties.getSecurity().getRefreshTtl().getSeconds(), "/", false);
            return token;
        }

        public void clearAuth(HttpServletResponse res) {
            set(res, ACCESS, "", 0, "/", true);
            set(res, REFRESH, "", 0, "/api/v1/auth", true);
        }

        private void set(HttpServletResponse res, String name, String value, long maxAge, String path, boolean httpOnly) {
            ResponseCookie cookie = ResponseCookie.from(name, value)
                    .httpOnly(httpOnly)
                    .secure(properties.getSecurity().isCookieSecure())
                    .sameSite("Lax")
                    .path(path)
                    .maxAge(maxAge)
                    .build();
            res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        }
    }

    public static String readCookie(HttpServletRequest request, String name) {
        return read(request, name);
    }

    private static String read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    static final class RateLimitFilter extends OncePerRequestFilter {
        private final int limit;
        private final Map<String, ArrayDeque<Long>> hits = new ConcurrentHashMap<>();

        RateLimitFilter(AppProperties properties) {
            this.limit = properties.getSecurity().getAuthRateLimit();
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String path = request.getRequestURI();
            boolean limited = "POST".equals(request.getMethod()) && (path.endsWith("/auth/login")
                    || path.endsWith("/auth/register") || path.endsWith("/auth/forgot-password"));
            if (limited && !allow(request.getRemoteAddr())) {
                write(response, 429, "RATE_LIMITED", "Too many attempts. Wait a minute and try again.");
                return;
            }
            chain.doFilter(request, response);
        }

        private boolean allow(String key) {
            long now = System.currentTimeMillis();
            ArrayDeque<Long> queue = hits.computeIfAbsent(key, ignored -> new ArrayDeque<>());
            synchronized (queue) {
                while (!queue.isEmpty() && now - queue.peekFirst() > 60_000) {
                    queue.removeFirst();
                }
                if (queue.size() >= limit) {
                    return false;
                }
                queue.addLast(now);
                return true;
            }
        }
    }

    static final class JwtFilter extends OncePerRequestFilter {
        private final TokenService tokens;
        private final UserAccountRepository users;

        JwtFilter(TokenService tokens, UserAccountRepository users) {
            this.tokens = tokens;
            this.users = users;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String token = read(request, ACCESS);
            if (token != null && !token.isBlank()) {
                try {
                    String subject = tokens.subject(token);
                    UserAccount user = users.findByPublicId(subject).orElse(null);
                    if (user != null && user.getAccountStatus() == AccountStatus.ACTIVE) {
                        List<SimpleGrantedAuthority> authorities = users.platformPermissions(user.getId()).stream()
                                .map(SimpleGrantedAuthority::new)
                                .toList();
                        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                new AuthPrincipal(user.getId(), user.getPublicId(), user.getEmail()), null, authorities);
                        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                } catch (Exception ignored) {
                    org.springframework.security.core.context.SecurityContextHolder.clearContext();
                }
            }
            try {
                chain.doFilter(request, response);
            } finally {
                org.springframework.security.core.context.SecurityContextHolder.clearContext();
            }
        }
    }

    static final class CsrfFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String method = request.getMethod();
            if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)
                    || request.getRequestURI().startsWith("/api/v1/webhooks/")) {
                chain.doFilter(request, response);
                return;
            }
            String cookie = read(request, CSRF);
            String header = request.getHeader("X-CSRF-Token");
            if (cookie == null || header == null || !MessageDigest.isEqual(cookie.getBytes(StandardCharsets.UTF_8), header.getBytes(StandardCharsets.UTF_8))) {
                write(response, 403, "CSRF", "Refresh the page and try again.");
                return;
            }
            chain.doFilter(request, response);
        }
    }

    public record AuthPrincipal(Long id, String publicId, String email) {}
}
