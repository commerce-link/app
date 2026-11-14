package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.deliveries.Allocation;
import pl.commercelink.inventory.deliveries.AllocationKey;
import pl.commercelink.inventory.deliveries.AllocationType;
import pl.commercelink.inventory.deliveries.DeliveryItem;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryCreationFormTest {

    @Test
    void unitCostsAreRoundedToWholeCents() {
        // given: the cost fields have no browser validation, so the posted costs may carry any fraction
        DeliveryCreationForm form = new DeliveryCreationForm();
        DeliveryItem item = new DeliveryItem();
        item.setUnitCost(10.00111);
        form.setItems(new ArrayList<>(List.of(item)));
        SuggestedDeliveryItem suggested = new SuggestedDeliveryItem();
        suggested.setUnitCost(3.145);
        form.setSuggestedItems(new ArrayList<>(List.of(suggested)));

        // when
        form.roundUnitCosts();

        // then
        assertThat(item.getUnitCost()).isEqualTo(10.0);
        assertThat(suggested.getUnitCost()).isEqualTo(3.15);
    }

    @Test
    void backKeepsAChosenSuggestionThePlanDoesNotCarry() {
        // given: the plan comes without suggestions (the page fetches them); step 2 merged a chosen one into the items
        DeliveryCreationForm fresh = new DeliveryCreationForm();
        fresh.setItems(new ArrayList<>());
        DeliveryCreationForm posted = new DeliveryCreationForm();
        DeliveryItem merged = new DeliveryItem("Fan", "5900000000009", "FAN-1", 89.0, List.of());
        merged.setRequestedQty(2);
        posted.setItems(new ArrayList<>(List.of(merged)));

        // when
        fresh.applyUserSelections(posted);

        // then
        assertThat(fresh.getSuggestedItems()).singleElement().satisfies(suggestion -> {
            assertThat(suggestion.getMfn()).isEqualTo("FAN-1");
            assertThat(suggestion.getName()).isEqualTo("Fan");
            assertThat(suggestion.getEan()).isEqualTo("5900000000009");
            assertThat(suggestion.getRequestedQty()).isEqualTo(2);
            assertThat(suggestion.getUnitCost()).isEqualTo(89.0);
        });
    }

    @Test
    void aChosenSuggestionWithoutEanDoesNotComeBack() {
        // given: a suggestion always carries its offer's EAN, so a row without one is a hand-made post
        DeliveryCreationForm fresh = new DeliveryCreationForm();
        fresh.setItems(new ArrayList<>());
        DeliveryCreationForm posted = new DeliveryCreationForm();
        DeliveryItem merged = new DeliveryItem("Fan", " ", "FAN-1", 89.0, List.of());
        merged.setRequestedQty(2);
        posted.setItems(new ArrayList<>(List.of(merged)));

        // when
        fresh.applyUserSelections(posted);

        // then
        assertThat(fresh.getSuggestedItems()).isNullOrEmpty();
    }

    @Test
    void anItemThatLeftThePlanDoesNotComeBackAsASuggestion() {
        // given: an item with sources is a planned item; one the plan no longer has is gone, not a suggestion
        DeliveryCreationForm fresh = new DeliveryCreationForm();
        fresh.setItems(new ArrayList<>());
        DeliveryCreationForm posted = new DeliveryCreationForm();
        Allocation allocation = new Allocation();
        allocation.setKey(new AllocationKey("order-1", "item-1", "buyer@example.com"));
        allocation.setType(AllocationType.Order);
        DeliveryItem gone = new DeliveryItem("Gone", "590", "GONE-1", 10.0, new ArrayList<>(List.of(allocation)));
        gone.setRequestedQty(1);
        posted.setItems(new ArrayList<>(List.of(gone)));
        SuggestedDeliveryItem untouched = new SuggestedDeliveryItem();
        untouched.setMfn("ZERO-1");
        posted.setSuggestedItems(new ArrayList<>(List.of(untouched)));

        // when
        fresh.applyUserSelections(posted);

        // then
        assertThat(fresh.getSuggestedItems()).isEmpty();
    }

    @Test
    void costOfTheEditProductDialogIsRoundedToWholeCents() {
        // given
        DeliveryFulfilmentUpdateForm form = new DeliveryFulfilmentUpdateForm();

        // when
        form.setUnitCost(19.996);

        // then
        assertThat(form.getUnitCost()).isEqualTo(20.0);
    }

    @Test
    void backRestoresTheUnitCostOfASuggestion() {
        // given: step 2 merged the suggestion into the items, so it comes back as an item without allocations
        DeliveryCreationForm fresh = new DeliveryCreationForm();
        SuggestedDeliveryItem suggested = new SuggestedDeliveryItem();
        suggested.setEan("5900000000005");
        suggested.setMfn("MFN-S");
        suggested.setUnitCost(100.0);
        fresh.setSuggestedItems(new ArrayList<>(List.of(suggested)));
        DeliveryCreationForm posted = new DeliveryCreationForm();
        DeliveryItem merged = new DeliveryItem();
        merged.setEan("5900000000005");
        merged.setMfn("MFN-S");
        merged.setRequestedQty(3);
        merged.setUnitCost(89.5);
        posted.setItems(new ArrayList<>(List.of(merged)));

        // when
        fresh.applyUserSelections(posted);

        // then
        assertThat(fresh.getSuggestedItems().getFirst().getRequestedQty()).isEqualTo(3);
        assertThat(fresh.getSuggestedItems().getFirst().getUnitCost()).isEqualTo(89.5);
    }

    @Test
    void overlayCopiesSuggestionsThatWereNotMergedYet() {
        // given: step 1 came back before step 2 merged the suggestions, so they are still suggestions
        DeliveryCreationForm fresh = new DeliveryCreationForm();
        SuggestedDeliveryItem planned = new SuggestedDeliveryItem();
        planned.setEan("5900000000005");
        planned.setMfn("MFN-S");
        planned.setUnitCost(100.0);
        fresh.setSuggestedItems(new ArrayList<>(List.of(planned)));
        DeliveryCreationForm posted = new DeliveryCreationForm();
        SuggestedDeliveryItem typed = new SuggestedDeliveryItem();
        typed.setEan("5900000000005");
        typed.setMfn("MFN-S");
        typed.setRequestedQty(5);
        typed.setUnitCost(92.0);
        posted.setSuggestedItems(new ArrayList<>(List.of(typed)));

        // when
        fresh.applyUserSelections(posted);

        // then
        assertThat(fresh.getSuggestedItems().getFirst().getRequestedQty()).isEqualTo(5);
        assertThat(fresh.getSuggestedItems().getFirst().getUnitCost()).isEqualTo(92.0);
    }

    @Test
    void overlayCopiesRequestedQtyAndUnitCostByEan() {
        // given
        DeliveryCreationForm fresh = formWithItem("5900000000001", 1, 10.0);
        DeliveryCreationForm posted = formWithItem("5900000000001", 5, 12.5);

        // when
        fresh.applyUserSelections(posted);

        // then
        DeliveryItem result = fresh.getItems().get(0);
        assertThat(result.getRequestedQty()).isEqualTo(5);
        assertThat(result.getUnitCost()).isEqualTo(12.5);
    }

    @Test
    void twoProductsUnderOneCodeKeepTheirOwnQtyAndCost() {
        // given
        DeliveryCreationForm fresh = formWithItems(List.of(
                deliveryItem("5900000000001", 1, 10.0), deliveryItem("5900000000002", 1, 4.0)));
        DeliveryCreationForm posted = formWithItems(List.of(
                deliveryItem("5900000000001", 5, 12.5), deliveryItem("5900000000002", 7, 3.5)));

        // when
        fresh.applyUserSelections(posted);

        // then
        assertThat(fresh.getItems()).extracting(DeliveryItem::getRequestedQty).containsExactly(5, 7);
        assertThat(fresh.getItems()).extracting(DeliveryItem::getUnitCost).containsExactly(12.5, 3.5);
    }

    @Test
    void overlayCopiesDeliveryHeaderFields() {
        // given
        DeliveryCreationForm fresh = formWithItem("5900000000001", 1, 10.0);
        DeliveryCreationForm posted = formWithItem("5900000000001", 1, 10.0);
        posted.setExternalDeliveryId("FV-77");
        posted.setEstimatedDeliveryAt(java.time.LocalDate.of(2026, 8, 20));
        posted.setSourceCurrency("EUR");
        posted.setShippingCost(15.5);
        posted.setPaymentCost(3.0);
        posted.setPaymentTerms(14);
        posted.setTax(1.08);
        posted.setRemoveUnselected(true);
        posted.setDeliveryAddressId("addr-2");

        // when
        fresh.applyUserSelections(posted);

        // then
        assertThat(fresh.getExternalDeliveryId()).isEqualTo("FV-77");
        assertThat(fresh.getEstimatedDeliveryAt()).isEqualTo(java.time.LocalDate.of(2026, 8, 20));
        assertThat(fresh.getSourceCurrency()).isEqualTo("EUR");
        assertThat(fresh.getShippingCost()).isEqualTo(15.5);
        assertThat(fresh.getPaymentCost()).isEqualTo(3.0);
        assertThat(fresh.getPaymentTerms()).isEqualTo(14);
        assertThat(fresh.getTax()).isEqualTo(1.08);
        assertThat(fresh.isRemoveUnselected()).isTrue();
        assertThat(fresh.getDeliveryAddressId()).isEqualTo("addr-2");
    }

    @Test
    void overlayCopiesAllocationSelectedFlagByOrderIdAndItemId() {
        // given
        Allocation freshAllocation = allocation("order-1", "item-1", false);
        DeliveryItem freshItem = deliveryItem("5900000000001", 1, 10.0, freshAllocation);
        DeliveryCreationForm fresh = formWithItems(List.of(freshItem));

        Allocation postedAllocation = allocation("order-1", "item-1", true);
        DeliveryItem postedItem = deliveryItem("5900000000001", 1, 10.0, postedAllocation);
        DeliveryCreationForm posted = formWithItems(List.of(postedItem));

        // when
        fresh.applyUserSelections(posted);

        // then
        assertThat(fresh.getItems().get(0).getAllocations().get(0).isSelected()).isTrue();
    }

    @Test
    void postedItemUnmatchedInItemsLandsInSuggestedItemsByEan() {
        // given
        DeliveryCreationForm fresh = new DeliveryCreationForm();
        SuggestedDeliveryItem other = new SuggestedDeliveryItem();
        other.setEan("5900000000003");
        other.setMfn("MFN-1");
        SuggestedDeliveryItem suggested = new SuggestedDeliveryItem();
        suggested.setEan("5900000000002");
        suggested.setMfn("MFN-1");
        suggested.setRequestedQty(0);
        fresh.setSuggestedItems(List.of(other, suggested));

        DeliveryCreationForm posted = formWithItem("5900000000002", 3, 20.0);

        // when
        fresh.applyUserSelections(posted);

        // then
        assertThat(fresh.getSuggestedItems()).extracting(SuggestedDeliveryItem::getRequestedQty).containsExactly(0, 3);
        assertThat(fresh.getItems()).isEmpty();
    }

    @Test
    void overlayNeverAddsOrRemovesItemRows() {
        // given
        DeliveryCreationForm fresh = formWithItem("5900000000001", 1, 10.0);
        DeliveryCreationForm posted = new DeliveryCreationForm();
        posted.setItems(List.of(
                deliveryItem("5900000000001", 5, 12.5),
                deliveryItem("5900000000009", 9, 99.0)
        ));

        // when
        fresh.applyUserSelections(posted);

        // then: an item without sources outside the plan is a suggestion chosen before, not a new item
        assertThat(fresh.getItems()).hasSize(1);
        assertThat(fresh.getSuggestedItems()).extracting(SuggestedDeliveryItem::getEan).containsExactly("5900000000009");
    }

    private DeliveryCreationForm formWithItem(String ean, int requestedQty, double unitCost) {
        return formWithItems(List.of(deliveryItem(ean, requestedQty, unitCost)));
    }

    private DeliveryCreationForm formWithItems(List<DeliveryItem> items) {
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setItems(items);
        return form;
    }

    private DeliveryItem deliveryItem(String ean, int requestedQty, double unitCost) {
        DeliveryItem item = new DeliveryItem();
        item.setEan(ean);
        item.setMfn("MFN-1");
        item.setRequestedQty(requestedQty);
        item.setUnitCost(unitCost);
        return item;
    }

    private DeliveryItem deliveryItem(String ean, int requestedQty, double unitCost, Allocation allocation) {
        DeliveryItem item = deliveryItem(ean, requestedQty, unitCost);
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
