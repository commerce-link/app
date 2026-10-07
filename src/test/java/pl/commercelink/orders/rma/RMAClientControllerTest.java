package pl.commercelink.orders.rma;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
                .thenReturn(new ShipmentCreationStart(ShipmentCreationStart.Outcome.STARTED, null));
        when(optimisticLockingExecutor.modifyAndSave(any(), any(), any())).thenAnswer(passThroughModifyAndSave());
        RMAReturnForm form = new RMAReturnForm();
        ShippingDetails address = ShippingDetails._default();
        form.setShippingDetails(address);
        form.setSelectedPackageTemplateId("7");

        // when
        controller.postClientBillingShippingForm("store-1", "rma-1", form, new ExtendedModelMap(),
                new RedirectAttributesModelMap(), Locale.forLanguageTag("pl"));

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
                .thenReturn(new ShipmentCreationStart(ShipmentCreationStart.Outcome.STARTED, null));
        when(optimisticLockingExecutor.modifyAndSave(any(), any(), any())).thenThrow(new RuntimeException("DynamoDB"));
        when(messageSource.getMessage(anyString(), any(), any())).thenAnswer(i -> i.getArgument(0));
        RMAReturnForm form = new RMAReturnForm();
        form.setShippingDetails(ShippingDetails._default());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.postClientBillingShippingForm("store-1", "rma-1", form, new ExtendedModelMap(),
                redirect, Locale.forLanguageTag("pl"));

        // then
        assertThat(view).isEqualTo("redirect:/store/store-1/client/rma/rma-1");
        assertThat(redirect.getFlashAttributes().get("successMessage")).isEqualTo("rma.shipment.has.been.created");
        assertThat(redirect.getFlashAttributes()).doesNotContainKey("errorMessage");
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
                Locale.forLanguageTag("pl"));
        return redirect;
    }

    @Test
    void aResubmissionRefusedBecauseTheReturnIsAlreadyHandledGetsANeutralMessage() {
        // given
        RMA rma = approvedRma();
        rma.getShipments().add(unconfirmedReturn());
        when(rmaShippingService.startReturnShipment(any(), any()))
                .thenReturn(new ShipmentCreationStart(ShipmentCreationStart.Outcome.GONE, null));

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
                    "Furgonetka nie potwierdziła nadania — sprawdź przesyłkę w jej panelu");
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
                .thenReturn(new ShipmentCreationStart(ShipmentCreationStart.Outcome.REFUSED, "Nieprawidłowy kod pocztowy"));

        // when
        RedirectAttributesModelMap redirect = submit("7");

        // then
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo("Nieprawidłowy kod pocztowy");
        assertThat(rma.getStatus()).isEqualTo(RMAStatus.Approved);
        verify(optimisticLockingExecutor, never()).modifyAndSave(any(), any(), any());
    }
}
