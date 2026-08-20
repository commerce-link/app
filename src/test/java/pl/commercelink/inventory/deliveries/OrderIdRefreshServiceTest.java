package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.api.SupplierOrderException;
import pl.commercelink.inventory.supplier.api.SupplierProvider;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderIdRefreshServiceTest {

    @Mock
    private DeliveriesRepository deliveriesRepository;
    @Mock
    private StoreSupplierProviderResolver providerResolver;
    @Mock
    private SupplierProvider supplierProvider;
    @InjectMocks
    private OrderIdRefreshService service;

    private OrderIdRefreshEventRequest request() {
        return new OrderIdRefreshEventRequest("s1", "d1", "IncomGroup", "ref-1");
    }

    private Delivery delivery() {
        Delivery delivery = new Delivery("s1", null, "IncomGroup");
        delivery.setPurchaseRef("ref-1");
        delivery.setExternalDeliveryId("22");
        return delivery;
    }

    @Test
    void updatesExternalIdWhenSupplierConfirmsNewNumber() {
        // given
        Delivery delivery = delivery();
        when(deliveriesRepository.findById("s1", "d1")).thenReturn(delivery);
        when(providerResolver.resolve("s1", "IncomGroup")).thenReturn(supplierProvider);
        when(supplierProvider.confirmedOrderId("ref-1")).thenReturn(Optional.of("ZS-123456"));

        // when
        service.refresh(request(), 1);

        // then
        assertEquals("ZS-123456", delivery.getExternalDeliveryId());
        assertTrue(delivery.getEvents().stream().anyMatch(e -> "DELIVERY_ORDER_ID_CONFIRMED".equals(e.getName())));
        verify(deliveriesRepository).save(delivery);
    }

    @Test
    void skipsSaveWhenConfirmedNumberAlreadySet() {
        // given
        Delivery delivery = delivery();
        delivery.setExternalDeliveryId("ZS-123456");
        when(deliveriesRepository.findById("s1", "d1")).thenReturn(delivery);
        when(providerResolver.resolve("s1", "IncomGroup")).thenReturn(supplierProvider);
        when(supplierProvider.confirmedOrderId("ref-1")).thenReturn(Optional.of("ZS-123456"));

        // when
        service.refresh(request(), 2);

        // then
        verify(deliveriesRepository, never()).save(any());
    }

    @Test
    void throwsForRetryWhenNumberStillPending() {
        // given
        when(deliveriesRepository.findById("s1", "d1")).thenReturn(delivery());
        when(providerResolver.resolve("s1", "IncomGroup")).thenReturn(supplierProvider);
        when(supplierProvider.confirmedOrderId("ref-1")).thenReturn(Optional.empty());

        // when / then
        assertThrows(ExternalOrderIdPendingException.class, () -> service.refresh(request(), 1));
        verify(deliveriesRepository, never()).save(any());
    }

    @Test
    void supplierFailureCountsAsStillPending() {
        // given
        when(deliveriesRepository.findById("s1", "d1")).thenReturn(delivery());
        when(providerResolver.resolve("s1", "IncomGroup")).thenReturn(supplierProvider);
        when(supplierProvider.confirmedOrderId("ref-1")).thenThrow(new SupplierOrderException("boom"));

        // when / then
        assertThrows(ExternalOrderIdPendingException.class, () -> service.refresh(request(), 1));
        verify(deliveriesRepository, never()).save(any());
    }

    @Test
    void lastAttemptRecordsUnconfirmedEventAndStillThrows() {
        // given
        Delivery delivery = delivery();
        when(deliveriesRepository.findById("s1", "d1")).thenReturn(delivery);
        when(providerResolver.resolve("s1", "IncomGroup")).thenReturn(supplierProvider);
        when(supplierProvider.confirmedOrderId("ref-1")).thenReturn(Optional.empty());

        // when / then
        assertThrows(ExternalOrderIdPendingException.class,
                () -> service.refresh(request(), OrderIdRefreshService.MAX_SQS_ATTEMPTS));
        assertTrue(delivery.getEvents().stream().anyMatch(e -> "DELIVERY_ORDER_ID_UNCONFIRMED".equals(e.getName())));
        verify(deliveriesRepository).save(delivery);
    }

    @Test
    void attemptsBeyondMaxAreConsumedSilently() {
        // when
        service.refresh(request(), OrderIdRefreshService.MAX_SQS_ATTEMPTS + 1);

        // then
        verifyNoInteractions(deliveriesRepository, providerResolver);
    }

    @Test
    void missingDeliveryIsConsumedSilently() {
        // given
        when(deliveriesRepository.findById("s1", "d1")).thenReturn(null);

        // when
        service.refresh(request(), 1);

        // then
        verify(deliveriesRepository, never()).save(any());
    }

    @Test
    void purchaseRefMismatchIsConsumedSilently() {
        // given
        Delivery delivery = delivery();
        delivery.setPurchaseRef("other-ref");
        when(deliveriesRepository.findById("s1", "d1")).thenReturn(delivery);

        // when
        service.refresh(request(), 1);

        // then
        verify(deliveriesRepository, never()).save(any());
        verifyNoInteractions(providerResolver);
    }

    @Test
    void unresolvableProviderRecordsUnconfirmedEventAndConsumes() {
        // given
        Delivery delivery = delivery();
        when(deliveriesRepository.findById("s1", "d1")).thenReturn(delivery);
        when(providerResolver.resolve("s1", "IncomGroup")).thenReturn(null);

        // when
        service.refresh(request(), 1);

        // then
        assertTrue(delivery.getEvents().stream().anyMatch(e -> "DELIVERY_ORDER_ID_UNCONFIRMED".equals(e.getName())));
        verify(deliveriesRepository).save(delivery);
    }

    @Test
    void throwingProviderResolutionRecordsUnconfirmedEventAndConsumes() {
        // given
        Delivery delivery = delivery();
        when(deliveriesRepository.findById("s1", "d1")).thenReturn(delivery);
        when(providerResolver.resolve("s1", "IncomGroup")).thenThrow(new RuntimeException("secret missing"));

        // when
        service.refresh(request(), 1);

        // then
        assertTrue(delivery.getEvents().stream().anyMatch(e -> "DELIVERY_ORDER_ID_UNCONFIRMED".equals(e.getName())));
        verify(deliveriesRepository).save(delivery);
    }

    @Test
    void confirmingClearsTheProvisionalFlag() {
        // given
        Delivery delivery = delivery();
        delivery.setExternalDeliveryIdProvisional(true);
        when(deliveriesRepository.findById("s1", "d1")).thenReturn(delivery);
        when(providerResolver.resolve("s1", "IncomGroup")).thenReturn(supplierProvider);
        when(supplierProvider.confirmedOrderId("ref-1")).thenReturn(Optional.of("ZS-123456"));

        // when
        service.refresh(request(), 1);

        // then
        assertFalse(delivery.isExternalDeliveryIdProvisional());
        verify(deliveriesRepository).save(delivery);
    }

    @Test
    void equalConfirmedIdStillClearsALingeringProvisionalFlag() {
        // given
        Delivery delivery = delivery();
        delivery.setExternalDeliveryId("ZS-123456");
        delivery.setExternalDeliveryIdProvisional(true);
        when(deliveriesRepository.findById("s1", "d1")).thenReturn(delivery);
        when(providerResolver.resolve("s1", "IncomGroup")).thenReturn(supplierProvider);
        when(supplierProvider.confirmedOrderId("ref-1")).thenReturn(Optional.of("ZS-123456"));

        // when
        service.refresh(request(), 1);

        // then
        assertFalse(delivery.isExternalDeliveryIdProvisional());
        assertFalse(delivery.getEvents().stream().anyMatch(e -> "DELIVERY_ORDER_ID_CONFIRMED".equals(e.getName())));
        verify(deliveriesRepository).save(delivery);
    }

    @Test
    void manualRefreshConfirmsAndClearsFlag() {
        // given
        Delivery delivery = delivery();
        delivery.setExternalDeliveryIdProvisional(true);
        when(deliveriesRepository.findById("s1", "d1")).thenReturn(delivery);
        when(providerResolver.resolve("s1", "IncomGroup")).thenReturn(supplierProvider);
        when(supplierProvider.confirmedOrderId("ref-1")).thenReturn(Optional.of("ZS-123456"));

        // when
        OrderIdRefreshService.ManualRefreshOutcome outcome = service.refreshManually("s1", "d1");

        // then
        assertEquals(OrderIdRefreshService.ManualRefreshOutcome.CONFIRMED, outcome);
        assertEquals("ZS-123456", delivery.getExternalDeliveryId());
        assertFalse(delivery.isExternalDeliveryIdProvisional());
        verify(deliveriesRepository).save(delivery);
    }

    @Test
    void manualRefreshReportsStillPendingWithoutSaving() {
        // given
        Delivery delivery = delivery();
        delivery.setExternalDeliveryIdProvisional(true);
        when(deliveriesRepository.findById("s1", "d1")).thenReturn(delivery);
        when(providerResolver.resolve("s1", "IncomGroup")).thenReturn(supplierProvider);
        when(supplierProvider.confirmedOrderId("ref-1")).thenReturn(Optional.empty());

        // when / then
        assertEquals(OrderIdRefreshService.ManualRefreshOutcome.STILL_PENDING, service.refreshManually("s1", "d1"));
        verify(deliveriesRepository, never()).save(any());
    }

    @Test
    void manualRefreshTreatsSupplierFailureAsStillPending() {
        // given
        Delivery delivery = delivery();
        delivery.setExternalDeliveryIdProvisional(true);
        when(deliveriesRepository.findById("s1", "d1")).thenReturn(delivery);
        when(providerResolver.resolve("s1", "IncomGroup")).thenReturn(supplierProvider);
        when(supplierProvider.confirmedOrderId("ref-1")).thenThrow(new SupplierOrderException("boom"));

        // when / then
        assertEquals(OrderIdRefreshService.ManualRefreshOutcome.STILL_PENDING, service.refreshManually("s1", "d1"));
    }

    @Test
    void manualRefreshUnavailableForMissingDeliveryOrRefOrProvider() {
        // given
        Delivery noRef = delivery();
        noRef.setPurchaseRef(null);
        when(deliveriesRepository.findById("s1", "missing")).thenReturn(null);
        when(deliveriesRepository.findById("s1", "noref")).thenReturn(noRef);
        Delivery unresolvable = delivery();
        unresolvable.setExternalDeliveryIdProvisional(true);
        when(deliveriesRepository.findById("s1", "d1")).thenReturn(unresolvable);
        when(providerResolver.resolve("s1", "IncomGroup")).thenThrow(new RuntimeException("secret missing"));

        // when / then
        assertEquals(OrderIdRefreshService.ManualRefreshOutcome.UNAVAILABLE, service.refreshManually("s1", "missing"));
        assertEquals(OrderIdRefreshService.ManualRefreshOutcome.UNAVAILABLE, service.refreshManually("s1", "noref"));
        assertEquals(OrderIdRefreshService.ManualRefreshOutcome.UNAVAILABLE, service.refreshManually("s1", "d1"));
    }

    @Test
    void manualRefreshUnavailableWhenDeliveryIsNotProvisional() {
        // given
        Delivery delivery = delivery();
        delivery.setExternalDeliveryIdProvisional(false);
        when(deliveriesRepository.findById("s1", "d1")).thenReturn(delivery);

        // when / then
        assertEquals(OrderIdRefreshService.ManualRefreshOutcome.UNAVAILABLE, service.refreshManually("s1", "d1"));
        verifyNoInteractions(providerResolver);
        verify(deliveriesRepository, never()).save(any());
    }

    @Test
    void manualRefreshUnavailableWhenRepositoryThrows() {
        // given
        when(deliveriesRepository.findById("s1", "d1")).thenThrow(new RuntimeException("boom"));

        // when / then
        assertEquals(OrderIdRefreshService.ManualRefreshOutcome.UNAVAILABLE, service.refreshManually("s1", "d1"));
        verify(deliveriesRepository, never()).save(any());
    }
}
