package pl.commercelink.web.rma;

import org.junit.jupiter.api.Test;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RmaNewTemplateTest {

    static String render(RmaNewPage page) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("navigation", null);
        variables.put("page", page);
        return SettingsTemplateRenderer.render("rma-new", variables);
    }

    // the layout carries its own forms, styles and Bulma buttons; assert on the page's own content only
    static String content(String html) {
        int from = html.indexOf("<section class=\"cl-page\">");
        int to = html.indexOf("</script>", html.indexOf("/js/rma-new.js"));
        assertThat(from).isPositive();
        assertThat(to).isGreaterThan(from);
        return html.substring(from, to);
    }

    static RmaNewPage start() {
        return new RmaNewPage(RmaNewPage.State.START, null, "/dashboard/rma", "RMA", null,
                "Zgłoszenie zwrotu, wymiany albo naprawy z zamówienia klienta.", null, null, null, null, List.of(), null,
                null, null, Map.of(), Map.of());
    }

    static RmaNewPage.ItemRow row(int i, String id, boolean selected, String claimed) {
        return new RmaNewPage.ItemRow(i, id, "Produkt " + id, "EAN: 590", false, 2, "1", selected,
                selected ? "Return" : null, selected ? "Piszczy" : null, "100,00", 100.0, claimed,
                claimed == null ? null : "/dashboard/rma/" + claimed + "-full");
    }

    static RmaNewPage order(Map<String, String> summaryErrors, Map<String, String> fieldErrors, String pageError) {
        return new RmaNewPage(RmaNewPage.State.ORDER, "8c1d0291", "/dashboard/orders/8c1d0291-full", "Zamówienie 8c1d0291", "order",
                "Zamówienie",
                new RmaNewPage.Header("8c1d0291-full", "8c1d0291", "/dashboard/orders/8c1d0291-full", "OrderStatus.Delivered",
                        "is-ok", "02.10.2026", "Anna Nowak", "Sklep"),
                null, null, null, List.of(row(0, "i1", true, null), row(1, "i2", false, null), row(2, "i3", false, "e3b0c442")),
                null, new RmaNewPage.Summary("anna@x.pl", 1, 1, "100,00", true), pageError, summaryErrors, fieldErrors);
    }

    @Test
    void theStartPageIsOneFocusedSearchCard() {
        // when
        String html = content(render(start()));

        // then
        assertThat(html.split("<h1", -1)).hasSize(2);
        assertThat(html).contains("cl-search-card").contains("autofocus").contains("name=\"q\"")
                .contains("Którego zamówienia dotyczy zgłoszenie?").doesNotContain("method=\"post\"")
                .contains("href=\"/dashboard/rma\"");
    }

    @Test
    void theOrderPageHasItemsSummaryAndNoDefaultResolution() {
        // when
        String html = content(render(order(Map.of(), Map.of(), null)));

        // then
        assertThat(html).contains("id=\"rma-create-form\"").contains("method=\"post\"").contains("action=\"/dashboard/rma/new\"")
                .contains("name=\"orderId\" value=\"8c1d0291-full\"").contains("name=\"from\" value=\"order\"")
                .contains("class=\"cl-layout-aside\"").contains("cl-layout-side is-sticky")
                .contains("name=\"items[0].selected\"").contains("name=\"_items[0].selected\"")
                .contains("id=\"items0.qty\"").contains("max=\"2\"")
                .contains("Zamówienie 8c1d0291").contains("Utwórz RMA");
        // the selected item has Return checked because it was posted; the others have no checked radio
        assertThat(html.split("value=\"Return\" checked", -1)).hasSize(2);
        assertThat(html.split("name=\"items\\[1\\]\\.resolution\"[^>]*checked", -1)).hasSize(1);
        // a claimed item has no checkbox name and links to its RMA
        assertThat(html).doesNotContain("name=\"items[2].selected\"").contains("href=\"/dashboard/rma/e3b0c442-full\"");
    }

    @Test
    void errorsAreSummarisedAndShownUnderTheirFields() {
        // given
        Map<String, String> summary = new LinkedHashMap<>();
        summary.put("items0.qty", "Produkt i1 — ilość: od 1 do 2 szt.");
        summary.put("email", "E-mail klienta: wpisz poprawny adres");

        // when
        String html = content(render(order(summary, Map.of("items0.qty", "ilość: od 1 do 2 szt.", "email", "E-mail klienta: wpisz poprawny adres"), null)));

        // then
        assertThat(html).contains("data-cl-error-summary").contains("Popraw zaznaczone pola, żeby utworzyć RMA")
                .contains("href=\"#items0.qty\"").contains("href=\"#email\"")
                .contains("aria-invalid=\"true\"").contains("id=\"items0.qty-error\"").contains("id=\"email-error\"");
        assertThat(html.split("data-cl-error-summary", -1)).hasSize(2);
    }

    @Test
    void theTemplateUsesOnlyTheDesignSystem() {
        // when
        String html = content(render(order(Map.of(), Map.of(), "Zaznacz co najmniej jedną pozycję.")));

        // then
        assertThat(html).contains("Zaznacz co najmniej jedną pozycję.").doesNotContain("style=").doesNotContain("onclick=")
                .doesNotContain("class=\"box").doesNotContain("class=\"tag").doesNotContain("class=\"tabs")
                .doesNotContain("class=\"button").doesNotContain("alert(")
                .contains("/js/rma-new.js");
    }
}
