package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.discounts.TargetValue;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.web.dtos.PromotionForm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StoreNewPromotionTemplateTest {

    private static final String LIST = "/dashboard/store/promotions";
    private static final ProductCatalog PARTS = new ProductCatalog("store-1", "Podzespoły");

    private Map<String, Object> page(PromotionForm form, Map<String, String> errors) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("settingsPage", null);
        variables.put("navigation", null);
        variables.put("form", form);
        variables.put("errors", errors);
        variables.put("formAction", LIST + "/new");
        variables.put("backHref", LIST);
        variables.put("backLabel", "Promocje");
        variables.put("catalogs", List.of(PARTS));
        variables.put("pricelistUrl", "/dashboard/store/discounts/pricelist");
        return variables;
    }

    private String rendered() {
        return SettingsTemplateRenderer.render("store-promotion-new", page(PromotionForm.empty(), Map.of()));
    }

    @Test
    void theFormPostsToItsOwnPageAndLeadsBackToTheList() {
        // when
        String html = rendered();

        // then
        assertThat(html).contains("action=\"/dashboard/store/promotions/new\"").contains("id=\"promotion-form\"")
                .contains("href=\"/dashboard/store/promotions\"").contains(">Promocje<")
                .contains(">Nowa promocja<").contains(">Utwórz promocję<");
        assertThat(html).doesNotContain("??").doesNotContain("name=\"storeId\"").doesNotContain("name=\"paymentProvider\"")
                .doesNotContain("name=\"maxUses\"");
    }

    @Test
    void theConditionAndTheDiscountHaveTheirOwnFieldsAndTexts() {
        // when
        String html = rendered();

        // then
        assertThat(html).contains(">W koszyku klienta musi być<").contains("id=\"conditionScope\"")
                .contains("Bez wyboru promocja działa dla każdego koszyka.")
                .contains("data-discount-picked=\"conditionCategories\"").contains(">Kategorie w warunku<")
                .contains("data-discount-picked=\"conditionBrands\"").contains(">Marki w warunku<")
                .contains("data-discount-picked=\"conditionProducts\"").contains(">Produkty w warunku<");
        assertThat(html).contains(">Obowiązuje na<").contains("id=\"scope\"")
                .contains("Bez wyboru rabat obejmuje cały koszyk.")
                .contains("data-discount-picked=\"categories\"").contains("data-discount-picked=\"brands\"")
                .contains("data-discount-picked=\"products\"").contains("rabat obejmie ją w każdym z nich");
        assertThat(html).doesNotContain("kod obejmie").doesNotContain("name=\"conditionTarget\"")
                .doesNotContain("name=\"target\"");
    }

    @Test
    void theQuantityFieldIsMarkedForTheScriptThatHidesItWithoutACondition() {
        // when
        String html = rendered();

        // then
        assertThat(html).contains("<div class=\"cl-span-3\" data-promotion-condition-quantity>")
                .contains("name=\"conditionQuantity\"").contains("value=\"1\"").contains(">Co najmniej (szt.)<");
    }

    @Test
    void theDialogsReadThePricelistOfTheCodes() {
        // when
        String html = rendered();

        // then
        assertThat(html).contains("id=\"discount-categories-dialog\"").contains("id=\"discount-products-dialog\"")
                .contains("id=\"discount-brands-dialog\"")
                .contains("data-pricelist-url=\"/dashboard/store/discounts/pricelist\"")
                .contains("<option value=\"" + PARTS.getCatalogId() + "\">Podzespoły</option>");
    }

    @Test
    void pickedValuesComeBackAfterARejectedSave() {
        // given
        PromotionForm form = PromotionForm.empty();
        form.setConditionProducts(List.of(new TargetValue("PIM-1", "Karta graficzna ASUS Dual RTX 5070 OC")));
        form.setConditionQuantity("2");
        form.setCategories(List.of("Monitory"));
        form.setBrands(List.of("ASUS"));

        // when
        String html = SettingsTemplateRenderer.render("store-promotion-new", page(form,
                Map.of("name", "store.promotions.name.required")));

        // then
        assertThat(html).contains("name=\"conditionProducts[0].id\" value=\"PIM-1\"")
                .contains("value=\"2\"")
                .contains("<input type=\"hidden\" name=\"categories\" value=\"Monitory\"/>")
                .contains("<input type=\"hidden\" name=\"brands\" value=\"ASUS\"/>");
    }

    @Test
    void rejectedFieldsAreListedAndMarked() {
        // when
        String html = SettingsTemplateRenderer.render("store-promotion-new", page(PromotionForm.empty(),
                Map.of("conditionQuantity", "store.promotions.conditionQuantity.invalid")));

        // then
        assertThat(html).contains("id=\"promotion-errors\"")
                .contains("href=\"#conditionQuantity\"").contains("Liczba sztuk to liczba całkowita, co najmniej 1.");
    }

    @Test
    void theSummaryNamesTheSelectionRule() {
        // when
        String html = rendered();

        // then
        assertThat(html).contains("data-promotion-summary").contains("produkt objęty kilkoma dostaje największy z ich rabatów")
                .contains("data-minimum-only=\"Gdy koszyk jest wart co najmniej {0} zł\"")
                .contains("data-condition=\"Gdy w koszyku jest co najmniej {0} szt. {1}\"")
                .contains("data-condition-any-products=\"produktów\"")
                .contains("data-categories=\"z kategorii {0}\"").contains("data-brands=\"marek {0}\"")
                .contains("data-and=\"oraz\"").contains("data-any-products=\"na produkty\"");
    }

    @Test
    void theFormRendersAloneForASaveWithoutReloading() {
        // given
        Map<String, Object> variables = page(PromotionForm.empty(), Map.of());
        variables.put("redirectTo", LIST);

        // when
        String html = SettingsTemplateRenderer.render("<div th:replace=\"~{store-promotion-new :: promotionForm}\"></div>",
                variables);

        // then
        assertThat(html).contains("id=\"promotion-form\"").contains("data-cl-redirect=\"/dashboard/store/promotions\"")
                .doesNotContain(">Nowa promocja<");
    }

    @Test
    void thePageLoadsItsScripts() {
        // when
        String html = rendered();

        // then
        assertThat(html).contains("/js/async-form.js").contains("/js/variant-fields.js").contains("/js/money.js")
                .contains("/js/discount-target-picker.js").contains("/js/promotion-form.js")
                .doesNotContain("/js/discount-code-form.js");
    }
}
