package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrderLifecycleEventPublisher;
import pl.commercelink.orders.OrderLifecycleEventType;
import pl.commercelink.orders.OrderStatus;
import org.springframework.ui.ExtendedModelMap;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrdersManager;
import pl.commercelink.web.dtos.OrderItemsForm;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.PositionGroup;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentTrackingStatus;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.shipping.ShipmentTrackingSubscriber;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrdersControllerTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";

    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private OrderItemsRepository orderItemsRepository;
    @Mock
    private MessageSource messageSource;
    @Mock
    private OrdersManager ordersManager;
    @Mock
    private OrderLifecycle orderLifecycle;
    @Mock
    private OrderLifecycleEventPublisher orderLifecycleEventPublisher;
    @Mock
    private RedirectAttributes redirectAttributes;
    @Mock
    private ShipmentTrackingSubscriber shipmentTrackingSubscriber;

    @InjectMocks
    private OrdersController ordersController;

    private MockedStatic<CustomSecurityContext> securityStub;

    @BeforeEach
    void setupStoreId() {
        securityStub = mockStatic(CustomSecurityContext.class);
        securityStub.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
    }

    @AfterEach
    void tearDown() {
        securityStub.close();
    }

    @Test
    @DisplayName("updateAddressDetails persists new billing details on order that is not yet invoiced")
    void updateAddressDetailsSetsBillingDetailsOnNonInvoicedOrderAndSaves() {
        // given
        Order existingOrder = orderBase();
        BillingDetails newBilling = new BillingDetails();
        newBilling.setCity("Krakow");
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setBillingDetails(newBilling);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);

        // when
        String view = ordersController.updateAddressDetails(ORDER_ID, "billing", updatedPayload, redirectAttributes, Locale.ENGLISH);

        // then
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(ordersRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getBillingDetails().getCity()).isEqualTo("Krakow");
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
    }

    @Test
    @DisplayName("updateAddressDetails sets locked-billing flash message and does not save when order is already invoiced")
    void updateAddressDetailsSetsFlashMessageAndSkipsSaveWhenOrderAlreadyInvoiced() {
        // given
        Order existingOrder = invoicedOrder();
        BillingDetails newBilling = new BillingDetails();
        newBilling.setCity("Krakow");
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setBillingDetails(newBilling);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);
        when(messageSource.getMessage(eq("error.message.billing.details.locked"), any(), eq(Locale.ENGLISH)))
                .thenReturn("Billing locked");

        // when
        ordersController.updateAddressDetails(ORDER_ID, "billing", updatedPayload, redirectAttributes, Locale.ENGLISH);

        // then
        verify(ordersRepository, never()).save(any());
        verify(redirectAttributes).addFlashAttribute(eq("errorMessage"), eq("Billing locked"));
    }

    @Test
    @DisplayName("updateAddressDetails persists new shipping details when type is shipping")
    void updateAddressDetailsSetsShippingDetailsAndSavesWhenTypeIsShipping() {
        // given
        Order existingOrder = orderBase();
        ShippingDetails newShipping = new ShippingDetails();
        newShipping.setCity("Wroclaw");
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setShippingDetails(newShipping);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);

        // when
        ordersController.updateAddressDetails(ORDER_ID, "shipping", updatedPayload, redirectAttributes, Locale.ENGLISH);

        // then
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(ordersRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getShippingDetails().getCity()).isEqualTo("Wroclaw");
    }

    @Test
    @DisplayName("updateShipments publishes ShipmentCreated when the saved order has a shipment with shipping data")
    void updateShipmentsPublishesShipmentCreatedWhenShippingDataPresent() {
        // given
        Order existingOrder = orderBase();
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setCarrier("DPD");
        shipment.setTrackingNo("TRACK-1");
        shipment.setShippedAt(LocalDateTime.now());
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setShipments(List.of(shipment));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);

        // when
        ordersController.updateShipments(ORDER_ID, updatedPayload, null);

        // then
        verify(orderLifecycleEventPublisher).publish(existingOrder, OrderLifecycleEventType.ShipmentCreated);
    }

    @Test
    @DisplayName("updateShipments keeps a shipment that only carries a manually entered collection point")
    void updateShipmentsKeepsShipmentWithCollectionPointOnly() {
        // given
        Order existingOrder = orderBase();
        Shipment dispatched = new Shipment(ShipmentType.Courier);
        dispatched.setCarrier("DPD");
        dispatched.setTrackingNo("TRACK-1");
        dispatched.setShippedAt(LocalDateTime.now());
        Shipment pointOnly = new Shipment(ShipmentType.PickupPoint);
        pointOnly.setCollectionPointCode("KRA01M");
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setShipments(List.of(dispatched, pointOnly));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);

        // when
        ordersController.updateShipments(ORDER_ID, updatedPayload, null);

        // then
        assertThat(existingOrder.getShipments()).hasSize(2);
        assertThat(existingOrder.getShipments().get(1).getCollectionPointCode()).isEqualTo("KRA01M");
    }

    @Test
    @DisplayName("updateShipments does not publish ShipmentCreated when no shipment has shipping data")
    void updateShipmentsSkipsPublishWhenShippingDataAbsent() {
        // given
        Order existingOrder = orderBase();
        Shipment shipment = new Shipment(ShipmentType.PersonalCollection);
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setShipments(List.of(shipment));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);

        // when
        ordersController.updateShipments(ORDER_ID, updatedPayload, null);

        // then
        verify(orderLifecycleEventPublisher, never()).publish(any(), any());
    }

    @Test
    @DisplayName("updateShipments publishes ShipmentCreated when the saved order has a personal-collection shipment")
    void updateShipmentsPublishesShipmentCreatedWhenCollectionDataPresent() {
        // given
        Order existingOrder = orderBase();
        Shipment shipment = new Shipment(ShipmentType.PersonalCollection);
        shipment.setShippedAt(LocalDateTime.now());
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setShipments(List.of(shipment));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);

        // when
        ordersController.updateShipments(ORDER_ID, updatedPayload, null);

        // then
        verify(orderLifecycleEventPublisher).publish(existingOrder, OrderLifecycleEventType.ShipmentCreated);
    }

    @Test
    @DisplayName("updateShipments leaves existing shipments untouched when the payload carries no shipments list")
    void updateShipmentsKeepsExistingShipmentsWhenPayloadShipmentsIsNull() {
        // given
        Order existingOrder = orderBase();
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setCarrier("DPD");
        shipment.setTrackingNo("TRACK-1");
        shipment.setShippedAt(LocalDateTime.now());
        existingOrder.setShipments(List.of(shipment));
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setShipments(null);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);

        // when
        ordersController.updateShipments(ORDER_ID, updatedPayload, null);

        // then
        assertThat(existingOrder.getShipments()).containsExactly(shipment);
        verifyNoInteractions(orderLifecycleEventPublisher);
    }

    @Test
    @DisplayName("updateShipments does not republish ShipmentCreated when resubmitted shipment data is unchanged")
    void updateShipmentsDoesNotRepublishWhenShipmentDataUnchanged() {
        // given
        LocalDateTime shippedAt = LocalDateTime.now();
        Order existingOrder = orderBase();
        Shipment existingShipment = new Shipment(ShipmentType.Courier);
        existingShipment.setCarrier("DPD");
        existingShipment.setTrackingNo("TRACK-1");
        existingShipment.setShippedAt(shippedAt);
        existingOrder.setShipments(List.of(existingShipment));
        Shipment resubmittedShipment = new Shipment(ShipmentType.Courier);
        resubmittedShipment.setCarrier("DPD");
        resubmittedShipment.setTrackingNo("TRACK-1");
        resubmittedShipment.setShippedAt(shippedAt);
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setShipments(List.of(resubmittedShipment));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);

        // when
        ordersController.updateShipments(ORDER_ID, updatedPayload, null);

        // then
        verify(orderLifecycleEventPublisher, never()).publish(any(), any());
    }

    @Test
    @DisplayName("updateShipments does not republish ShipmentCreated when only sub-minute shippedAt precision differs")
    void updateShipmentsDoesNotRepublishWhenShippedAtLosesSubMinutePrecision() {
        // given
        Order existingOrder = orderBase();
        Shipment existingShipment = new Shipment(ShipmentType.Courier);
        existingShipment.setCarrier("DPD");
        existingShipment.setTrackingNo("TRACK-1");
        existingShipment.setShippedAt(LocalDateTime.of(2026, 7, 2, 14, 31, 22));
        existingOrder.setShipments(List.of(existingShipment));
        Shipment resubmittedShipment = new Shipment(ShipmentType.Courier);
        resubmittedShipment.setCarrier("DPD");
        resubmittedShipment.setTrackingNo("TRACK-1");
        resubmittedShipment.setShippedAt(LocalDateTime.of(2026, 7, 2, 14, 31));
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setShipments(List.of(resubmittedShipment));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);

        // when
        ordersController.updateShipments(ORDER_ID, updatedPayload, null);

        // then
        verify(orderLifecycleEventPublisher, never()).publish(any(), any());
    }

    @Test
    @DisplayName("updateShipments does not publish ShipmentCreated when the order is cancelled")
    void updateShipmentsDoesNotPublishWhenOrderIsCancelled() {
        // given
        Order existingOrder = orderBase();
        existingOrder.setStatus(OrderStatus.Cancelled);
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setCarrier("DPD");
        shipment.setTrackingNo("TRACK-1");
        shipment.setShippedAt(LocalDateTime.now());
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setShipments(List.of(shipment));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);

        // when
        String view = ordersController.updateShipments(ORDER_ID, updatedPayload, null);

        // then
        verify(orderLifecycleEventPublisher, never()).publish(any(), any());
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
    }

    @Test
    @DisplayName("updateShipments republishes ShipmentCreated when the resubmitted tracking number changes")
    void updateShipmentsPublishesWhenTrackingNumberChanges() {
        // given
        LocalDateTime shippedAt = LocalDateTime.now();
        Order existingOrder = orderBase();
        Shipment existingShipment = new Shipment(ShipmentType.Courier);
        existingShipment.setCarrier("DPD");
        existingShipment.setTrackingNo("TRACK-1");
        existingShipment.setShippedAt(shippedAt);
        existingOrder.setShipments(List.of(existingShipment));
        Shipment resubmittedShipment = new Shipment(ShipmentType.Courier);
        resubmittedShipment.setCarrier("DPD");
        resubmittedShipment.setTrackingNo("TRACK-2");
        resubmittedShipment.setShippedAt(shippedAt);
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setShipments(List.of(resubmittedShipment));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);

        // when
        ordersController.updateShipments(ORDER_ID, updatedPayload, null);

        // then
        verify(orderLifecycleEventPublisher).publish(existingOrder, OrderLifecycleEventType.ShipmentCreated);
    }

    @Test
    @DisplayName("updateShipments keeps tracking subscription of an unchanged shipment and subscribes new ones")
    void updateShipmentsKeepsTrackingSubscriptionOfUnchangedShipmentAndSubscribesNewOnes() {
        // given
        Order existingOrder = orderBase();
        Shipment tracked = new Shipment(ShipmentType.Courier);
        tracked.setCarrier("DPD");
        tracked.setTrackingNo("PKG-1");
        tracked.setShippedAt(LocalDateTime.now());
        tracked.markTrackingActive("21037943", LocalDateTime.now());
        existingOrder.setShipments(new ArrayList<>(List.of(tracked)));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);
        Shipment resubmittedFirst = new Shipment(ShipmentType.Courier);
        resubmittedFirst.setCarrier("DPD");
        resubmittedFirst.setTrackingNo("PKG-1");
        resubmittedFirst.setShippedAt(LocalDateTime.now());
        Shipment resubmittedSecond = new Shipment(ShipmentType.Courier);
        resubmittedSecond.setCarrier("DPD");
        resubmittedSecond.setTrackingNo("PKG-2");
        resubmittedSecond.setShippedAt(LocalDateTime.now());
        Order updatedPayload = new Order(STORE_ID);
        updatedPayload.setShipments(List.of(resubmittedFirst, resubmittedSecond));

        // when
        ordersController.updateShipments(ORDER_ID, updatedPayload, null);

        // then
        assertThat(existingOrder.getShipments()).hasSize(2);
        assertThat(existingOrder.getShipments().get(0).getTrackingSubscriptionStatus()).isEqualTo(ShipmentTrackingStatus.ACTIVE);
        assertThat(existingOrder.getShipments().get(1).hasTrackingSubscription()).isFalse();
        verify(shipmentTrackingSubscriber).subscribe(STORE_ID, existingOrder);
        InOrder order = inOrder(shipmentTrackingSubscriber, orderLifecycle);
        order.verify(shipmentTrackingSubscriber).subscribe(STORE_ID, existingOrder);
        order.verify(orderLifecycle).update(existingOrder);
    }

    @Test
    void savingItemAppliesThePostedServiceFlag() {
        // given
        OrderItem item = existingOrderItem("Obudowy", false);
        OrderItem posted = postedOrderItem("Obudowy");
        posted.setService(true);

        // when
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, new ExtendedModelMap());

        // then
        assertThat(item.isService()).isTrue();
        verify(orderItemsRepository).save(item);
    }

    @Test
    void savingItemCanClearTheServiceFlag() {
        // given
        OrderItem item = existingOrderItem("Obudowy", true);
        OrderItem posted = postedOrderItem("Obudowy");
        posted.setService(false);

        // when
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, new ExtendedModelMap());

        // then
        assertThat(item.isService()).isFalse();
    }

    @Test
    void savingItemWithBlankCategoryNormalizesItToNull() {
        // given
        OrderItem item = existingOrderItem(null, true);
        OrderItem posted = postedOrderItem("");
        posted.setService(true);

        // when
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, new ExtendedModelMap());

        // then
        assertThat(item.getCategory()).isNull();
        assertThat(item.isService()).isTrue();
    }

    @Test
    void turningServiceFlagOnMarksItemDeliveredAndMovesItToServiceBand() {
        // given
        OrderItem item = existingOrderItem("Obudowy", false);
        item.setPosition(2);
        OrderItem posted = postedOrderItem("Obudowy");
        posted.setService(true);

        // when
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, new ExtendedModelMap());

        // then
        assertThat(item.isService()).isTrue();
        assertThat(item.getStatus()).isEqualTo(FulfilmentStatus.Delivered);
        assertThat(item.getDeliveryId()).isEqualTo(OrderItem.GENERIC_WAREHOUSE_ORDER_NO);
        assertThat(item.getPosition()).isEqualTo(PositionGroup.SERVICE_GROUP_START + 2);
    }

    @Test
    void turningServiceFlagOffResetsWarehouseFulfilledItemToNewProduct() {
        // given
        OrderItem item = existingOrderItem("Usługi", true);
        item.setDeliveryId(OrderItem.GENERIC_WAREHOUSE_ORDER_NO);
        item.setStatus(FulfilmentStatus.Delivered);
        item.setPosition(PositionGroup.SERVICE_GROUP_START + 2);
        OrderItem posted = postedOrderItem("Usługi");
        posted.setService(false);

        // when
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, new ExtendedModelMap());

        // then
        assertThat(item.isService()).isFalse();
        assertThat(item.getStatus()).isEqualTo(FulfilmentStatus.New);
        assertThat(item.getDeliveryId()).isNull();
        assertThat(item.getPosition()).isEqualTo(2);
    }

    @Test
    void postedServiceFlagIsIgnoredWhenItemHasSupplierAllocation() {
        // given
        OrderItem item = existingOrderItem("Laptopy", false);
        item.setEan("EAN-1");
        item.setManufacturerCode("MFN-1");
        item.setDeliveryId("delivery-1");
        item.setStatus(FulfilmentStatus.Ordered);
        item.setPosition(2);
        OrderItem posted = postedOrderItem("Laptopy");
        posted.setService(true);

        // when
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, new ExtendedModelMap());

        // then
        assertThat(item.isService()).isFalse();
        assertThat(item.getStatus()).isEqualTo(FulfilmentStatus.Ordered);
        assertThat(item.getDeliveryId()).isEqualTo("delivery-1");
        assertThat(item.getPosition()).isEqualTo(2);
    }

    @Test
    void postedPriceIsAppliedToAllocatedItemWhenOrderHasNoDocuments() {
        // given
        OrderItem item = existingOrderItem("Laptopy", false);
        item.setEan("EAN-1");
        item.setManufacturerCode("MFN-1");
        item.setDeliveryId("delivery-1");
        item.setStatus(FulfilmentStatus.Ordered);
        OrderItem posted = postedOrderItem("Laptopy");
        posted.setPrice(150.0);

        // when
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, new ExtendedModelMap());

        // then
        assertThat(item.getPrice()).isEqualTo(150.0);
        assertThat(item.getStatus()).isEqualTo(FulfilmentStatus.Ordered);
        assertThat(item.getDeliveryId()).isEqualTo("delivery-1");
    }

    @Test
    void postedPriceIsIgnoredWhenOrderHasDocuments() {
        // given
        OrderItem item = existingOrderItem("Laptopy", false);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(invoicedOrder());
        OrderItem posted = postedOrderItem("Laptopy");
        posted.setPrice(150.0);

        // when
        ordersController.saveOrderItem(ORDER_ID, item.getItemId(), posted, new ExtendedModelMap());

        // then
        assertThat(item.getPrice()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("removeDocument removes the invoice and saves the order without triggering the lifecycle")
    void removeDocumentRemovesInvoiceAndSavesWithoutLifecycle() {
        // given
        Order existingOrder = invoicedOrder();
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);

        // when
        String view = ordersController.removeDocument(ORDER_ID, pl.commercelink.documents.DocumentType.InvoiceVat, "FV/1/2026", redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(existingOrder.getDocuments()).isEmpty();
        verify(ordersRepository).save(existingOrder);
        verifyNoInteractions(orderLifecycle);
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
    }

    @Test
    @DisplayName("removeDocument sets error flash and does not save when the order is completed")
    void removeDocumentRejectsCompletedOrder() {
        // given
        Order existingOrder = invoicedOrder();
        existingOrder.setStatus(OrderStatus.Completed);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);
        when(messageSource.getMessage(eq("error.message.document.cannot.be.removed"), any(), eq(Locale.ENGLISH)))
                .thenReturn("Cannot remove");

        // when
        ordersController.removeDocument(ORDER_ID, pl.commercelink.documents.DocumentType.InvoiceVat, "FV/1/2026", redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(existingOrder.getDocuments()).hasSize(1);
        verify(ordersRepository, never()).save(any());
        verify(redirectAttributes).addFlashAttribute(eq("errorMessage"), eq("Cannot remove"));
    }

    @Test
    @DisplayName("removeDocument sets error flash and does not save when the document is a warehouse document")
    void removeDocumentRejectsWarehouseDocument() {
        // given
        Order existingOrder = orderBase();
        existingOrder.addDocument(new pl.commercelink.documents.Document(
                "wz-1", "WZ/1/2026", null, pl.commercelink.documents.DocumentType.GoodsIssue));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);
        when(messageSource.getMessage(eq("error.message.document.cannot.be.removed"), any(), eq(Locale.ENGLISH)))
                .thenReturn("Cannot remove");

        // when
        ordersController.removeDocument(ORDER_ID, pl.commercelink.documents.DocumentType.GoodsIssue, "WZ/1/2026", redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(existingOrder.getDocuments()).hasSize(1);
        verify(ordersRepository, never()).save(any());
        verify(redirectAttributes).addFlashAttribute(eq("errorMessage"), eq("Cannot remove"));
    }

    @Test
    void clearSupplierReleasesAnAllocatedItem() {
        // given
        OrderItem item = existingOrderItem("Laptopy", false);
        item.setEan("EAN-1");
        item.setManufacturerCode("MFN-1");
        item.setDeliveryId("Acme");
        item.setStatus(FulfilmentStatus.Allocation);
        when(orderItemsRepository.findById(ORDER_ID, item.getItemId())).thenReturn(item);

        // when
        String view = ordersController.clearSupplier(ORDER_ID, item.getItemId(), redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(item.getStatus()).isEqualTo(FulfilmentStatus.New);
        assertThat(item.getDeliveryId()).isNull();
        assertThat(item.getClaimedDeliveryId()).isNull();
        verify(orderItemsRepository).save(item);
        verifyNoInteractions(redirectAttributes);
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
    }

    @Test
    void clearSupplierKeepsWorkingForNewItems() {
        // given
        OrderItem item = existingOrderItem("Laptopy", false);
        item.setDeliveryId("Acme");
        item.setStatus(FulfilmentStatus.New);
        when(orderItemsRepository.findById(ORDER_ID, item.getItemId())).thenReturn(item);

        // when
        String view = ordersController.clearSupplier(ORDER_ID, item.getItemId(), redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(item.getStatus()).isEqualTo(FulfilmentStatus.New);
        assertThat(item.getDeliveryId()).isNull();
        verify(orderItemsRepository).save(item);
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
    }

    @Test
    void clearSupplierRefusesAnOrderedItem() {
        // given
        OrderItem item = existingOrderItem("Laptopy", false);
        item.setEan("EAN-1");
        item.setManufacturerCode("MFN-1");
        item.setDeliveryId("d-1");
        item.setClaimedDeliveryId("d-1");
        item.setStatus(FulfilmentStatus.Ordered);
        when(orderItemsRepository.findById(ORDER_ID, item.getItemId())).thenReturn(item);
        when(messageSource.getMessage(eq("order.item.clear.assign.blocked"), any(), eq(Locale.ENGLISH)))
                .thenReturn("blocked");

        // when
        String view = ordersController.clearSupplier(ORDER_ID, item.getItemId(), redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(item.getStatus()).isEqualTo(FulfilmentStatus.Ordered);
        assertThat(item.getDeliveryId()).isEqualTo("d-1");
        assertThat(item.getClaimedDeliveryId()).isEqualTo("d-1");
        verify(orderItemsRepository, never()).save(any());
        verify(redirectAttributes).addFlashAttribute("errorMessage", "blocked");
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
    }

    @Test
    void clearSupplierRefusesADeliveredItem() {
        // given
        OrderItem item = existingOrderItem("Laptopy", false);
        item.setEan("EAN-1");
        item.setManufacturerCode("MFN-1");
        item.setDeliveryId("d-1");
        item.setClaimedDeliveryId("d-1");
        item.setStatus(FulfilmentStatus.Delivered);
        when(orderItemsRepository.findById(ORDER_ID, item.getItemId())).thenReturn(item);
        when(messageSource.getMessage(eq("order.item.clear.assign.blocked"), any(), eq(Locale.ENGLISH)))
                .thenReturn("blocked");

        // when
        String view = ordersController.clearSupplier(ORDER_ID, item.getItemId(), redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(item.getStatus()).isEqualTo(FulfilmentStatus.Delivered);
        assertThat(item.getDeliveryId()).isEqualTo("d-1");
        assertThat(item.getClaimedDeliveryId()).isEqualTo("d-1");
        verify(orderItemsRepository, never()).save(any());
        verify(redirectAttributes).addFlashAttribute("errorMessage", "blocked");
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
    }

    @Test
    void assignSupplierRefusesAnOrderedItem() {
        // given
        OrderItem item = existingOrderItem("Laptopy", false);
        item.setEan("EAN-1");
        item.setManufacturerCode("MFN-1");
        item.setDeliveryId("d-1");
        item.setClaimedDeliveryId("d-1");
        item.setStatus(FulfilmentStatus.Ordered);
        when(orderItemsRepository.findById(ORDER_ID, item.getItemId())).thenReturn(item);
        when(messageSource.getMessage(eq("order.item.assign.supplier.blocked"), any(), eq(Locale.ENGLISH)))
                .thenReturn("blocked");

        // when
        String view = ordersController.assignSupplier(ORDER_ID, item.getItemId(), "MFN-2", 50.0, "d-2",
                new ExtendedModelMap(), redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(item.getStatus()).isEqualTo(FulfilmentStatus.Ordered);
        assertThat(item.getManufacturerCode()).isEqualTo("MFN-1");
        assertThat(item.getDeliveryId()).isEqualTo("d-1");
        assertThat(item.getClaimedDeliveryId()).isEqualTo("d-1");
        verify(orderItemsRepository, never()).save(any());
        verify(redirectAttributes).addFlashAttribute("errorMessage", "blocked");
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
    }

    @Test
    @DisplayName("updateOrderInfo rejects a fulfilment type change once a product item is allocated")
    void updateOrderInfoRejectsFulfilmentTypeChangeWhenAnItemIsAllocated() {
        // given
        Order existingOrder = orderBase();
        existingOrder.setStatus(OrderStatus.New);
        existingOrder.setFulfilmentType(pl.commercelink.orders.fulfilment.FulfilmentType.WarehouseFulfilment);
        OrderItem allocated = new OrderItem();
        allocated.setStatus(FulfilmentStatus.Allocation);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(allocated));
        when(messageSource.getMessage(eq("order.fulfilment.type.locked"), any(), eq(Locale.ENGLISH)))
                .thenReturn("Locked");
        Order payload = new Order(STORE_ID);
        payload.setStatus(OrderStatus.New);
        payload.setFulfilmentType(pl.commercelink.orders.fulfilment.FulfilmentType.DirectToConsumer);

        // when
        String view = ordersController.updateOrderInfo(ORDER_ID, payload, redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
        verify(redirectAttributes).addFlashAttribute("errorMessage", "Locked");
        verify(orderLifecycle, never()).update(any());
        assertThat(existingOrder.getFulfilmentType())
                .isEqualTo(pl.commercelink.orders.fulfilment.FulfilmentType.WarehouseFulfilment);
    }

    @Test
    @DisplayName("updateOrderInfo applies a fulfilment type change while every product item is New")
    void updateOrderInfoAppliesFulfilmentTypeChangeWhenAllProductsAreNew() {
        // given
        Order existingOrder = orderBase();
        existingOrder.setStatus(OrderStatus.New);
        existingOrder.setFulfilmentType(pl.commercelink.orders.fulfilment.FulfilmentType.WarehouseFulfilment);
        OrderItem fresh = new OrderItem();
        fresh.setStatus(FulfilmentStatus.New);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(fresh));
        Order payload = new Order(STORE_ID);
        payload.setStatus(OrderStatus.New);
        payload.setFulfilmentType(pl.commercelink.orders.fulfilment.FulfilmentType.DirectToConsumer);

        // when
        ordersController.updateOrderInfo(ORDER_ID, payload, redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(existingOrder.getFulfilmentType())
                .isEqualTo(pl.commercelink.orders.fulfilment.FulfilmentType.DirectToConsumer);
        verify(orderLifecycle).update(existingOrder);
    }

    @Test
    @DisplayName("updateOrderInfo keeps the fulfilment type when the disabled field is absent from the form")
    void updateOrderInfoKeepsFulfilmentTypeWhenTheFieldIsAbsent() {
        // given
        Order existingOrder = orderBase();
        existingOrder.setStatus(OrderStatus.New);
        existingOrder.setFulfilmentType(pl.commercelink.orders.fulfilment.FulfilmentType.DirectToConsumer);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(existingOrder);
        Order payload = new Order(STORE_ID);
        payload.setStatus(OrderStatus.New);
        payload.setFulfilmentType(null);

        // when
        ordersController.updateOrderInfo(ORDER_ID, payload, redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(existingOrder.getFulfilmentType())
                .isEqualTo(pl.commercelink.orders.fulfilment.FulfilmentType.DirectToConsumer);
        verifyNoInteractions(orderItemsRepository);
    }

    private OrderItem existingOrderItem(String category, boolean service) {
        OrderItem item = new OrderItem(ORDER_ID, category, "pozycja", 1, 100.0, null, false);
        item.setService(service);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(orderBase());
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(item));
        return item;
    }

    private OrderItem postedOrderItem(String category) {
        OrderItem posted = new OrderItem();
        posted.setCategory(category);
        posted.setName("pozycja");
        posted.setQty(1);
        return posted;
    }

    private Order orderBase() {
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        return order;
    }

    private Order invoicedOrder() {
        Order order = orderBase();
        order.addDocument(new pl.commercelink.documents.Document(
                "fv-1", "FV/1/2026", "https://example.com/fv/1",
                pl.commercelink.documents.DocumentType.InvoiceVat));
        return order;
    }

    @Test
    void movingDropshipItemsToTheWarehouseIsReportedAsSkipped() {
        // given
        OrderItem selected = new OrderItem();
        selected.setItemId("item-1");
        selected.setSelected(true);
        OrderItemsForm form = new OrderItemsForm(List.of(selected));
        when(ordersManager.moveOrderItemsToTheWarehouse(STORE_ID, ORDER_ID, List.of("item-1")))
                .thenReturn(new OrdersManager.Result(new Order(STORE_ID), List.of(), 1));
        when(messageSource.getMessage(eq("order.items.action.move.warehouse.dropship.error"), any(), any()))
                .thenReturn("skipped");

        // when
        String view = ordersController.moveSelectedItemsToTheWarehouse(ORDER_ID, form, redirectAttributes,
                Locale.forLanguageTag("pl"));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
        verify(redirectAttributes).addFlashAttribute("errorMessage", "skipped");
    }
}
