package pl.commercelink.web.inventory;

import pl.commercelink.stores.ConnectionMode;

import java.time.LocalDateTime;

public record SourceRow(String identity, String label, ConnectionMode mode, int products,
                        LocalDateTime feedLastModified, RelativeTime feedAge, boolean needsAttention) {
}
