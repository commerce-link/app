package pl.commercelink.inventory.supplier;

import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.api.InventoryItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
class DataCleanup {

    List<InventoryItem> run(List<InventoryItem> inventoryItems) {
        List<InventoryItem> data = inventoryItems.stream()
                .filter(InventoryItem::isSellable)
                .collect(Collectors.toList());
        return filterOutDuplicatedItemsChoosingTheOneWithHigherQty(data);
    }

    private List<InventoryItem> filterOutDuplicatedItemsChoosingTheOneWithHigherQty(List<InventoryItem> inventoryItems) {
        Map<String, InventoryItem> uniqueItems = new HashMap<>();
        for (InventoryItem item : inventoryItems) {
            String uid = item.uuid();
            InventoryItem existing = uniqueItems.get(uid);
            if (existing == null || existing.qty() < item.qty()) {
                uniqueItems.put(uid, item);
            }
        }
        return new ArrayList<>(uniqueItems.values());
    }
}
