package menupick.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 키별 고정 윈도 카운터. 상태는 불변 레코드를 compute로 교체해 락 없이 원자적으로 갱신합니다.
 * 만료된 키는 {@link #evictExpired()}로 정리해 메모리가 계속 늘지 않게 합니다.
 */
public final class FixedWindowRateLimiter {
    private final Duration window;
    private final Clock clock;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(Duration window, Clock clock) {
        this.window = window;
        this.clock = clock;
    }

    /** 요청 1회를 기록하고, 윈도 안에서 limit 이하이면 true. */
    public boolean tryAcquire(String key, int limit) {
        Instant now = clock.instant();
        Window updated = windows.compute(key, (k, current) ->
                current == null || current.isExpired(now, window) ? new Window(now, 1) : current.increment());
        return updated.count() <= limit;
    }

    /** 현재 윈도에서의 횟수(만료됐으면 0). */
    public int count(String key) {
        Window current = windows.get(key);
        return current == null || current.isExpired(clock.instant(), window) ? 0 : current.count();
    }

    /** 실패 횟수를 1 늘립니다(윈도가 만료됐으면 새로 시작). */
    public void record(String key) {
        tryAcquire(key, Integer.MAX_VALUE);
    }

    public void reset(String key) {
        windows.remove(key);
    }

    public Duration window() {
        return window;
    }

    public void evictExpired() {
        Instant now = clock.instant();
        windows.values().removeIf(w -> w.isExpired(now, window));
    }

    int size() {
        return windows.size();
    }

    private record Window(Instant startedAt, int count) {
        boolean isExpired(Instant now, Duration window) {
            return now.isAfter(startedAt.plus(window));
        }

        Window increment() {
            return new Window(startedAt, count == Integer.MAX_VALUE ? count : count + 1);
        }
    }
}
