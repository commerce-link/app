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
        assertThat(html).contains(">W koszyku klienta musi być<").contains(">Bez warunku<")
                .contains("name=\"conditionTarget\" value=\"\" data-cl-variant-select=\"conditionTarget\" checked=\"checked\"")
                .contains("data-discount-picked=\"conditionCategories\"").contains(">Kategorie w warunku<")
                .contains("data-discount-picked=\"conditionProducts\"").contains(">Produkty w warunku<")
                .contains("data-discount-picked=\"conditionBrands\"").contains(">Marki w warunku<");
        assertThat(html).contains(">Obowiązuje na<").contains(">Cały koszyk<")
                .contains("name=\"target\" value=\"\" data-cl-variant-select=\"target\" checked=\"checked\"")
                .contains("data-discount-picked=\"categories\"").contains("rabat obejmie ją w każdym z nich");
        assertThat(html).doesNotContain("kod obejmie");
    }

    @Test
    void theQuantityShowsOnlyWithACondition() {
        // when
        String html = rendered();

        // then
        assertThat(html).contains("data-cl-variant-when=\"conditionTarget=Category|Product|Brand\"")
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
        form.setConditionTarget("Product");
        form.setConditionProducts(List.of(new TargetValue("PIM-1", "Karta graficzna ASUS Dual RTX 5070 OC")));
        form.setConditionQuantity("2");
        form.setTarget("Category");
        form.setCategories(List.of("Zasilacze"));

        // when
        String html = SettingsTemplateRenderer.render("store-promotion-new", page(form,
                Map.of("name", "store.promotions.name.required")));

        // then
        assertThat(html).contains("name=\"conditionTarget\" value=\"Product\" data-cl-variant-select=\"conditionTarget\" checked=\"checked\"")
                .contains("name=\"conditionProducts[0].id\" value=\"PIM-1\"")
                .contains("value=\"2\"")
                .contains("name=\"target\" value=\"Category\" data-cl-variant-select=\"target\" checked=\"checked\"")
                .contains("<input type=\"hidden\" name=\"categories\" value=\"Zasilacze\"/>");
    }

    @Test
    void rejectedFieldsAreListedAndMarked() {
        // when
        String html = SettingsTemplateRenderer.render("store-promotion-new", page(PromotionForm.empty(),
                Map.of("conditionTargetValues", "store.discounts.targetValues.required.Brand",
                        "conditionQuantity", "store.promotions.conditionQuantity.invalid")));

        // then
        assertThat(html).contains("id=\"promotion-errors\"").contains("href=\"#conditionTargetValues\"")
                .contains("id=\"conditionTargetValues-error\"").contains("Wybierz co najmniej jedną markę.")
                .contains("href=\"#conditionQuantity\"").contains("Liczba sztuk to liczba całkowita, co najmniej 1.");
    }

    @Test
    void theSummaryNamesTheSelectionRule() {
        // when
        String html = rendered();

        // then
        assertThat(html).contains("data-promotion-summary").contains("produkt objęty kilkoma dostaje największy z ich rabatów")
                .contains("data-minimum-only=\"Gdy koszyk jest wart co najmniej {0} zł\"");
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
