package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.discounts.DiscountTarget;
import pl.commercelink.discounts.DiscountType;
import pl.commercelink.discounts.Promotion;
import pl.commercelink.discounts.TargetValue;
import pl.commercelink.starter.security.UserRole;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StorePromotionsTemplateTest {

    private static final String PATH = "/dashboard/store/promotions";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 12, 0);

    private static Promotion monitorAndCable() {
        Promotion promotion = promotion("p-1", "Monitor + kabel taniej", DiscountType.Percentage, 20);
        promotion.setConditionTarget(DiscountTarget.Category);
        promotion.setConditionValues(List.of(value("Monitory")));
        promotion.setConditionQuantity(1);
        promotion.setTarget(DiscountTarget.Category);
        promotion.setTargetValues(List.of(value("Kable i adaptery")));
        promotion.setValidFrom(LocalDateTime.of(2026, 11, 1, 0, 0));
        promotion.setValidTo(LocalDateTime.of(2026, 12, 31, 23, 59, 59));
        return promotion;
    }

    private static Promotion bigBasket() {
        Promotion promotion = promotion("p-2", "Duży koszyk", DiscountType.Amount, 150);
        promotion.setMinOrderValue(5000);
        return promotion;
    }

    private static Promotion brands() {
        Promotion promotion = promotion("p-3", "Dwie myszy gamingowe", DiscountType.Percentage, 15);
        promotion.setConditionTarget(DiscountTarget.Brand);
        promotion.setConditionValues(List.of(value("Logitech"), value("SteelSeries"), value("Razer"), value("HyperX")));
        promotion.setConditionQuantity(2);
        promotion.setMinOrderValue(300);
        promotion.setTarget(DiscountTarget.Brand);
        promotion.setTargetValues(List.of(value("Logitech"), value("SteelSeries"), value("Razer"), value("HyperX")));
        promotion.setActive(false);
        return promotion;
    }

    private Map<String, Object> page(List<Promotion> promotions, String q, String status) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("settingsPage", SettingsPage.forRequest(UserRole.ADMIN, PATH));
        variables.put("navigation", null);
        variables.put("promotions", PromotionListView.of(promotions, q, status, NOW, PATH));
        variables.put("listPath", PATH);
        variables.put("newPromotionHref", PATH + "/new");
        return variables;
    }

    private String rendered(List<Promotion> promotions) {
        return SettingsTemplateRenderer.render("store-promotions", page(promotions, null, null));
    }

    @Test
    void theListShowsTheConditionAndTheDiscountOfAPromotion() {
        // when
        String html = rendered(List.of(monitorAndCable(), bigBasket(), brands()));

        // then
        assertThat(html).contains(">Promocje<").contains("Rabaty, które koszyk nalicza sam, gdy klient spełni warunek");
        assertThat(html).contains("href=\"/dashboard/store/promotions/p-1\"").contains(">Monitor + kabel taniej<")
                .contains(">Monitory<").contains(">kategorie · od 1 szt.<")
                .contains(">20%<").contains(">na: Kable i adaptery<")
                .contains("01.11.2026 – 31.12.2026").contains(">Aktywna<");
        assertThat(html).contains(">Bez warunku<").contains(">koszyk od 5 000,00 zł<")
                .contains(">150,00 zł<").contains(">na: cały koszyk<").contains(">bez terminu<");
        assertThat(html).contains(">Logitech, SteelSeries, Razer<")
                .contains(">marki · +1 więcej · od 2 szt. · koszyk od 300,00 zł<")
                .contains(">na: Logitech, SteelSeries, Razer · +1 więcej<").contains(">Wyłączona<");
        assertThat(html).doesNotContain("??").doesNotContain("name=\"storeId\"").doesNotContain("name=\"target\"");
    }

    @Test
    void theNewPromotionButtonLeadsToItsOwnPage() {
        // when
        String html = rendered(List.of(bigBasket()));

        // then
        assertThat(html).contains("href=\"/dashboard/store/promotions/new\"").contains(">Dodaj promocję<");
    }

    @Test
    void theListIsSortedByName() {
        // when
        String html = rendered(List.of(monitorAndCable(), brands(), bigBasket()));

        // then
        assertThat(html.indexOf(">Duży koszyk<")).isLessThan(html.indexOf(">Dwie myszy gamingowe<"));
        assertThat(html.indexOf(">Dwie myszy gamingowe<")).isLessThan(html.indexOf(">Monitor + kabel taniej<"));
    }

    @Test
    void segmentsLinkToTheListInOneStatusWithTheirCounts() {
        // when
        String html = SettingsTemplateRenderer.render("store-promotions",
                page(List.of(monitorAndCable(), bigBasket(), brands()), null, "disabled"));

        // then
        assertThat(html).contains("href=\"/dashboard/store/promotions?status=active\"")
                .contains("href=\"/dashboard/store/promotions?status=disabled\" aria-current=\"page\"")
                .contains(">Zaplanowane<").contains(">Wygasłe<")
                .doesNotContain(">Duży koszyk<").contains(">Dwie myszy gamingowe<");
    }

    @Test
    void searchingByNameKeepsTheStatus() {
        // when
        String html = SettingsTemplateRenderer.render("store-promotions",
                page(List.of(monitorAndCable(), bigBasket()), "koszyk", "active"));

        // then
        String search = html.substring(html.indexOf("role=\"search\""), html.indexOf("name=\"q\""));
        assertThat(search).contains("name=\"status\" value=\"active\"");
        assertThat(html).contains(">Duży koszyk<").doesNotContain(">Monitor + kabel taniej<")
                .contains("href=\"/dashboard/store/promotions?q=koszyk&amp;status=expired\"");
    }

    @Test
    void aStoreWithoutPromotionsIsToldHowToCreateTheFirstOne() {
        // when
        String html = rendered(List.of());

        // then
        assertThat(html).contains("Nie masz jeszcze promocji. Działają tylko z podłączoną bramką płatności.")
                .contains(">Dodaj pierwszą promocję<")
                .doesNotContain("aria-label=\"Status promocji\"").doesNotContain("<table");
    }

    @Test
    void aFilterWithoutMatchesOffersToClearIt() {
        // when
        String html = SettingsTemplateRenderer.render("store-promotions", page(List.of(bigBasket()), "zima", null));

        // then
        assertThat(html).contains("Żadna promocja nie pasuje do filtrów").contains("href=\"/dashboard/store/promotions\"")
                .doesNotContain("<table");
    }

    private static Promotion promotion(String id, String name, DiscountType type, double value) {
        Promotion promotion = new Promotion();
        promotion.setStoreId("store-1");
        promotion.setPromotionId(id);
        promotion.setName(name);
        promotion.setType(type);
        promotion.setValue(value);
        promotion.setActive(true);
        return promotion;
    }

    private static TargetValue value(String name) {
        return new TargetValue(name, name);
    }
}
