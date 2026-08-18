package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.financials.ExchangeRates;
import pl.commercelink.warehouse.builtin.WarehouseAllocationsManager;
import pl.commercelink.web.dtos.DeliveryCreationForm;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeliveryCreationServiceTest {

    private static final String STORE_ID = "store-1";
    private static final String PROVIDER = "Elko";

    @Mock
    private DeliveriesRepository deliveriesRepository;
    @Mock
    private OrderAllocationsManager orderAllocationsManager;
    @Mock
    private WarehouseAllocationsManager warehouseAllocationsManager;
    @Mock
    private ExchangeRates exchangeRates;

    @InjectMocks
    private DeliveryCreationService service;

    @Test
    void completePendingFillsDeliveryAndCommitsAllocations() {
        // given
        Delivery delivery = new Delivery();
        delivery.setDeliveryId("delivery-1");
        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_PENDING);
        delivery.setPendingOrderForm("{\"provider\":\"Elko\"}");

        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setExternalDeliveryId("ELKO-1");
        form.setProvider(PROVIDER);
        form.setEstimatedDeliveryAt(LocalDate.now());
        form.setShippingCost(15.0);
        DeliveryItem item = new DeliveryItem();
        item.setRequestedQty(2);
        item.setUnitCost(90.0);
        form.getItems().add(item);

        // when
        service.completePending(STORE_ID, delivery, form);

        // then
        assertEquals("ELKO-1", delivery.getExternalDeliveryId());
        assertNull(delivery.getOrderStatus());
        assertNull(delivery.getPendingOrderForm());
        assertEquals(180.0, delivery.getTotalCost());
        verify(deliveriesRepository).save(delivery);
        verify(orderAllocationsManager).commit(eq(STORE_ID), eq(delivery.getDeliveryId()), any(), eq(form.getItems()));
        verify(warehouseAllocationsManager).commit(STORE_ID, delivery.getDeliveryId(), form.getProvider(), form.getItems());
    }

    @Test
    void runStillCreatesDeliveryFromScratch() {
        // given
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setExternalDeliveryId("ELKO-2");
        form.setProvider(PROVIDER);
        form.setEstimatedDeliveryAt(LocalDate.now());
        form.setShippingCost(10.0);
        form.setPaymentCost(5.0);
        DeliveryItem item = new DeliveryItem();
        item.setRequestedQty(3);
        item.setUnitCost(50.0);
        form.getItems().add(item);

        // when
        String deliveryId = service.run(STORE_ID, form, true);

        // then
        assertNotNull(deliveryId);
        ArgumentCaptor<Delivery> deliveryCaptor = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveriesRepository).save(deliveryCaptor.capture());
        Delivery saved = deliveryCaptor.getValue();
        assertEquals(deliveryId, saved.getDeliveryId());
        assertEquals("ELKO-2", saved.getExternalDeliveryId());
        assertTrue(saved.isManaged());
        assertEquals(165.0, saved.getTotalCost());
        verify(orderAllocationsManager).commit(eq(STORE_ID), eq(deliveryId), any(), eq(form.getItems()));
        verify(warehouseAllocationsManager).commit(STORE_ID, deliveryId, form.getProvider(), form.getItems());
    }
}
