package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.web.dtos.PickerOption;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class StoreRmaTemplateTest {

    private static final String PATH = "/dashboard/store/rma";
    private static final String SHIPPING = "/dashboard/store/shipping";
    private static final List<PickerOption> DPD_AND_INPOST = List.of(new PickerOption("dpd-1", "DPD"),
            new PickerOption("inpost-1", "InPost Kurier"));
    private static final RmaReadiness READY = new RmaReadiness("DPD", true, List.of("RMA - Karton S", "RMA - Karton M"),
            "Magazyn główny · ul. Magazynowa 1 · 00-001 Warszawa · Polska", SHIPPING);
    private static final RmaReadiness NOTHING_SAVED = new RmaReadiness(null, false, List.of(), null, SHIPPING);

    private Map<String, Object> page(List<PickerOption> options, String carrierId, RmaReadiness readiness,
                                     Map<String, String> errors) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("settingsPage", SettingsPage.forRequest(UserRole.ADMIN, PATH));
        variables.put("navigation", null);
        variables.put("carrierOptions", options);
        variables.put("carrierId", carrierId);
        variables.put("hasAuthorizedCarriers", options.stream().anyMatch(option -> !option.value().isEmpty()));
        variables.put("readiness", readiness);
        variables.put("errors", errors);
        variables.put("formAction", PATH);
        return variables;
    }

    private String rendered(List<PickerOption> options, String carrierId, RmaReadiness readiness,
                            Map<String, String> errors) {
        return SettingsTemplateRenderer.render("store-rma", page(options, carrierId, readiness, errors));
    }

    private String rendered(List<PickerOption> options, String carrierId, RmaReadiness readiness) {
        return rendered(options, carrierId, readiness, Map.of());
    }

    @Test
    void offersTheCarriersInTheSharedSelectWithTheSavedOneSelectedAndPostsBackToThePage() {
        // when
        String html = rendered(DPD_AND_INPOST, "inpost-1", READY);

        // then
        assertThat(html).contains("action=\"/dashboard/store/rma\"");
        assertThat(html).contains("<select class=\"cl-select\" id=\"carrierId\" name=\"carrierId\" autocomplete=\"off\" required=\"required\">");
        assertThat(html).contains("<option value=\"inpost-1\" selected=\"selected\">InPost Kurier</option>");
        assertThat(html).contains("<option value=\"dpd-1\">DPD</option>");
        assertThat(html).doesNotContain("type=\"radio\"").doesNotContain("name=\"store.storeId\"");
    }

    /** The list length comes from the shipping account, so a long one must stay a single control. */
    @Test
    void aLongCarrierListStaysOneSelect() {
        // given
        List<PickerOption> many = IntStream.rangeClosed(1, 15)
                .mapToObj(i -> new PickerOption("carrier-" + i, "Przewoźnik " + i)).toList();

        // when
        String html = rendered(many, "carrier-1", READY);

        // then
        assertThat(html).containsOnlyOnce("<select");
        assertThat(html.split("<option value=\"carrier-", -1)).hasSize(16);
    }

    /** Without the script the form falls back to a full page post; the fragment is what the async save answers with. */
    @Test
    void savesWithoutReloadingAndTheFragmentCarriesBothCards() {
        // when
        String page = rendered(DPD_AND_INPOST, "dpd-1", READY);
        String fragment = SettingsTemplateRenderer.render("<div th:replace=\"~{store-rma :: rmaForm}\"></div>",
                page(DPD_AND_INPOST, "dpd-1", READY, Map.of()));

        // then
        assertThat(page).contains("data-cl-async").contains("/js/async-form.js");
        assertThat(fragment).contains("id=\"store-rma-form\"").contains("Zwroty klientów działają").contains("Przewoźnik zwrotów");
    }

    @Test
    void saysReturnsAreOffBeforeTheFirstChoice() {
        // when
        String html = rendered(List.of(new PickerOption("", "Wybierz przewoźnika"), new PickerOption("dpd-1", "DPD")),
                "", NOTHING_SAVED);

        // then
        assertThat(html).contains("Zwroty wyłączone");
        assertThat(html).contains("<option value=\"\" selected=\"selected\">Wybierz przewoźnika</option>");
        assertThat(html).contains("Nie wybrano. Link zwrotu klienta kończy się błędem.");
    }

    @Test
    void marksACarrierRemovedFromTheAuthorizedListAndAsksForAnother() {
        // when
        String html = rendered(List.of(new PickerOption("gls-1", "GLS (nieautoryzowany)"), new PickerOption("dpd-1", "DPD")),
                "gls-1", new RmaReadiness("GLS", false, List.of("RMA - Karton S"), "Magazyn", SHIPPING));

        // then
        assertThat(html).contains("GLS nie jest już autoryzowany");
        assertThat(html).contains("<option value=\"gls-1\" selected=\"selected\">GLS (nieautoryzowany)</option>");
        assertThat(html).contains("GLS, usunięty z autoryzowanych");
        assertThat(html).doesNotContain("Zwroty wyłączone");
        // the saved copy still creates shipments, so the status must not claim that returns fail
        assertThat(html).contains("Zwroty klientów mogą nie działać: przewoźnik wymaga uwagi")
                .doesNotContain("Zwroty klientów nie zadziałają");
        assertThat(html).contains("<span class=\"cl-status is-warn\">Nieautoryzowany</span>");
    }

    @Test
    void withoutAuthorizedCarriersPointsToShippingAndOffersNoSave() {
        // when
        String html = rendered(List.of(), "", NOTHING_SAVED);

        // then
        assertThat(html).contains("Brak autoryzowanych przewoźników");
        assertThat(html).contains("href=\"/dashboard/store/shipping\"");
        assertThat(html).doesNotContain("<select").doesNotContain("Zapisz zmiany");
    }

    /** A status, not a setting: above the form, flat, and collapsed while there is nothing to fix. */
    @Test
    void theStatusSitsAboveTheFormAndIsCollapsedWhileReturnsWork() {
        // when
        String html = rendered(DPD_AND_INPOST, "dpd-1", READY);

        // then
        assertThat(html.indexOf("cl-card is-status")).isPositive().isLessThan(html.indexOf("Przewoźnik zwrotów"));
        assertThat(html).contains("Zwroty klientów działają").contains("fa-check-circle is-ok");
        assertThat(html).contains("<details class=\"cl-disclosure\" id=\"rma-readiness-details\">")
                .contains("Pokaż szczegóły");
    }

    @Test
    void theStatusIsOpenAndNamesTheProblemWhileSomethingIsMissing() {
        // when
        String html = rendered(DPD_AND_INPOST, "dpd-1", new RmaReadiness("DPD", true, List.of(), null, SHIPPING));

        // then
        assertThat(html).contains("Zwroty klientów nie zadziałają").contains("fa-exclamation-triangle is-warn");
        assertThat(html).doesNotContain("rma-readiness-details").doesNotContain("Pokaż szczegóły");
        // only what blocks returns is listed; the ready carrier row stays out so the form below remains in view
        assertThat(html).doesNotContain("cl-status is-ok").contains("Nazwa szablonu musi zaczynać się od");
    }

    @Test
    void readinessShowsTheTemplatesAndTheAddressWhenReady() {
        // when
        String html = rendered(DPD_AND_INPOST, "dpd-1", READY);

        // then
        assertThat(html).contains("RMA - Karton S, RMA - Karton M");
        assertThat(html).contains("Magazyn główny · ul. Magazynowa 1");
        assertThat(html).doesNotContain("cl-status is-warn").doesNotContain("Ustaw w Wysyłce");
        assertThat(html.split("cl-status is-ok", -1)).hasSize(4);
    }

    @Test
    void readinessNamesWhatIsMissingAndLinksToShipping() {
        // when
        String html = rendered(DPD_AND_INPOST, "dpd-1", new RmaReadiness("DPD", true, List.of(), null, SHIPPING));

        // then
        assertThat(html).contains("Nazwa szablonu musi zaczynać się od „RMA - ”.");
        assertThat(html).contains("Oznacz jeden adres odbioru jako domyślny.");
        assertThat(html).contains("aria-label=\"Ustaw w Wysyłce: Szablony paczek zwrotu\"");
        assertThat(html).contains("aria-label=\"Ustaw w Wysyłce: Domyślny adres odbioru\"");
    }

    @Test
    void aRejectedSaveListsTheErrorAndMarksTheField() {
        // when
        String html = rendered(List.of(new PickerOption("", "Wybierz przewoźnika"), new PickerOption("dpd-1", "DPD")),
                "", NOTHING_SAVED, Map.of("carrierId", "rma.settings.carrier.required"));

        // then
        assertThat(html).contains("id=\"store-rma-errors\"").contains("href=\"#carrierId\"");
        assertThat(html).contains("id=\"carrierId-error\"").contains("aria-invalid=\"true\"");
        assertThat(html).contains("cl-select is-invalid");
    }

    @Test
    void usesTheNewDesignInsteadOfTheBulmaBoxAndConfirmPopup() {
        // when
        String html = rendered(DPD_AND_INPOST, "dpd-1", READY);

        // then
        assertThat(html).contains("cl-card").contains("cl-select").contains("cl-button is-primary");
        assertThat(html).doesNotContain("onclick=\"confirmSave(this)\"").doesNotContain("button is-light")
                .doesNotContain("class=\"select\"");
    }
}
