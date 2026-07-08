package pl.commercelink.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.demo.registration.enabled", havingValue = "true")
public class DemoRegistrationRateLimiter {

    private final Clock clock;
    private final int maxPerIpPerHour;
    private final int maxPerDay;
    private final Map<String, Deque<Instant>> perIp = new HashMap<>();
    private final Deque<Instant> global = new ArrayDeque<>();

    @Autowired
    public DemoRegistrationRateLimiter(@Value("${app.demo.registration.max-per-ip-hourly}") int maxPerIpPerHour,
                                       @Value("${app.demo.registration.max-daily}") int maxPerDay) {
        this(Clock.systemUTC(), maxPerIpPerHour, maxPerDay);
    }

    DemoRegistrationRateLimiter(Clock clock, int maxPerIpPerHour, int maxPerDay) {
        this.clock = clock;
        this.maxPerIpPerHour = maxPerIpPerHour;
        this.maxPerDay = maxPerDay;
    }

    public synchronized boolean tryAcquire(String ip) {
        Instant now = clock.instant();
        Deque<Instant> ipWindow = perIp.computeIfAbsent(ip, key -> new ArrayDeque<>());
        prune(ipWindow, now.minus(1, ChronoUnit.HOURS));
        prune(global, now.minus(1, ChronoUnit.DAYS));
        perIp.values().removeIf(Deque::isEmpty);
        if (ipWindow.size() >= maxPerIpPerHour || global.size() >= maxPerDay) {
            return false;
        }
        ipWindow.addLast(now);
        perIp.putIfAbsent(ip, ipWindow);
        global.addLast(now);
        return true;
    }

    private static void prune(Deque<Instant> window, Instant cutoff) {
        while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
            window.removeFirst();
        }
    }
}
