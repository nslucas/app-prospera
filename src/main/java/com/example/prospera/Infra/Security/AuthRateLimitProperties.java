package com.example.prospera.Infra.Security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Component
@Validated
@ConfigurationProperties(prefix = "app.security.auth-rate-limit")
public class AuthRateLimitProperties {
    private boolean enabled = true;

    @Min(1)
    private long cacheMaxEntries = 100_000;

    @Valid
    private Limit loginIp = new Limit(30, Duration.ofMinutes(15));

    @Valid
    private Limit loginEmail = new Limit(5, Duration.ofMinutes(15));

    @Valid
    private Limit registerIp = new Limit(5, Duration.ofHours(1));

    @Valid
    private Limit registerEmail = new Limit(3, Duration.ofDays(1));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getCacheMaxEntries() {
        return cacheMaxEntries;
    }

    public void setCacheMaxEntries(long cacheMaxEntries) {
        this.cacheMaxEntries = cacheMaxEntries;
    }

    public Limit getLoginIp() {
        return loginIp;
    }

    public void setLoginIp(Limit loginIp) {
        this.loginIp = loginIp;
    }

    public Limit getLoginEmail() {
        return loginEmail;
    }

    public void setLoginEmail(Limit loginEmail) {
        this.loginEmail = loginEmail;
    }

    public Limit getRegisterIp() {
        return registerIp;
    }

    public void setRegisterIp(Limit registerIp) {
        this.registerIp = registerIp;
    }

    public Limit getRegisterEmail() {
        return registerEmail;
    }

    public void setRegisterEmail(Limit registerEmail) {
        this.registerEmail = registerEmail;
    }

    public static class Limit {
        @Min(1)
        private long capacity;

        @NotNull
        private Duration period;

        public Limit() {
        }

        public Limit(long capacity, Duration period) {
            this.capacity = capacity;
            this.period = period;
        }

        public long getCapacity() {
            return capacity;
        }

        public void setCapacity(long capacity) {
            this.capacity = capacity;
        }

        public Duration getPeriod() {
            return period;
        }

        public void setPeriod(Duration period) {
            this.period = period;
        }

        @AssertTrue(message = "rate limit period must be greater than zero")
        public boolean isPeriodPositive() {
            return period != null && !period.isZero() && !period.isNegative();
        }
    }
}
