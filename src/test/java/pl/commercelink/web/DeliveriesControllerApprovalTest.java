package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
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
import pl.commercelink.inventory.deliveries.SupplierPurchaseService;
import pl.commercelink.inventory.supplier.api.SupplierDeliveryAddress;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.RestockSuggestionService;
import pl.commercelink.web.dtos.DeliveryAllocationsForm;
import pl.commercelink.web.dtos.DeliveryCreationForm;
import pl.commercelink.web.dtos.PickerOption;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveriesControllerApprovalTest {

    private static final String STORE_ID = "store-1";
    private static final String DELIVERY_ID = "delivery-1";
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

    @InjectMocks
    private DeliveriesController deliveriesController;

    @Test
    void approvingRedirectsBackToTheStoreScopedDeliveryDetails() {
        // given
        when(supplierPurchaseService.approve(STORE_ID, DELIVERY_ID, "17200617"))
                .thenReturn(OperationResult.success(DELIVERY_ID));

        // when
        String view = deliveriesController.approvePurchase(STORE_ID, DELIVERY_ID, "17200617",
                redirectAttributes, Locale.forLanguageTag("pl"));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-1/deliveries/details?deliveryId=delivery-1");
        verify(supplierPurchaseService).approve(eq(STORE_ID), eq(DELIVERY_ID), eq("17200617"));
        verify(redirectAttributes, never()).addFlashAttribute(eq("errorMessage"), any());
    }

    @Test
    void approvalFailureAddsFlashErrorMessageAndRedirectsToTheRealisationScreen() {
        // given
        when(supplierPurchaseService.approve(STORE_ID, DELIVERY_ID, null))
                .thenReturn(OperationResult.failure("deliveries.approval.error.state"));
        when(messageSource.getMessage(eq("deliveries.approval.error.state"), eq(null), eq(Locale.ENGLISH)))
                .thenReturn("Delivery is no longer awaiting approval");

        // when
        String view = deliveriesController.approvePurchase(STORE_ID, DELIVERY_ID, null,
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
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
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
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
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
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
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
        when(supplierPurchaseService.approve(STORE_ID, DELIVERY_ID, "1"))
                .thenReturn(OperationResult.failure("deliveries.purchase.error.availability"));

        // when
        String view = deliveriesController.approvePurchase(STORE_ID, DELIVERY_ID, "1", redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo(
                "redirect:/dashboard/store/" + STORE_ID + "/deliveries/" + DELIVERY_ID + "/approval");
    }

    @Test
    void approvalScreenPreselectsTheSupplierAddressMatchingTheStoreDefault() {
        // given
        Delivery delivery = new Delivery();
        delivery.setStoreId(STORE_ID);
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setProvider(PROVIDER);
        delivery.setOrderStatus(DeliveryOrderStatus.AWAITING_APPROVAL);
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
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
    void retryPurchaseRefusesGlobalDeliveriesForStoreAdmin() {
        // given
        Delivery delivery = new Delivery(STORE_ID, null, PROVIDER);
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setConnectionMode(ConnectionMode.GLOBAL);
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(messageSource.getMessage(eq("deliveries.purchase.retry.error.global"), eq(null), eq(Locale.forLanguageTag("pl"))))
                .thenReturn("Dostawe globalna moze powtorzyc tylko administrator platformy.");

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);

            // when
            String view = deliveriesController.retryPurchase(DELIVERY_ID, redirectAttributes, Locale.forLanguageTag("pl"));

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=" + DELIVERY_ID);
            verify(supplierPurchaseService, never()).retry(any(), any());
            verify(redirectAttributes).addFlashAttribute("errorMessage", "Dostawe globalna moze powtorzyc tylko administrator platformy.");
        }
    }

    @Test
    void retryPurchaseFailureAddsFlashErrorMessage() {
        // given
        Delivery delivery = new Delivery(STORE_ID, null, PROVIDER);
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setConnectionMode(ConnectionMode.OWN);
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(supplierPurchaseService.retry(STORE_ID, DELIVERY_ID))
                .thenReturn(OperationResult.failure("deliveries.purchase.retry.error.state"));
        when(messageSource.getMessage(eq("deliveries.purchase.retry.error.state"), eq(null), eq(Locale.ENGLISH)))
                .thenReturn("This delivery cannot be retried.");

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);

            // when
            String view = deliveriesController.retryPurchase(DELIVERY_ID, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=" + DELIVERY_ID);
            verify(redirectAttributes).addFlashAttribute("errorMessage", "This delivery cannot be retried.");
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
    void retryPurchaseForSuperAdminFailureAddsFlashErrorMessage() {
        // given
        when(supplierPurchaseService.retry(STORE_ID, DELIVERY_ID))
                .thenReturn(OperationResult.failure("deliveries.purchase.retry.error.state"));
        when(messageSource.getMessage(eq("deliveries.purchase.retry.error.state"), eq(null), eq(Locale.ENGLISH)))
                .thenReturn("This delivery cannot be retried.");

        // when
        String view = deliveriesController.retryPurchaseForSuperAdmin(STORE_ID, DELIVERY_ID, redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-1/deliveries/details?deliveryId=delivery-1");
        verify(redirectAttributes).addFlashAttribute("errorMessage", "This delivery cannot be retried.");
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
            String view = deliveriesController.deleteSelectedAllocationsForSuperAdmin(STORE_ID, form);

            // then
            assertThat(view).isEqualTo(
                    "redirect:/dashboard/store/" + STORE_ID + "/deliveries/details?deliveryId=" + DELIVERY_ID);
            verify(deliveriesManager).deleteAllocations(eq(STORE_ID), eq(DELIVERY_ID), any());
        }
    }

    @Test
    void mergeIsRefusedWhenApprovalStatesOfSourceAndTargetDiffer() {
        // given
        Delivery source = awaitingApprovalDelivery();
        Delivery target = new Delivery(STORE_ID, null, PROVIDER);
        target.setDeliveryId("delivery-2");
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(source);
        when(deliveriesRepository.findById(STORE_ID, "delivery-2")).thenReturn(target);
        when(messageSource.getMessage(eq("deliveries.merge.error.approvalState"), eq(null), eq(Locale.ENGLISH)))
                .thenReturn("Deliveries with a different approval state cannot be merged.");
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
                    "Deliveries with a different approval state cannot be merged.");
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
        source.setDropshipOrderId("order-1");
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
        delivery.setDropshipOrderId("order-1");
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

    private Delivery awaitingApprovalDelivery() {
        Delivery delivery = new Delivery(STORE_ID, null, PROVIDER);
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setOrderStatus(DeliveryOrderStatus.AWAITING_APPROVAL);
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
        when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
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
}
