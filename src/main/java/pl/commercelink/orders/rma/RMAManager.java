package pl.commercelink.orders.rma;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Component
public class RMAManager {

    @Autowired
    private RMARepository rmaRepository;

    @Autowired
    private RMAItemsRepository rmaItemsRepository;

    @Autowired
    private RMALifecycle rmaLifecycle;

    public void markItemsAsReceived(String storeId, String rmaId, List<String> selectedRmaItemIds) {
        RMA rma = rmaRepository.findById(storeId, rmaId);
        List<RMAItem> rmaItems = rmaItemsRepository.findByRmaId(rmaId);

        List<RMAItem> qualifiedRmaItems = rmaItems.stream()
                .filter(item -> selectedRmaItemIds.contains(item.getRmaItemId()))
                .filter(item -> !item.hasOneOfTheStatuses(RMAItemStatus.MovedToWarehouse))
                .collect(Collectors.toList());

        if (qualifiedRmaItems.isEmpty()) {
            return;
        }

        qualifiedRmaItems.forEach(RMAItem::markAsReceived);

        boolean hasAllItemsReceived = rmaItems.stream().allMatch(i -> i.getStatus() == RMAItemStatus.Received);
        if (hasAllItemsReceived) {
            rma.markAsItemsReceived();
        }

        rmaItemsRepository.batchSave(qualifiedRmaItems);
        rmaLifecycle.update(rma, qualifiedRmaItems);
    }

    public OperationResult replaceSelectedItems(String storeId, String rmaId, List<String> selectedRmaItemIds) {
        return processReceivedItems(storeId, rmaId, selectedRmaItemIds, RMAItem::markAsMovedToWarehouseAndReplaced, false);
    }

    public OperationResult returnSelectedItems(String storeId, String rmaId, List<String> selectedRmaItemIds) {
        return processReceivedItems(storeId, rmaId, selectedRmaItemIds, RMAItem::markAsMovedToWarehouseAndReturned, false);
    }

    public OperationResult markItemsAsReturnedToClient(String storeId, String rmaId, List<String> selectedRmaItemIds) {
        return processReceivedItems(storeId, rmaId, selectedRmaItemIds, RMAItem::markAsReturnedToClient, true); // Not sending email
    }

    public OperationResult markItemsAsSentToDistributor(String storeId, String rmaId, List<String> selectedRmaItemIds) {
        return processReceivedItems(storeId, rmaId, selectedRmaItemIds, RMAItem::markAsSendToRepair, false);
    }

    private OperationResult processReceivedItems(String storeId, String rmaId, List<String> selectedRmaItemIds, Consumer<RMAItem> action, boolean emailSilent) {
        RMA rma = rmaRepository.findById(storeId, rmaId);
        List<RMAItem> rmaItems = rmaItemsRepository.findByRmaId(rmaId);

        List<RMAItem> qualifiedRmaItems = rmaItems.stream()
                .filter(item -> selectedRmaItemIds.contains(item.getRmaItemId()))
                .filter(item -> item.hasOneOfTheStatuses(RMAItemStatus.Received))
                .collect(Collectors.toList());

        if (qualifiedRmaItems.isEmpty()) {
            return OperationResult.failure();
        }

        qualifiedRmaItems.forEach(action);

        rmaItemsRepository.batchSave(qualifiedRmaItems);
        rmaLifecycle.update(rma, qualifiedRmaItems, emailSilent);

        return OperationResult.success(rma, qualifiedRmaItems);
    }

    public static class OperationResult {
        private final boolean success;
        private final RMA rma;
        private final List<RMAItem> rmaItems;

        public static OperationResult success(RMA rma, List<RMAItem> rmaItems) {
            return new OperationResult(true, rma, rmaItems);
        }

        public static OperationResult failure() {
            return new OperationResult(false, null, null);
        }

        private OperationResult(boolean success, RMA rma, List<RMAItem> rmaItems) {
            this.success = success;
            this.rma = rma;
            this.rmaItems = rmaItems;
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isFailure() {
            return !success;
        }

        public RMA getRma() {
            return rma;
        }

        public List<RMAItem> getRmaItems() {
            return rmaItems;
        }
    }

}
