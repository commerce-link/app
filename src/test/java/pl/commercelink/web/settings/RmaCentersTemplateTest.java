package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.web.dtos.PickerOption;
import pl.commercelink.web.dtos.RmaCenterForm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RmaCentersTemplateTest {

    private static final String PATH = "/dashboard/store/rma-centers";

    private RmaCenterView center(String title, boolean shared, boolean knownProvider, boolean lastForProvider,
                                 List<String> missing) {
        return new RmaCenterView("center-1", title, shared, knownProvider, lastForProvider,
                "Acme Serwis · ul. Serwisowa 8 · 31-234 Kraków · Polska", "rma@acme.pl · +48 600 700 800",
                missing, PATH + "/center-1", PATH + "/center-1/delete");
    }

    private String list(List<RmaCenterView> own, List<RmaCenterView> shared) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("settingsPage", SettingsPage.forRequest(UserRole.ADMIN, PATH));
        variables.put("navigation", null);
        variables.put("ownCenters", own);
        variables.put("sharedCenters", shared);
        variables.put("newHref", PATH + "/new");
        return SettingsTemplateRenderer.render("rma-centers", variables);
    }

    private String form(Map<String, String> errors) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("settingsPage", null);
        variables.put("navigation", null);
        variables.put("form", RmaCenterForm.empty());
        variables.put("errors", errors);
        variables.put("providerOptions", List.of(new PickerOption("Acme", "Acme")));
        variables.put("countries", List.of(new PickerOption("PL", "Polska")));
        variables.put("formAction", PATH + "/new");
        variables.put("pageTitle", "Dodaj nowe centrum RMA");
        variables.put("centersHref", PATH);
        variables.put("backLabel", "Centra RMA");
        return SettingsTemplateRenderer.render("rma-center-form", variables);
    }

    @Test
    void theListReplacesTheFieldTableWithRecordRows() {
        // when
        String html = list(List.of(center("Acme", false, true, false, List.of())), List.of());

        // then
        assertThat(html).contains("cl-list").contains("cl-list-item").contains("Acme Serwis · ul. Serwisowa 8");
        // The layout still ships Bulma of its own (the delete modal), so only this page's old vocabulary is asserted away.
        assertThat(html).doesNotContain("table is-fullwidth").doesNotContain("is-striped")
                .doesNotContain("button is-link is-light").doesNotContain("<th>");
    }

    /** The own-centres heading renders only next to shared centres; the list must not point at a missing element. */
    @Test
    void theOwnListIsLabelledByTheCardTitleWhenThereIsNoGroupHeading() {
        // when
        String alone = list(List.of(center("Acme", false, true, false, List.of())), List.of());
        String withShared = list(List.of(center("Acme", false, true, false, List.of())),
                List.of(center("Elko", true, true, false, List.of())));

        // then
        assertThat(alone).contains("class=\"cl-list\" aria-labelledby=\"rma-centers-title\"").doesNotContain("id=\"rma-centers-own\"");
        assertThat(withShared).contains("class=\"cl-list\" aria-labelledby=\"rma-centers-own\"").contains("id=\"rma-centers-own\"");
    }

    @Test
    void aSharedCentreIsMarkedAndOffersNoActions() {
        // when
        String html = list(List.of(), List.of(center("Elko", true, true, false, List.of())));

        // then
        assertThat(html).contains("cl-status is-neutral");
        assertThat(html).doesNotContain("cl-list-actions");
    }

    @Test
    void anIncompleteCentreSaysWhatIsMissingAndOffersToCompleteIt() {
        // when
        String html = list(List.of(center("Acme", false, true, false, List.of("rma.center.missing.phone"))), List.of());

        // then
        assertThat(html).contains("cl-status is-warn").contains("cl-list-desc is-warn").contains("telefon");
    }

    /** A supplier that left the registry can no longer be shipped to, so the row says so rather than looking healthy. */
    @Test
    void aCentreOfAnUnknownSupplierIsMarked() {
        // when
        String known = list(List.of(center("Acme", false, true, false, List.of())), List.of());
        String unknown = list(List.of(center("Acme", false, false, false, List.of())), List.of());

        // then
        assertThat(unknown).contains("Dostawca nieznany");
        assertThat(known).doesNotContain("Dostawca nieznany");
    }

    @Test
    void deletingTheOnlyCentreOfASupplierWarnsThatShipmentsLoseTheirAddress() {
        // when
        String last = list(List.of(center("Acme", false, true, true, List.of())), List.of());
        String oneOfMany = list(List.of(center("Acme", false, true, false, List.of())), List.of());

        // then
        assertThat(last).contains("To jedyny adres dla dostawcy Acme");
        assertThat(oneOfMany).doesNotContain("To jedyny adres");
    }

    @Test
    void theEmptyListSaysSoInsteadOfShowingABareTableHeader() {
        // when
        String html = list(List.of(), List.of());

        // then
        assertThat(html).contains("cl-list-empty");
        assertThat(html).doesNotContain("cl-list-item");
    }

    @Test
    void theFormPostsToThePageAndCarriesNoStoreIdOfItsOwn() {
        // when
        String html = form(Map.of());

        // then
        assertThat(html).contains("action=\"" + PATH + "/new\"").contains("data-cl-async")
                .contains("id=\"rma-center-form\"").contains("/js/async-form.js");
        assertThat(html).doesNotContain("name=\"storeId\"").doesNotContain("name=\"rmaCenterId\"");
        // The page used to bring its own Bulma shell and h1; now it wears the settings subpage header with a back link.
        assertThat(html).contains("cl-back").contains("cl-page-title").contains("Centra RMA");
    }

    /** The country used to be free text; the picker keeps it an ISO code like every other address in the settings. */
    @Test
    void theFormPicksTheCountryFromAListAndMarksTheOptionalEmail() {
        // when
        String html = form(Map.of());

        // then
        assertThat(html).contains("cl-select").contains("value=\"PL\"");
        assertThat(html).contains("cl-optional");
    }

    @Test
    void theFormShowsTheErrorSummaryAboveTheCard() {
        // when
        String html = form(Map.of("provider", "rma.center.provider.required"));

        // then
        assertThat(html).contains("rma-center-errors").contains("cl-alert is-bad").contains("Wybierz dostawcę.");
    }
}
