package pl.commercelink.orders.rma;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Whether an order item is already claimed by an RMA that is still open. Merely having an RMAItem is too
 * broad: after a rejection the item must become claimable again, and RMAs completed through acceptance
 * are excluded by callers via the item's Returned/Replaced status. Shared by the marketplace importer and
 * the manual add-item path so both apply the same rule.
 */
@Component
@RequiredArgsConstructor
public class OpenRmaCoverage {

    private final RMAItemsRepository rmaItemsRepository;
    private final RMARepository rmaRepository;

    public boolean coveredByAnotherOpenRma(String storeId, String orderItemId, String exceptRmaId) {
        Set<String> rmaIds = new LinkedHashSet<>();
        for (RMAItem rmaItem : rmaItemsRepository.findByOrderItemId(orderItemId)) {
            if (!rmaItem.getRmaId().equals(exceptRmaId)) {
                rmaIds.add(rmaItem.getRmaId());
            }
        }
        for (String rmaId : rmaIds) {
            RMA rma = rmaRepository.findById(storeId, rmaId);
            if (rma != null && rma.getStatus() != RMAStatus.Rejected) {
                return true;
            }
        }
        return false;
    }

    /**
     * Which of the order items an open RMA of this store already claims, with that RMA's id, for the new RMA page.
     * Stricter about "open" than coveredByAnotherOpenRma: a Completed RMA releases its items too, because the repair
     * and returned-to-client paths leave the order item Delivered, so nothing else would ever free it for a new
     * report. coveredByAnotherOpenRma keeps its rule for add-item and the marketplace importer. An RMA without a
     * status counts as open.
     */
    public Map<String, String> claims(String storeId, Collection<String> orderItemIds) {
        if (orderItemIds.isEmpty()) {
            return Map.of();
        }
        Map<String, RMA> rmas = new HashMap<>();
        Map<String, String> claims = new LinkedHashMap<>();
        for (RMAItem rmaItem : rmaItemsRepository.findByOrderItemIds(orderItemIds)) {
            RMA rma = rmas.computeIfAbsent(rmaItem.getRmaId(), id -> rmaRepository.findById(storeId, id));
            if (rma != null && (rma.getStatus() == null || !rma.getStatus().isClosed())) {
                claims.putIfAbsent(rmaItem.getItemId(), rma.getRmaId());
            }
        }
        return claims;
    }
}
