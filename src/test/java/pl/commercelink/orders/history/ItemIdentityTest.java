package pl.commercelink.orders.history;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.warehouse.api.ItemCondition;
import pl.commercelink.warehouse.api.WarehouseItemView;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.orders.history.ItemNowTest.at;
import static pl.commercelink.orders.history.ItemNowTest.order;
import static pl.commercelink.orders.history.ItemNowTest.rma;

class ItemIdentityTest {

    @Test
    void takesTheProductFromTheNewestOrderLine() {
        // given
        OrderLine older = product(order("o-1", OrderStatus.Completed, FulfilmentStatus.Returned, at(9, 2)), "Old name", "590", "MFN-1");
        OrderLine newer = product(order("o-2", OrderStatus.Assembly, FulfilmentStatus.Reserved, at(9, 29)), "Samsung 980 PRO", "590", "MFN-1");

        // when
        ItemIdentity identity = ItemIdentity.of(List.of(older, newer), List.of(), List.of());

        // then
        assertThat(identity).isEqualTo(new ItemIdentity("Samsung 980 PRO", "590", "MFN-1", 1));
    }

    @Test
    void fallsBackToTheRmaThenTheWarehouse() {
        // given
        RmaLine rma = rma("rma-1", null, at(9, 1));
        rma.item().setName("Z RMA");
        WarehouseItemView stock = new WarehouseItemView("store-1", "w-1", "Z magazynu", "590", "MFN-1", null, 1,
                FulfilmentStatus.Delivered, ItemCondition.Sealed);

        // then
        assertThat(ItemIdentity.of(List.of(), List.of(rma), List.of(stock)).name()).isEqualTo("Z RMA");
        assertThat(ItemIdentity.of(List.of(), List.of(), List.of(stock)).name()).isEqualTo("Z magazynu");
    }

    @Test
    void countsDifferentProductsByEanOrManufacturerCode() {
        // given
        OrderLine a = product(order("o-1", OrderStatus.Completed, FulfilmentStatus.Delivered, at(9, 1)), "A", "590-A", "MFN-A");
        OrderLine b = product(order("o-2", OrderStatus.Completed, FulfilmentStatus.Delivered, at(9, 2)), "B", "590-B", "MFN-A");
        OrderLine c = product(order("o-3", OrderStatus.Completed, FulfilmentStatus.Delivered, at(9, 3)), "C", null, "MFN-C");

        // when
        ItemIdentity identity = ItemIdentity.of(List.of(a, b, c), List.of(), List.of());

        // then
        assertThat(identity.productCount()).isEqualTo(2);
        assertThat(ItemAmbiguity.of(List.of(a, b, c), identity)).isEqualTo(new ItemAmbiguity(true, 3, 2));
    }

    @Test
    void twoOpenOrdersHoldingTheUnitAtOnceAreAmbiguous() {
        // given
        OrderLine a = product(order("o-1", OrderStatus.Assembly, FulfilmentStatus.Reserved, at(9, 1)), "A", "590", "M");
        OrderLine b = product(order("o-2", OrderStatus.New, FulfilmentStatus.Allocation, at(9, 2)), "A", "590", "M");

        // then
        assertThat(ItemAmbiguity.of(List.of(a, b), ItemIdentity.of(List.of(a, b), List.of(), List.of())).ambiguous()).isTrue();
    }

    @Test
    void aSaleAfterAReturnIsNotAmbiguous() {
        // given
        OrderLine sold = product(order("o-1", OrderStatus.Completed, FulfilmentStatus.Returned, at(9, 1)), "A", "590", "M");
        OrderLine resold = product(order("o-2", OrderStatus.Assembly, FulfilmentStatus.Reserved, at(9, 20)), "A", "590", "M");

        // then
        assertThat(ItemAmbiguity.of(List.of(sold, resold), ItemIdentity.of(List.of(sold, resold), List.of(), List.of())).ambiguous()).isFalse();
    }

    static OrderLine product(OrderLine line, String name, String ean, String mfn) {
        line.item().setName(name);
        line.item().setEan(ean);
        line.item().setManufacturerCode(mfn);
        return line;
    }
}
