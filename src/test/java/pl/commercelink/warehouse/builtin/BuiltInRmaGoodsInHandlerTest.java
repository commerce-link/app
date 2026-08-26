package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.taxonomy.Categories;
import pl.commercelink.warehouse.api.GoodsReceiptItem;
import pl.commercelink.warehouse.api.ItemCondition;
import pl.commercelink.warehouse.api.RmaGoodsInRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuiltInRmaGoodsInHandlerTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private BuiltInDocumentCreationService documentCreationService;
    @Mock
    private WarehouseItemFactory warehouseItemFactory;

    @Test
    void mergesReceivedItemIntoWarehouseItemWithTheSameCondition() {
        // given
        WarehouseItem openBox = aDeliveredItem(1);
        openBox.setCondition(ItemCondition.OpenBox);
        openBox.setSerialNo("SN-1");
        when(warehouseRepository.findByDeliveryIdAndStatuses(STORE_ID, "delivery-1", List.of(FulfilmentStatus.Delivered)))
                .thenReturn(List.of(openBox));
        GoodsReceiptItem received = GoodsReceiptItem.from(anRmaItem("SN-2"), ItemCondition.OpenBox);

        // when
        handler().receive(request(received, false), false);

        // then
        assertEquals(2, openBox.getQty());
        assertEquals("SN-1,SN-2", openBox.getSerialNo());
        verify(warehouseRepository).save(openBox);
        verify(warehouseItemFactory, never()).create(any(), any(GoodsReceiptItem.class));
    }

    @Test
    void createsNewWarehouseItemWhenNoItemWithTheSameConditionExists() {
        // given
        WarehouseItem sealed = aDeliveredItem(1);
        when(warehouseRepository.findByDeliveryIdAndStatuses(STORE_ID, "delivery-1", List.of(FulfilmentStatus.Delivered)))
                .thenReturn(List.of(sealed));
        GoodsReceiptItem received = GoodsReceiptItem.from(anRmaItem("SN-2"), ItemCondition.Damaged);
        WarehouseItem created = aDeliveredItem(1);
        when(warehouseItemFactory.create(STORE_ID, received)).thenReturn(created);

        // when
        handler().receive(request(received, false), false);

        // then
        assertEquals(1, sealed.getQty());
        verify(warehouseRepository).save(created);
    }

    @Test
    void movesItemRequiringRepairToRmaWithoutMerging() {
        // given
        GoodsReceiptItem received = GoodsReceiptItem.from(anRmaItem("SN-2"), ItemCondition.Damaged);
        WarehouseItem created = aDeliveredItem(1);
        when(warehouseItemFactory.create(STORE_ID, received)).thenReturn(created);

        // when
        handler().receive(request(received, true), false);

        // then
        assertEquals(FulfilmentStatus.InRMA, created.getStatus());
        verify(warehouseRepository).save(created);
        verify(warehouseRepository, never()).findByDeliveryIdAndStatuses(any(), any(), any());
    }

    private BuiltInRmaGoodsInHandler handler() {
        return new BuiltInRmaGoodsInHandler(STORE_ID, warehouseRepository, documentCreationService, warehouseItemFactory);
    }

    private RmaGoodsInRequest request(GoodsReceiptItem item, boolean itemsRequireRepair) {
        return RmaGoodsInRequest.builder()
                .storeId(STORE_ID)
                .items(List.of(item))
                .itemsRequireRepair(itemsRequireRepair)
                .build();
    }

    private RMAItem anRmaItem(String serialNo) {
        RMAItem rmaItem = new RMAItem();
        rmaItem.setDeliveryId("delivery-1");
        rmaItem.setEan("5901234123457");
        rmaItem.setMfn("MFN-1");
        rmaItem.setName("Widget");
        rmaItem.setQty(1);
        rmaItem.setCost(20.0);
        rmaItem.setTax(1.23);
        rmaItem.setSerialNo(serialNo);
        return rmaItem;
    }

    private WarehouseItem aDeliveredItem(int qty) {
        WarehouseItem item = new WarehouseItem(STORE_ID, "delivery-1", Categories.UNCATEGORIZED, "Widget", "5901234123457", "MFN-1", 20.0, qty);
        item.setStatus(FulfilmentStatus.Delivered);
        return item;
    }
}
