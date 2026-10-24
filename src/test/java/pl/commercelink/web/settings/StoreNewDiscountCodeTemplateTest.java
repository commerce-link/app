package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.web.dtos.DiscountCodeForm;
import pl.commercelink.web.dtos.PaymentOptionView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StoreNewDiscountCodeTemplateTest {

    private static final String LIST = "/dashboard/store/discounts";
    private static final List<PaymentOptionView> PAYNOW = List.of(new PaymentOptionView("paynow", "PayNow", true));

    private Map<String, Object> page(List<PaymentOptionView> gateways, Map<String, String> errors) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("settingsPage", null);
        variables.put("navigation", null);
        variables.put("form", DiscountCodeForm.empty());
        variables.put("errors", errors);
        variables.put("formAction", LIST + "/new");
        variables.put("backHref", LIST);
        variables.put("backLabel", "Kody rabatowe");
        variables.put("paymentOptions", gateways);
        variables.put("categoryGroups", DiscountCategoryGroup.of(List.of(
                catalog("Podzespoły", "Karty graficzne", "Procesory"),
                catalog("Outlet", "Karty graficzne", "Laptopy poleasingowe"))));
        variables.put("paymentsHref", "/dashboard/store/payments");
        return variables;
    }

    private String rendered() {
        return SettingsTemplateRenderer.render("store-discount-code-new", page(PAYNOW, Map.of()));
    }

    @Test
    void theFormPostsToItsOwnPageAndLeadsBackToTheList() {
        // when
        String html = rendered();

        // then
        assertThat(html).contains("action=\"/dashboard/store/discounts/new\"").contains("id=\"discount-code-form\"")
                .contains("href=\"/dashboard/store/discounts\"").contains(">Kody rabatowe<")
                .contains(">Nowy kod rabatowy<").contains(">Utwórz kod<");
        assertThat(html).doesNotContain("??").doesNotContain("name=\"storeId\"");
    }

    @Test
    void theFormGroupsCategoriesByCatalogWithEachNameOnce() {
        // when
        String html = rendered();

        // then
        assertThat(html).contains(">Podzespoły<").contains(">Outlet<").contains("też w: Outlet");
        assertThat(html.split("value=\"Karty graficzne\"", -1)).hasSize(2);
        assertThat(html.indexOf("value=\"Procesory\"")).isLessThan(html.indexOf("value=\"Laptopy poleasingowe\""));
        assertThat(html).contains("data-cl-variant-group=\"discount-target\" data-cl-variant=\"Category\"");
    }

    @Test
    void theGatewayChoiceStartsWithAnyGateway() {
        // when
        String html = rendered();

        // then
        assertThat(html).contains("<option value=\"\" selected=\"selected\">Każda bramka</option>");
        assertThat(html.indexOf(">Każda bramka<")).isLessThan(html.indexOf("<option value=\"paynow\">PayNow</option>"));
    }

    @Test
    void aStoreWithoutGatewaysIsSentToPaymentsInsteadOfTheForm() {
        // when
        String html = SettingsTemplateRenderer.render("store-discount-code-new", page(List.of(), Map.of()));

        // then
        assertThat(html).doesNotContain("id=\"discount-code-form\"").contains("Najpierw podłącz bramkę płatności")
                .contains("href=\"/dashboard/store/payments\"");
    }

    @Test
    void rejectedFieldsAreListedAndMarked() {
        // when
        String html = SettingsTemplateRenderer.render("store-discount-code-new", page(PAYNOW,
                Map.of("code", "store.discounts.code.taken", "targetValues", "store.discounts.targetValues.required")));

        // then
        assertThat(html).contains("id=\"discount-code-errors\"").contains("Sklep ma już kod o tej nazwie")
                .contains("href=\"#targetValues\"").contains("aria-invalid=\"true\"");
    }

    @Test
    void theFormRendersAloneForASaveWithoutReloading() {
        // given
        Map<String, Object> variables = page(PAYNOW, Map.of());
        variables.put("redirectTo", LIST);

        // when
        String html = SettingsTemplateRenderer.render("<div th:replace=\"~{store-discount-code-new :: codeForm}\"></div>",
                variables);

        // then
        assertThat(html).contains("id=\"discount-code-form\"").contains("data-cl-redirect=\"/dashboard/store/discounts\"")
                .doesNotContain(">Kody rabatowe<");
    }

    @Test
    void thePageLoadsItsScripts() {
        // when
        String html = rendered();

        // then
        assertThat(html).contains("/js/async-form.js").contains("/js/variant-fields.js")
                .contains("/js/discount-code-form.js");
    }

    private static ProductCatalog catalog(String name, String... categories) {
        ProductCatalog catalog = new ProductCatalog("store-1", name);
        for (String category : categories) {
            CategoryDefinition definition = new CategoryDefinition().withGeneratedId();
            definition.setName(category);
            catalog.getCategories().add(definition);
        }
        return catalog;
    }
}
