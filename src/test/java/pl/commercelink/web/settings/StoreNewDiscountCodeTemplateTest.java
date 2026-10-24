package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.discounts.TargetValue;
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

    private static final ProductCatalog PARTS = catalog("Podzespoły", "Karty graficzne", "Procesory");
    private static final ProductCatalog OUTLET = catalog("Outlet", "Karty graficzne", "Laptopy poleasingowe");

    private Map<String, Object> page(List<PaymentOptionView> gateways, Map<String, String> errors) {
        return page(gateways, errors, DiscountCodeForm.empty());
    }

    private Map<String, Object> page(List<PaymentOptionView> gateways, Map<String, String> errors, DiscountCodeForm form) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("settingsPage", null);
        variables.put("navigation", null);
        variables.put("form", form);
        variables.put("errors", errors);
        variables.put("formAction", LIST + "/new");
        variables.put("backHref", LIST);
        variables.put("backLabel", "Kody rabatowe");
        variables.put("paymentOptions", gateways);
        variables.put("catalogs", List.of(PARTS, OUTLET));
        variables.put("pricelistUrl", LIST + "/pricelist");
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
    void categoriesProductsAndBrandsArePickedInADialog() {
        // when
        String html = rendered();

        // then
        assertThat(html).contains("name=\"target\" value=\"Category\"").contains("name=\"target\" value=\"Product\"")
                .contains("name=\"target\" value=\"Brand\"")
                .contains("data-cl-variant-group=\"discount-target\" data-cl-variant=\"Category\"")
                .contains("data-cl-variant-group=\"discount-target\" data-cl-variant=\"Product\"")
                .contains("data-cl-variant-group=\"discount-target\" data-cl-variant=\"Brand\"")
                .contains(">Wybierz kategorie<").contains(">Wybierz produkty<").contains(">Wybierz marki<")
                .doesNotContain("type=\"checkbox\" name=\"categories\"");
    }

    @Test
    void theDialogsReadThePricelistOfTheChosenStoreCatalog() {
        // when
        String html = rendered();

        // then
        assertThat(html).contains("id=\"discount-categories-dialog\"").contains("id=\"discount-products-dialog\"")
                .contains("id=\"discount-brands-dialog\"")
                .contains("data-pricelist-url=\"/dashboard/store/discounts/pricelist\"")
                .contains("<option value=\"" + PARTS.getCatalogId() + "\">Podzespoły</option>")
                .contains("<option value=\"" + OUTLET.getCatalogId() + "\">Outlet</option>");
    }

    @Test
    void pickedCategoriesComeBackAfterARejectedSave() {
        // given
        DiscountCodeForm form = DiscountCodeForm.empty();
        form.setTarget("Category");
        form.setCategories(List.of("Karty graficzne", "Procesory"));

        // when
        String html = SettingsTemplateRenderer.render("store-discount-code-new", page(PAYNOW,
                Map.of("name", "store.discounts.name.required"), form));

        // then
        assertThat(html).contains("value=\"Category\" data-cl-variant-select=\"discount-target\" checked=\"checked\"")
                .contains("<input type=\"hidden\" name=\"categories\" value=\"Karty graficzne\"/>")
                .contains("<input type=\"hidden\" name=\"categories\" value=\"Procesory\"/>");
    }

    @Test
    void pickedProductsComeBackAfterARejectedSave() {
        // given
        DiscountCodeForm form = DiscountCodeForm.empty();
        form.setTarget("Product");
        form.setProducts(List.of(new TargetValue("PIM-1", "Zestaw Gamer RTX 5070"),
                new TargetValue("PIM-2", "Karta graficzna ASUS Dual RTX 5070 OC")));

        // when
        String html = SettingsTemplateRenderer.render("store-discount-code-new", page(PAYNOW,
                Map.of("name", "store.discounts.name.required"), form));

        // then
        assertThat(html).contains("value=\"Product\" data-cl-variant-select=\"discount-target\" checked=\"checked\"")
                .contains("name=\"products[0].id\" value=\"PIM-1\"")
                .contains("name=\"products[0].name\" value=\"Zestaw Gamer RTX 5070\"")
                .contains("name=\"products[1].id\" value=\"PIM-2\"")
                .contains(">Karta graficzna ASUS Dual RTX 5070 OC<");
    }

    @Test
    void pickedBrandsComeBackAfterARejectedSave() {
        // given
        DiscountCodeForm form = DiscountCodeForm.empty();
        form.setTarget("Brand");
        form.setBrands(List.of("ASUS", "MSI"));

        // when
        String html = SettingsTemplateRenderer.render("store-discount-code-new", page(PAYNOW,
                Map.of("name", "store.discounts.name.required"), form));

        // then
        assertThat(html).contains("value=\"Brand\" data-cl-variant-select=\"discount-target\" checked=\"checked\"")
                .contains("name=\"brands\" value=\"ASUS\"").contains("name=\"brands\" value=\"MSI\"");
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
                Map.of("code", "store.discounts.code.taken", "targetValues", "store.discounts.targetValues.required.Brand")));

        // then
        assertThat(html).contains("id=\"discount-code-errors\"").contains("Sklep ma już kod o tej nazwie")
                .contains("href=\"#targetValues\"").contains("id=\"targetValues\"").contains("aria-invalid=\"true\"")
                .contains("Wybierz co najmniej jedną markę.");
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
                .contains("/js/money.js").contains("/js/discount-code-form.js");
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
