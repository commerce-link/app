package pl.commercelink.web.deliveries.details;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.inventory.deliveries.DeliveriesQueryService;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.DeliveriesController;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** The page as DeliveriesController#showDeliveryDetails builds it for real, rendered through the real template. */
@ExtendWith(MockitoExtension.class)
class DeliveryDetailsPageRenderTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private DeliveriesQueryService deliveriesQueryService;
    @Mock
    private DeliveriesRepository deliveriesRepository;
    @Mock
    private SupplierRegistry supplierRegistry;
    @Mock
    private SupplierLabels supplierLabels;
    @Mock
    private RedirectAttributes redirectAttributes;
    @InjectMocks
    private DeliveriesController deliveriesController;

    @Test
    void thePaymentDialogPreselectsTheStoredMethodAndLoadsMoneyJsOnce() {
        // given
        Delivery delivery = new Delivery(STORE_ID, null, "Acme");
        delivery.setPaymentCost(150);
        delivery.addPayment(new Payment("REF-1", "Jan", PaymentSource.BankTransfer, 150, 0));
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);
        when(deliveriesRepository.findPendingDeliveriesByProvider(STORE_ID, "Acme", delivery.getDeliveryId())).thenReturn(List.of());
        // built before stubbing: the real labels would otherwise call a mock in the middle of when(...)
        SupplierLabelMap labels = new SupplierLabels(mock(StoresRepository.class)).forStore(null);
        when(supplierLabels.forStoreId(STORE_ID)).thenReturn(labels);
        Model model = new ConcurrentModel();
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);
            security.when(() -> CustomSecurityContext.hasRole("ADMIN")).thenReturn(true);
            deliveriesController.showDeliveryDetails(delivery.getDeliveryId(), null, null, null, model, redirectAttributes,
                    Locale.forLanguageTag("pl"));
        }
        Map<String, Object> variables = new HashMap<>(model.asMap());
        variables.put("navigation", null);

        // when
        String html = SettingsTemplateRenderer.render("deliveries/details", variables);

        // then
        assertThat(html).contains("id=\"addPaymentModal\"").contains("id=\"payment-0-dialog\"")
                .doesNotContain("Option[").containsPattern("<option value=\"BankTransfer\"[^>]*selected[^>]*>Przelew bankowy<")
                .contains(">Gotówka<");
        assertThat(DeliveryDetailsTemplates.occurrences(html, "/js/money.js")).isEqualTo(1);
    }
}
