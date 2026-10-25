package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.payments.statements.StatementBooking;
import pl.commercelink.web.dtos.BankStatementFormatForm;
import pl.commercelink.web.dtos.PickerOption;
import pl.commercelink.web.payments.StatementPage;
import pl.commercelink.web.payments.StatementPageFactory;
import pl.commercelink.web.payments.StatementPageFactoryTest;
import pl.commercelink.web.settings.StatementPreview;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.web.payments.StatementPageFixtures.*;

/** The bank statement pages rendered through Thymeleaf with the Polish messages: no template error, no missing text. */
@ExtendWith(MockitoExtension.class)
class StatementTemplatesTest {

    private static final Locale PL = Locale.forLanguageTag("pl");

    @Mock OrdersRepository ordersRepository;

    private final TemplateEngine engine = EnglishFragmentTemplateEngine.create(PL);

    @Test
    void rendersTheReconciliationOfAnUploadedStatement() {
        // given
        Context context = new Context(PL);
        context.setVariable("page", page(List.of()));

        // when
        String html = engine.process("payments-statement", context);

        // then
        assertThat(html).doesNotContain("??");
        assertThat(html).contains("Rozliczenie wyciągu").contains("Dopasowane automatycznie").contains("Paczki: jeden przelew, wiele zamówień");
        assertThat(html).contains("<mark>c42d91f0</mark>").contains("&lt;b&gt;");
        // the tiles are the summary: no lead with the account and file, no line of counts above them
        assertThat(html).contains("class=\"cl-stat-grid is-orders\"").doesNotContain("cl-page-lead")
                .doesNotContain("Operacje w pliku");
        assertThat(html).contains("Płatność w zamówieniu").contains("nr ref.: PRO 0391/10/2026").contains("Wypłata zbiorcza · ALLEGRO");
        assertThat(html).containsPattern("value=\"WB-JAN\"[^>]*checked=\"checked\"")
                .containsPattern("value=\"WB-ALLEGRO\"(?![^>]*checked)[^>]*>");
        assertThat(html).contains("Sprawdź paczkę").contains("Zatwierdź zaznaczone");
        // the payout's fee is worked out from the difference as when a payment is added by hand
        assertThat(html).contains("data-cl-payout-auto-fee").contains("Auto-uzupełnij prowizję jako różnicę")
                .contains("data-text-fee=\"Prowizja zamówienia {0}\"");
        // the dialogs name the operation in the shared key-value list, filled from the row's full title
        assertThat(html).contains("data-title=\"").contains("data-field=\"title\"").contains(">Płatnik<");
        // the dialog warns of an order's over- or underpayment in the words of the statement's rows
        assertThat(html).contains("data-text-overpaid=\"Nadpłata {0}\"").contains("data-text-underpaid=\"Niedopłata {0}\"")
                .contains("data-cl-assign-summary");
        // unticking every order of an assigned operation offers to remove the assignment
        assertThat(html).contains("data-text-clear=\"Usuń przypisanie\"").contains("data-cl-statement-payment");
        // the page script moves a row between groups and recounts them and their tiles
        assertThat(html).contains("data-cl-group=\"unmatched\"").contains("data-cl-tile=\"unmatched\"")
                .contains("data-cl-group-count").contains("data-cl-tile-amount");
        String receivables = html.substring(html.indexOf("id=\"statement-receivables\">"), html.indexOf("</script>", html.indexOf("id=\"statement-receivables\">")));
        assertThat(receivables).contains("\"shortId\":\"3f9a2c1e\"").doesNotContain("<b>");
    }

    @Test
    void rendersWhatABookingDid() {
        // given
        StatementBooking.Outcome booked = new StatementBooking.Outcome(incoming().get(0), null,
                List.of(new StatementBooking.OrderOutcome(JAN, StatementBooking.Result.BOOKED, null)));
        Context context = new Context(PL);
        context.setVariable("page", page(List.of(booked)));

        // when
        String html = engine.process("payments-statement", context);

        // then
        assertThat(html).doesNotContain("??");
        assertThat(html).contains("Zaksięgowane wpłaty: 1 · zamówienia: 1").contains("Już zaksięgowane");
    }

    @Test
    void rendersTheUploadFormAsAPageAndInTheDialog() {
        // given
        Context context = new Context(PL);
        context.setVariable("statementAccounts", List.of(new PickerOption("account-1", "mBank · \u20264567")));

        // when
        String page = engine.process("payments-statement-upload", context);
        String dialog = engine.process("<div th:replace=\"~{payments-statement-upload :: uploadForm(true)}\"></div>", context);

        // then
        assertThat(page).doesNotContain("??").contains("Wczytaj i dopasuj").contains("mBank · \u20264567")
                .contains("enctype=\"multipart/form-data\"");
        assertThat(dialog).doesNotContain("??").contains("cl-dialog-title").contains("data-cl-dialog-close");
    }

    @Test
    void rendersTheStatementFormatWithItsPreview() {
        // given
        BankStatementFormatForm form = BankStatementFormatForm.defaults();
        form.setDateColumn("Data");
        form.setAmountColumn("Kwota");
        form.setTitleColumn1("Tytul");
        form.attachSample("Data;Kwota;Tytul\n2026-10-07;159,00;zakupy\n".getBytes(), "wyciag.csv");
        StatementPreview preview = StatementPreview.of(form.sample(), "wyciag.csv", form.toPreviewFormat(), "PL61",
                messages(), PL);
        Context context = new Context(PL);
        context.setVariable("form", form);
        context.setVariable("errors", Map.of("dateFormat", "store.payments.statement.error.dateFormat",
                "channel-0-match", "store.payments.statement.error.channelMatch"));
        context.setVariable("preview", preview);
        context.setVariable("headers", preview.headers());
        context.setVariable("encodings", List.of(new PickerOption("windows-1250", "windows-1250")));
        context.setVariable("separators", List.of(new PickerOption(";", "Średnik ( ; )")));
        context.setVariable("dateFormats", List.of(new PickerOption("yyyy-MM-dd", "RRRR-MM-DD")));
        context.setVariable("decimalMarks", List.of(new PickerOption("comma", "1 234,56")));
        context.setVariable("sourceOptions", List.of(new PickerOption("", "Dowolne"), new PickerOption("SHOP", "Sklep"),
                new PickerOption("Allegro", "Allegro")));
        context.setVariable("methodOptions", List.of(new PickerOption("", "Dowolna"), new PickerOption("Installments", "Raty")));
        context.setVariable("formAction", "/dashboard/store/payments/bank-accounts/account-1/statement");
        context.setVariable("pageTitle", "Format wyciągu: mBank");
        context.setVariable("paymentsHref", "/dashboard/store/payments");
        context.setVariable("backLabel", "Płatności");

        // when
        String html = engine.process("store-bank-statement", context);

        // then
        assertThat(html).doesNotContain("??");
        assertThat(html).contains("Format wyciągu: mBank").contains("<option value=\"Kwota\">").contains("zakupy")
                .contains("159,00").contains("Wczytany plik: wyciag.csv").contains("name=\"channels[0].match\"")
                .contains("Wybierz format daty z listy.");
        // a payout row is told apart by its source and method only; matching the payer by name is enough
        assertThat(html).doesNotContain("channels[0].provider").contains("name=\"channels[0].maxFeePercent\"")
                .contains("name=\"channels[0].feeRates\"");
        // with JavaScript the rows are a list edited in a dialog; the fields stay as what is posted, plus a blank row to copy
        assertThat(html).contains("id=\"payout-row-dialog\"").contains("data-cl-dialog-open=\"payout-row-dialog\"")
                .contains("Dodaj wypłatę zbiorczą").contains("name=\"channels[@INDEX@].match\"")
                .contains("/js/dialog.js").contains("/js/payout-rows.js");
        // a refused row keeps its message next to its field, where the dialog reads it from
        assertThat(html).containsPattern("class=\"cl-input is-invalid\"[^>]*id=\"channel-0-match\"")
                .containsPattern("id=\"channel-0-match\"[\\s\\S]{0,400}Podaj tekst, po którym rozpoznamy płatnika.");
        // the direction is named as on the payment dialogs
        assertThat(html).contains(">Przychodząca<").doesNotContain("Wpływ");
        // one step: the file is read as soon as it is chosen, sent with the preview button; its own button only without JavaScript
        assertThat(html).contains("data-cl-submit-on-change=\"statement-preview\"").contains("id=\"statement-preview\"")
                .containsPattern("<noscript>\\s*<button[^>]*value=\"preview\"").contains("/js/submit-on-change.js");
    }

    private StatementPage page(List<StatementBooking.Outcome> outcomes) {
        stub(ordersRepository, openOrders());
        StatementPageFactory factory = new StatementPageFactory(ordersRepository, json(), messages());
        return factory.page(store(), StatementPageFactoryTest.statement(), Map.of(), outcomes, PL);
    }
}
