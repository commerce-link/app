package pl.commercelink.web.inventory;

import pl.commercelink.stores.ConnectionMode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

public record SourceRow(String identity, String label, ConnectionMode mode, int products,
                        LocalDateTime feedLastModified, RelativeTime feedAge, boolean needsAttention) {

    /** Identities may carry a legacy colon or an operator's spaces, so the link is built, not concatenated. */
    public String connectionPath() {
        return URLEncoder.encode(identity, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
