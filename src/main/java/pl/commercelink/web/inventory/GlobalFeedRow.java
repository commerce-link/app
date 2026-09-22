package pl.commercelink.web.inventory;

import java.time.LocalDateTime;

public record GlobalFeedRow(String supplier, LocalDateTime loadedAt, RelativeTime age) {
}
