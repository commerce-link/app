package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.payments.PayableRow;
import pl.commercelink.web.payments.PaymentSide;
import pl.commercelink.web.payments.PaymentsController;
import pl.commercelink.web.payments.PaymentsModelFactory;
import pl.commercelink.web.payments.PaymentsPageModel;
import pl.commercelink.web.payments.PaymentsQuery;
import pl.commercelink.web.payments.ReceivableRow;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Renders the real Payments page (payments.html) with the real message bundle through the model
 * {@link PaymentsController#payments} builds. Besides the paymentSources type mismatch it once caught, it proves the
 * template parses on both sides: a Thymeleaf expression error would surface only in a browser otherwise.
 */
@ExtendWith(MockitoExtension.class)
class PaymentSourcesPageRenderTest {

    private static final String STORE_ID = "store-1";

    /** SettingsTemplateRenderer builds a bare TemplateEngine; enumI18n is normally added by an unrelated starter
     * auto-configuration this test does not load, so a stand-in is supplied. */
    public static class EnumLocalizerStub {
        public String localize(Object value) {
            return value == null ? "" : value.toString();
        }
    }

    @Mock
    private PaymentsModelFactory factory;
    @InjectMocks
    private PaymentsController controller;

    private static PaymentsPageModel.Tile tile(boolean active) {
        return new PaymentsPageModel.Tile("Wszystkie", 2, "dostawy: 1", "zamowienia: 1", "/dashboard/payments", active);
    }

    private static PayableRow.PendingData pending() {
        return new PayableRow.PendingData("BankTransfer", "Jan", "REF-1", "0", "", "");
    }

    private static PaymentsPageModel model(PaymentSide side, boolean invoicingConnected) {
        return model(side, invoicingConnected, PaymentsQuery.parse(new LinkedMultiValueMap<>()));
    }

    private static PaymentsPageModel model(PaymentSide side, boolean invoicingConnected, PaymentsQuery query) {
        PayableRow payable = new PayableRow("/dashboard/deliveries/d-1", "DOS-1", true, "zamowiono 1.10", "Acme",
                "EXT-1", "15.10.2026", "za 14 dni", "is-neutral", "Nieoplacona", "is-bad", false, "brak faktury",
                "150,00 zl", "brutto", false, false, "d-1", "150.00", pending(), "Dodaj platnosc");
        ReceivableRow receivable = new ReceivableRow("/dashboard/orders/o-1", "ZAM-1", "Allegro", "Jan Kowalski",
                "jan@example.pl", "20.10.2026", "za 19 dni", "is-neutral", "Przelew", "Do zwrotu", "is-warn",
                "200,00 zl", "zwrot", true, true, "o-1", "200.00", pending(), "Zwroc");
        boolean payables = side == PaymentSide.PAYABLES;
        return new PaymentsPageModel(query, side, List.of(tile(true)),
                List.of(new PaymentsPageModel.SideTab("Do zaplaty", 1, "/dashboard/payments?side=payables", payables),
                        new PaymentsPageModel.SideTab("Do otrzymania", 1, "/dashboard/payments?side=receivables", !payables)),
                "Dostawca", "supplier", "Wszyscy", List.of(new PaymentsPageModel.Option("Acme", "Acme", 1, false)),
                "Szukaj", List.of(new PaymentsPageModel.Chip("Acme", "/dashboard/payments", "Usun filtr")),
                "1 pozycja", "150,00 zl", "do zaplaty", payables ? List.of(payable) : List.of(),
                payables ? List.of() : List.of(receivable), null, invoicingConnected,
                "/dashboard/payments?side=" + side.param());
    }

    private String render(PaymentsPageModel page) {
        return render(page, Map.of());
    }

    private String render(PaymentsPageModel page, Map<String, Object> flash) {
        when(factory.page(eq(STORE_ID), any(), any(), any())).thenReturn(page);
        Model model = new ConcurrentModel();
        String view;
        try (MockedStatic<CustomSecurityContext> security = mockStatic(CustomSecurityContext.class)) {
            security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
            view = controller.payments(new LinkedMultiValueMap<>(), new Locale("pl"), model);
        }
        assertThat(view).isEqualTo("payments");

        Map<String, Object> variables = new HashMap<>(model.asMap());
        variables.put("navigation", null);
        variables.put("enumI18n", new EnumLocalizerStub());
        variables.putAll(flash);
        return SettingsTemplateRenderer.render("payments", variables);
    }

    @Test
    void payablesSideRendersTheRowActionAndPaymentSourceOptions() {
        // given / when
        String html = render(model(PaymentSide.PAYABLES, true));

        // then: the add-payment dialog renders, with real enum names as option values and resolved Polish labels
        assertThat(html).contains("id=\"addPaymentModal\"")
                .containsPattern("<option[^>]*value=\"BankTransfer\"")
                .contains(">Przelew bankowy<")
                .containsPattern("<option[^>]*value=\"Cash\"")
                .contains(">Gotówka<")
                .doesNotContain("Option[").doesNotContain("??");
        // the row opens the dialog from its link and carries what the dialog needs
        assertThat(html).contains("data-cl-payment-open").contains("data-delivery-id=\"d-1\"")
                .contains("data-unpaid=\"150.00\"").contains("data-return-to=\"/dashboard/payments?side=payables\"")
                .contains("data-pending-ref=\"REF-1\"").contains("DOS-1");
        // this page's own add-payment dialog must load money.js exactly once
        assertThat(occurrences(html, "/js/money.js")).isEqualTo(1);
    }

    @Test
    void receivablesSideRendersARefundActionAndDisabledSyncWithoutInvoicing() {
        // given / when
        String html = render(model(PaymentSide.RECEIVABLES, false));

        // then
        assertThat(html).contains("data-order-id=\"o-1\"").contains("data-direction=\"Outgoing\"")
                .contains("ZAM-1").doesNotContain("data-delivery-id=").doesNotContain("??");
        assertThat(html).contains("id=\"payments-sync-reason\"").doesNotContain("/dashboard/deliveries/syncPaymentStatuses");
    }

    @Test
    void theSearchKeepsTheMenuChoicesAndTheOutcomeMessageIsSwappedWithTheResults() {
        // given
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("side", "payables");
        params.add("provider", "Acme");
        params.add("provider", "Hurtownia Łódzka 'Ząb'");
        PaymentsQuery query = PaymentsQuery.parse(params);

        // when
        String html = render(model(PaymentSide.PAYABLES, true, query), Map.of("paymentsNotice", "Wpłata zapisana: dostawa d-1."));

        // then: a new search does not drop the supplier filter (like the deliveries list)
        String search = html.substring(html.indexOf("class=\"cl-search-form\""), html.indexOf("id=\"payments-search\""));
        assertThat(search).contains("name=\"provider\" value=\"Acme\"")
                .contains("name=\"provider\" value=\"Hurtownia Łódzka &#39;Ząb&#39;\"")
                .doesNotContain("name=\"side\"");
        // the message is inside the block list-page.js replaces, so the next tile or filter clears it
        int results = html.indexOf("data-cl-list-results");
        assertThat(html.indexOf("Wpłata zapisana: dostawa d-1.")).isGreaterThan(results);
        // fetching payments from the invoicing system cannot be sent twice
        assertThat(html).containsPattern("action=\"/dashboard/deliveries/syncPaymentStatuses\"[^>]*data-cl-submit-once")
                .contains("/js/submit-once.js");
    }

    static int occurrences(String html, String needle) {
        return html.split(Pattern.quote(needle), -1).length - 1;
    }
}
