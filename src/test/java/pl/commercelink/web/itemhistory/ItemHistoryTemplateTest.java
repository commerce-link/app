package pl.commercelink.web.itemhistory;

import org.junit.jupiter.api.Test;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ItemHistoryTemplateTest {

    static String render(ItemHistoryPage page) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("navigation", null);
        variables.put("page", page);
        return SettingsTemplateRenderer.render("item-history", variables);
    }

    static ItemHistoryPage found(List<ItemHistoryPage.Event> events, Integer limit, String warning) {
        return new ItemHistoryPage("SN-1", true, true, warning,
                new ItemHistoryPage.Product("Samsung 980 PRO", "SN-1", "8806090295454", null),
                new ItemHistoryPage.Now("W zamówieniu", "is-info", "Zamówienie b81c4e07 · W kompletacji", "Zamówienie", "/dashboard/orders/b81c4e07"),
                "Od najnowszego zdarzenia.", events, limit, limit == null ? null : "Pokaż 2 wcześniejsze zdarzenia",
                limit == null ? null : "Pokazano starsze zdarzenia: 2", limit == null ? null : "Ukryto starsze zdarzenia: 2", null);
    }

    static ItemHistoryPage.Event event(String id) {
        return new ItemHistoryPage.Event("29.09.2026, 14:12", "fa-shopping-cart", "Zamówienie złożone", "Zamówienie " + id,
                "/dashboard/orders/" + id, id, "W kompletacji", "is-info", "Klient: Anna Nowak");
    }

    @Test
    void theSearchPageHasOneHeadingAFocusedFieldAndNoTimeline() {
        // when
        String html = render(ItemHistoryPage.empty());

        // then
        assertThat(html.split("<h1", -1)).hasSize(2);
        assertThat(html).contains("Znajdź przedmiot").contains("autofocus").doesNotContain("cl-timeline")
                .doesNotContain("Nie znaleziono przedmiotu").doesNotContain("is-header");
        assertThat(html.split("id=\"serial-search\"", -1)).hasSize(2);
    }

    @Test
    void aFoundItemShowsTheCardTheNowBandAndSameTabLinks() {
        // when
        String html = render(found(List.of(event("b81c4e07")), null, null));

        // then
        assertThat(html).contains("Samsung 980 PRO").contains("class=\"cl-now\"").contains("W zamówieniu")
                .contains("href=\"/dashboard/orders/b81c4e07\"").contains("data-cl-copy=\"8806090295454\"")
                .doesNotContain("target=\"_blank\"").doesNotContain("data-cl-timeline-limit")
                .doesNotContain("Kod producenta")
                .doesNotContain("Znajdź przedmiot").doesNotContain("Nie znaleziono przedmiotu")
                .doesNotContain("data-cl-timeline-more");
        assertThat(html.split("id=\"serial-search\"", -1)).hasSize(2);
        assertThat(html).doesNotContain("class=\"timeline");
    }

    @Test
    void aLongHistoryCollapsesThroughTheSharedFragment() {
        // when
        String html = render(found(List.of(event("a"), event("b")), 10, null));

        // then
        assertThat(html).contains("data-cl-timeline-limit=\"10\"").contains("Pokaż 2 wcześniejsze zdarzenia")
                .contains("aria-controls=\"item-events\"");
    }

    @Test
    void anAmbiguousNumberShowsTheWarning() {
        // when
        String html = render(found(List.of(event("a")), null, "Zamówienia z tym numerem: 512 · Różne produkty: 3"));

        // then
        assertThat(html).contains("cl-alert is-warn").contains("Zamówienia z tym numerem: 512");
    }

    @Test
    void escapesMarkupInTheSearchedNumber() {
        // when
        String html = render(new ItemHistoryPage("<b>x</b>", true, false, null, null, null, null, List.of(), null, null, null, null, null));

        // then
        assertThat(html).doesNotContain("<b>x</b>").contains("&lt;b&gt;x&lt;/b&gt;").contains("Nie znaleziono przedmiotu")
                .doesNotContain("Znajdź przedmiot");
        assertThat(html.split("id=\"serial-search\"", -1)).hasSize(2);
    }
}
