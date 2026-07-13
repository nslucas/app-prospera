package com.example.prospera.Infra.Security;

import com.example.prospera.Exceptions.RateLimitExceededException;
import com.github.benmanes.caffeine.cache.Ticker;
import io.github.bucket4j.TimeMeter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthRateLimitServiceTest {

    @Test
    void sixthFailedLoginForEmailIsBlockedAcrossDifferentIps() {
        MutableTime time = new MutableTime();
        AuthRateLimitService service = service(defaultProperties(), time);
        AuthRateLimitService.LoginAttempt lastAttempt = null;

        for (int i = 0; i < 5; i++) {
            lastAttempt = service.acquireLogin("10.0.0." + i, " User@Example.COM ");
        }

        assertThrows(RateLimitExceededException.class,
                () -> service.acquireLogin("10.0.0.99", "user@example.com"));

        service.loginSucceeded(lastAttempt);
        assertDoesNotThrow(() -> service.acquireLogin("10.0.0.100", "USER@example.com"));
    }

    @Test
    void thirtyFirstLoginFromOneIpIsBlockedAcrossDifferentEmails() {
        MutableTime time = new MutableTime();
        AuthRateLimitService service = service(defaultProperties(), time);

        for (int i = 0; i < 30; i++) {
            service.acquireLogin("203.0.113.10", "user" + i + "@example.com");
        }

        assertThrows(RateLimitExceededException.class,
                () -> service.acquireLogin("203.0.113.10", "another@example.com"));
    }

    @Test
    void registrationEnforcesIpAndEmailBucketsIndependently() {
        MutableTime time = new MutableTime();
        AuthRateLimitService service = service(defaultProperties(), time);

        for (int i = 0; i < 5; i++) {
            service.acquireRegistration("198.51.100.10", "ip-user" + i + "@example.com");
        }
        assertThrows(RateLimitExceededException.class,
                () -> service.acquireRegistration("198.51.100.10", "sixth@example.com"));

        for (int i = 0; i < 3; i++) {
            service.acquireRegistration("198.51.100." + (20 + i), " Same@Example.com ");
        }
        assertThrows(RateLimitExceededException.class,
                () -> service.acquireRegistration("198.51.100.30", "same@example.com"));
    }

    @Test
    void greedyRefillAllowsOneAttemptAfterProportionalTime() {
        AuthRateLimitProperties properties = defaultProperties();
        properties.setLoginIp(new AuthRateLimitProperties.Limit(2, Duration.ofSeconds(10)));
        MutableTime time = new MutableTime();
        AuthRateLimitService service = service(properties, time);

        service.acquireLogin("192.0.2.10", "first@example.com");
        service.acquireLogin("192.0.2.10", "second@example.com");
        RateLimitExceededException exception = assertThrows(RateLimitExceededException.class,
                () -> service.acquireLogin("192.0.2.10", "third@example.com"));
        assertEquals(5, exception.getRetryAfterSeconds());

        time.advance(Duration.ofSeconds(5));
        assertDoesNotThrow(() -> service.acquireLogin("192.0.2.10", "third@example.com"));
    }

    @Test
    void expiredCacheEntriesAreRemovedAndCacheIsBounded() {
        AuthRateLimitProperties properties = defaultProperties();
        properties.setCacheMaxEntries(2);
        properties.setLoginIp(new AuthRateLimitProperties.Limit(30, Duration.ofMinutes(1)));
        MutableTime time = new MutableTime();
        AuthRateLimitService service = service(properties, time);

        service.acquireLogin("192.0.2.1", "one@example.com");
        service.acquireLogin("192.0.2.2", "two@example.com");
        service.acquireLogin("192.0.2.3", "three@example.com");
        service.cleanUpCaches();
        assertEquals(2, service.loginIpBucketCount());

        time.advance(Duration.ofMinutes(3));
        service.cleanUpCaches();
        assertEquals(0, service.loginIpBucketCount());
    }

    @Test
    void disabledLimiterAllowsRequestsWithoutCreatingBuckets() {
        AuthRateLimitProperties properties = defaultProperties();
        properties.setEnabled(false);
        MutableTime time = new MutableTime();
        AuthRateLimitService service = service(properties, time);

        for (int i = 0; i < 100; i++) {
            service.acquireLogin("203.0.113.10", "user@example.com");
            service.acquireRegistration("203.0.113.10", "user@example.com");
        }

        assertEquals(0, service.loginIpBucketCount());
    }

    private AuthRateLimitService service(AuthRateLimitProperties properties, MutableTime time) {
        return new AuthRateLimitService(properties, time, time);
    }

    private AuthRateLimitProperties defaultProperties() {
        return new AuthRateLimitProperties();
    }

    private static class MutableTime implements TimeMeter, Ticker {
        private final AtomicLong nanos = new AtomicLong();

        @Override
        public long currentTimeNanos() {
            return nanos.get();
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }

        @Override
        public long read() {
            return nanos.get();
        }

        void advance(Duration duration) {
            nanos.addAndGet(duration.toNanos());
        }
    }
}
