package co.edu.unicesar.rutavida.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * At most {@code limit} requests per key in a sliding window. The panic button (POST /api/panic)
 * is the only public route that writes, so it is limited per network address, besides the limit
 * per device of PanicService (RN-13): the device id comes from the browser and can be changed.
 * The login uses it another way: it only {@link #record}s the failed attempts and asks
 * {@link #isLimited} before trying, so a person who types well is never stopped.
 *
 * <p>Kept in memory: a restart of the server forgets it, which is acceptable for this limit.
 */
public final class RequestRateLimiter {

    private final int limit;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> requests = new ConcurrentHashMap<>();

    public RequestRateLimiter(int limit, Duration window, Clock clock) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        this.limit = limit;
        this.window = Objects.requireNonNull(window, "window");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Counts one request of {@code key}; false if it already made {@code limit} in the window. */
    public boolean tryAcquire(String key) {
        Instant now = clock.instant();
        Deque<Instant> times = timesOf(key);
        synchronized (times) {
            dropOld(times, now);
            if (times.size() >= limit) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }

    /** True if {@code key} already has {@code limit} requests in the window; counts nothing. */
    public boolean isLimited(String key) {
        Deque<Instant> times = timesOf(key);
        synchronized (times) {
            dropOld(times, clock.instant());
            return times.size() >= limit;
        }
    }

    /** Counts one request of {@code key} without asking whether it is allowed. */
    public void record(String key) {
        Instant now = clock.instant();
        Deque<Instant> times = timesOf(key);
        synchronized (times) {
            dropOld(times, now);
            times.addLast(now);
        }
    }

    /** Forgets every request of {@code key} (after a successful login). */
    public void reset(String key) {
        requests.remove(key == null ? "?" : key);
    }

    private Deque<Instant> timesOf(String key) {
        return requests.computeIfAbsent(key == null ? "?" : key, k -> new ArrayDeque<>());
    }

    private void dropOld(Deque<Instant> times, Instant now) {
        Instant oldest = now.minus(window);
        while (!times.isEmpty() && times.peekFirst().isBefore(oldest)) {
            times.pollFirst();
        }
    }
}
