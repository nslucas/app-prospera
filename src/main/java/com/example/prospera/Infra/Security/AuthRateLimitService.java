package com.example.prospera.Infra.Security;

import com.example.prospera.Exceptions.RateLimitExceededException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
public class AuthRateLimitService {
    private final AuthRateLimitProperties properties;
    private final TimeMeter timeMeter;
    private final Ticker ticker;
    private final Cache<String, Bucket> loginIpBuckets;
    private final Cache<String, Bucket> loginEmailBuckets;
    private final Cache<String, Bucket> registerIpBuckets;
    private final Cache<String, Bucket> registerEmailBuckets;

    @Autowired
    public AuthRateLimitService(AuthRateLimitProperties properties) {
        this(properties, TimeMeter.SYSTEM_NANOTIME, Ticker.systemTicker());
    }

    AuthRateLimitService(AuthRateLimitProperties properties, TimeMeter timeMeter, Ticker ticker) {
        this.properties = properties;
        this.timeMeter = timeMeter;
        this.ticker = ticker;
        this.loginIpBuckets = newCache(properties.getLoginIp());
        this.loginEmailBuckets = newCache(properties.getLoginEmail());
        this.registerIpBuckets = newCache(properties.getRegisterIp());
        this.registerEmailBuckets = newCache(properties.getRegisterEmail());
    }

    public LoginAttempt acquireLogin(String clientIp, String email) {
        if (!properties.isEnabled()) {
            return LoginAttempt.disabled();
        }

        consume(loginIpBuckets, normalizeIp(clientIp), properties.getLoginIp());
        String emailKey = hashEmail(email);
        consume(loginEmailBuckets, emailKey, properties.getLoginEmail());
        return new LoginAttempt(emailKey, true);
    }

    public void loginSucceeded(LoginAttempt attempt) {
        if (attempt.enabled()) {
            loginEmailBuckets.invalidate(attempt.emailKey());
        }
    }

    public void loginSystemFailed(LoginAttempt attempt) {
        if (!attempt.enabled()) {
            return;
        }
        Bucket bucket = loginEmailBuckets.getIfPresent(attempt.emailKey());
        if (bucket != null) {
            bucket.addTokens(1);
        }
    }

    public void acquireRegistration(String clientIp, String email) {
        if (!properties.isEnabled()) {
            return;
        }
        consume(registerIpBuckets, normalizeIp(clientIp), properties.getRegisterIp());
        consume(registerEmailBuckets, hashEmail(email), properties.getRegisterEmail());
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    long loginIpBucketCount() {
        return loginIpBuckets.estimatedSize();
    }

    void cleanUpCaches() {
        loginIpBuckets.cleanUp();
        loginEmailBuckets.cleanUp();
        registerIpBuckets.cleanUp();
        registerEmailBuckets.cleanUp();
    }

    private Cache<String, Bucket> newCache(AuthRateLimitProperties.Limit limit) {
        Duration expiry = limit.getPeriod().multipliedBy(2);
        return Caffeine.newBuilder()
                .maximumSize(properties.getCacheMaxEntries())
                .expireAfterAccess(expiry)
                .ticker(ticker)
                .build();
    }

    private void consume(Cache<String, Bucket> cache, String key, AuthRateLimitProperties.Limit limit) {
        Bucket bucket = cache.get(key, ignored -> newBucket(limit));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            throw new RateLimitExceededException(retryAfterSeconds(probe.getNanosToWaitForRefill()));
        }
    }

    private Bucket newBucket(AuthRateLimitProperties.Limit limit) {
        return Bucket.builder()
                .withCustomTimePrecision(timeMeter)
                .addLimit(bandwidth -> bandwidth.capacity(limit.getCapacity())
                        .refillGreedy(limit.getCapacity(), limit.getPeriod()))
                .build();
    }

    private String hashEmail(String email) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalizeEmail(email).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String normalizeIp(String clientIp) {
        return clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();
    }

    private long retryAfterSeconds(long nanos) {
        long seconds = TimeUnit.NANOSECONDS.toSeconds(nanos);
        if (nanos % TimeUnit.SECONDS.toNanos(1) != 0) {
            seconds++;
        }
        return Math.max(1, seconds);
    }

    public record LoginAttempt(String emailKey, boolean enabled) {
        private static LoginAttempt disabled() {
            return new LoginAttempt("", false);
        }
    }
}
