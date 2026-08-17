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
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveryOrderStatus;
import pl.commercelink.inventory.deliveries.DeliveriesQueryService;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.SupplierPurchaseService;
import pl.commercelink.inventory.supplier.api.SupplierDeliveryAddress;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.DeliveryCreationForm;
import pl.commercelink.web.dtos.PickerOption;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    private DeliveriesRepository deliveriesRepository;

    @Mock
    private StoresRepository storesRepository;

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
            String view = deliveriesController.showDeliveryDetailsForSuperAdmin(STORE_ID, DELIVERY_ID, model);

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
            String view = deliveriesController.showDeliveryDetails(DELIVERY_ID, model);

            // then
            assertThat(view).isEqualTo("deliveryDetails");
            assertThat(model.containsAttribute("approvalAddresses")).isFalse();
            assertThat(model.containsAttribute("approvalAddressOptions")).isFalse();
            verify(supplierPurchaseService, never()).deliveryAddressesForDelivery(any(), any());
        }
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
        when(messageSource.getMessage(eq("deliveries.purchase.retry.error.state"), eq(null), eq(Locale.forLanguageTag("pl"))))
                .thenReturn("Tej dostawy nie mozna powtorzyc.");

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);

            // when
            String view = deliveriesController.retryPurchase(DELIVERY_ID, redirectAttributes, Locale.forLanguageTag("pl"));

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=" + DELIVERY_ID);
            verify(supplierPurchaseService, never()).retry(any(), any());
            verify(redirectAttributes).addFlashAttribute("errorMessage", "Tej dostawy nie mozna powtorzyc.");
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
