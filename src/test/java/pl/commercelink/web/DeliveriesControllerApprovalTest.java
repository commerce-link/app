package pl.commercelink.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.inventory.deliveries.Allocation;
import pl.commercelink.inventory.deliveries.AllocationKey;
import pl.commercelink.inventory.deliveries.AllocationType;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveryItem;
import pl.commercelink.inventory.deliveries.DeliveryOrderStatus;
import pl.commercelink.inventory.deliveries.DeliveriesManager;
import pl.commercelink.inventory.deliveries.DeliveriesPlanningService;
import pl.commercelink.inventory.deliveries.DeliveriesQueryService;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.DeliveryOrderedQtyUpdateService;
import pl.commercelink.inventory.deliveries.DeliveryReceptionService;
import pl.commercelink.inventory.deliveries.DeliveryTaxResolver;
import pl.commercelink.inventory.deliveries.DeliveryType;
import pl.commercelink.inventory.deliveries.DropshipOrderLocator;
import pl.commercelink.inventory.deliveries.InvoiceSyncResult;
import pl.commercelink.inventory.deliveries.InvoiceSyncService;
import pl.commercelink.inventory.deliveries.SupplierPurchaseService;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.SupplierDeliveryAddress;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.SupplierOrderOption;
import pl.commercelink.inventory.supplier.api.SupplierOrderOptionChoice;
import pl.commercelink.inventory.supplier.api.SupplierOrderOptionsContext;
import pl.commercelink.inventory.supplier.api.SupplierType;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.PaymentDirection;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.warehouse.RestockSuggestionService;
import pl.commercelink.web.dtos.AddPaymentForm;
import pl.commercelink.web.payments.PaymentsReturn;
import pl.commercelink.web.dtos.DeliveryAllocationsForm;
import pl.commercelink.web.dtos.DeliveryCreationForm;
import pl.commercelink.web.dtos.PickerOption;
import pl.commercelink.web.dtos.RoutedOrderView;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveriesControllerApprovalTest {

    private static final String STORE_ID = "store-1";
    private static final String DELIVERY_ID = "delivery-1";
    private static final LocalDate ESTIMATED_DELIVERY_AT = LocalDate.of(2026, 9, 1);
    private static final String PROVIDER = "Action";

    @Mock
    private SupplierPurchaseService supplierPurchaseService;

    @Mock
    private MessageSource messageSource;

    @Mock
    private RedirectAttributes redirectAttributes;

    @Mock
    private DeliveriesQueryService deliveriesQueryService;

    @Mock
    private DeliveriesManager deliveriesManager;

    @Mock
    private DeliveryOrderedQtyUpdateService deliveryOrderedQtyUpdateService;

    @Mock
    private DeliveryReceptionService deliveryReceptionService;

    @Mock
    private DeliveriesRepository deliveriesRepository;

    @Mock
    private StoresRepository storesRepository;

    @Mock
    private DeliveriesPlanningService deliveriesPlanningService;

    @Mock
    private RestockSuggestionService restockSuggestionService;

    @Mock
    private DeliveryTaxResolver deliveryTaxResolver;

    @Mock
    private OrdersRepository ordersRepository;

    @Mock
    private DropshipOrderLocator dropshipOrderLocator;

    @Mock
    private SupplierRegistry supplierRegistry;

    @Mock
    private SupplierLabels supplierLabels;

    @Mock
    private InvoiceSyncService invoiceSynchronizationService;

    @InjectMocks
    private DeliveriesController deliveriesController;

    @BeforeEach
    void setUpSupplierLabels() {
        lenient().when(supplierLabels.forStoreId(any()))
                .thenReturn(new SupplierLabels(mock(StoresRepository.class)).forStore(null));
    }

    @Test
    void approvingRedirectsBackToTheStoreScopedDeliveryDetails() {
        // given
        when(supplierPurchaseService.approve(STORE_ID, DELIVERY_ID, "17200617", Map.of()))
                .thenReturn(OperationResult.success(DELIVERY_ID));

        // when
        String view = deliveriesController.approvePurchase(STORE_ID, DELIVERY_ID, "17200617", Map.of(),
                redirectAttributes, Locale.forLanguageTag("pl"));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-1/deliveries/details?deliveryId=delivery-1");
        verify(supplierPurchaseService).approve(eq(STORE_ID), eq(DELIVERY_ID), eq("17200617"), eq(Map.of()));
        verify(redirectAttributes, never()).addFlashAttribute(eq("errorMessage"), any());
    }

    @Test
    void approvalFailureAddsFlashErrorMessageAndRedirectsToTheRealisationScreen() {
        // given
        when(supplierPurchaseService.approve(STORE_ID, DELIVERY_ID, null, Map.of()))
                .thenReturn(OperationResult.failure("deliveries.approval.error.state"));
        when(messageSource.getMessage(eq("deliveries.approval.error.state"), eq(null), eq(Locale.ENGLISH)))
                .thenReturn("Delivery is no longer awaiting approval");

        // when
        String view = deliveriesController.approvePurchase(STORE_ID, DELIVERY_ID, null, Map.of(),
                redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-1/deliveries/delivery-1/approval");
        verify(redirectAttributes).addFlashAttribute("errorMessage", "Delivery is no longer awaiting approval");
    }

    @Test
    void rejectingRedirectsToTheDeliveriesListWithASuccessMessage() {
        // given
        when(supplierPurchaseService.reject(STORE_ID, DELIVERY_ID, "out of stock"))
                .thenReturn(OperationResult.success(DELIVERY_ID));
        when(messageSource.getMessage(eq("deliveries.approval.rejected.success"), eq(null), eq(Locale.forLanguageTag("pl"))))
                .thenReturn("Zgłoszenie odrzucone. Pozycje wróciły do puli dostawcy, dostawa została usunięta.");

        // when
        String view = deliveriesController.rejectPurchase(STORE_ID, DELIVERY_ID, "out of stock",
                redirectAttributes, Locale.forLanguageTag("pl"));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/deliveries");
        verify(supplierPurchaseService).reject(eq(STORE_ID), eq(DELIVERY_ID), eq("out of stock"));
        verify(redirectAttributes).addFlashAttribute("successMessage",
                "Zgłoszenie odrzucone. Pozycje wróciły do puli dostawcy, dostawa została usunięta.");
        verify(redirectAttributes, never()).addFlashAttribute(eq("errorMessage"), any());
    }

    @Test
    void rejectionFailureAddsFlashErrorMessageAndRedirectsToTheRealisationScreen() {
        // given
        when(supplierPurchaseService.reject(STORE_ID, DELIVERY_ID, "reason"))
                .thenReturn(OperationResult.failure("deliveries.approval.error.state"));
        when(messageSource.getMessage(eq("deliveries.approval.error.state"), eq(null), eq(Locale.ENGLISH)))
                .thenReturn("Delivery is no longer awaiting approval");

        // when
        String view = deliveriesController.rejectPurchase(STORE_ID, DELIVERY_ID, "reason",
                redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-1/deliveries/delivery-1/approval");
        verify(redirectAttributes).addFlashAttribute("errorMessage", "Delivery is no longer awaiting approval");
    }

    @Test
    void purchaseConfirmationHidesDeliveryAddressesWhenApprovalIsRequired() {
        // given
        DeliveryCreationForm form = new DeliveryCreationForm();
        Model model = new ConcurrentModel();
        when(supplierPurchaseService.isOrderingAvailable(STORE_ID, PROVIDER)).thenReturn(true);
        when(supplierPurchaseService.requiresApproval(STORE_ID, PROVIDER)).thenReturn(true);

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);

            // when
            String view = deliveriesController.validatePurchase(PROVIDER, form, model);

            // then
            assertThat(view).isEqualTo("deliveryPurchaseConfirmation");
            assertThat(model.getAttribute("requiresApproval")).isEqualTo(true);
            assertThat(model.containsAttribute("deliveryAddresses")).isFalse();
            assertThat(model.containsAttribute("deliveryAddressOptions")).isFalse();
            verify(supplierPurchaseService, never()).deliveryAddresses(any(), any());
        }
    }

    @Test
    void purchaseConfirmationExposesDeliveryAddressesWhenApprovalIsNotRequired() {
        // given
        DeliveryCreationForm form = new DeliveryCreationForm();
        Model model = new ConcurrentModel();
        SupplierDeliveryAddress address = new SupplierDeliveryAddress("addr-1", "Street 1", "Warsaw", "00-001", "PL");
        when(supplierPurchaseService.isOrderingAvailable(STORE_ID, PROVIDER)).thenReturn(true);
        when(supplierPurchaseService.requiresApproval(STORE_ID, PROVIDER)).thenReturn(false);
        when(supplierPurchaseService.deliveryAddresses(STORE_ID, PROVIDER)).thenReturn(List.of(address));

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);

            // when
            String view = deliveriesController.validatePurchase(PROVIDER, form, model);

            // then
            assertThat(view).isEqualTo("deliveryPurchaseConfirmation");
            assertThat(model.containsAttribute("requiresApproval")).isFalse();
            assertThat(model.getAttribute("deliveryAddresses")).isEqualTo(List.of(address));
            assertThat(model.getAttribute("deliveryAddressOptions"))
                    .isEqualTo(List.of(new PickerOption("addr-1", address.label())));
        }
    }

    @Test
    void purchaseConfirmationExposesOrderOptionsWithDefaultsPreselected() {
        // given
        DeliveryCreationForm form = new DeliveryCreationForm();
        Model model = new ConcurrentModel();
        SupplierOrderOption laneOption = new SupplierOrderOption("lane", "Lane",
                List.of(new SupplierOrderOptionChoice("fast", "Fast", null)), "fast", true);
        when(supplierPurchaseService.isOrderingAvailable(STORE_ID, PROVIDER)).thenReturn(true);
        when(supplierPurchaseService.requiresApproval(STORE_ID, PROVIDER)).thenReturn(false);
        when(supplierPurchaseService.deliveryAddresses(STORE_ID, PROVIDER)).thenReturn(List.of());
        when(supplierPurchaseService.orderOptions(eq(STORE_ID), eq(PROVIDER), any()))
                .thenReturn(List.of(laneOption));

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);

            // when
            String view = deliveriesController.validatePurchase(PROVIDER, form, model);

            // then
            assertThat(view).isEqualTo("deliveryPurchaseConfirmation");
            assertThat((List<?>) model.getAttribute("orderOptions")).hasSize(1);
            assertThat(model.getAttribute("selectedOptions")).isEqualTo(Map.of("lane", "fast"));
            assertThat(model.containsAttribute("orderOptionsError")).isFalse();
        }
    }

    @Test
    void purchaseConfirmationBlocksWhenOptionsCannotBeFetched() {
        // given
        DeliveryCreationForm form = new DeliveryCreationForm();
        Model model = new ConcurrentModel();
        when(supplierPurchaseService.isOrderingAvailable(STORE_ID, PROVIDER)).thenReturn(true);
        when(supplierPurchaseService.requiresApproval(STORE_ID, PROVIDER)).thenReturn(false);
        when(supplierPurchaseService.deliveryAddresses(STORE_ID, PROVIDER)).thenReturn(List.of());
        when(supplierPurchaseService.orderOptions(eq(STORE_ID), eq(PROVIDER), any()))
                .thenThrow(new RuntimeException("supplier unavailable"));

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);

            // when
            String view = deliveriesController.validatePurchase(PROVIDER, form, model);

            // then
            assertThat(view).isEqualTo("deliveryPurchaseConfirmation");
            assertThat(model.getAttribute("orderOptionsError")).isEqualTo("supplier unavailable");
            assertThat((List<?>) model.getAttribute("orderOptions")).isEmpty();
        }
    }

    @Test
    void deliveryDetailsNoLongerExposesApprovalAddressesForSuperAdmin() {
        // given
        Delivery delivery = new Delivery(STORE_ID, null, PROVIDER);
        delivery.setOrderStatus(DeliveryOrderStatus.AWAITING_APPROVAL);
        Model model = new ConcurrentModel();
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, DELIVERY_ID)).thenReturn(delivery);

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);

            // when
            String view = deliveriesController.showDeliveryDetailsForSuperAdmin(
                    STORE_ID, DELIVERY_ID, model, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo("deliveryDetails");
            assertThat(model.containsAttribute("approvalAddresses")).isFalse();
            assertThat(model.containsAttribute("approvalAddressOptions")).isFalse();
            verify(supplierPurchaseService, never()).deliveryAddressesForDelivery(any(), any());
        }
    }

    @Test
    void deliveryDetailsHidesApprovalAddressesWhenViewerIsNotSuperAdmin() {
        // given
        Delivery delivery = new Delivery(STORE_ID, null, PROVIDER);
        delivery.setOrderStatus(DeliveryOrderStatus.AWAITING_APPROVAL);
        Model model = new ConcurrentModel();
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, DELIVERY_ID)).thenReturn(delivery);

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            String view = deliveriesController.showDeliveryDetails(DELIVERY_ID, model, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo("deliveryDetails");
            assertThat(model.containsAttribute("approvalAddresses")).isFalse();
            assertThat(model.containsAttribute("approvalAddressOptions")).isFalse();
            verify(supplierPurchaseService, never()).deliveryAddressesForDelivery(any(), any());
        }
    }

    @Test
    void dropshipDeliveryDetailsResolveTheContactThroughTheLocator() {
        // given
        Delivery delivery = new Delivery(STORE_ID, null, PROVIDER);
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setType(DeliveryType.DROPSHIP);
        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_PENDING);
        Model model = new ConcurrentModel();
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(dropshipOrderLocator.locate(DELIVERY_ID)).thenReturn(Optional.of("order-1"));
        Order order = new Order();
        order.setOrderId("order-1");
        ShippingDetails shippingDetails = new ShippingDetails();
        shippingDetails.setName("Jan");
        order.setShippingDetails(shippingDetails);
        when(ordersRepository.findById(STORE_ID, "order-1")).thenReturn(order);

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            String view = deliveriesController.showDeliveryDetails(DELIVERY_ID, model, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo("deliveryDetails");
            assertThat(model.getAttribute("dropshipContact")).isEqualTo(shippingDetails);
            assertThat(model.getAttribute("dropshipShipment")).isNull();
        }
    }

    @Test
    void dropshipDeliveryDetailsRenderWithoutContactWhenTheLocatorHasNoAnswerYet() {
        // given
        Delivery delivery = new Delivery(STORE_ID, null, PROVIDER);
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setType(DeliveryType.DROPSHIP);
        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_PENDING);
        Model model = new ConcurrentModel();
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(dropshipOrderLocator.locate(DELIVERY_ID)).thenReturn(Optional.empty());

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            String view = deliveriesController.showDeliveryDetails(DELIVERY_ID, model, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo("deliveryDetails");
            assertThat(model.getAttribute("dropshipContact")).isNull();
            assertThat(model.getAttribute("dropshipShipment")).isNull();
            verifyNoInteractions(ordersRepository);
        }
    }

    @Test
    void dropshipDeliveryDetailsRenderWithoutContactWhenTheLocatorInvariantIsViolated() {
        // given
        Delivery delivery = new Delivery(STORE_ID, null, PROVIDER);
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setType(DeliveryType.DROPSHIP);
        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_PENDING);
        Model model = new ConcurrentModel();
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(dropshipOrderLocator.locate(DELIVERY_ID)).thenThrow(
                new IllegalStateException("Dropship delivery " + DELIVERY_ID + " is claimed by orders [a, b]"));

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            String view = deliveriesController.showDeliveryDetails(DELIVERY_ID, model, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo("deliveryDetails");
            assertThat(model.getAttribute("dropshipContact")).isNull();
            assertThat(model.getAttribute("dropshipShipment")).isNull();
            verifyNoInteractions(ordersRepository);
        }
    }

    @Test
    void showDeliveryDetailsRedirectsToListWhenDeliveryIsGone() {
        // given
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, DELIVERY_ID)).thenReturn(null);
        when(messageSource.getMessage(eq("deliveries.error.notFound"), eq(null), eq(Locale.ENGLISH)))
                .thenReturn("This delivery does not exist or has been removed.");
        Model model = new ConcurrentModel();

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);

            // when
            String view = deliveriesController.showDeliveryDetails(DELIVERY_ID, model, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/deliveries");
            verify(redirectAttributes).addFlashAttribute("errorMessage",
                    "This delivery does not exist or has been removed.");
        }
    }

    @Test
    void showDeliveryDetailsForSuperAdminRedirectsToListWhenDeliveryIsGone() {
        // given
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, DELIVERY_ID)).thenReturn(null);
        when(messageSource.getMessage(eq("deliveries.error.notFound"), eq(null), eq(Locale.forLanguageTag("pl"))))
                .thenReturn("Dostawa nie istnieje lub została usunięta.");
        Model model = new ConcurrentModel();

        // when
        String view = deliveriesController.showDeliveryDetailsForSuperAdmin(
                STORE_ID, DELIVERY_ID, model, redirectAttributes, Locale.forLanguageTag("pl"));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/deliveries");
        verify(redirectAttributes).addFlashAttribute("errorMessage", "Dostawa nie istnieje lub została usunięta.");
    }

    @Test
    void approvalScreenRendersForADeliveryAwaitingApproval() {
        // given
        Delivery delivery = new Delivery();
        delivery.setStoreId(STORE_ID);
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setProvider(PROVIDER);
        delivery.setOrderStatus(DeliveryOrderStatus.AWAITING_APPROVAL);
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(supplierPurchaseService.deliveryAddressesForDelivery(STORE_ID, DELIVERY_ID))
                .thenReturn(List.of(new SupplierDeliveryAddress("1", "ul. Testowa 1", "Kraków", "31-140", "PL")));
        Model model = new ConcurrentModel();

        // when
        String view = deliveriesController.showApprovalScreen(STORE_ID, DELIVERY_ID, model, redirectAttributes);

        // then
        assertThat(view).isEqualTo("deliveryApproval");
        assertThat(model.getAttribute("delivery")).isSameAs(delivery);
        assertThat((List<?>) model.getAttribute("approvalAddresses")).hasSize(1);
    }

    @Test
    void approvalScreenRedirectsToDetailsWhenTheDeliveryIsNotAwaitingApproval() {
        // given
        Delivery delivery = new Delivery();
        delivery.setStoreId(STORE_ID);
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_PENDING);
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        Model model = new ConcurrentModel();

        // when
        String view = deliveriesController.showApprovalScreen(STORE_ID, DELIVERY_ID, model, redirectAttributes);

        // then
        assertThat(view).isEqualTo(
                "redirect:/dashboard/store/" + STORE_ID + "/deliveries/details?deliveryId=" + DELIVERY_ID);
        verify(supplierPurchaseService, never()).deliveryAddressesForDelivery(any(), any());
    }

    @Test
    void approvalScreenReflashesTheErrorMessageWhenBouncingBackToDetails() {
        // given
        Delivery delivery = new Delivery();
        delivery.setStoreId(STORE_ID);
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_PENDING);
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        Model model = new ConcurrentModel();
        model.addAttribute("errorMessage", "Delivery is no longer awaiting approval");

        // when
        String view = deliveriesController.showApprovalScreen(STORE_ID, DELIVERY_ID, model, redirectAttributes);

        // then
        assertThat(view).isEqualTo(
                "redirect:/dashboard/store/" + STORE_ID + "/deliveries/details?deliveryId=" + DELIVERY_ID);
        verify(redirectAttributes).addFlashAttribute("errorMessage", "Delivery is no longer awaiting approval");
    }

    @Test
    void failedApprovalReturnsToTheRealisationScreen() {
        // given
        when(supplierPurchaseService.approve(STORE_ID, DELIVERY_ID, "1", Map.of()))
                .thenReturn(OperationResult.failure("deliveries.purchase.error.availability"));

        // when
        String view = deliveriesController.approvePurchase(STORE_ID, DELIVERY_ID, "1", Map.of(),
                redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo(
                "redirect:/dashboard/store/" + STORE_ID + "/deliveries/" + DELIVERY_ID + "/approval");
    }

    @Test
    void approveEndpointPassesSupplierOrderChoicesToTheService() {
        // given
        when(supplierPurchaseService.approve(STORE_ID, DELIVERY_ID, "17200617", Map.of("lane", "fast")))
                .thenReturn(OperationResult.success(DELIVERY_ID));

        // when
        String view = deliveriesController.approvePurchase(STORE_ID, DELIVERY_ID, "17200617",
                Map.of("supplierOrderChoices[lane]", "fast", "deliveryAddressId", "17200617"),
                redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-1/deliveries/details?deliveryId=delivery-1");
        verify(supplierPurchaseService).approve(eq(STORE_ID), eq(DELIVERY_ID), eq("17200617"), eq(Map.of("lane", "fast")));
    }

    @Test
    void approvalScreenPreselectsTheSupplierAddressMatchingTheStoreDefault() {
        // given
        Delivery delivery = new Delivery();
        delivery.setStoreId(STORE_ID);
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setProvider(PROVIDER);
        delivery.setOrderStatus(DeliveryOrderStatus.AWAITING_APPROVAL);
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(supplierPurchaseService.deliveryAddressesForDelivery(STORE_ID, DELIVERY_ID))
                .thenReturn(List.of(new SupplierDeliveryAddress("17200617", "ul. Łobzowska 22/1", "Kraków", "31-140", "PL")));

        ShippingDetails storeDefault = new ShippingDetails();
        storeDefault.setStreetAndNumber("Łobzowska 22/1");
        storeDefault.setPostalCode("31140");
        Store store = new Store();
        store.setShippingDetails(new ArrayList<>(List.of(storeDefault)));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);

        Model model = new ConcurrentModel();

        // when
        deliveriesController.showApprovalScreen(STORE_ID, DELIVERY_ID, model, redirectAttributes);

        // then
        assertThat(model.getAttribute("suggestedAddressId")).isEqualTo("17200617");
        assertThat(model.getAttribute("suggestedAddress")).isSameAs(storeDefault);
    }

    @Test
    void retryPurchaseRedirectsBackToDeliveryDetailsOnSuccess() {
        // given
        Delivery delivery = new Delivery(STORE_ID, null, PROVIDER);
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setConnectionMode(ConnectionMode.OWN);
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(supplierPurchaseService.retry(STORE_ID, DELIVERY_ID)).thenReturn(OperationResult.success(DELIVERY_ID));

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);

            // when
            String view = deliveriesController.retryPurchase(DELIVERY_ID, redirectAttributes, Locale.forLanguageTag("pl"));

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=" + DELIVERY_ID);
            verify(supplierPurchaseService).retry(STORE_ID, DELIVERY_ID);
            verify(redirectAttributes, never()).addFlashAttribute(eq("errorMessage"), any());
        }
    }

    @Test
    void retryPurchaseForSuperAdminRedirectsToStoreScopedDeliveryDetailsOnSuccess() {
        // given
        when(supplierPurchaseService.retry(STORE_ID, DELIVERY_ID)).thenReturn(OperationResult.success(DELIVERY_ID));

        // when
        String view = deliveriesController.retryPurchaseForSuperAdmin(STORE_ID, DELIVERY_ID, redirectAttributes, Locale.forLanguageTag("pl"));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-1/deliveries/details?deliveryId=delivery-1");
        verify(supplierPurchaseService).retry(STORE_ID, DELIVERY_ID);
        verify(redirectAttributes, never()).addFlashAttribute(eq("errorMessage"), any());
    }

    @Test
    void reconcilePurchaseAddsFlashSuccessMessageOnSuccess() {
        // given
        Delivery delivery = new Delivery(STORE_ID, null, PROVIDER);
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setConnectionMode(ConnectionMode.OWN);
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(supplierPurchaseService.reconcile(STORE_ID, DELIVERY_ID)).thenReturn(OperationResult.success(DELIVERY_ID));
        when(messageSource.getMessage(eq("deliveries.purchase.reconcile.found"), eq(null), eq(Locale.forLanguageTag("pl"))))
                .thenReturn("Dostawca potwierdzil zamowienie.");

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);

            // when
            String view = deliveriesController.reconcilePurchase(DELIVERY_ID, redirectAttributes, Locale.forLanguageTag("pl"));

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=" + DELIVERY_ID);
            verify(supplierPurchaseService).reconcile(STORE_ID, DELIVERY_ID);
            verify(redirectAttributes).addFlashAttribute("successMessage", "Dostawca potwierdzil zamowienie.");
            verify(redirectAttributes, never()).addFlashAttribute(eq("errorMessage"), any());
        }
    }

    @Test
    void completePurchaseRedirectsBackToDeliveryDetailsOnSuccess() {
        // given
        Delivery delivery = new Delivery(STORE_ID, null, PROVIDER);
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setConnectionMode(ConnectionMode.OWN);
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(supplierPurchaseService.completeManually(STORE_ID, DELIVERY_ID, "17200617", ESTIMATED_DELIVERY_AT))
                .thenReturn(OperationResult.success(DELIVERY_ID));
        when(messageSource.getMessage(eq("deliveries.purchase.complete.success"), eq(null), eq(Locale.forLanguageTag("pl"))))
                .thenReturn("Dostawa oznaczona jako zamowiona.");

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);

            // when
            String view = deliveriesController.completePurchase(DELIVERY_ID, "17200617", ESTIMATED_DELIVERY_AT,
                    redirectAttributes, Locale.forLanguageTag("pl"));

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=" + DELIVERY_ID);
            verify(supplierPurchaseService).completeManually(STORE_ID, DELIVERY_ID, "17200617", ESTIMATED_DELIVERY_AT);
            verify(redirectAttributes).addFlashAttribute("successMessage", "Dostawa oznaczona jako zamowiona.");
            verify(redirectAttributes, never()).addFlashAttribute(eq("errorMessage"), any());
        }
    }

    @Test
    void reconcilePurchaseForSuperAdminAddsFlashSuccessMessageOnSuccess() {
        // given
        when(supplierPurchaseService.reconcile(STORE_ID, DELIVERY_ID)).thenReturn(OperationResult.success(DELIVERY_ID));
        when(messageSource.getMessage(eq("deliveries.purchase.reconcile.found"), eq(null), eq(Locale.forLanguageTag("pl"))))
                .thenReturn("Dostawca potwierdzil zamowienie.");

        // when
        String view = deliveriesController.reconcilePurchaseForSuperAdmin(STORE_ID, DELIVERY_ID, redirectAttributes, Locale.forLanguageTag("pl"));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-1/deliveries/details?deliveryId=delivery-1");
        verify(supplierPurchaseService).reconcile(STORE_ID, DELIVERY_ID);
        verify(redirectAttributes).addFlashAttribute("successMessage", "Dostawca potwierdzil zamowienie.");
    }

    @Test
    void forcePurchaseRedirectsBackToDeliveryDetailsOnSuccess() {
        // given
        Delivery delivery = new Delivery(STORE_ID, null, PROVIDER);
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setConnectionMode(ConnectionMode.OWN);
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(supplierPurchaseService.forceRetry(STORE_ID, DELIVERY_ID)).thenReturn(OperationResult.success(DELIVERY_ID));

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);

            // when
            String view = deliveriesController.forcePurchase(DELIVERY_ID, redirectAttributes, Locale.forLanguageTag("pl"));

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=" + DELIVERY_ID);
            verify(supplierPurchaseService).forceRetry(STORE_ID, DELIVERY_ID);
            verify(redirectAttributes, never()).addFlashAttribute(eq("errorMessage"), any());
        }
    }

    @Test
    void forcePurchaseForSuperAdminRedirectsToStoreScopedDeliveryDetailsOnSuccess() {
        // given
        when(supplierPurchaseService.forceRetry(STORE_ID, DELIVERY_ID)).thenReturn(OperationResult.success(DELIVERY_ID));

        // when
        String view = deliveriesController.forcePurchaseForSuperAdmin(STORE_ID, DELIVERY_ID, redirectAttributes, Locale.forLanguageTag("pl"));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-1/deliveries/details?deliveryId=delivery-1");
        verify(supplierPurchaseService).forceRetry(STORE_ID, DELIVERY_ID);
        verify(redirectAttributes, never()).addFlashAttribute(eq("errorMessage"), any());
    }

    @Test
    void completePurchaseForSuperAdminRedirectsToStoreScopedDeliveryDetailsOnSuccess() {
        // given
        when(supplierPurchaseService.completeManually(STORE_ID, DELIVERY_ID, "17200617", ESTIMATED_DELIVERY_AT))
                .thenReturn(OperationResult.success(DELIVERY_ID));
        when(messageSource.getMessage(eq("deliveries.purchase.complete.success"), eq(null), eq(Locale.forLanguageTag("pl"))))
                .thenReturn("Dostawa oznaczona jako zamowiona.");

        // when
        String view = deliveriesController.completePurchaseForSuperAdmin(STORE_ID, DELIVERY_ID, "17200617", ESTIMATED_DELIVERY_AT,
                redirectAttributes, Locale.forLanguageTag("pl"));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-1/deliveries/details?deliveryId=delivery-1");
        verify(supplierPurchaseService).completeManually(STORE_ID, DELIVERY_ID, "17200617", ESTIMATED_DELIVERY_AT);
        verify(redirectAttributes).addFlashAttribute("successMessage", "Dostawa oznaczona jako zamowiona.");
    }

    // Every purchase endpoint reports a service failure the same way: the translated message as a flash error and a
    // redirect back to the delivery details (store-scoped for the super admin).
    @ParameterizedTest(name = "{0} as {1}")
    @CsvSource({
            "retry, storeAdmin, deliveries.purchase.retry.error.state, This delivery cannot be retried.",
            "reconcile, storeAdmin, deliveries.purchase.reconcile.notFound, The supplier does not see an order with this reference number.",
            "complete, storeAdmin, deliveries.purchase.complete.error.state, Cannot complete - the delivery is not in a failed order state.",
            "force, storeAdmin, deliveries.purchase.retry.error.state, This delivery cannot be retried.",
            "retry, superAdmin, deliveries.purchase.retry.error.state, This delivery cannot be retried.",
            "reconcile, superAdmin, deliveries.purchase.reconcile.error.failed, Failed to check the order with the supplier.",
            "force, superAdmin, deliveries.purchase.retry.error.state, This delivery cannot be retried.",
            "complete, superAdmin, deliveries.purchase.complete.error.number, Enter the supplier order number."
    })
    void purchaseFailureAddsFlashErrorMessage(String endpoint, String actor, String errorKey, String message) {
        // given
        boolean superAdmin = actor.equals("superAdmin");
        if (!superAdmin) {
            Delivery delivery = new Delivery(STORE_ID, null, PROVIDER);
            delivery.setDeliveryId(DELIVERY_ID);
            delivery.setConnectionMode(ConnectionMode.OWN);
            when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        }
        stubPurchaseFailure(endpoint, errorKey);
        when(messageSource.getMessage(eq(errorKey), eq(null), eq(Locale.ENGLISH))).thenReturn(message);

        // when
        String view = superAdmin
                ? callPurchaseEndpointAsSuperAdmin(endpoint, Locale.ENGLISH)
                : callPurchaseEndpointAsStoreAdmin(endpoint, Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo(superAdmin
                ? "redirect:/dashboard/store/store-1/deliveries/details?deliveryId=delivery-1"
                : "redirect:/dashboard/deliveries/details?deliveryId=" + DELIVERY_ID);
        verify(redirectAttributes).addFlashAttribute("errorMessage", message);
        verify(redirectAttributes, never()).addFlashAttribute(eq("successMessage"), any());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "retry, deliveries.purchase.retry.error.global, Dostawe globalna moze powtorzyc tylko administrator platformy.",
            "reconcile, deliveries.purchase.retry.error.global, Dostawe globalna moze powtorzyc tylko administrator platformy.",
            "complete, deliveries.purchase.complete.error.global, Dostawe globalna moze ukonczyc tylko administrator platformy.",
            "force, deliveries.purchase.retry.error.global, Dostawe globalna moze powtorzyc tylko administrator platformy."
    })
    void purchaseRefusesGlobalDeliveriesForStoreAdmin(String endpoint, String errorKey, String message) {
        // given
        Delivery delivery = new Delivery(STORE_ID, null, PROVIDER);
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setConnectionMode(ConnectionMode.GLOBAL);
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(messageSource.getMessage(eq(errorKey), eq(null), eq(Locale.forLanguageTag("pl")))).thenReturn(message);

        // when
        String view = callPurchaseEndpointAsStoreAdmin(endpoint, Locale.forLanguageTag("pl"));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=" + DELIVERY_ID);
        switch (endpoint) {
            case "retry" -> verify(supplierPurchaseService, never()).retry(any(), any());
            case "reconcile" -> verify(supplierPurchaseService, never()).reconcile(any(), any());
            case "complete" -> verify(supplierPurchaseService, never()).completeManually(any(), any(), any(), any());
            default -> verify(supplierPurchaseService, never()).forceRetry(any(), any());
        }
        verify(redirectAttributes).addFlashAttribute("errorMessage", message);
    }

    private void stubPurchaseFailure(String endpoint, String errorKey) {
        OperationResult<String> failure = OperationResult.failure(errorKey);
        switch (endpoint) {
            case "retry" -> when(supplierPurchaseService.retry(STORE_ID, DELIVERY_ID)).thenReturn(failure);
            case "reconcile" -> when(supplierPurchaseService.reconcile(STORE_ID, DELIVERY_ID)).thenReturn(failure);
            case "complete" -> when(supplierPurchaseService.completeManually(STORE_ID, DELIVERY_ID, "17200617", ESTIMATED_DELIVERY_AT))
                    .thenReturn(failure);
            default -> when(supplierPurchaseService.forceRetry(STORE_ID, DELIVERY_ID)).thenReturn(failure);
        }
    }

    private String callPurchaseEndpointAsStoreAdmin(String endpoint, Locale locale) {
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            return switch (endpoint) {
                case "retry" -> deliveriesController.retryPurchase(DELIVERY_ID, redirectAttributes, locale);
                case "reconcile" -> deliveriesController.reconcilePurchase(DELIVERY_ID, redirectAttributes, locale);
                case "complete" -> deliveriesController.completePurchase(DELIVERY_ID, "17200617", ESTIMATED_DELIVERY_AT,
                        redirectAttributes, locale);
                default -> deliveriesController.forcePurchase(DELIVERY_ID, redirectAttributes, locale);
            };
        }
    }

    private String callPurchaseEndpointAsSuperAdmin(String endpoint, Locale locale) {
        return switch (endpoint) {
            case "retry" -> deliveriesController.retryPurchaseForSuperAdmin(STORE_ID, DELIVERY_ID, redirectAttributes, locale);
            case "reconcile" -> deliveriesController.reconcilePurchaseForSuperAdmin(STORE_ID, DELIVERY_ID, redirectAttributes, locale);
            case "complete" -> deliveriesController.completePurchaseForSuperAdmin(STORE_ID, DELIVERY_ID, "17200617",
                    ESTIMATED_DELIVERY_AT, redirectAttributes, locale);
            default -> deliveriesController.forcePurchaseForSuperAdmin(STORE_ID, DELIVERY_ID, redirectAttributes, locale);
        };
    }

    @Test
    void backEndpointReturnsCreateViewWithPostedRequestedQtyAppliedToMatchingItem() {
        // given
        Delivery delivery = new Delivery(STORE_ID, null, PROVIDER);
        Allocation allocation = new Allocation();
        allocation.setKey(new AllocationKey(null, "item-1", "Warehouse"));
        allocation.setType(AllocationType.Warehouse);
        allocation.setMfn("MFN-1");
        allocation.setName("Product 1");
        allocation.setQty(1);
        delivery.setAllocations(List.of(allocation));

        when(deliveriesPlanningService.run(STORE_ID, PROVIDER)).thenReturn(delivery);
        when(deliveryTaxResolver.resolveFor(PROVIDER)).thenReturn(0.23);
        when(restockSuggestionService.suggestForDelivery(eq(STORE_ID), eq(PROVIDER), any(Set.class))).thenReturn(List.of());

        DeliveryCreationForm posted = new DeliveryCreationForm();
        DeliveryItem postedItem = new DeliveryItem();
        postedItem.setMfn("MFN-1");
        postedItem.setRequestedQty(7);
        postedItem.setUnitCost(42.0);
        posted.setItems(List.of(postedItem));

        Model model = new ConcurrentModel();

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);

            // when
            String view = deliveriesController.backFromPurchaseConfirmation(PROVIDER, posted, model);

            // then
            assertThat(view).isEqualTo("deliveryCreate");
            DeliveryCreationForm resultForm = (DeliveryCreationForm) model.getAttribute("form");
            assertThat(resultForm.getItems().get(0).getRequestedQty()).isEqualTo(7);
        }
    }

    @Test
    void backEndpointForSuperAdminReturnsCreateViewWithPostedRequestedQtyAppliedToMatchingItem() {
        // given
        Delivery delivery = new Delivery(STORE_ID, null, PROVIDER);
        Allocation allocation = new Allocation();
        allocation.setKey(new AllocationKey(null, "item-1", "Warehouse"));
        allocation.setType(AllocationType.Warehouse);
        allocation.setMfn("MFN-1");
        allocation.setName("Product 1");
        allocation.setQty(1);
        delivery.setAllocations(List.of(allocation));

        when(deliveriesPlanningService.run(STORE_ID, PROVIDER)).thenReturn(delivery);
        when(deliveryTaxResolver.resolveFor(PROVIDER)).thenReturn(0.23);
        when(restockSuggestionService.suggestForDelivery(eq(STORE_ID), eq(PROVIDER), any(Set.class))).thenReturn(List.of());

        DeliveryCreationForm posted = new DeliveryCreationForm();
        DeliveryItem postedItem = new DeliveryItem();
        postedItem.setMfn("MFN-1");
        postedItem.setRequestedQty(7);
        postedItem.setUnitCost(42.0);
        posted.setItems(List.of(postedItem));

        Model model = new ConcurrentModel();

        // when
        String view = deliveriesController.backFromPurchaseConfirmationForSuperAdmin(STORE_ID, PROVIDER, posted, model);

        // then
        assertThat(view).isEqualTo("deliveryCreate");
        DeliveryCreationForm resultForm = (DeliveryCreationForm) model.getAttribute("form");
        assertThat(resultForm.getItems().get(0).getRequestedQty()).isEqualTo(7);
    }

    @Test
    void createDeliveryFormRedirectsToPreviewWhenThePlanningServiceHasNothingToOffer() {
        // given
        when(deliveriesPlanningService.run(STORE_ID, PROVIDER)).thenReturn(null);
        Model model = new ConcurrentModel();

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);

            // when
            String view = deliveriesController.createDeliveryForm(PROVIDER, model);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/deliveries/preview");
        }
    }

    @Test
    void backEndpointRedirectsToPreviewWhenThePlanningServiceHasNothingToOffer() {
        // given
        when(deliveriesPlanningService.run(STORE_ID, PROVIDER)).thenReturn(null);
        DeliveryCreationForm posted = new DeliveryCreationForm();
        Model model = new ConcurrentModel();

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);

            // when
            String view = deliveriesController.backFromPurchaseConfirmation(PROVIDER, posted, model);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/deliveries/preview");
        }
    }

    @Test
    void updateDeliveryIsBlockedWhileAwaitingApproval() {
        // given
        Delivery existing = awaitingApprovalDelivery();
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(existing);
        when(messageSource.getMessage(eq("deliveries.edit.locked.awaitingApproval"), eq(null), eq(Locale.ENGLISH)))
                .thenReturn("The delivery is awaiting approval and cannot be edited.");
        Delivery updated = new Delivery();
        updated.setStoreId(STORE_ID);
        updated.setDeliveryId(DELIVERY_ID);

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            // when
            String view = deliveriesController.updateDelivery(updated, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=" + DELIVERY_ID);
            verify(deliveriesManager, never()).updateDelivery(any());
            verify(redirectAttributes).addFlashAttribute("errorMessage",
                    "The delivery is awaiting approval and cannot be edited.");
        }
    }

    @Test
    void updateDeliveryIsAllowedForSuperAdminWhileAwaitingApproval() {
        // given
        Delivery updated = new Delivery();
        updated.setStoreId(STORE_ID);
        updated.setDeliveryId(DELIVERY_ID);

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);

            // when
            String view = deliveriesController.updateDelivery(updated, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo(
                    "redirect:/dashboard/store/" + STORE_ID + "/deliveries/details?deliveryId=" + DELIVERY_ID);
            verify(deliveriesManager).updateDelivery(updated);
            verify(redirectAttributes, never()).addFlashAttribute(eq("errorMessage"), any());
        }
    }

    @Test
    void deleteSelectedAllocationsIsBlockedForStoreAdminWhileAwaitingApproval() {
        // given
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(awaitingApprovalDelivery());
        when(messageSource.getMessage(eq("deliveries.edit.locked.awaitingApproval"), eq(null), eq(Locale.ENGLISH)))
                .thenReturn("The delivery is awaiting approval and cannot be edited.");
        DeliveryAllocationsForm form = new DeliveryAllocationsForm(STORE_ID, DELIVERY_ID, PROVIDER, List.of());

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);

            // when
            String view = deliveriesController.deleteSelectedAllocations(form, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=" + DELIVERY_ID);
            verify(deliveriesManager, never()).deleteAllocations(any(), any(), any());
        }
    }

    @Test
    void deleteSelectedAllocationsForSuperAdminWorksWhileAwaitingApproval() {
        // given
        DeliveryAllocationsForm form = new DeliveryAllocationsForm(STORE_ID, DELIVERY_ID, PROVIDER, List.of());

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);

            // when
            String view = deliveriesController.deleteSelectedAllocationsForSuperAdmin(STORE_ID, form, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo(
                    "redirect:/dashboard/store/" + STORE_ID + "/deliveries/details?deliveryId=" + DELIVERY_ID);
            verify(deliveriesManager).deleteAllocations(eq(STORE_ID), eq(DELIVERY_ID), any());
        }
    }

    @Test
    void mergeIsRefusedWhenOrderStatusesOfSourceAndTargetDiffer() {
        // given
        Delivery source = awaitingApprovalDelivery();
        Delivery target = new Delivery(STORE_ID, null, PROVIDER);
        target.setDeliveryId("delivery-2");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(source);
        when(deliveriesRepository.findById(STORE_ID, "delivery-2")).thenReturn(target);
        when(messageSource.getMessage(eq("deliveries.merge.error.statusMismatch"), eq(null), eq(Locale.ENGLISH)))
                .thenReturn("Deliveries with a different supplier order status cannot be merged.");
        DeliveryAllocationsForm form = new DeliveryAllocationsForm(STORE_ID, DELIVERY_ID, PROVIDER, List.of());
        form.setTargetDeliveryId("delivery-2");

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);

            // when
            String view = deliveriesController.mergeSelectedAllocationsForSuperAdmin(
                    STORE_ID, form, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo(
                    "redirect:/dashboard/store/" + STORE_ID + "/deliveries/details?deliveryId=" + DELIVERY_ID);
            verify(deliveriesManager, never()).reassignAllocations(any(), any(), any(), any(), any());
            verify(redirectAttributes).addFlashAttribute("errorMessage",
                    "Deliveries with a different supplier order status cannot be merged.");
        }
    }

    @Test
    void mergeIsAllowedBetweenTwoAwaitingApprovalDeliveries() {
        // given
        Delivery source = awaitingApprovalDelivery();
        Delivery target = awaitingApprovalDelivery();
        target.setDeliveryId("delivery-2");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(source);
        when(deliveriesRepository.findById(STORE_ID, "delivery-2")).thenReturn(target);
        DeliveryAllocationsForm form = new DeliveryAllocationsForm(STORE_ID, DELIVERY_ID, PROVIDER, List.of());
        form.setTargetDeliveryId("delivery-2");

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);

            // when
            String view = deliveriesController.mergeSelectedAllocationsForSuperAdmin(
                    STORE_ID, form, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo(
                    "redirect:/dashboard/store/" + STORE_ID + "/deliveries/details?deliveryId=" + DELIVERY_ID);
            verify(deliveriesManager).reassignAllocations(eq(STORE_ID), eq(DELIVERY_ID), eq("delivery-2"), any(), any());
        }
    }

    @Test
    void mergeIsRefusedWhenEitherDeliveryIsDropship() {
        // given
        Delivery source = new Delivery(STORE_ID, null, PROVIDER);
        source.setDeliveryId(DELIVERY_ID);
        source.setType(DeliveryType.DROPSHIP);
        Delivery target = new Delivery(STORE_ID, null, PROVIDER);
        target.setDeliveryId("delivery-2");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(source);
        when(deliveriesRepository.findById(STORE_ID, "delivery-2")).thenReturn(target);
        when(messageSource.getMessage(eq("deliveries.merge.error.dropship"), eq(null), eq(Locale.ENGLISH)))
                .thenReturn("Dropshipping deliveries cannot be merged or split.");
        DeliveryAllocationsForm form = new DeliveryAllocationsForm(STORE_ID, DELIVERY_ID, PROVIDER, List.of());
        form.setTargetDeliveryId("delivery-2");

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);

            // when
            String view = deliveriesController.mergeSelectedAllocationsForSuperAdmin(
                    STORE_ID, form, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo(
                    "redirect:/dashboard/store/" + STORE_ID + "/deliveries/details?deliveryId=" + DELIVERY_ID);
            verify(deliveriesManager, never()).reassignAllocations(any(), any(), any(), any(), any());
            verify(redirectAttributes).addFlashAttribute("errorMessage",
                    "Dropshipping deliveries cannot be merged or split.");
        }
    }

    @Test
    void splitIsRefusedForADropshipDelivery() {
        // given
        Delivery delivery = new Delivery(STORE_ID, null, PROVIDER);
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setType(DeliveryType.DROPSHIP);
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(messageSource.getMessage(eq("deliveries.merge.error.dropship"), eq(null), eq(Locale.ENGLISH)))
                .thenReturn("Dropshipping deliveries cannot be merged or split.");
        DeliveryAllocationsForm form = new DeliveryAllocationsForm(STORE_ID, DELIVERY_ID, PROVIDER, List.of());
        form.setTargetExternalDeliveryId("EXT-2");

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);

            // when
            String view = deliveriesController.splitSelectedAllocationsForSuperAdmin(
                    STORE_ID, form, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo(
                    "redirect:/dashboard/store/" + STORE_ID + "/deliveries/details?deliveryId=" + DELIVERY_ID);
            verify(deliveriesManager, never()).splitAllocations(any(), any(), any(), any(), any(), any());
            verify(redirectAttributes).addFlashAttribute("errorMessage",
                    "Dropshipping deliveries cannot be merged or split.");
        }
    }

    @Test
    void receivingAllocationsIsBlockedWhileAwaitingApproval() {
        // given
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(awaitingApprovalDelivery());
        when(messageSource.getMessage(eq("deliveries.edit.locked.awaitingApproval"), eq(null), eq(Locale.ENGLISH)))
                .thenReturn("The delivery is awaiting approval and cannot be edited.");
        DeliveryAllocationsForm form = new DeliveryAllocationsForm(STORE_ID, DELIVERY_ID, PROVIDER, List.of());

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            // when
            String view = deliveriesController.markSelectedAllocationsAsReceived(form, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=" + DELIVERY_ID);
            verify(deliveryReceptionService, never()).receive(any(), any(), any(), any(), any(), any());
        }
    }

    @Test
    void updateItemQtyIsBlockedForStoreAdminWhileAwaitingApproval() {
        // given
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(awaitingApprovalDelivery());
        when(messageSource.getMessage(eq("deliveries.edit.locked.awaitingApproval"), eq(null), eq(Locale.ENGLISH)))
                .thenReturn("The delivery is awaiting approval and cannot be edited.");

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);

            // when
            String view = deliveriesController.updateDeliveryItemQty(DELIVERY_ID, "MFN-1", 5, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=" + DELIVERY_ID);
            verify(deliveryOrderedQtyUpdateService, never()).run(any(), any(), any(), anyInt());
        }
    }

    @Test
    void updateItemQtyForSuperAdminWorksWhileAwaitingApproval() {
        // given
        when(deliveryOrderedQtyUpdateService.run(STORE_ID, DELIVERY_ID, "MFN-1", 5))
                .thenReturn(OperationResult.success());

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);

            // when
            String view = deliveriesController.updateDeliveryItemQtyForSuperAdmin(
                    STORE_ID, DELIVERY_ID, "MFN-1", 5, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo(
                    "redirect:/dashboard/store/" + STORE_ID + "/deliveries/details?deliveryId=" + DELIVERY_ID);
            verify(deliveryOrderedQtyUpdateService).run(STORE_ID, DELIVERY_ID, "MFN-1", 5);
        }
    }

    @Test
    void deleteDeliveryIsBlockedWhileAwaitingApproval() {
        // given
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(awaitingApprovalDelivery());
        when(messageSource.getMessage(eq("deliveries.edit.locked.awaitingApproval"), eq(null), eq(Locale.ENGLISH)))
                .thenReturn("The delivery is awaiting approval and cannot be edited.");

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);

            // when
            String view = deliveriesController.deleteDelivery(DELIVERY_ID, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=" + DELIVERY_ID);
            verify(deliveriesRepository, never()).delete(any(Delivery.class));
        }
    }

    @Test
    void mergeTargetsExcludeDeliveriesWithADifferentApprovalState() {
        // given
        Delivery delivery = awaitingApprovalDelivery();
        delivery.setAllocations(List.of());
        Delivery awaitingTarget = awaitingApprovalDelivery();
        awaitingTarget.setDeliveryId("delivery-2");
        Delivery regularTarget = new Delivery(STORE_ID, null, PROVIDER);
        regularTarget.setDeliveryId("delivery-3");
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(deliveriesRepository.findPendingDeliveriesByProvider(STORE_ID, PROVIDER, DELIVERY_ID))
                .thenReturn(List.of(awaitingTarget, regularTarget));
        Model model = new ConcurrentModel();

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);

            // when
            deliveriesController.showDeliveryDetailsForSuperAdmin(STORE_ID, DELIVERY_ID, model, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(model.getAttribute("mergeTargetDeliveries")).isEqualTo(List.of(awaitingTarget));
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"splitAsStoreAdmin", "splitAsSuperAdmin", "deleteAllocationsAsSuperAdmin", "deleteDeliveryAsStoreAdmin"})
    void editsAreBlockedWhileOrderPending(String action) {
        // given
        boolean superAdmin = action.endsWith("AsSuperAdmin");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(orderPendingDelivery(DELIVERY_ID));
        when(messageSource.getMessage(eq("deliveries.edit.locked.orderPending"), eq(null), eq(Locale.ENGLISH)))
                .thenReturn("The delivery has a supplier order in progress and cannot be edited.");
        DeliveryAllocationsForm form = new DeliveryAllocationsForm(STORE_ID, DELIVERY_ID, PROVIDER, List.of());
        form.setTargetExternalDeliveryId("EXT-1");

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            if (superAdmin) {
                security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);
            } else {
                security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            }

            // when
            String view = switch (action) {
                case "splitAsStoreAdmin" -> deliveriesController.splitSelectedAllocations(form, redirectAttributes, Locale.ENGLISH);
                case "splitAsSuperAdmin" -> deliveriesController.splitSelectedAllocationsForSuperAdmin(
                        STORE_ID, form, redirectAttributes, Locale.ENGLISH);
                case "deleteAllocationsAsSuperAdmin" -> deliveriesController.deleteSelectedAllocationsForSuperAdmin(
                        STORE_ID, form, redirectAttributes, Locale.ENGLISH);
                default -> deliveriesController.deleteDelivery(DELIVERY_ID, redirectAttributes, Locale.ENGLISH);
            };

            // then
            assertThat(view).isEqualTo(superAdmin
                    ? "redirect:/dashboard/store/" + STORE_ID + "/deliveries/details?deliveryId=" + DELIVERY_ID
                    : "redirect:/dashboard/deliveries/details?deliveryId=" + DELIVERY_ID);
            switch (action) {
                case "splitAsStoreAdmin", "splitAsSuperAdmin" ->
                        verify(deliveriesManager, never()).splitAllocations(any(), any(), any(), any(), any(), any());
                case "deleteAllocationsAsSuperAdmin" -> verify(deliveriesManager, never()).deleteAllocations(any(), any(), any());
                default -> verify(deliveriesRepository, never()).delete(any(Delivery.class));
            }
            verify(redirectAttributes).addFlashAttribute("errorMessage",
                    "The delivery has a supplier order in progress and cannot be edited.");
        }
    }

    @Test
    void mergeIsBlockedWhenSourceAndTargetAreBothOrderPending() {
        // given
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(orderPendingDelivery(DELIVERY_ID));
        when(deliveriesRepository.findById(STORE_ID, "delivery-2")).thenReturn(orderPendingDelivery("delivery-2"));
        when(messageSource.getMessage(eq("deliveries.edit.locked.orderPending"), eq(null), eq(Locale.ENGLISH)))
                .thenReturn("The delivery has a supplier order in progress and cannot be edited.");
        DeliveryAllocationsForm form = new DeliveryAllocationsForm(STORE_ID, DELIVERY_ID, PROVIDER, List.of());
        form.setTargetDeliveryId("delivery-2");

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);

            // when
            String view = deliveriesController.mergeSelectedAllocationsForSuperAdmin(
                    STORE_ID, form, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo(
                    "redirect:/dashboard/store/" + STORE_ID + "/deliveries/details?deliveryId=" + DELIVERY_ID);
            verify(deliveriesManager, never()).reassignAllocations(any(), any(), any(), any(), any());
            verify(redirectAttributes).addFlashAttribute("errorMessage",
                    "The delivery has a supplier order in progress and cannot be edited.");
        }
    }

    @Test
    void mergeIntoAnOrderPendingTargetIsRefusedAsAStatusMismatch() {
        // given
        Delivery source = new Delivery(STORE_ID, null, PROVIDER);
        source.setDeliveryId(DELIVERY_ID);
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(source);
        when(deliveriesRepository.findById(STORE_ID, "delivery-2")).thenReturn(orderPendingDelivery("delivery-2"));
        when(messageSource.getMessage(eq("deliveries.merge.error.statusMismatch"), eq(null), eq(Locale.ENGLISH)))
                .thenReturn("Deliveries with a different supplier order status cannot be merged.");
        DeliveryAllocationsForm form = new DeliveryAllocationsForm(STORE_ID, DELIVERY_ID, PROVIDER, List.of());
        form.setTargetDeliveryId("delivery-2");

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);

            // when
            String view = deliveriesController.mergeSelectedAllocationsForSuperAdmin(
                    STORE_ID, form, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo(
                    "redirect:/dashboard/store/" + STORE_ID + "/deliveries/details?deliveryId=" + DELIVERY_ID);
            verify(deliveriesManager, never()).reassignAllocations(any(), any(), any(), any(), any());
            verify(redirectAttributes).addFlashAttribute("errorMessage",
                    "Deliveries with a different supplier order status cannot be merged.");
        }
    }

    @Test
    void mergeIsAllowedBetweenTwoFailedDeliveries() {
        // given
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(failedDelivery(DELIVERY_ID));
        when(deliveriesRepository.findById(STORE_ID, "delivery-2")).thenReturn(failedDelivery("delivery-2"));
        DeliveryAllocationsForm form = new DeliveryAllocationsForm(STORE_ID, DELIVERY_ID, PROVIDER, List.of());
        form.setTargetDeliveryId("delivery-2");

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);

            // when
            String view = deliveriesController.mergeSelectedAllocationsForSuperAdmin(
                    STORE_ID, form, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo(
                    "redirect:/dashboard/store/" + STORE_ID + "/deliveries/details?deliveryId=" + DELIVERY_ID);
            verify(deliveriesManager).reassignAllocations(eq(STORE_ID), eq(DELIVERY_ID), eq("delivery-2"), any(), any());
        }
    }

    @Test
    void splitIsBlockedForStoreAdminWhileOrderDispatched() {
        // given
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(dispatchedDelivery(DELIVERY_ID));
        when(messageSource.getMessage(eq("deliveries.edit.locked.orderPending"), eq(null), eq(Locale.ENGLISH)))
                .thenReturn("The delivery has a supplier order in progress and cannot be edited.");
        DeliveryAllocationsForm form = new DeliveryAllocationsForm(STORE_ID, DELIVERY_ID, PROVIDER, List.of());
        form.setTargetExternalDeliveryId("EXT-1");

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);

            // when
            String view = deliveriesController.splitSelectedAllocations(form, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=" + DELIVERY_ID);
            verify(deliveriesManager, never()).splitAllocations(any(), any(), any(), any(), any(), any());
            verify(redirectAttributes).addFlashAttribute("errorMessage",
                    "The delivery has a supplier order in progress and cannot be edited.");
        }
    }

    @Test
    void deleteSelectedAllocationsForSuperAdminIsBlockedWhileTheOrderIsStillBeingPlaced() {
        // given
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(dispatchedDelivery(DELIVERY_ID));
        when(messageSource.getMessage(eq("deliveries.edit.locked.orderPending"), eq(null), eq(Locale.ENGLISH)))
                .thenReturn("The delivery has a supplier order in progress and cannot be edited.");
        DeliveryAllocationsForm form = new DeliveryAllocationsForm(STORE_ID, DELIVERY_ID, PROVIDER, List.of());

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);

            // when
            String view = deliveriesController.deleteSelectedAllocationsForSuperAdmin(
                    STORE_ID, form, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo(
                    "redirect:/dashboard/store/" + STORE_ID + "/deliveries/details?deliveryId=" + DELIVERY_ID);
            verify(deliveriesManager, never()).deleteAllocations(any(), any(), any());
        }
    }

    @Test
    void deleteSelectedAllocationsForSuperAdminIsAllowedWhenTheOrderOutcomeIsUnknown() {
        // given
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID))
                .thenReturn(dispatchedDeliveryWithUnknownOutcome(DELIVERY_ID));
        DeliveryAllocationsForm form = new DeliveryAllocationsForm(STORE_ID, DELIVERY_ID, PROVIDER, List.of());

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);

            // when
            String view = deliveriesController.deleteSelectedAllocationsForSuperAdmin(
                    STORE_ID, form, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo(
                    "redirect:/dashboard/store/" + STORE_ID + "/deliveries/details?deliveryId=" + DELIVERY_ID);
            verify(deliveriesManager).deleteAllocations(STORE_ID, DELIVERY_ID, List.of());
        }
    }

    @Test
    void deleteSelectedAllocationsForStoreAdminIsAllowedWhenTheOrderOutcomeIsUnknown() {
        // given
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID))
                .thenReturn(dispatchedDeliveryWithUnknownOutcome(DELIVERY_ID));
        DeliveryAllocationsForm form = new DeliveryAllocationsForm(STORE_ID, DELIVERY_ID, PROVIDER, List.of());

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);

            // when
            String view = deliveriesController.deleteSelectedAllocations(form, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=" + DELIVERY_ID);
            verify(deliveriesManager).deleteAllocations(STORE_ID, DELIVERY_ID, List.of());
        }
    }

    @Test
    void mergeIsBlockedWhenSourceAndTargetAreBothOrderDispatched() {
        // given
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(dispatchedDelivery(DELIVERY_ID));
        when(deliveriesRepository.findById(STORE_ID, "delivery-2")).thenReturn(dispatchedDelivery("delivery-2"));
        when(messageSource.getMessage(eq("deliveries.edit.locked.orderPending"), eq(null), eq(Locale.ENGLISH)))
                .thenReturn("The delivery has a supplier order in progress and cannot be edited.");
        DeliveryAllocationsForm form = new DeliveryAllocationsForm(STORE_ID, DELIVERY_ID, PROVIDER, List.of());
        form.setTargetDeliveryId("delivery-2");

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);

            // when
            String view = deliveriesController.mergeSelectedAllocationsForSuperAdmin(
                    STORE_ID, form, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo(
                    "redirect:/dashboard/store/" + STORE_ID + "/deliveries/details?deliveryId=" + DELIVERY_ID);
            verify(deliveriesManager, never()).reassignAllocations(any(), any(), any(), any(), any());
            verify(redirectAttributes).addFlashAttribute("errorMessage",
                    "The delivery has a supplier order in progress and cannot be edited.");
        }
    }

    @Test
    void deleteDeliveryIsBlockedWhileOrderDispatched() {
        // given
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(dispatchedDelivery(DELIVERY_ID));
        when(messageSource.getMessage(eq("deliveries.edit.locked.orderPending"), eq(null), eq(Locale.ENGLISH)))
                .thenReturn("The delivery has a supplier order in progress and cannot be edited.");

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);

            // when
            String view = deliveriesController.deleteDelivery(DELIVERY_ID, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=" + DELIVERY_ID);
            verify(deliveriesRepository, never()).delete(any(Delivery.class));
        }
    }

    @Test
    void mergeTargetsExcludeDeliveriesWithADifferentOrderStatus() {
        // given
        Delivery delivery = failedDelivery(DELIVERY_ID);
        delivery.setAllocations(List.of());
        Delivery failedTarget = failedDelivery("delivery-2");
        Delivery regularTarget = new Delivery(STORE_ID, null, PROVIDER);
        regularTarget.setDeliveryId("delivery-3");
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(deliveriesRepository.findPendingDeliveriesByProvider(STORE_ID, PROVIDER, DELIVERY_ID))
                .thenReturn(List.of(failedTarget, regularTarget));
        Model model = new ConcurrentModel();

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);

            // when
            deliveriesController.showDeliveryDetailsForSuperAdmin(STORE_ID, DELIVERY_ID, model, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(model.getAttribute("mergeTargetDeliveries")).isEqualTo(List.of(failedTarget));
        }
    }

    private Delivery awaitingApprovalDelivery() {
        Delivery delivery = new Delivery(STORE_ID, null, PROVIDER);
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setOrderStatus(DeliveryOrderStatus.AWAITING_APPROVAL);
        return delivery;
    }

    private Delivery orderPendingDelivery(String deliveryId) {
        Delivery delivery = new Delivery(STORE_ID, null, PROVIDER);
        delivery.setDeliveryId(deliveryId);
        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_PENDING);
        return delivery;
    }

    private Delivery dispatchedDelivery(String deliveryId) {
        Delivery delivery = new Delivery(STORE_ID, null, PROVIDER);
        delivery.setDeliveryId(deliveryId);
        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_DISPATCHED);
        return delivery;
    }

    private Delivery dispatchedDeliveryWithUnknownOutcome(String deliveryId) {
        Delivery delivery = new Delivery(STORE_ID, null, PROVIDER);
        delivery.setDeliveryId(deliveryId);
        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_DISPATCHED);
        delivery.setOrderErrorMessage("Supplier did not confirm the order");
        return delivery;
    }

    private Delivery failedDelivery(String deliveryId) {
        Delivery delivery = new Delivery(STORE_ID, null, PROVIDER);
        delivery.setDeliveryId(deliveryId);
        delivery.setOrderStatus(DeliveryOrderStatus.FAILED);
        return delivery;
    }

    @Test
    void approvalScreenSuggestsNothingWhenTheStoreHasNoDefaultAddress() {
        // given
        Delivery delivery = new Delivery();
        delivery.setStoreId(STORE_ID);
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setProvider(PROVIDER);
        delivery.setOrderStatus(DeliveryOrderStatus.AWAITING_APPROVAL);
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(supplierPurchaseService.deliveryAddressesForDelivery(STORE_ID, DELIVERY_ID))
                .thenReturn(List.of(new SupplierDeliveryAddress("17200617", "ul. Łobzowska 22/1", "Kraków", "31-140", "PL")));
        when(storesRepository.findById(STORE_ID)).thenReturn(new Store());

        Model model = new ConcurrentModel();

        // when
        deliveriesController.showApprovalScreen(STORE_ID, DELIVERY_ID, model, redirectAttributes);

        // then
        assertThat(model.getAttribute("suggestedAddressId")).isNull();
        assertThat(model.getAttribute("suggestedAddress")).isNull();
    }

    @Test
    void deliveryDetailsSuggestsEstimatedDeliveryDateForFailedPurchase() {
        // given
        Delivery delivery = new Delivery(STORE_ID, null, PROVIDER);
        delivery.setOrderStatus(DeliveryOrderStatus.FAILED);
        Model model = new ConcurrentModel();
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(supplierPurchaseService.suggestEstimatedDeliveryAt(delivery)).thenReturn(ESTIMATED_DELIVERY_AT);

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            deliveriesController.showDeliveryDetails(DELIVERY_ID, model, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(model.getAttribute("suggestedEstimatedDeliveryAt")).isEqualTo(ESTIMATED_DELIVERY_AT);
        }
    }

    @Test
    void deliveryDetailsSuggestsEstimatedDeliveryDateForDispatchedPurchase() {
        // given
        Delivery delivery = new Delivery(STORE_ID, null, PROVIDER);
        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_DISPATCHED);
        Model model = new ConcurrentModel();
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(supplierPurchaseService.suggestEstimatedDeliveryAt(delivery)).thenReturn(ESTIMATED_DELIVERY_AT);

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            deliveriesController.showDeliveryDetails(DELIVERY_ID, model, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(model.getAttribute("suggestedEstimatedDeliveryAt")).isEqualTo(ESTIMATED_DELIVERY_AT);
        }
    }

    @Test
    void deliveryDetailsDoesNotSuggestEstimatedDeliveryDateForNonFailedDelivery() {
        // given
        Delivery delivery = new Delivery(STORE_ID, null, PROVIDER);
        Model model = new ConcurrentModel();
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, DELIVERY_ID)).thenReturn(delivery);

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            deliveriesController.showDeliveryDetails(DELIVERY_ID, model, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(model.containsAttribute("suggestedEstimatedDeliveryAt")).isFalse();
            verify(supplierPurchaseService, never()).suggestEstimatedDeliveryAt(any());
        }
    }

    @Test
    void approvalScreenExposesTheConsigneeOfADropshipDelivery() {
        // given
        Delivery delivery = new Delivery();
        delivery.setStoreId(STORE_ID);
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setProvider(PROVIDER);
        delivery.setType(DeliveryType.DROPSHIP);
        delivery.setOrderStatus(DeliveryOrderStatus.AWAITING_APPROVAL);
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        Order order = new Order(STORE_ID);
        order.setOrderId("order-1");
        ShippingDetails consignee = new ShippingDetails();
        consignee.setName("Jan");
        consignee.setSurname("Kowalski");
        order.setShippingDetails(consignee);
        when(dropshipOrderLocator.locate(DELIVERY_ID)).thenReturn(Optional.of("order-1"));
        when(ordersRepository.findById(STORE_ID, "order-1")).thenReturn(order);
        Model model = new ConcurrentModel();

        // when
        String view = deliveriesController.showApprovalScreen(STORE_ID, DELIVERY_ID, model, redirectAttributes);

        // then
        assertThat(view).isEqualTo("deliveryApproval");
        assertThat(model.getAttribute("consignee")).isSameAs(consignee);
        verify(supplierPurchaseService, never()).deliveryAddressesForDelivery(any(), any());
    }

    @Test
    void approvalScreenExposesOrderOptionsForDropshipDeliveries() {
        // given
        Delivery delivery = new Delivery();
        delivery.setStoreId(STORE_ID);
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setProvider(PROVIDER);
        delivery.setType(DeliveryType.DROPSHIP);
        delivery.setOrderStatus(DeliveryOrderStatus.AWAITING_APPROVAL);
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(dropshipOrderLocator.locate(DELIVERY_ID)).thenReturn(Optional.of("order-1"));
        Order order = new Order(STORE_ID);
        order.setOrderId("order-1");
        Shipment pickupShipment = new Shipment(ShipmentType.PickupPoint);
        pickupShipment.setCarrier("InPost");
        pickupShipment.setCollectionPointCode("WAW04A");
        order.setShipments(List.of(pickupShipment));
        when(ordersRepository.findById(STORE_ID, "order-1")).thenReturn(order);
        SupplierOrderOption laneOption = new SupplierOrderOption("lane", "Lane",
                List.of(new SupplierOrderOptionChoice("fast", "Fast", null)), null, true);
        ArgumentCaptor<SupplierOrderOptionsContext> context =
                ArgumentCaptor.forClass(SupplierOrderOptionsContext.class);
        when(supplierPurchaseService.orderOptions(eq(STORE_ID), eq(PROVIDER), context.capture()))
                .thenReturn(List.of(laneOption));
        Model model = new ConcurrentModel();

        // when
        String view = deliveriesController.showApprovalScreen(STORE_ID, DELIVERY_ID, model, redirectAttributes);

        // then — the render path must resolve the order and pass its pickup point through, not
        // just any dropship context.
        assertThat(view).isEqualTo("deliveryApproval");
        assertThat((List<?>) model.getAttribute("orderOptions")).hasSize(1);
        assertThat(context.getValue().dropship()).isTrue();
        assertThat(context.getValue().pickupPoint()).isNotNull();
        assertThat(context.getValue().pickupPoint().carrier()).isEqualTo("InPost");
        assertThat(context.getValue().pickupPoint().code()).isEqualTo("WAW04A");
    }

    private static Store storeRouting(String supplierName, String externalSupplierId) {
        StoreSupplierConnection connection = new StoreSupplierConnection(supplierName, ConnectionMode.OWN);
        connection.setExternalSupplierId(externalSupplierId);
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setSupplierConnections(new ArrayList<>(List.of(connection)));
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setFulfilmentConfiguration(config);
        return store;
    }

    private static Order routedOrder(String orderId, String externalSupplierId) {
        Order order = new Order(STORE_ID);
        order.setOrderId(orderId);
        order.setExternalSupplierId(externalSupplierId);
        return order;
    }

    private static Delivery awaitingWarehouseDeliveryFor(String provider, String... orderIds) {
        Delivery delivery = new Delivery();
        delivery.setStoreId(STORE_ID);
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setProvider(provider);
        delivery.setOrderStatus(DeliveryOrderStatus.AWAITING_APPROVAL);
        List<Allocation> allocations = new ArrayList<>();
        for (String orderId : orderIds) {
            Allocation allocation = new Allocation();
            allocation.setKey(new AllocationKey(orderId, orderId + "-item", "client@example.com"));
            allocations.add(allocation);
        }
        delivery.setAllocations(allocations);
        return delivery;
    }

    @Test
    void approvalScreenWarnsTheSuperAdminAboutAnOrderTheMarketplaceRoutedToAnotherSupplier() {
        // given
        Delivery delivery = awaitingWarehouseDeliveryFor("Acme", "order-1", "order-2");
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(supplierPurchaseService.deliveryAddressesForDelivery(STORE_ID, DELIVERY_ID)).thenReturn(List.of());
        when(storesRepository.findById(STORE_ID)).thenReturn(storeRouting("Bravo", "2"));
        when(ordersRepository.findById(STORE_ID, "order-1")).thenReturn(routedOrder("order-1", "2"));
        when(ordersRepository.findById(STORE_ID, "order-2")).thenReturn(routedOrder("order-2", null));
        Model model = new ConcurrentModel();

        // when
        deliveriesController.showApprovalScreen(STORE_ID, DELIVERY_ID, model, redirectAttributes);

        // then
        @SuppressWarnings("unchecked")
        List<RoutedOrderView> routed = (List<RoutedOrderView>) model.getAttribute("routedOrders");
        assertThat(routed).hasSize(1);
        assertThat(routed.get(0).orderId()).isEqualTo("order-1");
        assertThat(routed.get(0).supplier().isMatched()).isTrue();
        assertThat(routed.get(0).supplier().supplierName()).isEqualTo("Bravo");
    }

    @Test
    void approvalScreenWarnsTheSuperAdminAboutAnOrderRoutedToAnIdNoSupplierCarries() {
        // given
        Delivery delivery = awaitingWarehouseDeliveryFor("Acme", "order-1");
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(supplierPurchaseService.deliveryAddressesForDelivery(STORE_ID, DELIVERY_ID)).thenReturn(List.of());
        when(storesRepository.findById(STORE_ID)).thenReturn(storeRouting("Bravo", "2"));
        when(ordersRepository.findById(STORE_ID, "order-1")).thenReturn(routedOrder("order-1", "9"));
        Model model = new ConcurrentModel();

        // when
        deliveriesController.showApprovalScreen(STORE_ID, DELIVERY_ID, model, redirectAttributes);

        // then
        @SuppressWarnings("unchecked")
        List<RoutedOrderView> routed = (List<RoutedOrderView>) model.getAttribute("routedOrders");
        assertThat(routed).hasSize(1);
        assertThat(routed.get(0).supplier().isMatched()).isFalse();
        assertThat(routed.get(0).supplier().externalSupplierId()).isEqualTo("9");
    }

    @Test
    void approvalScreenHasNoRoutingNoteWhenNoOrderWasRouted() {
        // given
        Delivery delivery = awaitingWarehouseDeliveryFor("Acme", "order-1");
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(supplierPurchaseService.deliveryAddressesForDelivery(STORE_ID, DELIVERY_ID)).thenReturn(List.of());
        when(storesRepository.findById(STORE_ID)).thenReturn(storeRouting("Acme", "2"));
        when(ordersRepository.findById(STORE_ID, "order-1")).thenReturn(routedOrder("order-1", null));
        Model model = new ConcurrentModel();

        // when
        deliveriesController.showApprovalScreen(STORE_ID, DELIVERY_ID, model, redirectAttributes);

        // then
        assertThat((List<?>) model.getAttribute("routedOrders")).isEmpty();
    }

    @Test
    void approvalScreenShowsNoRoutingForADropshipDeliveryWhoseOrderCannotBeResolved() {
        // given
        Delivery delivery = awaitingWarehouseDeliveryFor("Acme", "order-1");
        delivery.setType(DeliveryType.DROPSHIP);
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(dropshipOrderLocator.locate(DELIVERY_ID)).thenReturn(Optional.empty());
        Model model = new ConcurrentModel();

        // when
        deliveriesController.showApprovalScreen(STORE_ID, DELIVERY_ID, model, redirectAttributes);

        // then
        assertThat((List<?>) model.getAttribute("routedOrders")).isEmpty();
    }

    @Test
    void paymentFromThePaymentsPageGoesBackThere() {
        // given
        Delivery delivery = new Delivery();
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        AddPaymentForm form = new AddPaymentForm();
        form.setBankAmount("100");
        form.setProcessingFee("0");
        form.setSource(PaymentSource.BankTransfer);
        form.setReturnTo("/dashboard/payments?side=payables&focus=overdue");

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = deliveriesController.addPayment(DELIVERY_ID, form, redirectAttributes, Locale.ENGLISH);
        }

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/payments?side=payables&focus=overdue");
        verify(redirectAttributes).addFlashAttribute(eq(PaymentsReturn.NOTICE), any());
    }

    @Test
    void addPaymentToADeliveryReadsACommaAmount() {
        // given: the dialog's amounts are text, read on the server whatever the browser's language
        Delivery delivery = new Delivery();
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        AddPaymentForm form = new AddPaymentForm();
        form.setBankAmount("1 499,99");
        form.setProcessingFee("2,50");
        form.setSource(PaymentSource.BankTransfer);

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = deliveriesController.addPayment(DELIVERY_ID, form, redirectAttributes, Locale.ENGLISH);
        }

        // then: a payout to the supplier keeps its sign
        assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=" + DELIVERY_ID);
        assertThat(delivery.getPayments()).hasSize(1);
        assertThat(delivery.getPayments().get(0).getAmount()).isEqualTo(1499.99);
        assertThat(delivery.getPayments().get(0).getFee()).isEqualTo(2.5);
        assertThat(delivery.getPayments().get(0).getDirection()).isEqualTo(PaymentDirection.Outgoing);
        verify(deliveriesRepository).save(delivery);
        verify(redirectAttributes, never()).addFlashAttribute(eq("errorMessage"), any());
    }

    @Test
    void theDeliveryPaymentsEditReadsCommaAmounts() {
        // given: the Bulma edit modal posts payments[i].amount / .fee as text
        Delivery posted = new Delivery();
        WebDataBinder binder = new WebDataBinder(posted, "delivery");
        deliveriesController.paymentAmounts(binder);
        MutablePropertyValues values = new MutablePropertyValues();
        values.add("payments[0].amount", "1 499,99");
        values.add("payments[0].fee", "2,50");
        values.add("payments[0].source", "BankTransfer");
        values.add("payments[1].amount", "1.000");

        // when
        binder.bind(values);

        // then
        assertThat(posted.getPayments().get(0).getAmount()).isEqualTo(1499.99);
        assertThat(posted.getPayments().get(0).getFee()).isEqualTo(2.5);
        assertThat(binder.getBindingResult().getFieldErrors()).extracting(FieldError::getField)
                .containsExactly("payments[1].amount");
    }

    @Test
    void aDeliveryPaymentsEditWithAnAmountThatIsNotANumberSavesNothing() {
        // given
        BindingResult binding = new BeanPropertyBindingResult(new Delivery(), "delivery");
        binding.rejectValue("payments", "typeMismatch");
        when(messageSource.getMessage(eq("error.message.payment.amount.format"), eq(null), eq(Locale.ENGLISH)))
                .thenReturn("Enter the amount as a number");

        // when
        String view = deliveriesController.updatePayments(DELIVERY_ID, new Delivery(), binding, redirectAttributes,
                Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=" + DELIVERY_ID);
        verify(redirectAttributes).addFlashAttribute("errorMessage", "Enter the amount as a number");
        verify(deliveriesRepository, never()).save(any());
    }

    @Test
    void syncWithoutInvoicingSystemFlashesAnErrorOnThePaymentsPage() {
        // given
        when(invoiceSynchronizationService.sync(STORE_ID)).thenReturn(InvoiceSyncResult.notConfigured());
        when(messageSource.getMessage("payments.sync.notConfigured", null, Locale.ENGLISH)).thenReturn("not configured");

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = deliveriesController.syncPaymentStatuses(redirectAttributes, Locale.ENGLISH);
        }

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/payments");
        verify(redirectAttributes).addFlashAttribute(PaymentsReturn.ERROR, "not configured");
    }

    @Test
    void syncWithPaidDeliveriesFlashesANoticeNamingThem() {
        // given
        when(invoiceSynchronizationService.sync(STORE_ID))
                .thenReturn(new InvoiceSyncResult(true, 3, List.of("aaaa0001", "aaaa0002"), 1, List.of()));
        when(messageSource.getMessage(eq("payments.sync.result"), any(Object[].class), eq(Locale.ENGLISH))).thenReturn("Checked 3.");
        when(messageSource.getMessage(eq("payments.sync.result.paid"), eq(new Object[]{"aaaa0001, aaaa0002"}), eq(Locale.ENGLISH)))
                .thenReturn("Paid: aaaa0001, aaaa0002.");

        // when
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = deliveriesController.syncPaymentStatuses(redirectAttributes, Locale.ENGLISH);
        }

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/payments");
        verify(redirectAttributes).addFlashAttribute(PaymentsReturn.NOTICE, "Checked 3. Paid: aaaa0001, aaaa0002.");
        verify(redirectAttributes, never()).addFlashAttribute(eq(PaymentsReturn.ERROR), any());
    }
}
