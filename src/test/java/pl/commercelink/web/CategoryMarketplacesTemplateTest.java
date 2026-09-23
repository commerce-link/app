package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import pl.commercelink.products.MarketplaceDefinition;
import pl.commercelink.web.catalog.MarketplaceDefinitionRow;
import pl.commercelink.web.dtos.MarketplaceDefinitionForm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryMarketplacesTemplateTest {

    private static final String CATALOG_ID = "c1";
    private static final String CATEGORY_ID = "k1";

    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/catalog/" + name + ".html"), StandardCharsets.UTF_8);
    }

    private static MarketplaceDefinition definition(String name, double markup, int totalQty, int perDistributor,
                                                    int distributors, int local, int warehouse) {
        return new MarketplaceDefinition(name, markup, totalQty, perDistributor, distributors, local, warehouse);
    }

    private static MarketplaceDefinitionRow row(String name, MarketplaceDefinition definition, int approved, boolean connected) {
        return MarketplaceDefinitionRow.of(CATALOG_ID, CATEGORY_ID, name, name, Optional.ofNullable(definition), approved, connected);
    }

    /** The list as the controller builds it: one listing marketplace, one incomplete, one not configured, one orphan. */
    private static String renderedList(List<MarketplaceDefinitionRow> rows, List<MarketplaceDefinitionRow> orphans) {
        Context context = new Context();
        context.setVariable("rows", rows);
        context.setVariable("orphans", orphans);
        context.setVariable("storeMarketplacesHref", "/dashboard/store/marketplaces");
        context.setVariable("backHref", "/dashboard/catalogs/c1/category/k1/settings");
        context.setVariable("backLabel", "Category settings");
        context.setVariable("lead", "GPU · marketplaces");
        return EnglishFragmentTemplateEngine.create()
                .process("catalog/category-marketplaces", Set.of("div.cl-page-body"), context);
    }

    private static String renderedForm(MarketplaceDefinitionForm form, Map<String, String> errors) {
        Context context = new Context();
        context.setVariable("form", form);
        context.setVariable("errors", errors);
        context.setVariable("marketplaceName", "Allegro");
        context.setVariable("approvedProducts", 14);
        context.setVariable("formAction", "/dashboard/catalogs/c1/category/k1/settings/marketplaces/Allegro");
        context.setVariable("backHref", "/dashboard/catalogs/c1/category/k1/settings/marketplaces");
        context.setVariable("redirectTo", null);
        return EnglishFragmentTemplateEngine.create()
                .process("catalog/category-marketplace", Set.of("marketplaceForm"), context);
    }

    @Test
    void theDefinitionFormSavesWithoutReloadAndAsksTheServerAgainOnAnError() throws Exception {
        // when / then
        assertThat(source("category-marketplace")).contains("th:fragment=\"marketplaceForm\"")
                .contains("id=\"marketplace-definition-form\"").contains("data-cl-async")
                .contains("data-cl-redirect=${redirectTo}").contains("@{/js/async-form.js}")
                .contains("errorSummary('marketplace-definition-errors'").contains("id=\"conditions\"");
    }

    @Test
    void neitherPageCarriesATableAnAdapterSelectOrAnInlineStyle() throws Exception {
        // given
        String list = source("category-marketplaces");
        String form = source("category-marketplace");

        // then
        assertThat(list).doesNotContain("<table").doesNotContain("<select").doesNotContain("style=");
        assertThat(form).doesNotContain("<table").doesNotContain("<select").doesNotContain("style=");
    }

    @Test
    void everyDefinitionIsDeletedThroughAConfirmation() throws Exception {
        // when / then
        assertThat(source("category-marketplaces")).contains("data-cl-confirm")
                .contains("data-cl-confirm-title=#{catalog.category.marketplace.delete.title")
                .contains("confirm-dialog :: dialog").contains("@{/js/confirm-dialog.js}");
    }

    @Test
    void aListingMarketplaceShowsItsConditionsAndTheNumberOfApprovedProducts() {
        // given
        MarketplaceDefinitionRow allegro = row("Allegro", definition("Allegro", 1.1, 5, 1, 2, 1, 3), 14, true);
        allegro.definition().get().setExportSelectedProducts(true);

        // when
        String html = renderedList(List.of(allegro), List.of());

        // then
        assertThat(html).contains("Markup ×1,10").contains("From the warehouse from 3 pcs.")
                .contains("From distributors: 5 pcs. in total, 1 at each of at least 2 (1 local)")
                .contains("Approved products only: 14")
                .contains("<span class=\"cl-status is-ok\">Listing</span>")
                .contains(">Edit</a>").contains("aria-label=\"Edit: Allegro\"")
                .contains("href=\"/dashboard/catalogs/c1/category/k1/settings/marketplaces/Allegro\"");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void anIncompleteDefinitionSaysWhatIsMissingAndAMarketplaceWithoutOneOffersToConfigureIt() {
        // given
        MarketplaceDefinitionRow morele = row("Morele", definition("Morele", 1.05, 0, 0, 0, 0, 0), 0, true);
        MarketplaceDefinitionRow csCart = row("CS-Cart", null, 0, true);

        // when
        String html = renderedList(List.of(morele, csCart), List.of());

        // then
        assertThat(html).contains("<span class=\"cl-status is-warn\">Incomplete</span>")
                .contains("A condition is missing").contains(">Complete</a>")
                .contains("Not configured — the products of this category are not listed there.")
                .contains(">Configure</a>");
        // the accessible name repeats the visible text, which depends on the state (WCAG 2.5.3)
        assertThat(html).contains("aria-label=\"Complete: Morele\"").contains("aria-label=\"Configure: CS-Cart\"")
                .doesNotContain("aria-label=\"Edit: Morele\"").doesNotContain("aria-label=\"Edit: CS-Cart\"");
        // the marketplace without a definition has nothing to delete
        assertThat(html).contains("aria-label=\"Delete: Morele\"").doesNotContain("aria-label=\"Delete: CS-Cart\"");
    }

    @Test
    void aDefinitionWithoutANameIsListedApartAndNamedLikeAnyUntitledRecord() {
        // given
        MarketplaceDefinitionRow unnamed = row(null, definition(null, 1.0, 0, 5, 3, 0, 0), 0, false);

        // when
        String html = renderedList(List.of(), List.of(unnamed));

        // then
        assertThat(html).contains("class=\"cl-list-group\"").contains("Definitions without a marketplace")
                .contains("(untitled)").contains("Marketplace not connected")
                .contains("href=\"/dashboard/catalogs/c1/category/k1/settings/marketplaces/_unnamed_/delete\"");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void aStoreWithoutAMarketplaceIsSentToTheSettingsPageThatConnectsOne() {
        // when
        String html = renderedList(List.of(), List.of());

        // then
        assertThat(html).contains("cl-alert is-warn").contains("The store has no marketplace connected.")
                .contains("href=\"/dashboard/store/marketplaces\"").contains("Connect one in Settings");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void theFormPostsTheValuesOfTheSavedDefinition() {
        // given
        MarketplaceDefinitionForm form = MarketplaceDefinitionForm.from(definition("Allegro", 1.1, 5, 1, 2, 1, 3));

        // when
        String html = renderedForm(form, Map.of());

        // then
        assertThat(html).contains("id=\"markup\" name=\"markup\" type=\"text\" value=\"1,10\"")
                .contains("id=\"minWarehouseQty\" name=\"minWarehouseQty\" type=\"text\" value=\"3\"")
                .contains("id=\"minDistributorsQty\" name=\"minDistributorsQty\" type=\"text\" value=\"5\"")
                .contains("id=\"enabled\" name=\"enabled\" value=\"true\"").contains("checked=\"checked\"")
                .contains("List the products of this category on Allegro")
                .contains("Approved today: 14.");
        assertThat(html).doesNotContain("??");
    }

    @Test
    void aMissingConditionIsShownAtTheGroupOfThresholdsTheSummaryLinksTo() {
        // when
        String html = renderedForm(new MarketplaceDefinitionForm(),
                Map.of("conditions", "catalog.category.marketplace.conditions.required"));

        // then
        assertThat(html).contains("href=\"#conditions\"").contains("id=\"conditions\"")
                .contains("Give a condition: a warehouse quantity or a distributor threshold");
    }

    /** RF-31: a threshold of 0 in total means no total at all; "0 pcs. in total" read as if nothing were in stock. */
    @Test
    void aZeroTotalFromDistributorsIsLeftOutOfTheConditions() {
        // given
        MarketplaceDefinitionRow allegro = row("Allegro", definition("Allegro", 1.1, 0, 2, 1, 0, 0), 0, true);

        // when
        String html = renderedList(List.of(allegro), List.of());

        // then
        assertThat(html).contains("From distributors: 2 at each of at least 1 (0 local)").doesNotContain("in total");
    }
}
