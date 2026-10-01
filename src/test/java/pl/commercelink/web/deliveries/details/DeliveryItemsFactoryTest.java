package pl.commercelink.web.deliveries.details;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveryOrderStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.web.deliveries.details.DeliveryFixtures.*;
import static pl.commercelink.web.deliveries.details.DeliveryPageModel.ActionState;

class DeliveryItemsFactoryTest {

    private static DeliveryPageModel.ItemsCard items(Delivery delivery, DeliveryViewer viewer) {
        return items(data(delivery), viewer, Set.of());
    }

    private static DeliveryPageModel.ItemsCard items(DeliveryPageData data, DeliveryViewer viewer, Set<Integer> checked) {
        return DeliveryItemsFactory.items(data, viewer,
                DeliveryLinks.of(viewer.superAdmin(), STORE_ID, data.delivery().getDeliveryId()), checked);
    }

    @Test
    void productsKeepTheOrderOfTheirFirstAllocationAndSumTheirLines() {
        // given
        Delivery delivery = withAllocations(warehouse(),
                warehouseAllocation("Samsung MirageDrive 2TB NVMe", "5900000000006", "MFN-MIRAGE-01", 635.0, 2),
                orderAllocation("NVIDIA ValueKing RTX Ultra", "5900000000002", "MFN-VALUE-01", 3814.0, 1, false),
                orderAllocation("Samsung MirageDrive 2TB NVMe", "5900000000006", "MFN-MIRAGE-01", 635.0, 1, false));
        delivery.getAllocations().get(2).setInAllocation(false);

        // when
        DeliveryPageModel.ItemsCard card = items(delivery, ADMIN);

        // then
        assertThat(card.products()).extracting(DeliveryPageModel.ProductRow::mfn).containsExactly("MFN-MIRAGE-01", "MFN-VALUE-01");
        DeliveryPageModel.ProductRow ssd = card.products().get(0);
        assertThat(ssd.orderedQty()).isEqualTo(3);
        assertThat(ssd.receivedQty()).isEqualTo(1);
        assertThat(ssd.complete()).isFalse();
        assertThat(ssd.value()).isEqualTo("1 905,00");
        assertThat(ssd.unitCost()).isEqualTo("635,00");
        assertThat(ssd.minQty()).isEqualTo(1);
        assertThat(ssd.allocations()).extracting(DeliveryPageModel.AllocationRow::index).containsExactly(0, 2);
        assertThat(card.goodsNet()).isEqualTo("5 719,00");
        assertThat(card.goodsGross()).isEqualTo("7 034,37");
        assertThat(card.allocationCount()).isEqualTo(3);
        assertThat(card.pendingCount()).isEqualTo(2);
    }

    @Test
    void anAllocationNamesItsDestinationAndItsState() {
        // given
        Delivery delivery = partlyReceived(warehouse());

        // when
        List<DeliveryPageModel.AllocationRow> rows = items(delivery, ADMIN).products().stream()
                .flatMap(product -> product.allocations().stream()).toList();

        // then
        DeliveryPageModel.AllocationRow order = rows.get(0);
        assertThat(order.warehouse()).isFalse();
        assertThat(order.orderShortId()).isEqualTo("a9f693b8");
        assertThat(order.customer()).isEqualTo("marek.pawlak");
        assertThat(order.href()).isEqualTo("/dashboard/orders/" + ORDER_ID);
        assertThat(order.received()).isTrue();
        assertThat(order.checkbox()).isFalse();
        assertThat(order.stateKey()).isEqualTo("deliveries.details.items.state.received");
        assertThat(order.stateTone()).isEqualTo("is-ok");
        DeliveryPageModel.AllocationRow stock = rows.get(1);
        assertThat(stock.warehouse()).isTrue();
        assertThat(stock.customer()).isNull();
        assertThat(stock.href()).isEqualTo("/dashboard/warehouse/items/wh-MFN-MIRAGE-01");
        assertThat(stock.checkbox()).isTrue();
        assertThat(stock.stateKey()).isEqualTo("deliveries.details.items.state.waiting");
        assertThat(stock.stateTone()).isEqualTo("is-neutral");
    }

    @Test
    void everyAllocationCarriesTheFieldsTheFormBindsBack() {
        // when
        DeliveryPageModel.AllocationFields fields = items(warehouse(), ADMIN).products().get(1).allocations().get(0).fields();

        // then
        assertThat(fields.itemId()).isEqualTo("wh-MFN-MIRAGE-01");
        assertThat(fields.orderId()).isNull();
        assertThat(fields.keyName()).isEqualTo("Warehouse");
        assertThat(fields.type()).isEqualTo("Warehouse");
        assertThat(fields.qty()).isEqualTo(2);
        assertThat(fields.mfn()).isEqualTo("MFN-MIRAGE-01");
        assertThat(fields.inAllocation()).isTrue();
        assertThat(fields.unitCost()).isEqualTo("635.0");
    }

    @Test
    void theSuperAdminGetsTextInsteadOfLinksHeCannotOpen() {
        // when
        DeliveryPageModel.ItemsCard card = items(warehouse(), SUPER_ADMIN);

        // then
        assertThat(card.products()).allSatisfy(product -> {
            assertThat(product.historyHref()).isNull();
            assertThat(product.allocations()).allSatisfy(row -> assertThat(row.href()).isNull());
        });
        assertThat(card.products()).allSatisfy(product -> assertThat(product.menu()).isTrue());
    }

    @Test
    void warehouseActionsAreHiddenInADropshipDelivery() {
        // when
        DeliveryPageModel.SelectionBar bar = items(dropship(), ADMIN).selection();

        // then
        assertThat(bar.receive().visible()).isFalse();
        assertThat(bar.merge().visible()).isFalse();
        assertThat(bar.split().visible()).isFalse();
        assertThat(bar.moveVisible()).isFalse();
        assertThat(bar.ship()).isEqualTo(ActionState.on());
        assertThat(bar.remove()).isEqualTo(ActionState.on());
        assertThat(bar.removeMessageKey()).isEqualTo("deliveries.details.remove.message.dropship");
        assertThat(items(dropship(), ADMIN).products().get(0).changeQty().visible()).isFalse();
    }

    @Test
    void receivingWaitsForTheOrderToBePlaced() {
        // given
        Delivery ordering = withStatus(warehouse(), DeliveryOrderStatus.ORDER_PENDING);

        // when
        DeliveryPageModel.ItemsCard admin = items(ordering, ADMIN);
        DeliveryPageModel.ItemsCard superAdmin = items(ordering, SUPER_ADMIN);

        // then
        assertThat(admin.selectable()).isTrue();
        assertThat(admin.selection().receive()).isEqualTo(ActionState.off("deliveries.details.reason.ordering"));
        assertThat(admin.selection().remove()).isEqualTo(ActionState.off("deliveries.details.reason.ordering"));
        assertThat(admin.products().get(0).changeQty()).isEqualTo(ActionState.off("deliveries.details.reason.ordering"));
        assertThat(superAdmin.selectable()).isFalse();
    }

    @Test
    void theUserOnlyReceives() {
        // when
        DeliveryPageModel.ItemsCard card = items(warehouse(), USER);

        // then
        assertThat(card.selectable()).isTrue();
        assertThat(card.selection().receive()).isEqualTo(ActionState.on());
        assertThat(card.selection().merge().visible()).isFalse();
        assertThat(card.selection().split().visible()).isFalse();
        assertThat(card.selection().remove().visible()).isFalse();
        assertThat(card.products().get(0).changeQty().visible()).isFalse();
        assertThat(items(dropship(), USER).selectable()).isFalse();
    }

    @Test
    void movingToAnotherDeliveryNeedsATarget() {
        // given
        Delivery target = warehouse();
        target.setDeliveryId("4c1d9e07-0000-0000-0000-000000000000");
        target.setExternalDeliveryId(null);
        target.setEstimatedDeliveryAt(LocalDate.of(2026, 10, 10));
        DeliveryPageData withTarget = new DeliveryPageData(warehouse(), "Manual-Hurt", null, List.of(target), null,
                List.of(), null, Set.of(), null, null, NOW);

        // when
        DeliveryPageModel.SelectionBar none = items(warehouse(), ADMIN).selection();
        DeliveryPageModel.SelectionBar one = items(withTarget, ADMIN, Set.of()).selection();

        // then
        assertThat(none.merge()).isEqualTo(ActionState.off("deliveries.details.reason.noMergeTargets"));
        assertThat(one.merge()).isEqualTo(ActionState.on());
        assertThat(one.mergeTargets()).containsExactly(
                new DeliveryPageModel.MergeTarget("4c1d9e07-0000-0000-0000-000000000000", "4c1d9e07", null, "10.10.2026"));
    }

    @Test
    void removalAfterAnUnknownOutcomeWarnsThatTheSupplierOrderMayExist() {
        // given
        Delivery dispatched = withStatus(warehouse(), DeliveryOrderStatus.ORDER_DISPATCHED);
        Delivery unknown = outcomeUnknown(warehouse());

        // when / then
        assertThat(items(dispatched, ADMIN).selection().remove()).isEqualTo(ActionState.off("deliveries.details.reason.ordering"));
        assertThat(items(unknown, ADMIN).selection().remove()).isEqualTo(ActionState.on());
        assertThat(items(unknown, ADMIN).selection().removeMessageKey()).isEqualTo("deliveries.details.remove.message.unknown");
        assertThat(items(warehouse(), ADMIN).selection().removeMessageKey()).isEqualTo("deliveries.details.remove.message");
    }

    @Test
    void changingTheOrderedQuantityExplainsWhyItIsGreyed() {
        // given
        Delivery withDocument = withGoodsReceipt(warehouse());
        Delivery partly = partlyReceived(warehouse());

        // when / then
        assertThat(items(withDocument, ADMIN).products().get(0).changeQty()).isEqualTo(ActionState.off("deliveries.details.reason.documents"));
        assertThat(items(partly, ADMIN).products().get(0).changeQty()).isEqualTo(ActionState.off("deliveries.details.reason.allReceived"));
        assertThat(items(partly, ADMIN).products().get(1).changeQty()).isEqualTo(ActionState.on());
        assertThat(items(partly, ADMIN).products().get(1).qtyHref())
                .isEqualTo("/dashboard/deliveries/details?deliveryId=" + DELIVERY_ID + "&open=qty&mfn=MFN-MIRAGE-01#qty-dialog");
    }

    @Test
    void preselectedAllocationsAreCheckedOnlyWhenTheyCanBe() {
        // given
        Delivery delivery = partlyReceived(warehouse());

        // when
        DeliveryPageModel.ItemsCard card = items(data(delivery), ADMIN, Set.of(0, 1));

        // then
        assertThat(card.products().get(0).allocations().get(0).checked()).isFalse();
        assertThat(card.products().get(1).allocations().get(0).checked()).isTrue();
        assertThat(DeliveryItemsFactory.pendingIndexes(delivery)).containsExactly(1);
    }

    @Test
    void aReceivedOrEmptyDeliveryHasNoCheckboxes() {
        // when / then
        assertThat(items(received(warehouse()), ADMIN).selectable()).isFalse();
        DeliveryPageModel.ItemsCard empty = items(withAllocations(warehouse()), ADMIN);
        assertThat(empty.selectable()).isFalse();
        assertThat(empty.products()).isEmpty();
        assertThat(empty.goodsNet()).isEqualTo("0,00");
    }

    @Test
    void theColumnSaysShippedForADropshipDelivery() {
        // when / then
        assertThat(items(dropship(), ADMIN).receivedColumnKey()).isEqualTo("deliveries.details.items.column.shipped");
        assertThat(items(warehouse(), ADMIN).receivedColumnKey()).isEqualTo("deliveries.details.items.column.received");
        assertThat(items(received(dropship()), ADMIN).products().get(0).allocations().get(0).stateKey())
                .isEqualTo("deliveries.details.items.state.shipped");
    }

    @Test
    void theGrossFooterIsEmptyWhileTheVatIsUnset() {
        // given
        Delivery delivery = warehouse();
        delivery.setTax(0.0);

        // when
        DeliveryPageModel.ItemsCard card = items(delivery, ADMIN);

        // then
        assertThat(card.goodsNet()).isEqualTo("5 084,00");
        assertThat(card.goodsGross()).isNull();
    }
}
