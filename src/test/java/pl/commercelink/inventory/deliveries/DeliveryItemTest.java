package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class DeliveryItemTest {

    @Test
    void allocationsOfOneEanFormOneLine() {
        // given
        Allocation order = allocation("item-1", "Dysk 2TB", "5900000000001", "MFN-X", 10.0, 1);
        Allocation stock = allocation("item-2", "Dysk 2TB", "5900000000001", "MFN-X", 10.0, 2);

        // when
        List<DeliveryItem> lines = DeliveryItem.groupAndUnify(List.of(order, stock));

        // then
        assertThat(lines).singleElement().satisfies(line -> {
            assertThat(line.getOrderedQty()).isEqualTo(3);
            assertThat(line.getAllocations()).containsExactly(order, stock);
        });
    }

    @Test
    void twoProductsUnderOneCodeFormTwoLines() {
        // given
        Allocation disk = allocation("item-1", "Dysk 2TB", "5900000000001", "MFN-X", 10.0, 1);
        Allocation fan = allocation("item-2", "Wentylator", "5900000000002", "MFN-X", 4.0, 2);

        // when
        List<DeliveryItem> lines = DeliveryItem.groupAndUnify(List.of(disk, fan));

        // then
        assertThat(lines).extracting(DeliveryItem::getEan, DeliveryItem::getName, DeliveryItem::getMfn,
                        DeliveryItem::getUnitCost, DeliveryItem::getOrderedQty)
                .containsExactlyInAnyOrder(
                        tuple("5900000000001", "Dysk 2TB", "MFN-X", 10.0, 1),
                        tuple("5900000000002", "Wentylator", "MFN-X", 4.0, 2));
    }

    private static Allocation allocation(String itemId, String name, String ean, String mfn, double unitCost, int qty) {
        Allocation allocation = new Allocation();
        allocation.setKey(new AllocationKey(null, itemId, "Warehouse"));
        allocation.setType(AllocationType.Warehouse);
        allocation.setName(name);
        allocation.setEan(ean);
        allocation.setMfn(mfn);
        allocation.setUnitCost(unitCost);
        allocation.setQty(qty);
        allocation.setInAllocation(true);
        return allocation;
    }
}
