package pl.commercelink.orders.rma;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Whether an order item is already claimed by an RMA that is still open. Merely having an RMAItem is too
 * broad: after a rejection the item must become claimable again, and RMAs completed through acceptance
 * are excluded by callers via the item's Returned/Replaced status. Shared by the marketplace importer and
 * the manual add-item path so both apply the same rule.
 */
@Component
public class OpenRmaCoverage {

    @Autowired
    private RMAItemsRepository rmaItemsRepository;

    @Autowired
    private RMARepository rmaRepository;

    public boolean coversOrderItem(String storeId, String orderItemId, String ignoringRmaId) {
        Set<String> rmaIds = new LinkedHashSet<>();
        for (RMAItem rmaItem : rmaItemsRepository.findByOrderItemId(orderItemId)) {
            if (!rmaItem.getRmaId().equals(ignoringRmaId)) {
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
}
