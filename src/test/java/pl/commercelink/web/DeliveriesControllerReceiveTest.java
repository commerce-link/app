package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.documents.Document;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.DeliveryReceptionService;
import pl.commercelink.inventory.deliveries.Dropship;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.web.dtos.DeliveryAllocationsForm;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveriesControllerReceiveTest {

    @Mock
    private DeliveriesRepository deliveriesRepository;

    @Mock
    private DeliveryReceptionService deliveryReceptionService;

    @Mock
    private MessageSource messageSource;

    @Mock
    private RedirectAttributes redirectAttributes;

    @InjectMocks
    private DeliveriesController controller;

    @Test
    void receiveOnDropshipDeliveryRedirectsWithoutReceiving() {
        // given
        Delivery delivery = new Delivery("store-1", null, "Acme");
        delivery.setDropship(new Dropship("order-1"));
        when(deliveriesRepository.findById("store-1", delivery.getDeliveryId())).thenReturn(delivery);
        when(messageSource.getMessage(eq("deliveries.receive.error.dropship"), any(), any()))
                .thenReturn("blocked");
        DeliveryAllocationsForm form = new DeliveryAllocationsForm();
        form.setStoreId("store-1");
        form.setDeliveryId(delivery.getDeliveryId());

        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);

            // when
            String view = controller.markSelectedAllocationsAsReceived(form, redirectAttributes, Locale.ENGLISH);

            // then
            assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=" + delivery.getDeliveryId());
            verify(redirectAttributes).addFlashAttribute("errorMessage", "blocked");
            verifyNoInteractions(deliveryReceptionService);
        }
    }

    @Test
    void receiveOnRegularDeliveryReachesReceptionService() {
        // given
        Delivery delivery = new Delivery("store-1", null, "Acme");
        when(deliveriesRepository.findById("store-1", delivery.getDeliveryId())).thenReturn(delivery);
        when(deliveryReceptionService.receive(any(), any(), any(), any(), any(), any()))
                .thenReturn(OperationResult.success(null));
        DeliveryAllocationsForm form = new DeliveryAllocationsForm();
        form.setStoreId("store-1");
        form.setDeliveryId(delivery.getDeliveryId());

        // when
        String view = controller.markSelectedAllocationsAsReceived(form, redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=" + delivery.getDeliveryId());
        verify(deliveryReceptionService).receive(any(), any(), any(), any(), any(), any());
    }
}
