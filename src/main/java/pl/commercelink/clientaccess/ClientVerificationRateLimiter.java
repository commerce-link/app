package pl.commercelink.clientaccess;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

@Component
public class ClientVerificationRateLimiter {

    private final Clock clock;
    private final int maxPerIpPerHour;
    private final Map<String, Deque<Instant>> perIp = new HashMap<>();

    @Autowired
    public ClientVerificationRateLimiter(@Value("${app.client-verification.max-per-ip-hourly}") int maxPerIpPerHour) {
        this(Clock.systemUTC(), maxPerIpPerHour);
    }

    ClientVerificationRateLimiter(Clock clock, int maxPerIpPerHour) {
        this.clock = clock;
        this.maxPerIpPerHour = maxPerIpPerHour;
    }

    public synchronized boolean tryAcquire(String ip) {
        Instant now = clock.instant();
        Instant hourCutoff = now.minus(1, ChronoUnit.HOURS);
        perIp.values().forEach(window -> prune(window, hourCutoff));
        perIp.values().removeIf(Deque::isEmpty);
        Deque<Instant> ipWindow = perIp.computeIfAbsent(ip, key -> new ArrayDeque<>());
        if (ipWindow.size() >= maxPerIpPerHour) {
            return false;
        }
        ipWindow.addLast(now);
        return true;
    }

    private static void prune(Deque<Instant> window, Instant cutoff) {
        while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
            window.removeFirst();
        }
    }
}
