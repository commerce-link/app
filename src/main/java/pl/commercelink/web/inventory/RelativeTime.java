package pl.commercelink.web.inventory;

import java.time.Duration;
import java.time.LocalDateTime;

public record RelativeTime(String messageKey, long amount) {

    public static RelativeTime between(LocalDateTime then, LocalDateTime now) {
        Duration age = Duration.between(then, now);
        long minutes = Math.max(1, age.toMinutes());
        if (minutes < 60) {
            return new RelativeTime("inventory.time.minutes", minutes);
        }
        long hours = age.toHours();
        if (hours < 24) {
            return new RelativeTime("inventory.time.hours", hours);
        }
        if (hours < 48) {
            return new RelativeTime("inventory.time.yesterday", 1);
        }
        return new RelativeTime("inventory.time.days", age.toDays());
    }

    public Object[] args() {
        return new Object[]{amount};
    }
}
