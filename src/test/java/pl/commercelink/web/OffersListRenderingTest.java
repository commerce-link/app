package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import pl.commercelink.web.offers.OfferListPage;
import pl.commercelink.web.offers.OfferListPage.*;
import pl.commercelink.web.offers.OfferListQuery;
import pl.commercelink.web.offers.OfferSegment;
import pl.commercelink.web.orders.Pagination;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Renders the offers template with a real model and the Polish bundle. */
class OffersListRenderingTest {

    private static final String FRAGMENT = "<div th:replace=\"~{offers :: results}\"></div>";

    private static OfferRow offerRow() {
        return new OfferRow("/dashboard/offer/o1", "Stacje CAD", "8f3a21c7", "8f3a21c7-full", "pozycje: 9",
                "Biuro Lis s.c.", "biuro@lis.pl", "02.10", "Jan Kowalski", "Wygasa", "is-warn", "jutro, 04.10", true,
                "48 960,00 PLN", "netto 39 804,88 PLN", "https://app.example/store/s/client/offer/o1",
                "/dashboard/offer/o1/copy", "/dashboard/offer/o1/copy?withContact=true",
                "/dashboard/offer/o1/delete?returnTo=%2Fdashboard%2Foffers", "Usunąć ofertę „Stacje CAD”?",
                "Oferta zniknie z listy…", "Akcje: Stacje CAD", "Kopiuj link do oferty 8f3a21c7 dla klienta");
    }

    private static TemplateRow templateRow() {
        return new TemplateRow("/dashboard/offer/t1", "Szablon CAD", "e41c09d2", "e41c09d2-full", "pozycje: 9", "28.08",
                "Jan Kowalski", "8 160,00 PLN", "/dashboard/offer/new?intent=template&sourceId=t1",
                "/dashboard/offer/t1/delete?returnTo=%2Fdashboard%2Foffers%3Fsegment%3Dtemplates", "Usunąć szablon „Szablon CAD”?",
                "Szablon zniknie z listy…", "Akcje: Szablon CAD");
    }

    private static OfferListPage page(OfferSegment segment, List<OfferRow> offers, List<TemplateRow> templates,
                                      List<BasketRow> baskets, List<Chip> chips, EmptyState empty) {
        OfferListQuery query = new OfferListQuery(segment, null, List.of(), null, null, 1);
        int total = offers.size() + templates.size() + baskets.size();
        return new OfferListPage(query,
                List.of(new SegmentLink("Oferty", "/dashboard/offers", segment == OfferSegment.OFFERS),
                        new SegmentLink("Szablony", "/dashboard/offers?segment=templates", segment == OfferSegment.TEMPLATES),
                        new SegmentLink("Koszyki ze sklepu", "/dashboard/offers?segment=baskets", segment == OfferSegment.BASKETS)),
                List.of(new Option("active", "Ważne", false), new Option("expiring", "Wygasają w 7 dni", true)),
                "Wygasają w 7 dni", new DateMenu("wszystkie", null, null), chips, "Oferty: " + total,
                offers, templates, baskets, Pagination.of(1, total, 25, n -> "/dashboard/offers?page=" + n), empty, chips.size());
    }

    @Test
    void offerRowCarriesTheLinkClientValidityValueAndActions() {
        // given
        OfferListPage page = page(OfferSegment.OFFERS, List.of(offerRow()), List.of(), List.of(), List.of(), null);

        // when
        String html = SettingsTemplateRenderer.render(FRAGMENT, Map.of("page", page));

        // then
        assertThat(html).contains("class=\"cl-table is-orders is-offers\"")
                .contains("<a class=\"cl-row-link cl-cell-name\" href=\"/dashboard/offer/o1\">Stacje CAD</a>")
                .contains("Biuro Lis s.c.").contains("biuro@lis.pl").contains("Jan Kowalski")
                .contains("cl-status is-warn").contains("jutro, 04.10").contains("48 960,00 PLN")
                .contains("data-cl-copy=\"https://app.example/store/s/client/offer/o1\"")
                .contains("action=\"/dashboard/offer/o1/copy\"").contains("action=\"/dashboard/offer/o1/copy?withContact=true\"")
                .contains("href=\"/dashboard/offer/o1/delete?returnTo=%2Fdashboard%2Foffers\"").contains("data-cl-confirm")
                .contains("aria-label=\"Akcje: Stacje CAD\"")
                .doesNotContain("??").doesNotContain("class=\"button").doesNotContain("class=\"table");
    }

    @Test
    void templateRowCreatesAnOfferAndHasNoClientColumn() {
        // given
        OfferListPage page = page(OfferSegment.TEMPLATES, List.of(), List.of(templateRow()), List.of(), List.of(), null);

        // when
        String html = SettingsTemplateRenderer.render(FRAGMENT, Map.of("page", page));

        // then
        assertThat(html).contains("href=\"/dashboard/offer/new?intent=template&amp;sourceId=t1\"").contains("Utwórz ofertę")
                .contains("Szablon to zestaw pozycji bez klienta").doesNotContain(">Klient<").doesNotContain("data-cl-filter-menu=\"validity\"");
    }

    @Test
    void storeBasketRowIsOnlyAPreviewLink() {
        // given
        BasketRow row = new BasketRow("/dashboard/basket/view/b1", "d7a20e31", "d7a20e31-full", "gość, bez danych",
                "03.10, 11:42", "pozycje: 2", "1 698,00 PLN");
        OfferListPage page = page(OfferSegment.BASKETS, List.of(), List.of(), List.of(row), List.of(), null);

        // when
        String html = SettingsTemplateRenderer.render(FRAGMENT, Map.of("page", page));

        // then
        assertThat(html).contains("href=\"/dashboard/basket/view/b1\"").contains("Usuwane automatycznie po 14 dniach")
                .doesNotContain("cl-menu-glyph").doesNotContain("data-cl-confirm");
    }

    @Test
    void emptyFilteredListOffersClearing() {
        // given
        OfferListPage page = page(OfferSegment.OFFERS, List.of(), List.of(), List.of(),
                List.of(new Chip("Szukane: Projektor", "/dashboard/offers", "Usuń zawężenie: Szukane: Projektor")),
                new EmptyState("Brak wyników dla wybranych filtrów.", "Wyczyść filtry", "/dashboard/offers"));

        // when
        String html = SettingsTemplateRenderer.render(FRAGMENT, Map.of("page", page));

        // then
        assertThat(html).contains("cl-list-empty").contains("Brak wyników dla wybranych filtrów.").contains("Szukane: Projektor")
                .doesNotContain("<table");
    }

    @Test
    void wholePageHasOneHeadingTheNewOfferMenuAndTheNotice() {
        // given
        OfferListPage page = page(OfferSegment.OFFERS, List.of(offerRow()), List.of(), List.of(), List.of(), null);

        // when
        String html = SettingsTemplateRenderer.render("offers", Map.of("page", page, "offerNotice", "Usunięto ofertę „X”."));

        // then
        assertThat(html.split("<h1", -1)).hasSize(2);
        assertThat(html).contains("summary class=\"cl-button is-primary\"").contains("Nowa oferta")
                .contains("href=\"/dashboard/offer/new?intent=manual\"").contains("href=\"/dashboard/offers?segment=templates\"")
                .contains("href=\"/dashboard/offer/new/csv\"").contains("cl-menu-desc")
                .contains("cl-alert is-ok").contains("Usunięto ofertę „X”.")
                .contains("data-cl-list-results").contains("id=\"cl-confirm-dialog\"")
                .contains("/js/list-page.js").contains("/js/menu.js").contains("/js/confirm-dialog.js").contains("/js/copy-field.js")
                .doesNotContain("??");
    }

    @Test
    void templateSourceHasNoInlineHandlersOrStyles() throws Exception {
        // given
        String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/templates/offers.html"));

        // when / then
        assertThat(source).doesNotContain("onclick").doesNotContain(" style=");
    }
}
