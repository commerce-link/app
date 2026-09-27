package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveriesQueryService;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Renders the real {@code fragments/payments-section :: paymentsSection(...)}
 * call that {@code deliveryDetails.html} makes (same arguments, same order), fed by the exact model
 * {@link DeliveriesController#showDeliveryDetails} builds for real (mocked repositories, real controller method) —
 * not a hand-picked {@code paymentSources} stand-in. Rendering {@code deliveryDetails.html} itself is not possible
 * through this lightweight template engine: its top form uses classic Spring {@code th:field} binding, which needs
 * a live {@code BindingResult}/{@code RequestContext} that only a real Spring MVC view-resolution provides; the
 * payments section does not use {@code th:field} (raw {@code name=}, see payments-section.html's own doc comment),
 * so rendering that fragment directly is both possible and exactly where the regression lived.
 * <p>
 * Nothing covered this render before: the shared "paymentSources" attribute became an
 * {@code OrderLabels.Option} list, but the Bulma edit modal (#paymentsEditModal) still read it as raw
 * {@code PaymentSource} values, so options rendered as {@code Option[value=..., labelKey=...]} and nothing could
 * be preselected or saved. This test fails while payments-section.html reads the raw values instead of
 * value()/labelKey().
 */
@ExtendWith(MockitoExtension.class)
class DeliveryPaymentsSectionPageRenderTest {

    private static final String STORE_ID = "store-1";
    private static final String DELIVERY_ID = "delivery-1";

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
    void deliveryDetailsPageRendersThePaymentsEditModalWithSelectedEnumOptions() {
        // given: a delivery with one BankTransfer payment, so the edit modal has a row to preselect
        Delivery delivery = new Delivery(STORE_ID, null, "Acme");
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setPaymentCost(150);
        delivery.addPayment(new Payment("REF-1", "Jan", PaymentSource.BankTransfer, 150, 0));
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        when(deliveriesRepository.findPendingDeliveriesByProvider(STORE_ID, "Acme", DELIVERY_ID)).thenReturn(List.of());
        var labels = new SupplierLabels(mock(StoresRepository.class)).forStoreId(STORE_ID);
        when(supplierLabels.forStoreId(STORE_ID)).thenReturn(labels);

        Model model = new ConcurrentModel();
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);
            security.when(() -> CustomSecurityContext.hasRole("ADMIN")).thenReturn(true);
            view = deliveriesController.showDeliveryDetails(DELIVERY_ID, model, redirectAttributes, Locale.forLanguageTag("pl"));
        }
        assertThat(view).isEqualTo("deliveryDetails");

        // when: the same fragment call deliveryDetails.html makes (payments-section.html:701-709), fed by the
        // real model (model.asMap() already carries "delivery", "paymentSources" and "pendingPayment" as the
        // controller set them)
        Map<String, Object> variables = new HashMap<>(model.asMap());
        String html = SettingsTemplateRenderer.render(
                "<div th:replace=\"~{fragments/payments-section :: paymentsSection("
                        + "payments=${delivery.payments},"
                        + "addPaymentAction=@{/dashboard/deliveries/{deliveryId}/addPayment(deliveryId=${delivery.deliveryId})},"
                        + "updatePaymentsAction=@{/dashboard/deliveries/{deliveryId}/updatePayments(deliveryId=${delivery.deliveryId})},"
                        + "expectedAmount=${delivery.unpaidAmount},"
                        + "pendingPayment=${pendingPayment},"
                        + "paymentSources=${paymentSources},"
                        + "addDisabled=false,"
                        + "editEnabled=true,"
                        + "mode='delivery')}\"></div>",
                variables);

        // then: the shared add-payment dialog renders, and the Bulma edit modal preselects the real enum value
        assertThat(html).contains("id=\"addPaymentModal\"").contains("id=\"paymentsEditModal\"")
                .doesNotContain("Option[")
                .containsPattern("<option value=\"BankTransfer\"[^>]*selected[^>]*>Przelew bankowy<")
                .contains(">Gotówka<");
        // the delivery details page's payments section must load money.js exactly once
        assertThat(occurrences(html, "/js/money.js")).isEqualTo(1);
    }

    static int occurrences(String html, String needle) {
        return html.split(Pattern.quote(needle), -1).length - 1;
    }
}
