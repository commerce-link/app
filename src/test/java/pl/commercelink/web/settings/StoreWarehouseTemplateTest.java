package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.web.dtos.WarehouseDocumentsForm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StoreWarehouseTemplateTest {

    private static final Locale POLISH = Locale.forLanguageTag("pl");

    private ShippingDetails complete(String id, String company, boolean isDefault) {
        ShippingDetails details = new ShippingDetails();
        details.setId(id);
        details.setCompanyName(company);
        details.setStreetAndNumber("ul. Logistyczna 7");
        details.setPostalCode("95-010");
        details.setCity("Stryków");
        details.setCountry("PL");
        details.setEmail("przyjecia@sklep.pl");
        details.setPhone("+48 600 300 400");
        details.set_default(isDefault);
        return details;
    }

    private ShippingDetails incomplete() {
        ShippingDetails details = new ShippingDetails();
        details.setId("a-3");
        details.setCompanyName("Oddział Berlin");
        details.setStreetAndNumber("Lagerstraße 3");
        details.setCity("Berlin");
        details.setCountry("DE");
        details.set_default(false);
        return details;
    }

    private Map<String, Object> page(List<ShippingDetails> addresses, List<WarehousePrinterView> printers, boolean typesAvailable,
                                     WarehouseDocumentsForm form, Map<String, String> errors) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("settingsPage", SettingsPage.forRequest(UserRole.ADMIN, "/dashboard/store/warehouse"));
        variables.put("navigation", null);
        variables.put("form", form);
        variables.put("errors", errors);
        variables.put("formAction", "/dashboard/store/warehouse");
        variables.put("invoicingConnected", true);
        variables.put("invoicingHref", "/dashboard/store/invoicing");
        variables.put("addresses", addresses.stream().map(a -> WarehouseAddressView.of(a, POLISH, "/dashboard/store/warehouse/addresses")).toList());
        variables.put("newAddressHref", "/dashboard/store/warehouse/addresses/new");
        variables.put("printers", printers);
        variables.put("newPrinterHref", "/dashboard/store/warehouse/printers/new");
        variables.put("printerTypesAvailable", typesAvailable);
        return variables;
    }

    private WarehouseDocumentsForm documents() {
        WarehouseDocumentsForm form = new WarehouseDocumentsForm();
        form.setDocumentsEnabled(true);
        form.setWarehouseId("MAG-01");
        form.setCostCenterId("KC-01");
        return form;
    }

    @Test
    void separatesDocumentsAddressesAndPrintersEachWithItsOwnAction() {
        // given
        WarehousePrinterView printer = new WarehousePrinterView("p-1", "Zebra pakowanie", "Zebra ZPL", "ZD-1",
                "/dashboard/store/warehouse/printers/p-1", "/dashboard/store/warehouse/printers/p-1/delete");

        // when
        String html = SettingsTemplateRenderer.render("store-warehouse",
                page(List.of(complete("a-1", "Centrum Stryków", true)), List.of(printer), true, documents(), Map.of()));

        // then
        assertThat(html).contains("<h1 class=\"cl-page-title\">Magazyn</h1>");
        assertThat(html).contains(">Dokumenty magazynowe</legend>").contains(">Zapisz zmiany<")
                .contains(">Twórz dokument przy przyjęciu i wydaniu towaru<");
        assertThat(html).contains(">Adresy przyjęcia towaru</h2>").contains(">Drukarki etykiet</h2>");
        assertThat(html).contains("data-cl-reveal=\"warehouse-documents-ids\"").contains("value=\"MAG-01\"");
        assertThat(html).contains("href=\"/dashboard/store/warehouse/addresses/new\"").contains("href=\"/dashboard/store/warehouse/printers/new\"");
        assertThat(html).contains("Zebra ZPL · ZD-1");
        assertThat(html).containsOnlyOnce("cl-button is-primary").containsOnlyOnce("type=\"checkbox\"");
        assertThat(html).doesNotContain("<table").doesNotContain("??").doesNotContain("store.storeId");
    }

    @Test
    void summarisesEachAddressWithItsDefaultAndIncompleteState() {
        // when
        String html = SettingsTemplateRenderer.render("store-warehouse",
                page(List.of(complete("a-1", "Centrum Stryków", true), complete("a-2", "Magazyn Warszawa", false), incomplete()),
                        List.of(), true, documents(), Map.of()));

        // then
        assertThat(html).containsPattern("Centrum Stryków</span>\\s*<span class=\"cl-status is-info\">Domyślny</span>");
        assertThat(html).contains("ul. Logistyczna 7 · 95-010 Stryków · Polska");
        assertThat(html).containsPattern("Oddział Berlin</span>\\s*<span class=\"cl-status is-warn\">Niekompletny</span>");
        assertThat(html).contains("Lagerstraße 3 · Berlin · Niemcy")
                .contains("<p class=\"cl-list-desc is-warn\">Brakuje: kod pocztowy</p>");
        assertThat(html).contains(">Uzupełnij<");
    }

    @Test
    void theDefaultShortcutTakesThePlaceOfTheDefaultPillAndRowsKeepTwoActions() {
        // when
        String html = SettingsTemplateRenderer.render("store-warehouse",
                page(List.of(complete("a-1", "Centrum Stryków", true), complete("a-2", "Magazyn Warszawa", false), incomplete()),
                        List.of(), true, documents(), Map.of()));

        // then
        assertThat(html).containsPattern("Magazyn Warszawa</span>\\s*<form class=\"cl-list-title-action\" method=\"post\"\\s+"
                + "action=\"/dashboard/store/warehouse/addresses/a-2/default\">\\s*<button type=\"submit\" class=\"cl-link-button\" "
                + "aria-label=\"Ustaw jako domyślny: Magazyn Warszawa\">Ustaw jako domyślny</button>");
        assertThat(html).doesNotContain("addresses/a-1/default").doesNotContain("addresses/a-3/default");
        assertThat(html).containsOnlyOnce("Ustaw jako domyślny</button>");
        assertThat(html).doesNotContainPattern("<div class=\"cl-list-actions\">(?:(?!</div>).)*/default");
    }

    /** WCAG 2.5.3: the accessible name starts with the visible text; the question stays in the dialog title. */
    @Test
    void rowActionsAreNamedByTheirVisibleTextAndTheRecord() {
        // when
        String html = SettingsTemplateRenderer.render("store-warehouse",
                page(List.of(complete("a-2", "Magazyn Warszawa", false), incomplete()), List.of(), true, documents(), Map.of()));

        // then
        assertThat(html).contains("aria-label=\"Edytuj: Magazyn Warszawa\">Edytuj</a>")
                .contains("aria-label=\"Uzupełnij: Oddział Berlin\">Uzupełnij</a>")
                .contains("aria-label=\"Usuń: Oddział Berlin\"");
        assertThat(html).doesNotContain("aria-label=\"Usunąć");
    }

    @Test
    void aRecordWithoutANameIsCalledUntitledInsteadOfNull() {
        // when
        String html = SettingsTemplateRenderer.render("store-warehouse",
                page(List.of(complete("a-9", null, false)), List.of(), true, documents(), Map.of()));

        // then
        assertThat(html).contains("<span>(bez nazwy)</span>").containsPattern("aria-label=\"(Edytuj|Uzupełnij): \\(bez nazwy\\)\"")
                .doesNotContain(">null<").doesNotContain(": null\"").doesNotContain("„null”");
    }

    @Test
    void deletingIsConfirmedInADialogOrOnItsOwnPage() {
        // when
        String html = SettingsTemplateRenderer.render("store-warehouse",
                page(List.of(incomplete()), List.of(), true, documents(), Map.of()));

        // then
        assertThat(html).contains("href=\"/dashboard/store/warehouse/addresses/a-3/delete\"").contains("data-cl-confirm")
                .contains("data-cl-confirm-title=\"Usunąć adres „Oddział Berlin”?\"")
                .contains("data-cl-confirm-action=\"Usuń adres\"").contains("class=\"cl-link-button is-danger\"");
        assertThat(html).contains("<dialog class=\"cl-dialog\" id=\"cl-confirm-dialog\"");
    }

    @Test
    void emptyListsInviteToAddAndSayWhenNoPrinterTypeIsInstalled() {
        // when
        String withTypes = SettingsTemplateRenderer.render("store-warehouse", page(List.of(), List.of(), true, documents(), Map.of()));
        String withoutTypes = SettingsTemplateRenderer.render("store-warehouse", page(List.of(), List.of(), false, documents(), Map.of()));

        // then
        assertThat(withTypes).contains("Nie ma jeszcze adresu przyjęcia towaru.").contains("Nie ma jeszcze drukarki etykiet.");
        assertThat(withoutTypes).contains("nie ma zainstalowanej obsługi").doesNotContain("href=\"/dashboard/store/warehouse/printers/new\"");
    }

    @Test
    void warnsWhenNoInvoicingSystemIsConnected() {
        // given
        Map<String, Object> variables = page(List.of(), List.of(), true, documents(), Map.of());
        variables.put("invoicingConnected", false);

        // when
        String html = SettingsTemplateRenderer.render("store-warehouse", variables);

        // then
        assertThat(html).contains("cl-alert is-warn").contains("href=\"/dashboard/store/invoicing\"");
        // Only relevant while documents are on: it is revealed with their ids, not shown to stores that keep them off.
        assertThat(html.indexOf("cl-alert is-warn")).isGreaterThan(html.indexOf("id=\"warehouse-documents-ids\""));
    }

    @Test
    void missingIdsAreListedAboveTheDocumentsFormAndNextToTheFields() {
        // given
        WarehouseDocumentsForm form = documents();
        form.setCostCenterId("");

        // when
        String html = SettingsTemplateRenderer.render("store-warehouse", page(List.of(), List.of(), true, form,
                Map.of("costCenterId", "store.warehouse.documents.costCenterId.required")));

        // then
        assertThat(html).contains("id=\"warehouse-documents-errors\"").contains("href=\"#costCenterId\"")
                .contains("Podaj ID centrum kosztów albo wyłącz dokumenty magazynowe.");
    }

    @Test
    void theDocumentsFormRendersOnItsOwnForAsyncSaves() {
        // given
        Map<String, Object> variables = page(List.of(complete("a-1", "Magazyn Warszawa", true)), List.of(), true, documents(), Map.of());
        variables.put("savedMessage", "Zapisano");

        // when
        String html = SettingsTemplateRenderer.render("<div th:replace=\"~{store-warehouse :: documentsForm}\"></div>", variables);

        // then
        assertThat(html).startsWith("<form").contains("data-success-message=\"Zapisano\"").doesNotContain("Magazyn Warszawa");
    }

    @Test
    void dropsTheTableFormTheSaveConfirmationAndTheOldEndpoints() throws Exception {
        // when
        String template = Files.readString(Path.of("src/main/resources/templates/store-warehouse.html"), StandardCharsets.UTF_8);

        // then
        assertThat(template).doesNotContain("confirmSave").doesNotContain("confirmDelete").doesNotContain("/warehouse/edit")
                .doesNotContain("printers/add").doesNotContain("Cancel").doesNotContain("pattern=")
                .doesNotContain("<script>\n        document.querySelector").doesNotContain("style=");
    }
}
