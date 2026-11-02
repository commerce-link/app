package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.marketplace.links.OfferMatchResult;
import pl.commercelink.marketplace.links.OfferMatchResult.Row;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class MarketplaceOfferMatchTemplateTest {

    private static final String ACTION = "/dashboard/store/marketplaces/Empik/offer-matching";

    @Test
    void beforeTheFirstRunThePageExplainsWhatMatchingDoesAndOffersToRunIt() {
        // when
        String html = render(null, null);

        // then
        assertThat(html).contains("Dopasowania jeszcze nie uruchamiano")
                .contains("action=\"" + ACTION + "\"").contains("method=\"post\"").contains("data-cl-submit-once")
                .contains(">Dopasuj oferty<");
        assertThat(html).doesNotContain("cl-stat-grid").doesNotContain("<textarea").doesNotContain("??");
    }

    @Test
    void theLatestRunShowsCountsGroupsReasonsAndEansToCopy() {
        // given
        OfferMatchResult result = new OfferMatchResult(Instant.parse("2026-11-02T09:15:00Z"), 9,
                List.of(new Row("sku-1", "Mysz", List.of("5901234123457"), "pim-1", null)),
                List.of(new Row("sku-2", "Klawiatura", List.of("5901234123458"), "pim-2", OfferMatchResult.ALREADY_LINKED)),
                List.of(new Row("sku-3", "Monitor", List.of("5901234123459", "5901234123460"), null, null)),
                List.of(new Row("sku-5", null, List.of(), null, OfferMatchResult.NO_EAN),
                        new Row("sku-6", "Kabel", List.of("5901234123461"), null, OfferMatchResult.AMBIGUOUS),
                        new Row("sku-7", "Pad", List.of("5901234123462"), "pim-7", OfferMatchResult.STALE_LINK)),
                2, 3);

        // when
        String html = render(OfferMatchingView.of(result), null);

        // then
        assertThat(html).contains("Ostatni przebieg: 02.11.2026 10:15")
                .contains("cl-stat-grid")
                .contains("Połączone w tym przebiegu")
                .contains("Połączone, produktu brak w katalogu")
                .contains("Brak karty w PIM")
                .contains("Do przejrzenia");
        assertThat(html).contains(">5901234123459</textarea>").contains(">5901234123458</textarea>");
        assertThat(html).contains("Oferta nie ma EAN").contains("różne karty PIM").contains("PIM już nie zna");
        assertThat(html).contains("Połączona wcześniej").contains("pim-1").contains("sku-5");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void inactiveOffersGetAPillAndAreNotOfferedForCopying() {
        // given
        OfferMatchResult result = new OfferMatchResult(Instant.parse("2026-11-02T09:15:00Z"), 2, List.of(), List.of(),
                List.of(new Row("old-1", "Stara", List.of("5901234123457"), null, null, false, null),
                        new Row("new-1", "Nowa", List.of("5901234123458"), null, null, true, null)),
                List.of(), 0, 0);

        // when
        String html = render(OfferMatchingView.of(result), null);

        // then
        assertThat(html).contains("nieaktywna").contains(">5901234123458</textarea>")
                .contains("Nieaktywnych ofert nie ma na liście do skopiowania");
        assertThat(html).doesNotContain(">5901234123457</textarea>").doesNotContain("??");
    }

    @Test
    void aRowWithoutKeyAndTitleGetsAPlaceholderAndNoEmptySkuLine() {
        // given
        OfferMatchResult result = new OfferMatchResult(Instant.parse("2026-11-02T09:15:00Z"), 1, List.of(), List.of(),
                List.of(), List.of(new Row(null, null, List.of("5901234123457"), null, OfferMatchResult.NO_KEY)), 0, 0);

        // when
        String html = render(OfferMatchingView.of(result), null);

        // then
        assertThat(html).contains("bez nazwy").doesNotContain("SKU <span").doesNotContain("??");
    }

    @Test
    void aRowWithoutTitleShowsItsSkuOnce() {
        // given
        OfferMatchResult result = new OfferMatchResult(Instant.parse("2026-11-02T09:15:00Z"), 1, List.of(), List.of(),
                List.of(), List.of(new Row("sku-unique", null, List.of(), null, OfferMatchResult.NO_EAN)), 0, 0);

        // when
        String html = render(OfferMatchingView.of(result), null);

        // then
        assertThat(html.split("sku-unique", -1)).hasSize(2);
    }

    @Test
    void statTilesGroupThousandsLikeTheTexts() {
        // given
        List<Row> many = IntStream.range(0, 12345)
                .mapToObj(index -> new Row("sku-" + index, "P", List.of("59" + index), null, null)).toList();
        OfferMatchResult result = new OfferMatchResult(Instant.now(), many.size(), List.of(), List.of(), many,
                List.of(), 0, 0);

        // when
        String html = render(OfferMatchingView.of(result), null);

        // then
        assertThat(html).containsPattern("cl-stat-value\">12\u00a0345<");
    }

    @Test
    void aLargeGroupRendersItsFirstRowsAndSaysSo() {
        // given
        List<Row> missing = IntStream.range(0, OfferMatchingView.ROW_LIMIT + 1)
                .mapToObj(index -> new Row("sku-" + index, "Produkt " + index, List.of("590000" + (100000 + index)), null, null))
                .toList();
        OfferMatchResult result = new OfferMatchResult(Instant.now(), missing.size(), List.of(), List.of(), missing,
                List.of(), 0, 0);

        // when
        String html = render(OfferMatchingView.of(result), null);

        // then
        assertThat(html).contains("Pokazano pierwsze " + OfferMatchingView.ROW_LIMIT + " z " + (OfferMatchingView.ROW_LIMIT + 1));
        assertThat(html).contains("sku-" + (OfferMatchingView.ROW_LIMIT - 1) + "<").doesNotContain("sku-" + OfferMatchingView.ROW_LIMIT + "<");
        assertThat(html).contains("590000" + (100000 + OfferMatchingView.ROW_LIMIT));
        assertThat(html).contains("Brak ofert w tej grupie.");
    }

    @Test
    void aRunThatCannotBeOrderedIsExplainedInsteadOfTheButton() {
        // given
        Map<String, Object> extra = Map.of("runBlockedMessage", "Połączenie z Empik nie działa.",
                "runBlockedHref", "/dashboard/store/marketplaces/Empik");

        // when
        String html = render(null, null, extra);

        // then
        assertThat(html).contains("cl-alert is-warn").contains("Połączenie z Empik nie działa.")
                .contains("href=\"/dashboard/store/marketplaces/Empik\"");
        assertThat(html).doesNotContain("method=\"post\"").doesNotContain("??");
    }

    @Test
    void aRefusedRunIsReportedOnThePage() {
        // when
        String html = render(null, null, Map.of("settingsErrorMessage", "Sklep jest nieaktywny."));

        // then
        assertThat(html).contains("cl-alert is-bad").contains("Sklep jest nieaktywny.");
    }

    @Test
    void anOrderedRunIsConfirmedOnThePage() {
        // when
        String html = render(null, "Dopasowanie ofert z Empik zlecone.");

        // then
        assertThat(html).contains("cl-alert is-ok").contains("Dopasowanie ofert z Empik zlecone.");
    }

    private static String render(OfferMatchingView matching, String savedMessage) {
        return render(matching, savedMessage, Map.of());
    }

    private static String render(OfferMatchingView matching, String savedMessage, Map<String, Object> extra) {
        Map<String, Object> variables = new HashMap<>(extra);
        variables.put("matching", matching);
        variables.put("settingsSavedMessage", savedMessage);
        variables.put("displayName", "Empik");
        variables.put("formAction", ACTION);
        variables.put("marketplacesHref", "/dashboard/store/marketplaces");
        variables.put("backLabel", "Marketplace'y");
        variables.put("pageTitle", "Dopasuj oferty · Empik");
        return SettingsTemplateRenderer.render("store-marketplace-offer-matching", variables);
    }
}
