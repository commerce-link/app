package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Review Focus 5, Task 17 fix round 1: renders the real Payments page (payments.html) through the exact model
 * {@link WebController#payments} builds, not a hand-picked stand-in. This is what should have caught the
 * paymentSources type mismatch before it reached a browser (the report only opened the dialog in a manual E2E
 * check, which happened to catch it that time, but no automated test did).
 */
@ExtendWith(MockitoExtension.class)
class PaymentSourcesPageRenderTest {

    private static final String STORE_ID = "store-1";

    /** SettingsTemplateRenderer builds a bare TemplateEngine; enumI18n is normally added by an unrelated starter
     * auto-configuration this test does not load, so a stand-in is supplied (payments.html's own use of it, an
     * order-status label, is not part of what this test checks). */
    public static class EnumLocalizerStub {
        public String localize(Object value) {
            return value == null ? "" : value.toString();
        }
    }

    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private DeliveriesRepository deliveriesRepository;
    @Mock
    private SupplierLabels supplierLabels;
    @InjectMocks
    private WebController webController;

    private static Order unpaidOrder() {
        Order order = new Order(STORE_ID);
        order.setOrderId("11111111-1111-1111-1111-111111111111");
        order.setStatus(OrderStatus.New);
        BillingDetails billing = new BillingDetails();
        billing.setEmail("jan@example.pl");
        order.setBillingDetails(billing);
        order.setTotalPrice(200);
        return order;
    }

    private static Delivery unpaidDelivery() {
        Delivery delivery = new Delivery(STORE_ID, null, "Acme");
        delivery.setPaymentCost(150);
        return delivery;
    }

    @Test
    void paymentsPageRendersPaymentSourceOptionsAsEnumNamesWithResolvedLabels() {
        // given: exactly the model WebController.payments(...) builds for real
        var labels = new SupplierLabels(mock(StoresRepository.class)).forStoreId(STORE_ID);
        when(ordersRepository.findAllActiveOrders(STORE_ID)).thenReturn(List.of(unpaidOrder()));
        when(deliveriesRepository.findUnpaidDeliveries(STORE_ID)).thenReturn(List.of(unpaidDelivery()));
        when(supplierLabels.forStoreId(STORE_ID)).thenReturn(labels);

        Model model = new ConcurrentModel();
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = webController.payments(model);
        }
        assertThat(view).isEqualTo("payments");

        Map<String, Object> variables = new HashMap<>(model.asMap());
        variables.put("navigation", null);
        variables.put("enumI18n", new EnumLocalizerStub());

        // when
        String html = SettingsTemplateRenderer.render("payments", variables);

        // then: the add-payment dialog renders, with real enum names as option values and resolved Polish labels
        assertThat(html).contains("id=\"addPaymentModal\"")
                .containsPattern("<option[^>]*value=\"BankTransfer\"")
                .contains(">Przelew bankowy<")
                .containsPattern("<option[^>]*value=\"Cash\"")
                .contains(">Gotówka<")
                .doesNotContain("Option[").doesNotContain("??");
        // Task 24 hygiene ruling (c): this page's own add-payment dialog must load money.js exactly once
        assertThat(occurrences(html, "/js/money.js")).isEqualTo(1);
    }

    static int occurrences(String html, String needle) {
        return html.split(Pattern.quote(needle), -1).length - 1;
    }
}
