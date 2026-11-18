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
        Instant oldest = now.minus(window);
        Deque<Instant> times = requests.computeIfAbsent(key == null ? "?" : key, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst().isBefore(oldest)) {
                times.pollFirst();
            }
            if (times.size() >= limit) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }
}
