package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import pl.commercelink.inventory.search.InventorySearchResult;
import pl.commercelink.inventory.search.MatchedBy;
import pl.commercelink.inventory.search.OfferRow;
import pl.commercelink.inventory.search.PriceSummary;
import pl.commercelink.inventory.search.ProductHeader;
import pl.commercelink.inventory.search.WarehouseRow;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.warehouse.api.ItemCondition;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryResultsRenderingTest {

    private static final String RESULTS = "<div th:replace=\"~{fragments/inventory-results :: results}\"></div>";
    private static final ProductHeader PRODUCT = new ProductHeader("Logitech MX Master 3S", "Logitech", "5901234123457", "910-006559");

    private final TemplateEngine engine = EnglishFragmentTemplateEngine.create();

    private Context context(Object result, boolean canManageSuppliers) {
        Context context = new Context();
        context.setVariable("result", result);
        context.setVariable("superAdmin", false);
        context.setVariable("canManageSuppliers", canManageSuppliers);
        context.setVariable("manageSuppliersUrl", "/dashboard/store/fulfilment");
        context.setVariable("warehouseUrl", "/dashboard/warehouse");
        return context;
    }

    private InventorySearchResult.Found found() {
        return found(true);
    }

    private InventorySearchResult.Found found(boolean warehouseChecked) {
        return new InventorySearchResult.Found(MatchedBy.EAN, PRODUCT,
                List.of(new OfferRow("Elko", "Elko", ConnectionMode.GLOBAL, "5901234123457", "910-006559", 389.0, 58, 3, true),
                        new OfferRow("AB", "AB", ConnectionMode.GLOBAL, "5901234123457", "910-006559", 405.0, 80, 1, false),
                        new OfferRow("manual-nowak", "Hurtownia Nowak", ConnectionMode.MANUAL, "5901234123457", "910-006559", 439.9, 0, 0, false)),
                List.of(new WarehouseRow("5901234123457", "910-006559", 355.2, 3, false, ItemCondition.Sealed),
                        new WarehouseRow("5901234123457", "910-006559", 349.0, 2, true, ItemCondition.Damaged)),
                new PriceSummary(389.0, "Elko", 405.0, 3, 138, 2, 3, 3, 2),
                warehouseChecked);
    }

    @Test
    void rendersTheFoundProductWithWarehouseGroupFirstAndOneCheapestSupplier() {
        // when
        String html = engine.process(RESULTS, context(found(), true));

        // then
        assertThat(html).doesNotContain("??");
        assertThat(html).contains("data-inventory-fragment=\"results\"").contains("data-announce=\"Offers found: 3\"");
        assertThat(html).contains("Logitech MX Master 3S").contains("Matched by: EAN");
        assertThat(html).contains("389,00 PLN").contains("405,00 PLN").contains("355,20 PLN")
                .contains("at 2 of 3").contains("+2 in transit");
        assertThat(html.indexOf("Your warehouse")).isLessThan(html.indexOf("data-inventory-sortable"));
        assertThat(html).doesNotContain(">Suppliers<").doesNotContain("PLN gross").doesNotContain("%");
        assertThat(html).contains("purchase cost").contains("in transit").contains("Damaged");
        assertThat(html.split("is-cheapest", -1)).hasSize(2);
        assertThat(html).contains("data-sort-price=\"405.0\"").contains("data-sort-delivery=\"3\"").contains("data-inventory-sortable");
        assertThat(html).contains("Gross price").contains("Manufacturer code").contains(">EAN<").contains("Delivery").contains("3 days").contains("1 day")
                .contains("58 pcs").contains("cl-status is-neutral\">none<");
        assertThat(html).doesNotContain("fa-sort").contains("<colgroup>");
        assertThat(html).contains("global").contains("manual");
    }

    @Test
    void omitsTheWarehouseFigureWhenTheBuiltInWarehouseWasNotQueried() {
        // when
        String html = engine.process(RESULTS, context(found(false), true));

        // then
        assertThat(html).doesNotContain("??");
        assertThat(html).doesNotContain("In warehouse").doesNotContain("+2 in transit");
    }

    @Test
    void rendersNotFoundWithTheQueryAndTips() {
        // when
        String html = engine.process(RESULTS, context(new InventorySearchResult.NotFound("ABC-123-XX"), true));

        // then
        assertThat(html).doesNotContain("??");
        assertThat(html).contains("Nothing found for “ABC-123-XX”");
        assertThat(html).contains("Check the code for typos").contains("supplier you have not enabled");
    }

    @Test
    void knownProductWithoutOffersOffersTheManageShortcutOnlyToAdmin() {
        // given
        InventorySearchResult known = new InventorySearchResult.KnownWithoutOffers(MatchedBy.MFN, PRODUCT);

        // when
        String admin = engine.process(RESULTS, context(known, true));
        String user = engine.process(RESULTS, context(known, false));

        // then
        assertThat(admin).contains("No active supplier offers this product right now").contains("Enable more suppliers")
                .contains("href=\"/dashboard/store/fulfilment\"");
        assertThat(user).doesNotContain("Enable more suppliers");
        assertThat(admin + user).doesNotContain("??");
    }

    @Test
    void rendersTheValidationMessage() {
        // given
        Context context = context(null, true);
        context.setVariable("validationError", true);

        // when
        String html = engine.process(RESULTS, context);

        // then
        assertThat(html).contains("Enter at least 3 characters.").contains("data-inventory-results-heading").doesNotContain("??");
    }

    @Test
    void rendersTheEmptyState() {
        // when
        String html = engine.process("<div th:replace=\"~{fragments/inventory-results :: emptyState}\"></div>", context(null, true));

        // then
        assertThat(html).contains("Check the price and availability of a product").contains("Enter an EAN").doesNotContain("??");
    }
}
