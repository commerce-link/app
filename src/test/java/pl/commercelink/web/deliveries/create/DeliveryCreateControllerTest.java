package pl.commercelink.web.deliveries.create;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.inventory.deliveries.*;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.orders.Order;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.DeliveryCreationForm;
import pl.commercelink.web.dtos.DeliveryFulfilmentUpdateForm;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryCreateControllerTest {

    private static final String STORE_ID = "store-1";
    private static final String PROVIDER = "Acme";
    private static final String ORDER_ID = "order-1";

    @Mock private DeliveryScopes scopes;
    @Mock private DeliveryScope scope;
    @Mock private SupplierPurchaseService supplierPurchaseService;
    @Mock private DeliveryFulfilmentUpdateService fulfilmentUpdateService;
    @Mock private SupplierLabels supplierLabels;
    @Mock private MessageSource messageSource;

    @InjectMocks private DeliveryCreateController controller;

    private final RedirectAttributes flash = new RedirectAttributesModelMap();

    @BeforeEach
    void setUp() {
        lenient().when(supplierLabels.forStoreId(any()))
                .thenReturn(new SupplierLabels(mock(StoresRepository.class)).forStore(null));
        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(invocation -> "msg:" + invocation.getArgument(0));
        lenient().when(scope.provider()).thenReturn(PROVIDER);
        lenient().when(scopes.resolve(eq(STORE_ID), eq(PROVIDER), any())).thenReturn(new DeliveryScopes.Resolution.Found(scope));
    }

    private <T> T asStoreAdmin(Supplier<T> call) {
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);
            return call.get();
        }
    }

    private <T> T asSuperAdmin(Supplier<T> call) {
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);
            return call.get();
        }
    }

    private static DeliveryCreationForm requested(int qty) {
        DeliveryCreationForm form = new DeliveryCreationForm();
        DeliveryItem item = new DeliveryItem();
        item.setMfn("MFN-1");
        item.setRequestedQty(qty);
        form.setItems(new ArrayList<>(List.of(item)));
        return form;
    }

    private static BindingResult binding(DeliveryCreationForm form) {
        return new BeanPropertyBindingResult(form, "form");
    }

    @Test
    void stepOneShowsThePlannedForm() {
        // given
        DeliveryCreationForm planned = requested(2);
        when(scope.plannedForm()).thenReturn(planned);
        Model model = new ConcurrentModel();

        // when
        String view = asStoreAdmin(() -> controller.items(PROVIDER, null, null, model, flash, Locale.ENGLISH));

        // then
        assertThat(view).isEqualTo("deliveries/create/items");
        assertThat(model.getAttribute("form")).isSameAs(planned);
        assertThat(((DeliveryCreatePage) model.getAttribute("page")).links().items())
                .isEqualTo("/dashboard/deliveries/create/Acme");
    }

    @Test
    void stepOneWithNothingPlannedGoesBackToThePreview() {
        // given
        when(scope.plannedForm()).thenReturn(null);

        // when
        String view = asStoreAdmin(() -> controller.items(PROVIDER, null, null, new ConcurrentModel(), flash, Locale.ENGLISH));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/deliveries/preview");
    }

    @Test
    void everyPostRechecksDropshipEligibility() {
        // given
        when(scopes.resolve(STORE_ID, PROVIDER, ORDER_ID))
                .thenReturn(new DeliveryScopes.Resolution.Refused("orders.dropship.rejected.providerMismatch"));
        List<Supplier<String>> posts = List.of(
                () -> controller.purchase(PROVIDER, requested(1), binding(requested(1)), ORDER_ID, null, new ConcurrentModel(), flash, Locale.ENGLISH),
                () -> controller.manual(PROVIDER, requested(1), binding(requested(1)), ORDER_ID, null, new ConcurrentModel(), flash, Locale.ENGLISH),
                () -> controller.save(PROVIDER, requested(1), binding(requested(1)), ORDER_ID, null, new ConcurrentModel(), flash, Locale.ENGLISH),
                () -> controller.confirm(PROVIDER, "ref-1", requested(1), binding(requested(1)), ORDER_ID, null, new ConcurrentModel(), flash, Locale.ENGLISH),
                () -> controller.back(PROVIDER, requested(1), binding(requested(1)), ORDER_ID, "order", new ConcurrentModel(), flash, Locale.ENGLISH));
        Model validateModel = new ConcurrentModel();

        // when
        List<String> views = posts.stream().map(this::asStoreAdmin).toList();
        String validate = asStoreAdmin(() -> controller.validate(PROVIDER, requested(1), binding(requested(1)), ORDER_ID, null,
                validateModel, flash, Locale.ENGLISH));

        // then
        assertThat(views).allMatch(view -> view.equals("redirect:/dashboard/orders/" + ORDER_ID));
        assertThat(flash.getFlashAttributes().get("errorMessage")).isEqualTo("msg:orders.dropship.rejected.providerMismatch");
        assertThat(validate).isEqualTo("deliveries/create/purchase :: validationResult");
        assertThat(validateModel.getAttribute("validationError")).isEqualTo("msg:orders.dropship.rejected.providerMismatch");
        verify(scope, never()).save(any());
        verify(scope, never()).submit(any(), any());
        verify(scope, never()).validate(any());
        verify(scope, never()).releaseUnselected(any());
    }

    @Test
    void manualStepMergesTheSuggestionsAndShowsTheRecordPage() {
        // given
        DeliveryCreationForm form = requested(1);
        Model model = new ConcurrentModel();

        // when
        String view = asStoreAdmin(() -> controller.manual(PROVIDER, form, binding(form), null, null, model, flash, Locale.ENGLISH));

        // then
        assertThat(view).isEqualTo("deliveries/create/manual");
        verify(supplierPurchaseService).mergeSuggestedItems(form);
        assertThat(form.getStoreId()).isEqualTo(STORE_ID);
        assertThat(form.getProvider()).isEqualTo(PROVIDER);
        assertThat((Map<?, ?>) model.getAttribute("errors")).isEmpty();
    }

    @Test
    void nothingRequestedStaysOnStepOneWithTheReason() {
        // given
        DeliveryCreationForm posted = requested(0);
        when(scope.plannedForm()).thenReturn(requested(1));
        Model model = new ConcurrentModel();

        // when
        String manual = asStoreAdmin(() -> controller.manual(PROVIDER, posted, binding(posted), null, null, model, flash, Locale.ENGLISH));
        when(scope.purchaseAvailable()).thenReturn(true);
        Model purchaseModel = new ConcurrentModel();
        String purchase = asStoreAdmin(() -> controller.purchase(PROVIDER, requested(0), binding(requested(0)), null, null, purchaseModel, flash, Locale.ENGLISH));

        // then
        assertThat(manual).isEqualTo("deliveries/create/items");
        assertThat(purchase).isEqualTo("deliveries/create/items");
        assertThat(model.getAttribute("stepError")).isEqualTo("deliveries.create.error.nothingRequested");
        assertThat(purchaseModel.getAttribute("stepError")).isEqualTo("deliveries.create.error.nothingRequested");
    }

    @Test
    void dropshipWithNothingTickedAndReleaseReleasesTheLinesAndReturnsToTheOrder() {
        // given
        when(scopes.resolve(STORE_ID, PROVIDER, ORDER_ID)).thenReturn(new DeliveryScopes.Resolution.Found(scope));
        when(scope.dropship()).thenReturn(true);
        DeliveryCreationForm form = requested(0);
        form.setRemoveUnselected(true);

        // when
        String view = asStoreAdmin(() -> controller.manual(PROVIDER, form, binding(form), ORDER_ID, null, new ConcurrentModel(), flash, Locale.ENGLISH));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
        verify(scope).releaseUnselected(form);
        assertThat(flash.getFlashAttributes().get("successMessage")).isEqualTo("msg:orders.dropship.unselectedReleased");
    }

    @Test
    void saveWithoutTheSupplierOrderNumberShowsTheErrorsAndReleasesNothing() {
        // given
        when(scope.requiresOrderIdentity()).thenReturn(true);
        DeliveryCreationForm form = requested(1);
        form.setRemoveUnselected(true);
        Model model = new ConcurrentModel();

        // when
        String view = asStoreAdmin(() -> controller.save(PROVIDER, form, binding(form), null, null, model, flash, Locale.ENGLISH));

        // then
        assertThat(view).isEqualTo("deliveries/create/manual");
        assertThat((Map<String, String>) model.getAttribute("errors"))
                .containsKeys("externalDeliveryId", "estimatedDeliveryAt");
        verify(scope, never()).save(any());
    }

    @Test
    void saveRecordsTheDeliveryAndOpensIt() {
        // given
        when(scope.requiresOrderIdentity()).thenReturn(true);
        DeliveryCreationForm form = requested(1);
        form.setExternalDeliveryId("ACM-1");
        form.setEstimatedDeliveryAt(LocalDate.of(2026, 10, 9));
        when(scope.save(form)).thenReturn(OperationResult.success("d-1"));

        // when
        String view = asStoreAdmin(() -> controller.save(PROVIDER, form, binding(form), null, null, new ConcurrentModel(), flash, Locale.ENGLISH));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=d-1");
    }

    @Test
    void failedSaveStaysOnTheRecordPageWithTheServicesMessage() {
        // given
        DeliveryCreationForm form = requested(1);
        when(scope.save(form)).thenReturn(OperationResult.failure("orders.dropship.error.nothingSelected"));
        Model model = new ConcurrentModel();

        // when
        String view = asStoreAdmin(() -> controller.save(PROVIDER, form, binding(form), null, null, model, flash, Locale.ENGLISH));

        // then
        assertThat(view).isEqualTo("deliveries/create/manual");
        assertThat(model.getAttribute("errorMessage")).isEqualTo("msg:orders.dropship.error.nothingSelected");
    }

    @Test
    void purchaseWithoutAnIntegrationGoesBackToStepOne() {
        // given
        when(scope.purchaseAvailable()).thenReturn(false);

        // when
        String view = asStoreAdmin(() -> controller.purchase(PROVIDER, requested(1), binding(requested(1)), null, null, new ConcurrentModel(), flash, Locale.ENGLISH));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/deliveries/create/Acme");
    }

    @Test
    void purchaseStepGetsAFreshPurchaseRefAndTheScopesModel() {
        // given
        when(scope.purchaseAvailable()).thenReturn(true);
        DeliveryCreationForm form = requested(1);
        Model model = new ConcurrentModel();

        // when
        String view = asStoreAdmin(() -> controller.purchase(PROVIDER, form, binding(form), null, null, model, flash, Locale.ENGLISH));

        // then
        assertThat(view).isEqualTo("deliveries/create/purchase");
        assertThat((String) model.getAttribute("purchaseRef")).isNotBlank();
        verify(supplierPurchaseService).mergeSuggestedItems(form);
        verify(scope).addPurchaseModel(form, model);
    }

    @Test
    void purchaseStepIgnoresAFieldThatDidNotBind() {
        // given
        when(scope.purchaseAvailable()).thenReturn(true);
        DeliveryCreationForm form = requested(1);
        BindingResult binding = binding(form);
        binding.rejectValue("shippingCost", "typeMismatch");

        // when
        String view = asStoreAdmin(() -> controller.purchase(PROVIDER, form, binding, null, null, new ConcurrentModel(), flash, Locale.ENGLISH));

        // then
        assertThat(view).isEqualTo("deliveries/create/purchase");
    }

    @Test
    void confirmPlacesTheOrderAndAnEmptyVatFallsBackToTheSuppliersDefault() {
        // given
        when(scope.purchaseAvailable()).thenReturn(true);
        when(scope.defaultTax()).thenReturn(1.0);
        DeliveryCreationForm form = requested(1);
        BindingResult binding = binding(form);
        binding.rejectValue("tax", "typeMismatch");
        when(scope.submit(form, "ref-1")).thenReturn(OperationResult.success(new PurchaseSubmission("d-9", false)));

        // when
        String view = asStoreAdmin(() -> controller.confirm(PROVIDER, "ref-1", form, binding, null, null, new ConcurrentModel(), flash, Locale.ENGLISH));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=d-9");
        assertThat(form.getTax()).isEqualTo(1.0);
    }

    @Test
    void warehouseConfirmAlwaysPostsPlnCostsWhateverCurrencyTheRecordStepLeft() {
        // given
        when(scope.purchaseAvailable()).thenReturn(true);
        DeliveryCreationForm form = requested(1);
        form.setSourceCurrency("EUR");
        when(scope.submit(form, "ref-1")).thenReturn(OperationResult.success(new PurchaseSubmission("d-9", false)));

        // when
        asStoreAdmin(() -> controller.confirm(PROVIDER, "ref-1", form, binding(form), null, null, new ConcurrentModel(), flash, Locale.ENGLISH));

        // then
        verify(scope).submit(argThat(submitted -> "PLN".equals(submitted.getSourceCurrency())), eq("ref-1"));
    }

    @Test
    void integrationConfirmRecordsTheDefaultOrderDataWhateverTheHiddenFieldsCarry() {
        // given: the operator typed costs in "Zarejestruj zamówienie", went back and ordered through the integration
        when(scopes.resolve(STORE_ID, PROVIDER, ORDER_ID)).thenReturn(new DeliveryScopes.Resolution.Found(scope));
        when(scope.purchaseAvailable()).thenReturn(true);
        when(scope.defaultTax()).thenReturn(1.23);
        DeliveryCreationForm form = requested(1);
        form.setSourceCurrency("EUR");
        form.setShippingCost(15);
        form.setPaymentCost(3);
        form.setPaymentTerms(14);
        form.setTax(1.0);
        when(scope.submit(form, "ref-1")).thenReturn(OperationResult.success(new PurchaseSubmission("d-9", false)));

        // when
        asStoreAdmin(() -> controller.confirm(PROVIDER, "ref-1", form, binding(form), ORDER_ID, null, new ConcurrentModel(), flash, Locale.ENGLISH));

        // then
        verify(scope).submit(argThat(submitted -> "PLN".equals(submitted.getSourceCurrency())
                && submitted.getShippingCost() == 0 && submitted.getPaymentCost() == 0
                && submitted.getPaymentTerms() == 0 && submitted.getTax() == 1.23), eq("ref-1"));
    }

    @Test
    void failedConfirmKeepsThePurchaseRefForAnIdempotentRetry() {
        // given
        when(scope.purchaseAvailable()).thenReturn(true);
        DeliveryCreationForm form = requested(1);
        when(scope.submit(form, "ref-1")).thenReturn(OperationResult.failure("deliveries.purchase.error.failed"));
        Model model = new ConcurrentModel();

        // when
        String view = asStoreAdmin(() -> controller.confirm(PROVIDER, "ref-1", form, binding(form), null, null, model, flash, Locale.ENGLISH));

        // then
        assertThat(view).isEqualTo("deliveries/create/purchase");
        assertThat(model.getAttribute("purchaseRef")).isEqualTo("ref-1");
        assertThat(model.getAttribute("errorMessage")).isEqualTo("msg:deliveries.purchase.error.failed");
        verify(scope).addPurchaseModel(form, model);
    }

    @Test
    void validationFailureBecomesTheRetryMessage() {
        // given
        when(scope.validate(any())).thenThrow(new IllegalStateException("timeout"));
        Model model = new ConcurrentModel();

        // when
        String view = asStoreAdmin(() -> controller.validate(PROVIDER, requested(1), binding(requested(1)), null, null, model, flash, Locale.ENGLISH));

        // then
        assertThat(view).isEqualTo("deliveries/create/purchase :: validationResult");
        assertThat(model.getAttribute("validationError")).isEqualTo("msg:deliveries.purchase.confirm.checkFailed (timeout)");
    }

    @Test
    void refusedLiveCheckWithoutAReasonAnswersWithTheGenericCheckFailure() {
        // given
        when(scopes.resolve(STORE_ID, PROVIDER, ORDER_ID)).thenReturn(new DeliveryScopes.Resolution.Refused(null));
        Model model = new ConcurrentModel();

        // when
        String view = asStoreAdmin(() -> controller.validate(PROVIDER, requested(1), binding(requested(1)), ORDER_ID, null,
                model, flash, Locale.ENGLISH));

        // then
        assertThat(view).isEqualTo("deliveries/create/purchase :: validationResult");
        assertThat(model.getAttribute("validationError")).isEqualTo("msg:deliveries.purchase.confirm.checkFailed");
        assertThat(model.containsAttribute("page")).isFalse();
        assertThat(flash.getFlashAttributes()).isEmpty();
    }

    @Test
    void backFromManualKeepsTheTypedOrderNumber() {
        // given
        when(scope.plannedForm()).thenReturn(requested(1));
        DeliveryCreationForm posted = requested(3);
        posted.setExternalDeliveryId("ACM-77");
        Model model = new ConcurrentModel();

        // when
        String view = asStoreAdmin(() -> controller.back(PROVIDER, posted, binding(posted), null, null, model, flash, Locale.ENGLISH));

        // then
        assertThat(view).isEqualTo("deliveries/create/items");
        DeliveryCreationForm shown = (DeliveryCreationForm) model.getAttribute("form");
        assertThat(shown.getExternalDeliveryId()).isEqualTo("ACM-77");
        assertThat(shown.getItems().getFirst().getRequestedQty()).isEqualTo(3);
    }

    @Test
    void unreadableItemNumbersSendBothStepTwoRoutesBackToStepOneInsteadOfPostingZeros() {
        // given
        when(scope.purchaseAvailable()).thenReturn(true);
        when(scope.plannedForm()).thenReturn(requested(1));
        DeliveryCreationForm manualForm = requested(1);
        BindingResult manualBinding = binding(manualForm);
        manualBinding.rejectValue("items[0].unitCost", "typeMismatch");
        DeliveryCreationForm purchaseForm = requested(1);
        BindingResult purchaseBinding = binding(purchaseForm);
        purchaseBinding.rejectValue("items[0].requestedQty", "typeMismatch");
        Model manualModel = new ConcurrentModel();
        Model purchaseModel = new ConcurrentModel();

        // when
        String manual = asStoreAdmin(() -> controller.manual(PROVIDER, manualForm, manualBinding, null, null, manualModel, flash, Locale.ENGLISH));
        String purchase = asStoreAdmin(() -> controller.purchase(PROVIDER, purchaseForm, purchaseBinding, null, null, purchaseModel, flash, Locale.ENGLISH));

        // then
        assertThat(manual).isEqualTo("deliveries/create/items");
        assertThat(purchase).isEqualTo("deliveries/create/items");
        assertThat(manualModel.getAttribute("stepError")).isEqualTo("deliveries.create.error.itemNumber");
        assertThat(purchaseModel.getAttribute("stepError")).isEqualTo("deliveries.create.error.itemNumber");
    }

    @Test
    void unreadableSuggestionNumbersAlsoStopStepTwo() {
        // given
        when(scope.plannedForm()).thenReturn(requested(1));
        DeliveryCreationForm form = requested(1);
        form.setSuggestedItems(new ArrayList<>(List.of(new pl.commercelink.web.dtos.SuggestedDeliveryItem())));
        BindingResult binding = binding(form);
        binding.rejectValue("suggestedItems[0].requestedQty", "typeMismatch");
        Model model = new ConcurrentModel();

        // when
        String view = asStoreAdmin(() -> controller.manual(PROVIDER, form, binding, null, null, model, flash, Locale.ENGLISH));

        // then
        assertThat(view).isEqualTo("deliveries/create/items");
        assertThat(model.getAttribute("stepError")).isEqualTo("deliveries.create.error.itemNumber");
    }

    @Test
    void backWithAnEmptyVatFallsBackToTheSuppliersDefault() {
        // given
        when(scope.plannedForm()).thenReturn(requested(1));
        when(scope.defaultTax()).thenReturn(1.23);
        DeliveryCreationForm posted = requested(1);
        BindingResult binding = binding(posted);
        binding.rejectValue("tax", "typeMismatch");
        Model model = new ConcurrentModel();

        // when
        String view = asStoreAdmin(() -> controller.back(PROVIDER, posted, binding, null, null, model, flash, Locale.ENGLISH));

        // then
        assertThat(view).isEqualTo("deliveries/create/items");
        assertThat(((DeliveryCreationForm) model.getAttribute("form")).getTax()).isEqualTo(1.23);
    }

    @Test
    void fulfilmentAnswersJsonAndTheFallbackReloadsStepOne() {
        // given
        DeliveryFulfilmentUpdateForm update = new DeliveryFulfilmentUpdateForm();
        update.setEan("590");
        update.setMfn("NEW");
        update.setUnitCost(12.5);
        when(fulfilmentUpdateService.run(STORE_ID, PROVIDER, update)).thenReturn(OperationResult.success(),
                OperationResult.failure("error.message.delivery.fulfilment.invalid"));

        // when
        FulfilmentUpdateResponse ok = asStoreAdmin(() -> controller.fulfilmentJson(PROVIDER, update, Locale.ENGLISH));
        String fallback = asStoreAdmin(() -> controller.fulfilment(PROVIDER, update, flash, Locale.ENGLISH));

        // then
        assertThat(ok).isEqualTo(new FulfilmentUpdateResponse(true, "msg:deliveries.create.fulfilment.saved", "590", "NEW", "12.50"));
        assertThat(fallback).isEqualTo("redirect:/dashboard/deliveries/create/Acme");
        assertThat(flash.getFlashAttributes().get("errorMessage")).isEqualTo("msg:error.message.delivery.fulfilment.invalid");
    }

    @Test
    void superAdminRoutesStayInTheStoreTheyNamed() {
        // given
        when(scopes.resolve("store-9", PROVIDER, null)).thenReturn(new DeliveryScopes.Resolution.Found(scope));
        when(scope.requiresOrderIdentity()).thenReturn(false);
        DeliveryCreationForm form = requested(1);
        when(scope.save(form)).thenReturn(OperationResult.success("d-1"));

        // when
        String view = asSuperAdmin(() -> controller.saveForSuperAdmin("store-9", PROVIDER, form, binding(form), null, null,
                new ConcurrentModel(), flash, Locale.ENGLISH));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-9/deliveries/details?deliveryId=d-1");
        assertThat(form.getStoreId()).isEqualTo("store-9");
    }

    @Test
    void unusualSupplierNameRoundTripsThroughTheRedirects() {
        // given
        String provider = "Hurt Łódź/2";
        when(scopes.resolve(STORE_ID, provider, null)).thenReturn(new DeliveryScopes.Resolution.Found(scope));
        when(scope.purchaseAvailable()).thenReturn(false);

        // when
        String view = asStoreAdmin(() -> controller.purchase(provider, requested(1), binding(requested(1)), null, null,
                new ConcurrentModel(), flash, Locale.ENGLISH));

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/deliveries/create/Hurt%20%C5%81%C3%B3d%C5%BA%2F2");
        verify(scopes).resolve(STORE_ID, provider, null);
    }
}
