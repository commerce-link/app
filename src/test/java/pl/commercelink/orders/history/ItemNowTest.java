package pl.commercelink.orders.history;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RMAItemStatus;
import pl.commercelink.warehouse.api.ItemCondition;
import pl.commercelink.warehouse.api.WarehouseItemView;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ItemNowTest {

    @Test
    void anRmaInProgressWinsOverEverything() {
        // given
        RmaLine rma = rma("rma-1", RMAItemStatus.SentForRepair, at(9, 21));

        // when
        ItemNow now = ItemNow.resolve(List.of(order("o-1", OrderStatus.Completed, FulfilmentStatus.Delivered, at(9, 2))),
                List.of(rma), List.of(warehouse(FulfilmentStatus.Delivered)));

        // then
        assertThat(now.state()).isEqualTo(ItemNow.State.IN_RMA);
        assertThat(now.rmaLine()).isSameAs(rma);
    }

    @Test
    void theNewestOpenOrderHoldingTheUnitWinsOverAStaleWarehouseRow() {
        // given: the reservation took the unit from the warehouse, the warehouse row still lists the number
        OrderLine resale = order("o-2", OrderStatus.Assembly, FulfilmentStatus.Reserved, at(9, 29));

        // when
        ItemNow now = ItemNow.resolve(List.of(order("o-1", OrderStatus.Completed, FulfilmentStatus.Returned, at(9, 2)), resale),
                List.of(rma("rma-1", RMAItemStatus.MovedToWarehouse, at(9, 21))), List.of(warehouse(FulfilmentStatus.Delivered)));

        // then
        assertThat(now.state()).isEqualTo(ItemNow.State.IN_ORDER);
        assertThat(now.orderLine()).isSameAs(resale);
    }

    @Test
    void aCompletedOrWithTheCustomerDeliveredOrderMeansAtTheCustomer() {
        // then
        assertThat(ItemNow.resolve(List.of(order("o-1", OrderStatus.Delivered, FulfilmentStatus.Delivered, at(9, 2))),
                List.of(), List.of()).state()).isEqualTo(ItemNow.State.AT_CUSTOMER);
    }

    @Test
    void aReturnedItemOrACancelledOrderDoesNotHoldTheUnit() {
        // when
        ItemNow now = ItemNow.resolve(List.of(
                        order("o-1", OrderStatus.Completed, FulfilmentStatus.Returned, at(9, 2)),
                        order("o-2", OrderStatus.Cancelled, FulfilmentStatus.Reserved, at(9, 5))),
                List.of(rma("rma-1", RMAItemStatus.MovedToWarehouse, at(9, 3))), List.of(warehouse(FulfilmentStatus.Delivered)));

        // then
        assertThat(now.state()).isEqualTo(ItemNow.State.IN_STOCK);
    }

    @Test
    void mapsTheWarehouseStatusesAndPrefersStockOverOtherRows() {
        // then
        assertThat(ItemNow.resolve(List.of(), List.of(), List.of(warehouse(FulfilmentStatus.Destroyed), warehouse(FulfilmentStatus.Delivered)))
                .state()).isEqualTo(ItemNow.State.IN_STOCK);
        assertThat(ItemNow.resolve(List.of(), List.of(), List.of(warehouse(FulfilmentStatus.Reserved))).state())
                .isEqualTo(ItemNow.State.RESERVED);
        assertThat(ItemNow.resolve(List.of(), List.of(), List.of(warehouse(FulfilmentStatus.Ordered))).state())
                .isEqualTo(ItemNow.State.INBOUND);
        assertThat(ItemNow.resolve(List.of(), List.of(), List.of(warehouse(FulfilmentStatus.Destroyed))).state())
                .isEqualTo(ItemNow.State.WAREHOUSE_OTHER);
    }

    @Test
    void nothingKnownIsUnknown() {
        // then
        assertThat(ItemNow.resolve(List.of(), List.of(), List.of()).state()).isEqualTo(ItemNow.State.UNKNOWN);
    }

    @Test
    void survivesRecordsWithoutDatesOrStatuses() {
        // given
        OrderLine undated = order("o-1", OrderStatus.Assembly, FulfilmentStatus.Reserved, null);
        undated.order().setStatus(OrderStatus.Assembly);
        RmaLine noStatus = rma("rma-1", null, null);

        // when
        ItemNow now = ItemNow.resolve(List.of(undated), List.of(noStatus), List.of());

        // then
        assertThat(now.state()).isEqualTo(ItemNow.State.IN_ORDER);
    }

    public static LocalDateTime at(int month, int day) {
        return LocalDateTime.of(2026, month, day, 12, 0);
    }

    public static OrderLine order(String id, OrderStatus status, FulfilmentStatus itemStatus, LocalDateTime orderedAt) {
        Order order = new Order();
        order.setStoreId("store-1");
        order.setOrderId(id);
        order.setStatus(status);
        order.setOrderedAt(orderedAt);
        OrderItem item = new OrderItem();
        item.setOrderId(id);
        item.setStatus(itemStatus);
        return new OrderLine(order, item);
    }

    public static RmaLine rma(String id, RMAItemStatus status, LocalDateTime createdAt) {
        RMA rma = new RMA();
        rma.setStoreId("store-1");
        rma.setRmaId(id);
        rma.setCreatedAt(createdAt);
        RMAItem item = new RMAItem();
        item.setRmaId(id);
        item.setStatus(status);
        return new RmaLine(rma, item);
    }

    public static WarehouseItemView warehouse(FulfilmentStatus status) {
        return new WarehouseItemView("store-1", "w-" + status, "Dysk SSD", "590", "MFN-1", null, 1, status, ItemCondition.OpenBox);
    }
}
