package pl.commercelink.web.orders;

import org.junit.jupiter.api.Test;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.api.ItemCondition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OrderItemRowTest {

    private static final Order ORDER = new Order("store-1");

    private static OrderItemRow.Context context(Order order, boolean readOnly) {
        return context(order, readOnly, false);
    }

    private static OrderItemRow.Context context(Order order, boolean readOnly, boolean superAdmin) {
        return new OrderItemRow.Context(order, readOnly, superAdmin, new SupplierLabels(mock(StoresRepository.class)).forStore(null),
                item -> "/dashboard/deliveries/details?deliveryId=" + item.getDeliveryId(),
                serial -> "/dashboard/item/history?serialNo=" + serial);
    }

    private static OrderItemRow.Context context() {
        return context(ORDER, false);
    }

    private static OrderItem item(FulfilmentStatus status, String sku) {
        OrderItem item = new OrderItem(ORDER.getOrderId(), "CPU", "AMD Ryzen 7", 2, 749, sku, false);
        item.setStatus(status);
        item.setCost(579);
        return item;
    }

    private static ItemAction.State state(OrderItemRow row, ItemAction action) {
        return row.actions().stream().filter(state -> state.action() == action).findFirst().orElseThrow();
    }

    @Test
    void aNewItemOffersEveryAssignmentAndExplainsWhatItCannotDo() {
        // when
        OrderItemRow row = OrderItemRow.of(item(FulfilmentStatus.New, "MFN-1"), 0, context(ORDER, false));

        // then
        assertThat(state(row, ItemAction.ASSIGN_SKU).available()).isTrue();
        assertThat(state(row, ItemAction.ASSIGN_SUPPLIER).available()).isTrue();
        assertThat(state(row, ItemAction.ASSIGN_WAREHOUSE).available()).isTrue();
        assertThat(hasAction(row, ItemAction.CLEAR_SUPPLIER)).isFalse();
        assertThat(hasAction(row, ItemAction.SPLIT_GROUP)).isFalse();
        assertThat(state(row, ItemAction.CONSOLIDATE).labelKey()).isEqualTo("order.item.menu.consolidate");
        assertThat(state(row, ItemAction.EDIT).available()).isTrue();
        assertThat(row.unitPrice()).isEqualTo("749,00");
        // the cost is gross, like the price above it, so the two compare at a glance (client request 2026-09-30)
        assertThat(row.unitCost()).isEqualTo("712,17");
        assertThat(row.statusTone()).isEqualTo("is-neutral");
        assertThat(row.readyForAllocation()).isFalse();
        assertThat(row.removable()).isTrue();
    }

    @Test
    void anOrderedItemAndABundleGiveTheirReasons() {
        // given
        OrderItem ordered = item(FulfilmentStatus.Ordered, "MFN-1");
        ordered.setDeliveryId("Acme");
        // C-05b: a bundle sku must start with '#' (GroupSku.isGroup requires the prefix), e.g. "#1xA|1xB".
        OrderItem bundle = item(FulfilmentStatus.New, "#1xA|1xB");

        // when
        OrderItemRow orderedRow = OrderItemRow.of(ordered, 0, context(ORDER, false));
        OrderItemRow bundleRow = OrderItemRow.of(bundle, 1, context(ORDER, false));

        // then
        assertThat(state(orderedRow, ItemAction.ASSIGN_SKU).reasonKey()).isEqualTo("order.item.unavailable.not.new");
        assertThat(state(orderedRow, ItemAction.CLEAR_SUPPLIER).reasonKey()).isEqualTo("order.item.unavailable.fulfilled");
        assertThat(state(bundleRow, ItemAction.ASSIGN_SUPPLIER).reasonKey()).isEqualTo("order.item.unavailable.group");
        assertThat(state(bundleRow, ItemAction.SPLIT_GROUP).available()).isTrue();
    }

    @Test
    void aClaimedItemNamesTheDeliveryThatHoldsIt() {
        // given
        OrderItem claimed = item(FulfilmentStatus.Allocation, "MFN-1");
        claimed.markAsClaimed("7f3a9c2e-0000-0000-0000-000000000000");

        // when
        OrderItemRow row = OrderItemRow.of(claimed, 0, context(ORDER, false));

        // then
        assertThat(state(row, ItemAction.CLEAR_SUPPLIER).reasonKey()).isEqualTo("order.item.unavailable.claimed");
        assertThat(state(row, ItemAction.CLEAR_SUPPLIER).reasonArg()).isEqualTo("7f3a9c2e");
    }

    @Test
    void consolidationStopsOnceTheOrderIsInvoiced() {
        // given
        Order invoiced = new Order("store-1");
        invoiced.addDocument(new Document("fv", "FV/1", null, DocumentType.InvoiceVat));

        // when
        OrderItemRow row = OrderItemRow.of(item(FulfilmentStatus.Delivered, "MFN-1"), 0, context(invoiced, false));

        // then
        assertThat(state(row, ItemAction.CONSOLIDATE).available()).isFalse();
        assertThat(state(row, ItemAction.CONSOLIDATE).reasonKey()).isEqualTo("order.item.unavailable.invoiced");
    }

    @Test
    void theCostIsGrossAtTheItemsOwnVatRate() {
        // given
        OrderItem item = item(FulfilmentStatus.New, "MFN-1");
        item.setTax(1.08);

        // when
        OrderItemRow row = OrderItemRow.of(item, 0, context());

        // then: 579 net + 8% VAT
        assertThat(row.unitCost()).isEqualTo("625,32");
    }

    @Test
    void aReadOnlyViewerGetsNoActionsButStillTheCost() {
        // when
        OrderItemRow forSuperAdmin = OrderItemRow.of(item(FulfilmentStatus.New, "MFN-1"), 0, context(ORDER, true, true));

        // then
        assertThat(forSuperAdmin.unitCost()).isEqualTo("712,17");
        assertThat(forSuperAdmin.actions()).isEmpty();
        assertThat(forSuperAdmin.editHref()).isNull();
    }

    @Test
    void aDamagedItemCarriesItsConditionAsABadPill() {
        // given
        OrderItem damaged = item(FulfilmentStatus.Delivered, "MFN-1");
        damaged.setCondition(ItemCondition.Damaged);
        damaged.setSerialNo("SN-1");

        // when
        OrderItemRow row = OrderItemRow.of(damaged, 0, context(ORDER, false));

        // then
        assertThat(row.conditionKey()).isEqualTo("ItemCondition.Damaged");
        assertThat(row.conditionTone()).isEqualTo("is-bad");
        assertThat(row.serialHref()).isEqualTo("/dashboard/item/history?serialNo=SN-1");
        assertThat(row.deliveredProduct()).isTrue();
    }

    @Test
    void skuIsShownOnlyWhileTheItemIsNew() {
        // given
        OrderItem fresh = item(FulfilmentStatus.New, "ABC:DEF");
        OrderItem delivered = item(FulfilmentStatus.Delivered, "ABC:DEF");
        // when
        OrderItemRow freshRow = OrderItemRow.of(fresh, 0, context());
        OrderItemRow deliveredRow = OrderItemRow.of(delivered, 1, context());
        // then
        assertThat(freshRow.sku()).isEqualTo("ABC:DEF");
        assertThat(deliveredRow.sku()).isNull();
    }

    private static boolean hasAction(OrderItemRow row, ItemAction action) {
        return row.actions().stream().anyMatch(state -> state.action() == action);
    }

    @Test
    void splitSetIsAbsentForAnItemThatIsNotASet() {
        // given
        OrderItem bundle = item(FulfilmentStatus.New, "#1xA|1xB");

        // when
        OrderItemRow plainRow = OrderItemRow.of(item(FulfilmentStatus.New, "MFN-1"), 0, context());
        OrderItemRow bundleRow = OrderItemRow.of(bundle, 1, context());

        // then
        assertThat(hasAction(plainRow, ItemAction.SPLIT_GROUP)).isFalse();
        assertThat(plainRow.actions()).hasSize(6);
        assertThat(hasAction(bundleRow, ItemAction.SPLIT_GROUP)).isTrue();
        assertThat(bundleRow.actions()).hasSize(8);
    }

    @Test
    void anAllocatedItemOffersOnlyRemovingTheSupplier() {
        // given
        OrderItem allocated = item(FulfilmentStatus.Allocation, "MFN-1");
        allocated.setDeliveryId("Acme");

        // when
        OrderItemRow row = OrderItemRow.of(allocated, 0, context());

        // then
        assertThat(hasAction(row, ItemAction.ASSIGN_SKU)).isFalse();
        assertThat(hasAction(row, ItemAction.ASSIGN_SUPPLIER)).isFalse();
        assertThat(hasAction(row, ItemAction.ASSIGN_WAREHOUSE)).isFalse();
        assertThat(state(row, ItemAction.CLEAR_SUPPLIER).available()).isTrue();
    }

    @Test
    void warehouseIsGreyedWhenTheMarketplaceChoseTheSupplier() {
        // given
        Order routed = new Order("store-1");
        routed.setExternalSupplierId("company-7");

        // when
        OrderItemRow row = OrderItemRow.of(item(FulfilmentStatus.New, "MFN-1"), 0, context(routed, false));

        // then
        assertThat(state(row, ItemAction.ASSIGN_WAREHOUSE).reasonKey()).isEqualTo("order.item.unavailable.routed");
        assertThat(state(row, ItemAction.ASSIGN_SUPPLIER).available()).isTrue();
    }

    @Test
    void aClosedOrderLinksTheNameToTheItemPage() {
        // given
        OrderItem item = item(FulfilmentStatus.Delivered, "MFN-1");

        // when
        OrderItemRow row = OrderItemRow.of(item, 0, context(ORDER, true));

        // then
        assertThat(row.viewHref()).isEqualTo("/dashboard/orders/" + ORDER.getOrderId() + "/items/" + item.getItemId());
        assertThat(row.editHref()).isNull();
    }

    @Test
    void aClosedOrderLinksTheNameToTheItemPageNotForASuperAdmin() {
        // when
        OrderItemRow row = OrderItemRow.of(item(FulfilmentStatus.Delivered, "MFN-1"), 0, context(ORDER, true, true));

        // then
        assertThat(row.viewHref()).isNull();
    }

    @Test
    void aClosedOrderLinksTheNameToTheItemPageNotForAnOpenOrder() {
        // when
        OrderItemRow row = OrderItemRow.of(item(FulfilmentStatus.New, "MFN-1"), 0, context(ORDER, false));

        // then: the open order reaches the item page through the item menu
        assertThat(row.viewHref()).isNull();
        assertThat(row.editHref()).isNotNull();
    }

    @Test
    void theSkuIsShownOnlyWhenItDiffersFromTheManufacturerCode() {
        // given
        OrderItem same = item(FulfilmentStatus.New, "MFN-1");
        same.setManufacturerCode("MFN-1");
        OrderItem different = item(FulfilmentStatus.New, "SKU-1");
        different.setManufacturerCode("MFN-1");

        // when
        OrderItemRow sameRow = OrderItemRow.of(same, 0, context());
        OrderItemRow differentRow = OrderItemRow.of(different, 1, context());

        // then
        assertThat(sameRow.skuShown()).isFalse();
        assertThat(differentRow.skuShown()).isTrue();
        assertThat(sameRow.hasCodes()).isTrue();
    }

    @Test
    void aCommentIsAMarkerAfterTheNameButConsolidationIsNot() {
        // given
        OrderItem plain = item(FulfilmentStatus.New, "MFN-1");
        OrderItem commented = item(FulfilmentStatus.New, "MFN-1");
        commented.setComment("  Check the box  ");
        OrderItem consolidated = item(FulfilmentStatus.New, "MFN-1");
        consolidated.setConsolidated(true);

        // when
        OrderItemRow plainRow = OrderItemRow.of(plain, 0, context());
        OrderItemRow commentedRow = OrderItemRow.of(commented, 1, context());
        OrderItemRow consolidatedRow = OrderItemRow.of(consolidated, 2, context());

        // then
        assertThat(plainRow.hasMarkers()).isFalse();
        assertThat(commentedRow.hasMarkers()).isTrue();
        assertThat(commentedRow.comment()).isEqualTo("Check the box");
        assertThat(consolidatedRow.hasMarkers()).isFalse();
        assertThat(consolidatedRow.consolidated()).isTrue();
    }

    @Test
    void anItemWithoutCategoryAndCodesHasNoCodesLine() {
        // given
        OrderItem bare = new OrderItem(ORDER.getOrderId(), null, "Montaż", 1, 99, null, false);
        bare.setStatus(FulfilmentStatus.New);

        // when
        OrderItemRow row = OrderItemRow.of(bare, 0, context());

        // then
        assertThat(row.hasCodes()).isFalse();
    }

    @Test
    void splittingASetIsGreyedOnceTheOrderIsInvoicedOrWhileAnEReceiptIsBeingIssued() {
        // given: splitting rewrites the lines the sale document lists
        OrderItem bundle = item(FulfilmentStatus.New, "#1xA|1xB");
        Order invoiced = new Order("store-1");
        invoiced.addDocument(new Document("r1", "PAR/1", null, DocumentType.Receipt));

        // when
        ItemAction.State afterDocument = OrderItemRow.actions(bundle, invoiced, false).stream()
                .filter(a -> a.action() == ItemAction.SPLIT_GROUP).findFirst().orElseThrow();
        ItemAction.State whileIssuing = OrderItemRow.actions(bundle, new Order("store-1"), true).stream()
                .filter(a -> a.action() == ItemAction.SPLIT_GROUP).findFirst().orElseThrow();
        ItemAction.State open = OrderItemRow.actions(bundle, new Order("store-1"), false).stream()
                .filter(a -> a.action() == ItemAction.SPLIT_GROUP).findFirst().orElseThrow();

        // then
        assertThat(afterDocument.available()).isFalse();
        assertThat(afterDocument.reasonKey()).isEqualTo("order.item.unavailable.sale.invoiced");
        assertThat(whileIssuing.available()).isFalse();
        assertThat(whileIssuing.reasonKey()).isEqualTo("order.item.unavailable.receipt");
        assertThat(open.available()).isTrue();
    }
}
