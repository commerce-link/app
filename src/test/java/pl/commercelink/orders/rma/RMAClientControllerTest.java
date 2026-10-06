package pl.commercelink.orders.rma;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.shipping.ShipmentCreationStart;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
}
