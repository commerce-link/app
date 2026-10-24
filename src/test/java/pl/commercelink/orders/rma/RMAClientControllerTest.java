package pl.commercelink.orders.rma;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCreationState;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.shipping.ShipmentCreationStart;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.publicpage.PublicPage;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pl.commercelink.testsupport.OptimisticLockingExecutorMocks.passThroughModifyAndSave;

@ExtendWith(MockitoExtension.class)
class RMAClientControllerTest {

    @Mock private RMARepository rmaRepository;
    @Mock private RMAItemsRepository rmaItemsRepository;
    @Mock private StoresRepository storesRepository;
    @Mock private RMAShippingService rmaShippingService;
    @Mock private OptimisticLockingExecutor optimisticLockingExecutor;
    @Mock private MessageSource messageSource;

    @InjectMocks
    private RMAClientController controller;

    @Test
    void theCustomersPackageTemplateIsKeptWithTheAddressForARetryOfTheReturn() {
        // given
        RMA rma = new RMA("store-1");
        rma.setRmaId("rma-1");
        rma.setStatus(RMAStatus.Approved);
        when(rmaRepository.findById("store-1", "rma-1")).thenReturn(rma);
        when(storesRepository.findById("store-1")).thenReturn(new Store());
        when(rmaShippingService.startReturnShipment(any(), any()))
                .thenReturn(new ShipmentCreationStart(ShipmentCreationStart.Outcome.STARTED, null, false));
        when(optimisticLockingExecutor.modifyAndSave(any(), any(), any())).thenAnswer(passThroughModifyAndSave());
        RMAReturnForm form = new RMAReturnForm();
        ShippingDetails address = ShippingDetails._default();
        form.setShippingDetails(address);
        form.setSelectedPackageTemplateId("7");

        // when
        controller.postClientBillingShippingForm("store-1", "rma-1", form, new ExtendedModelMap(),
                new RedirectAttributesModelMap(), new MockHttpServletResponse(), Locale.forLanguageTag("pl"));

        // then
        assertThat(rma.getReturnPackageTemplateId()).isEqualTo("7");
        assertThat(rma.getShippingDetails()).isSameAs(address);
        assertThat(rma.getStatus()).isEqualTo(RMAStatus.WaitingForItems);
    }

    @Test
    void aStartedReturnIsReportedAsStartedEvenWhenTheRmaCouldNotBeUpdatedAfterwards() {
        // given
        RMA rma = new RMA("store-1");
        rma.setRmaId("rma-1");
        rma.setStatus(RMAStatus.Approved);
        when(rmaRepository.findById("store-1", "rma-1")).thenReturn(rma);
        when(storesRepository.findById("store-1")).thenReturn(new Store());
        when(rmaShippingService.startReturnShipment(any(), any()))
                .thenReturn(new ShipmentCreationStart(ShipmentCreationStart.Outcome.STARTED, null, false));
        when(optimisticLockingExecutor.modifyAndSave(any(), any(), any())).thenThrow(new RuntimeException("DynamoDB"));
        when(messageSource.getMessage(anyString(), any(), any())).thenAnswer(i -> i.getArgument(0));
        RMAReturnForm form = new RMAReturnForm();
        form.setShippingDetails(ShippingDetails._default());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.postClientBillingShippingForm("store-1", "rma-1", form, new ExtendedModelMap(),
                redirect, new MockHttpServletResponse(), Locale.forLanguageTag("pl"));

        // then
        assertThat(view).isEqualTo("redirect:/store/store-1/client/rma/rma-1");
        assertThat(redirect.getFlashAttributes().get("successMessage")).isEqualTo("rma.shipment.has.been.created");
        assertThat(redirect.getFlashAttributes()).doesNotContainKey("errorMessage");
    }
    private static final String UNAVAILABLE = "client-return/unavailable";
    private static final String RMA_PATH = "/store/store-1/client/rma/rma-1";

    @ParameterizedTest
    @EnumSource(value = RMAStatus.class, names = {"New", "Rejected", "Completed"})
    void aReturnThatIsNotOpenForTheCustomerAnswersTheUnavailablePageWith404(RMAStatus status) {
        // given
        Store store = namedStore();
        RMA rma = new RMA("store-1");
        rma.setRmaId("rma-1");
        rma.setStatus(status);
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(rmaRepository.findById("store-1", "rma-1")).thenReturn(rma);
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.getRMAForClient("store-1", "rma-1", model, response, Locale.forLanguageTag("pl"));

        // then
        assertThat(view).isEqualTo(UNAVAILABLE);
        assertThat(response.getStatus()).isEqualTo(404);
        PublicPage page = (PublicPage) model.get("publicPage");
        assertThat(page.storeName()).isEqualTo("Nova Tech");
        assertThat(page.otherLanguageHref()).isEqualTo(RMA_PATH + "?lang=en");
        assertThat(model).doesNotContainKeys("rma", "errorMessage");
    }

    @Test
    void aMissingReturnAnswersTheSameUnavailablePage() {
        // given
        when(storesRepository.findById("store-1")).thenReturn(namedStore());
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.getRMAForClient("store-1", "rma-1", model, response, Locale.forLanguageTag("pl"));

        // then
        assertThat(view).isEqualTo(UNAVAILABLE);
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(((PublicPage) model.get("publicPage")).storeName()).isEqualTo("Nova Tech");
    }

    @Test
    void aReturnWithoutItemsAnswersTheUnavailablePage() {
        // given
        when(storesRepository.findById("store-1")).thenReturn(namedStore());
        RMA rma = new RMA("store-1");
        rma.setRmaId("rma-1");
        rma.setStatus(RMAStatus.Approved);
        when(rmaRepository.findById("store-1", "rma-1")).thenReturn(rma);
        when(rmaItemsRepository.findByRmaId("rma-1")).thenReturn(List.of());
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.getRMAForClient("store-1", "rma-1", new ExtendedModelMap(), response, Locale.forLanguageTag("pl"));

        // then
        assertThat(view).isEqualTo(UNAVAILABLE);
        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void aStoreWithoutReturnSettingsAnswersTheUnavailablePageWithoutTheSettingsMessage() {
        // given
        Store store = namedStore();
        when(storesRepository.findById("store-1")).thenReturn(store);
        RMA rma = new RMA("store-1");
        rma.setRmaId("rma-1");
        rma.setStatus(RMAStatus.Approved);
        when(rmaRepository.findById("store-1", "rma-1")).thenReturn(rma);
        when(rmaItemsRepository.findByRmaId("rma-1")).thenReturn(List.of(new RMAItem()));
        doThrow(new InvalidReturnConfigurationException("Store return settings not configured."))
                .when(rmaShippingService).validateStoreReturnConfiguration(store);
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.getRMAForClient("store-1", "rma-1", model, response, Locale.forLanguageTag("pl"));

        // then
        assertThat(view).isEqualTo(UNAVAILABLE);
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(model).doesNotContainKey("errorMessage");
    }

    @Test
    void anUnknownStoreAnswersTheUnavailablePageWithoutABrand() {
        // given
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.getRMAForClient("store-1", "rma-1", model, response, Locale.forLanguageTag("pl"));

        // then
        assertThat(view).isEqualTo(UNAVAILABLE);
        assertThat(response.getStatus()).isEqualTo(404);
        PublicPage page = (PublicPage) model.get("publicPage");
        assertThat(page.storeId()).isNull();
        assertThat(page.storeName()).isEmpty();
        verifyNoInteractions(rmaRepository);
    }

    @Test
    void aSubmissionForAReturnNotApprovedAnswersTheUnavailablePageLinkedToTheReturnPage() {
        // given
        when(storesRepository.findById("store-1")).thenReturn(namedStore());
        RMA rma = new RMA("store-1");
        rma.setRmaId("rma-1");
        rma.setStatus(RMAStatus.WaitingForItems);
        when(rmaRepository.findById("store-1", "rma-1")).thenReturn(rma);
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.postClientBillingShippingForm("store-1", "rma-1", new RMAReturnForm(), model,
                new RedirectAttributesModelMap(), response, Locale.forLanguageTag("pl"));

        // then
        assertThat(view).isEqualTo(UNAVAILABLE);
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(((PublicPage) model.get("publicPage")).otherLanguageHref()).isEqualTo(RMA_PATH + "?lang=en");
        verify(rmaShippingService, never()).startReturnShipment(any(), any());
    }

    @Test
    void aSubmissionInAnUnknownStoreAnswersTheUnavailablePage() {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.postClientBillingShippingForm("store-1", "rma-1", new RMAReturnForm(), new ExtendedModelMap(),
                new RedirectAttributesModelMap(), response, Locale.forLanguageTag("pl"));

        // then
        assertThat(view).isEqualTo(UNAVAILABLE);
        assertThat(response.getStatus()).isEqualTo(404);
        verifyNoInteractions(rmaShippingService);
    }

    private static Store namedStore() {
        Store store = new Store();
        store.setStoreId("store-1");
        store.setName("Nova Tech");
        return store;
    }

    private RMA approvedRma() {
        RMA rma = new RMA("store-1");
        rma.setRmaId("rma-1");
        rma.setStatus(RMAStatus.Approved);
        rma.setShipments(new ArrayList<>());
        when(rmaRepository.findById("store-1", "rma-1")).thenReturn(rma);
        when(storesRepository.findById("store-1")).thenReturn(new Store());
        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(optimisticLockingExecutor.modifyAndSave(any(), any(), any())).thenAnswer(passThroughModifyAndSave());
        return rma;
    }

    private static Shipment unconfirmedReturn() {
        Shipment s = new Shipment(ShipmentType.Courier);
        s.setProvider("furgonetka");
        s.setCreation(ShipmentCreationState.pending("cmd-0", LocalDateTime.now())
                .failedWithKey(ShipmentCreationState.UNCONFIRMED_KEY));
        return s;
    }

    private RedirectAttributesModelMap submit(String templateId) {
        RMAReturnForm form = new RMAReturnForm();
        form.setShippingDetails(ShippingDetails._default());
        form.setSelectedPackageTemplateId(templateId);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        controller.postClientBillingShippingForm("store-1", "rma-1", form, new ExtendedModelMap(), redirect,
                new MockHttpServletResponse(), Locale.forLanguageTag("pl"));
        return redirect;
    }

    @Test
    void aResubmissionRefusedBecauseTheReturnIsAlreadyHandledGetsANeutralMessage() {
        // given
        RMA rma = approvedRma();
        rma.getShipments().add(unconfirmedReturn());
        when(rmaShippingService.startReturnShipment(any(), any()))
                .thenReturn(new ShipmentCreationStart(ShipmentCreationStart.Outcome.GONE, null, false));

        // when
        RedirectAttributesModelMap redirect = submit("7");

        // then
        assertThat(redirect.getFlashAttributes().get("warningMessage")).isEqualTo("rma.shipment.return.in.progress");
        assertThat(redirect.getFlashAttributes()).doesNotContainKey("errorMessage");
        verify(optimisticLockingExecutor, never()).modifyAndSave(any(), any(), any());
    }

    @Test
    void anUnconfirmedReturnIsNotExplainedToTheCustomerInTheOperatorsWords() {
        // given: the check could not be sent, so the paid label may exist and only the operator can tell
        RMA rma = approvedRma();
        when(rmaShippingService.startReturnShipment(any(), any())).thenAnswer(i -> {
            rma.getShipments().add(unconfirmedReturn());
            return new ShipmentCreationStart(ShipmentCreationStart.Outcome.REFUSED,
                    "Furgonetka nie potwierdziła nadania — sprawdź przesyłkę w jej panelu", false);
        });

        // when
        RedirectAttributesModelMap redirect = submit("7");

        // then
        assertThat(redirect.getFlashAttributes().get("warningMessage")).isEqualTo("rma.shipment.return.in.progress");
        assertThat(redirect.getFlashAttributes()).doesNotContainKey("errorMessage");
        assertThat(rma.getStatus()).isEqualTo(RMAStatus.WaitingForItems);
        assertThat(rma.getReturnPackageTemplateId()).isEqualTo("7");
        assertThat(rma.getShippingDetails()).isNotNull();
    }

    @Test
    void aCleanRefusalShowsTheReasonSoTheCustomerCanCorrectTheData() {
        // given
        RMA rma = approvedRma();
        when(rmaShippingService.startReturnShipment(any(), any()))
                .thenReturn(new ShipmentCreationStart(ShipmentCreationStart.Outcome.REFUSED, "Nieprawidłowy kod pocztowy", true));

        // when
        RedirectAttributesModelMap redirect = submit("7");

        // then
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo("Nieprawidłowy kod pocztowy");
        assertThat(rma.getStatus()).isEqualTo(RMAStatus.Approved);
        verify(optimisticLockingExecutor, never()).modifyAndSave(any(), any(), any());
    }

    @Test
    void aRefusalWithoutTheProvidersAnswerIsNotShownToTheCustomerInTechnicalWords() {
        // given: the adapter gave up before the provider answered, in its own English words
        RMA rma = approvedRma();
        when(rmaShippingService.startReturnShipment(any(), any())).thenReturn(new ShipmentCreationStart(
                ShipmentCreationStart.Outcome.REFUSED, "could not create the package: Read timed out", false));

        // when
        RedirectAttributesModelMap redirect = submit("7");

        // then
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo("rma.shipment.creation.failed");
        assertThat(rma.getStatus()).isEqualTo(RMAStatus.Approved);
    }
}
