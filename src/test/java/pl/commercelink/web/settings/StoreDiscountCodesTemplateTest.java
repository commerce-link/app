package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.discounts.DiscountCode;
import pl.commercelink.discounts.DiscountTarget;
import pl.commercelink.discounts.DiscountType;
import pl.commercelink.discounts.TargetValue;
import pl.commercelink.starter.security.UserRole;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StoreDiscountCodesTemplateTest {

    private static final String PATH = "/dashboard/store/discounts";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 24, 12, 0);

    private static DiscountCode wholeBasket() {
        DiscountCode code = code("LATO-10", "Kampania letnia", DiscountType.Percentage, 10);
        code.setMinOrderValue(500);
        code.setUsedCount(12);
        return code;
    }

    private static DiscountCode categories() {
        DiscountCode code = code("GPU-50", "Rabat na podzespoły", DiscountType.Amount, 50);
        code.setTarget(DiscountTarget.Category);
        code.setTargetValues(List.of(category("Karty graficzne"), category("Procesory"), category("Płyty główne"),
                category("Zasilacze")));
        code.setPaymentProvider("paynow");
        code.setMaxUses(100);
        code.setUsedCount(6);
        code.setValidFrom(LocalDateTime.of(2026, 10, 1, 0, 0));
        code.setValidTo(LocalDateTime.of(2026, 12, 31, 23, 59, 59));
        return code;
    }

    private Map<String, Object> page(List<DiscountCode> codes, String q, String status, String target, String newCodeHref) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("settingsPage", SettingsPage.forRequest(UserRole.ADMIN, PATH));
        variables.put("navigation", null);
        variables.put("codes", DiscountCodeListView.of(codes, q, status, target, NOW, Map.of("paynow", "PayNow"), PATH));
        variables.put("listPath", PATH);
        variables.put("newCodeHref", newCodeHref);
        variables.put("paymentsHref", "/dashboard/store/payments");
        return variables;
    }

    private String rendered(List<DiscountCode> codes) {
        return SettingsTemplateRenderer.render("store-discount-codes", page(codes, null, null, null, PATH + "/new"));
    }

    @Test
    void theListShowsEveryColumnOfACode() {
        // when
        String html = rendered(List.of(wholeBasket(), categories()));

        // then
        assertThat(html).contains("href=\"/dashboard/store/discounts/LATO-10\"").contains(">Kampania letnia<");
        assertThat(html).contains(">10%<").contains(">od 500,00 zł<").contains(">Cały koszyk<").contains(">Każda<")
                .contains(">bez limitu<");
        assertThat(html).contains(">50,00 zł<").contains("Karty graficzne, Procesory, Płyty główne")
                .contains("Kategorie · +1 więcej").contains(">PayNow<").contains("6 / 100")
                .contains("01.10.2026 – 31.12.2026");
        assertThat(html).contains(">Aktywny<");
        assertThat(html).doesNotContain("??").doesNotContain("name=\"storeId\"");
    }

    @Test
    void theNewCodeButtonLeadsToItsOwnPage() {
        // when
        String html = rendered(List.of(wholeBasket()));

        // then
        assertThat(html).contains("href=\"/dashboard/store/discounts/new\"").contains(">Dodaj kod<")
                .doesNotContain("id=\"discount-code-form\"");
    }

    @Test
    void aStoreWithoutGatewaysIsSentToPaymentsInsteadOfANewCode() {
        // when
        String html = SettingsTemplateRenderer.render("store-discount-codes", page(List.of(), null, null, null, null));

        // then
        assertThat(html).doesNotContain("href=\"/dashboard/store/discounts/new\"")
                .contains("Najpierw podłącz bramkę płatności").contains("href=\"/dashboard/store/payments\"");
    }

    @Test
    void segmentsLinkToTheListInOneStatusWithTheirCounts() {
        // when
        String html = SettingsTemplateRenderer.render("store-discount-codes",
                page(List.of(wholeBasket(), categories()), null, "active", null, PATH + "/new"));

        // then
        assertThat(html).contains("href=\"/dashboard/store/discounts?status=expired\"")
                .contains("href=\"/dashboard/store/discounts?status=active\" aria-current=\"page\"");
        assertThat(html).contains("<option value=\"category\">Kategorie</option>")
                .contains("name=\"q\"").contains("data-cl-submit-on-change");
    }

    @Test
    void searchingKeepsTheTargetFilterAndFilteringKeepsTheSearch() {
        // when
        String html = SettingsTemplateRenderer.render("store-discount-codes",
                page(List.of(wholeBasket(), categories()), "gpu", "active", "category", PATH + "/new"));

        // then
        String search = html.substring(html.indexOf("role=\"search\""), html.indexOf("name=\"q\""));
        String filter = html.substring(html.indexOf("id=\"discount-target-filter\"") - 600, html.indexOf("id=\"discount-target-filter\""));
        assertThat(search).contains("name=\"target\" value=\"category\"").contains("name=\"status\" value=\"active\"");
        assertThat(filter).contains("name=\"q\" value=\"gpu\"").contains("name=\"status\" value=\"active\"");
    }

    @Test
    void aStoreWithoutCodesIsToldHowToCreateTheFirstOne() {
        // when
        String html = rendered(List.of());

        // then
        assertThat(html).contains("Nie masz jeszcze kodów rabatowych.").contains(">Dodaj pierwszy kod<")
                .doesNotContain("formularzu poniżej").doesNotContain("aria-label=\"Status kodu\"")
                .doesNotContain("name=\"q\"").doesNotContain("<table");
    }

    @Test
    void aStoreWithoutCodesAndGatewaysIsNotOfferedAFirstCode() {
        // when
        String html = SettingsTemplateRenderer.render("store-discount-codes", page(List.of(), null, null, null, null));

        // then
        assertThat(html).contains("Nie masz jeszcze kodów rabatowych.").doesNotContain(">Dodaj pierwszy kod<");
    }

    @Test
    void aFilterWithoutMatchesOffersToClearIt() {
        // when
        String html = SettingsTemplateRenderer.render("store-discount-codes",
                page(List.of(wholeBasket()), "zima", null, null, PATH + "/new"));

        // then
        assertThat(html).contains("Żaden kod nie pasuje do filtrów").contains("href=\"/dashboard/store/discounts\"")
                .doesNotContain("<table");
    }

    @Test
    void thePageLoadsItsScripts() {
        // when
        String html = rendered(List.of(wholeBasket()));

        // then
        assertThat(html).contains("/js/submit-on-change.js");
    }

    private static DiscountCode code(String code, String name, DiscountType type, double value) {
        DiscountCode discountCode = new DiscountCode();
        discountCode.setStoreId("store-1");
        discountCode.setCode(code);
        discountCode.setName(name);
        discountCode.setType(type);
        discountCode.setValue(value);
        discountCode.setActive(true);
        return discountCode;
    }

    private static TargetValue category(String name) {
        return new TargetValue(name, name);
    }
}
