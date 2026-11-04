package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.marketplace.links.OfferMatchError;
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
    void theLatestRunShowsCountsGroupsAndReasons() {
        // given
        OfferMatchResult result = new OfferMatchResult(Instant.parse("2026-11-02T09:15:00Z"), 9,
                List.of(new Row("sku-1", "Mysz", List.of("5901234123457"), null, true),
                        new Row("sku-3", "Monitor", List.of("5901234123459", "5901234123460"), null, true)),
                List.of(new Row("sku-5", null, List.of(), OfferMatchResult.NO_EAN, true),
                        new Row("sku-6", "Kabel", List.of("5901234123461"), OfferMatchResult.NO_KEY, true)),
                2, 3);

        // when
        String html = render(OfferMatchingView.of(result), null);

        // then
        assertThat(html).contains("Ostatni przebieg: 02.11.2026 10:15")
                .contains("cl-stat-grid")
                .contains("Połączone")
                .contains("Do przejrzenia")
                .doesNotContain("W katalogu")
                .doesNotContain("Brak w katalogu")
                .doesNotContain("<textarea")
                .doesNotContain("PIM");
        assertThat(html).contains("Oferta nie ma poprawnego EAN-u").contains("nie podał klucza oferty")
                .doesNotContain("kodu producenta");
        assertThat(html).contains("5901234123459").contains("sku-5");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void anOfferThatLostItsEansShowsTheKeptLinkInsteadOfAskingForOne() {
        // given
        OfferMatchResult result = new OfferMatchResult(Instant.parse("2026-11-02T09:15:00Z"), 1, List.of(),
                List.of(new Row("PS-1", "Lampa", List.of("2000000001012"), OfferMatchResult.NO_EAN_KEPT_LINK, true)),
                0, 0);

        // when
        String html = render(OfferMatchingView.of(result), null);

        // then
        assertThat(html).contains("Oferta nie ma już EAN-u, zamówienia dostają EAN-y z poprzedniego powiązania")
                .contains("2000000001012")
                .doesNotContain("Połącz ją ręcznie")
                .doesNotContain("??");
    }

    @Test
    void everyNonEmptyGroupOffersItsOffersAsCsvAndAnEmptyOneDoesNot() {
        // given
        OfferMatchResult result = new OfferMatchResult(Instant.parse("2026-11-02T09:15:00Z"), 1,
                List.of(),
                List.of(new Row("sku-5", "Pad", List.of(), OfferMatchResult.NO_EAN, true)),
                0, 0);

        // when
        String html = render(OfferMatchingView.of(result), null);

        // then
        assertThat(html).contains("href=\"" + ACTION + "/toReview.csv\"")
                .contains("Pobierz CSV")
                .doesNotContain(ACTION + "/linked.csv")
                .doesNotContain("notInCatalog");
    }

    @Test
    void inactiveOffersGetAPill() {
        // given
        OfferMatchResult result = new OfferMatchResult(Instant.parse("2026-11-02T09:15:00Z"), 2,
                List.of(new Row("old-1", "Stara", List.of("5901234123457"), null, false),
                        new Row("new-1", "Nowa", List.of("5901234123458"), null, true)),
                List.of(), 0, 0);

        // when
        String html = render(OfferMatchingView.of(result), null);

        // then
        assertThat(html).contains("nieaktywna").contains("5901234123457").doesNotContain("??");
    }

    @Test
    void aRowWithoutKeyAndTitleGetsAPlaceholderAndNoEmptySkuLine() {
        // given
        OfferMatchResult result = new OfferMatchResult(Instant.parse("2026-11-02T09:15:00Z"), 1, List.of(),
                List.of(new Row(null, null, List.of("5901234123457"), OfferMatchResult.NO_KEY, true)), 0, 0);

        // when
        String html = render(OfferMatchingView.of(result), null);

        // then
        assertThat(html).contains("bez nazwy").doesNotContain("SKU <span").doesNotContain("??");
    }

    @Test
    void aRowWithoutTitleShowsItsSkuOnce() {
        // given
        OfferMatchResult result = new OfferMatchResult(Instant.parse("2026-11-02T09:15:00Z"), 1, List.of(),
                List.of(new Row("sku-unique", null, List.of(), OfferMatchResult.NO_EAN, true)), 0, 0);

        // when
        String html = render(OfferMatchingView.of(result), null);

        // then
        assertThat(html.split("sku-unique", -1)).hasSize(2);
    }

    @Test
    void statTilesGroupThousandsLikeTheTexts() {
        // given
        List<Row> many = IntStream.range(0, 12345)
                .mapToObj(index -> new Row("sku-" + index, "P", List.of("59" + index), null, true)).toList();
        OfferMatchResult result = new OfferMatchResult(Instant.now(), many.size(), many, List.of(), 0, 0);

        // when
        String html = render(OfferMatchingView.of(result), null);

        // then
        assertThat(html).containsPattern("cl-stat-value\">12\u00a0345<")
                .containsPattern("cl-status is-neutral\">12\u00a0345<");
    }

    @Test
    void aLargeGroupRendersItsFirstRowsAndSaysSo() {
        // given
        List<Row> linked = IntStream.range(0, OfferMatchingView.ROW_LIMIT + 1)
                .mapToObj(index -> new Row("sku-" + index, "Produkt " + index, List.of("590000" + (100000 + index)), null, true))
                .toList();
        OfferMatchResult result = new OfferMatchResult(Instant.now(), linked.size(), linked, List.of(), 0, 0);

        // when
        String html = render(OfferMatchingView.of(result), null);

        // then
        assertThat(html).contains("Pokazano pierwsze " + OfferMatchingView.ROW_LIMIT + " z " + (OfferMatchingView.ROW_LIMIT + 1));
        assertThat(html).contains("sku-" + (OfferMatchingView.ROW_LIMIT - 1) + "<").doesNotContain("sku-" + OfferMatchingView.ROW_LIMIT + "<");
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

    private static OfferMatchingRunView run(boolean running, Integer total, OfferMatchError error) {
        return new OfferMatchingRunView(running, !running, 400, total, "02.11.2026 10:59", "02.11.2026 10:50",
                error == null ? null : "store.marketplaces.offer.matching.error." + error.name(),
                error == OfferMatchError.MARKETPLACE_ERROR ? 503 : null);
    }

    @Test
    void aRunningRunShowsItsProgressInsteadOfTheButton() {
        // when
        String html = render(null, null, Map.of("run", run(true, 6150, null)));

        // then
        assertThat(html).contains("cl-spinner").contains("Dopasowanie trwa").containsPattern("400 z 6.150 ofert")
                .contains("10:59").contains("aria-live=\"polite\"");
        assertThat(html).doesNotContain("method=\"post\"").doesNotContain("??");
    }

    @Test
    void aRunningRunWithoutKnownTotalCountsWhatItChecked() {
        // when
        String html = render(null, null, Map.of("run", run(true, null, null)));

        // then
        assertThat(html).contains("Dopasowanie trwa").contains("400 ofert").doesNotContain(" z null").doesNotContain("??");
    }

    @Test
    void theLatestResultStaysVisibleDuringARun() {
        // given
        OfferMatchResult result = new OfferMatchResult(Instant.parse("2026-11-01T09:15:00Z"), 1,
                List.of(new Row("sku-3", "Monitor", List.of("5901234123459"), null, true)), List.of(), 0, 0);

        // when
        String html = render(OfferMatchingView.of(result), null, Map.of("run", run(true, 6150, null)));

        // then
        assertThat(html).contains("Ostatni przebieg: 01.11.2026 10:15").contains("Dopasowanie trwa").contains("cl-stat-grid");
    }

    @Test
    void aFailedRunExplainsWhyAndOffersANewOne() {
        // when
        String html = render(null, null, Map.of("run", run(false, 6150, OfferMatchError.MARKETPLACE_ERROR)));

        // then
        assertThat(html).contains("cl-alert is-bad").contains("02.11.2026 10:50").contains("HTTP 503")
                .contains("Empik").contains("method=\"post\"");
        assertThat(html).doesNotContain("Dopasowanie trwa").doesNotContain("??");
    }

    @Test
    void everyFailureHasASentenceOnThePage() {
        for (OfferMatchError error : OfferMatchError.values()) {
            // when
            String html = render(null, null, Map.of("run", run(false, 6150, error)));

            // then
            assertThat(html).as(error.name()).contains("cl-alert is-bad").doesNotContain("??")
                    .doesNotContain("store.marketplaces.offer.matching.error");
        }
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
        variables.put("csvHrefBase", ACTION);
        variables.put("marketplacesHref", "/dashboard/store/marketplaces");
        variables.put("backLabel", "Marketplace'y");
        variables.put("pageTitle", "Dopasuj oferty · Empik");
        return SettingsTemplateRenderer.render("store-marketplace-offer-matching", variables);
    }
}
