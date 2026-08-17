package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.deliveries.Allocation;
import pl.commercelink.inventory.deliveries.AllocationKey;
import pl.commercelink.inventory.deliveries.AllocationType;
import pl.commercelink.inventory.deliveries.DeliveryItem;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryCreationFormTest {

    @Test
    void overlayCopiesRequestedQtyAndUnitCostByMfn() {
        // given
        DeliveryCreationForm fresh = formWithItem("MFN-1", 1, 10.0);
        DeliveryCreationForm posted = formWithItem("MFN-1", 5, 12.5);

        // when
        fresh.applyUserSelections(posted);

        // then
        DeliveryItem result = fresh.getItems().get(0);
        assertThat(result.getRequestedQty()).isEqualTo(5);
        assertThat(result.getUnitCost()).isEqualTo(12.5);
    }

    @Test
    void overlayCopiesAllocationSelectedFlagByOrderIdAndItemId() {
        // given
        Allocation freshAllocation = allocation("order-1", "item-1", false);
        DeliveryItem freshItem = deliveryItem("MFN-1", 1, 10.0, freshAllocation);
        DeliveryCreationForm fresh = formWithItems(List.of(freshItem));

        Allocation postedAllocation = allocation("order-1", "item-1", true);
        DeliveryItem postedItem = deliveryItem("MFN-1", 1, 10.0, postedAllocation);
        DeliveryCreationForm posted = formWithItems(List.of(postedItem));

        // when
        fresh.applyUserSelections(posted);

        // then
        assertThat(fresh.getItems().get(0).getAllocations().get(0).isSelected()).isTrue();
    }

    @Test
    void postedItemUnmatchedInItemsLandsInSuggestedItemsByMfn() {
        // given
        DeliveryCreationForm fresh = new DeliveryCreationForm();
        SuggestedDeliveryItem suggested = new SuggestedDeliveryItem();
        suggested.setMfn("MFN-2");
        suggested.setRequestedQty(0);
        fresh.setSuggestedItems(List.of(suggested));

        DeliveryCreationForm posted = formWithItem("MFN-2", 3, 20.0);

        // when
        fresh.applyUserSelections(posted);

        // then
        assertThat(fresh.getSuggestedItems().get(0).getRequestedQty()).isEqualTo(3);
        assertThat(fresh.getItems()).isEmpty();
    }

    @Test
    void postedItemMatchingNothingIsIgnored() {
        // given
        DeliveryCreationForm fresh = new DeliveryCreationForm();
        DeliveryCreationForm posted = formWithItem("MFN-UNKNOWN", 3, 20.0);

        // when / then
        fresh.applyUserSelections(posted);
        assertThat(fresh.getItems()).isEmpty();
        assertThat(fresh.getSuggestedItems()).isEmpty();
    }

    @Test
    void overlayNeverAddsOrRemovesRows() {
        // given
        DeliveryCreationForm fresh = formWithItem("MFN-1", 1, 10.0);
        DeliveryCreationForm posted = new DeliveryCreationForm();
        posted.setItems(List.of(
                deliveryItem("MFN-1", 5, 12.5),
                deliveryItem("MFN-EXTRA", 9, 99.0)
        ));

        // when
        fresh.applyUserSelections(posted);

        // then
        assertThat(fresh.getItems()).hasSize(1);
        assertThat(fresh.getSuggestedItems()).isEmpty();
    }

    private DeliveryCreationForm formWithItem(String mfn, int requestedQty, double unitCost) {
        return formWithItems(List.of(deliveryItem(mfn, requestedQty, unitCost)));
    }

    private DeliveryCreationForm formWithItems(List<DeliveryItem> items) {
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setItems(items);
        return form;
    }

    private DeliveryItem deliveryItem(String mfn, int requestedQty, double unitCost) {
        DeliveryItem item = new DeliveryItem();
        item.setMfn(mfn);
        item.setRequestedQty(requestedQty);
        item.setUnitCost(unitCost);
        return item;
    }

    private DeliveryItem deliveryItem(String mfn, int requestedQty, double unitCost, Allocation allocation) {
        DeliveryItem item = deliveryItem(mfn, requestedQty, unitCost);
        item.setAllocations(List.of(allocation));
        return item;
    }

    private Allocation allocation(String orderId, String itemId, boolean selected) {
        Allocation allocation = new Allocation();
        allocation.setKey(new AllocationKey(orderId, itemId, "Warehouse"));
        allocation.setType(AllocationType.Warehouse);
        allocation.setSelected(selected);
        return allocation;
    }
}
