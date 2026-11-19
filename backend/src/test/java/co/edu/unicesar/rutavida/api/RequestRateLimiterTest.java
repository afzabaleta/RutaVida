package co.edu.unicesar.rutavida.api;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RequestRateLimiter with a clock moved by hand: 3 requests per key in 10 minutes. */
class RequestRateLimiterTest {

    /** A clock that the test moves forward. */
    private static final class HandClock extends Clock {

        private Instant now = Instant.parse("2026-11-19T14:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final HandClock clock = new HandClock();
    private final RequestRateLimiter limiter = new RequestRateLimiter(3, Duration.ofMinutes(10), clock);

    @Test
    void eachKeyHasItsOwnLimitInASlidingWindow() {
        assertTrue(limiter.tryAcquire("10.0.0.1"));
        assertTrue(limiter.tryAcquire("10.0.0.1"));
        assertTrue(limiter.tryAcquire("10.0.0.1"));
        assertFalse(limiter.tryAcquire("10.0.0.1"), "the fourth in the window");
        assertTrue(limiter.tryAcquire("10.0.0.2"), "another address is not affected");

        clock.advance(Duration.ofMinutes(10).plusSeconds(1));
        assertTrue(limiter.tryAcquire("10.0.0.1"), "the old requests left the window");
    }

    @Test
    void theLoginCountsOnlyWhatItRecordsAndASuccessClearsIt() {
        assertFalse(limiter.isLimited("ip|ana"));
        assertFalse(limiter.isLimited("ip|ana"), "asking counts nothing");

        limiter.record("ip|ana");
        limiter.record("ip|ana");
        assertFalse(limiter.isLimited("ip|ana"));
        limiter.record("ip|ana");
        assertTrue(limiter.isLimited("ip|ana"), "three failures");

        limiter.reset("ip|ana");
        assertFalse(limiter.isLimited("ip|ana"));

        limiter.record(null);
        limiter.record(null);
        limiter.record(null);
        assertTrue(limiter.isLimited(null), "a missing key is one key, not an error");
    }

    @Test
    void theLimitMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new RequestRateLimiter(0, Duration.ofMinutes(1), clock));
    }
}
