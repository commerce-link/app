package pl.commercelink.orders.rma;

import java.util.*;
import java.util.stream.Collectors;

public class RMAItemsForm {
    private List<RMAItem> rmaItems = new ArrayList<>();

    public RMAItemsForm() {}

    public RMAItemsForm(List<RMAItem> rmaItems) {
        this.rmaItems = rmaItems;
    }

    public List<RMAItem> getRmaItems() {
        return rmaItems;
    }

    public void setRmaItems(List<RMAItem> rmaItems) {
        this.rmaItems = rmaItems;
    }

    public List<RMAItem> getSelectedRMAItems() {
        return rmaItems.stream().filter(RMAItem::isSelected).toList();
    }

    public List<String> getSelectedRMAItemIds() {
        return this.rmaItems.stream()
                .filter(RMAItem::isSelected)
                .map(RMAItem::getRmaItemId)
                .collect(Collectors.toList());
    }

    public RMAItem getRmaItemBy(String rmaItemId) {
        return this.rmaItems.stream()
                .filter(item -> item.getRmaItemId().equals(rmaItemId))
                .findFirst()
                .orElse(null);
    }

}
