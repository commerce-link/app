package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.warehouse.api.WarehouseItemView;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuiltInStockQuerySerialTest {

    @Mock private WarehouseRepository warehouseRepository;
    @InjectMocks private BuiltInStockQueryService service;

    @Test
    void keepsOnlyItemsWhoseSerialListHoldsTheWholeNumber() {
        // given: the scan matches fragments too
        when(warehouseRepository.findBySerialNoCandidates("store-1", "1234"))
                .thenReturn(List.of(item("w-1", "1234,9999"), item("w-2", "AB12345")));

        // when
        List<WarehouseItemView> items = service.findAllBySerialNo("store-1", "1234");

        // then
        assertThat(items).extracting(WarehouseItemView::getItemId).containsExactly("w-1");
        assertThat(items.get(0).getName()).isEqualTo("Dysk SSD");
        assertThat(items.get(0).getStatus()).isEqualTo(FulfilmentStatus.Delivered);
    }

    static WarehouseItem item(String itemId, String serials) {
        WarehouseItem item = new WarehouseItem();
        item.setStoreId("store-1");
        item.setItemId(itemId);
        item.setName("Dysk SSD");
        item.setQty(1);
        item.setSerialNo(serials);
        item.setStatus(FulfilmentStatus.Delivered);
        return item;
    }
}
