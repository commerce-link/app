package pl.commercelink.inventory;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * {@code requestedAtMillis} is the cache's clock at the moment of the request. A message without it (one sent by
 * hand) always rebuilds.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class StoreInventoryRefreshRequest {

    private String storeId;
    private Long requestedAtMillis;
}
