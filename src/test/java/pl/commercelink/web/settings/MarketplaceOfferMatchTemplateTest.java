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
        assertThat(html).contains(">5901234123459\n5901234123460</textarea>").contains(">5901234123458</textarea>");
        assertThat(html).contains("Oferta nie ma EAN").contains("różne karty PIM").contains("PIM już nie zna");
        assertThat(html).contains("Połączona wcześniej").contains("pim-1").contains("sku-5");
        assertThat(html).doesNotContain("??");
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
    void anOrderedRunIsConfirmedOnThePage() {
        // when
        String html = render(null, "Dopasowanie ofert z Empik zlecone.");

        // then
        assertThat(html).contains("cl-alert is-ok").contains("Dopasowanie ofert z Empik zlecone.");
    }

    private static String render(OfferMatchingView matching, String savedMessage) {
        Map<String, Object> variables = new HashMap<>();
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
