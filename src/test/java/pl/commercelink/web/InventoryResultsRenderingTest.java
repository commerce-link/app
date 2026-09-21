package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import pl.commercelink.inventory.search.CodeMatch;
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
        context.setVariable("manageSuppliersUrl", "/dashboard/store/suppliers");
        context.setVariable("warehouseUrl", "/dashboard/warehouse");
        return context;
    }

    private InventorySearchResult.Found found() {
        return found(true);
    }

    private InventorySearchResult.Found found(boolean warehouseChecked) {
        return new InventorySearchResult.Found(MatchedBy.EAN, PRODUCT,
                List.of(offer("manual-nowak", "Hurtownia Nowak", ConnectionMode.MANUAL, "5903000000000", "910-006559",
                                389.0, 478.47, 0, 0, true, 3, 12, true, false, CodeMatch.EAN_DIFFERS),
                        offer("AB", "AB", ConnectionMode.GLOBAL, "5901234123457", "910-006559",
                                405.0, 498.15, 18.0, 1000.0, true, 2, 80, false, false, CodeMatch.SAME),
                        offer("HurtPol", "Hurt-Pol", ConnectionMode.MANUAL, "5900000000099", "MXM3S-BOX",
                                412.0, 506.76, 0, 0, true, 5, 4, false, false, CodeMatch.BOTH_DIFFER),
                        offer("Elko", "Elko", ConnectionMode.GLOBAL, "5901234123457", "910-OTHER",
                                379.0, 466.17, 0, 0, false, 0, 0, false, false, CodeMatch.CODE_DIFFERS)),
                List.of(new WarehouseRow("5901234123457", "910-006559", 288.78, 355.2, 3, false, ItemCondition.Sealed, CodeMatch.SAME),
                        new WarehouseRow("5901234123457", "910-006559", 283.74, 349.0, 2, true, ItemCondition.Damaged, CodeMatch.SAME)),
                new PriceSummary(478.47, 478.47, 498.15, 3, 96, 3, 2, false),
                warehouseChecked);
    }

    private static OfferRow offer(String supplier, String label, ConnectionMode mode, String ean, String code,
                                  double net, double gross, double delivery, double freeFrom, boolean deliveryKnown,
                                  int leadDays, int qty, boolean cheapest, boolean sharesLabel, CodeMatch match) {
        return new OfferRow(supplier, label, mode, ean, code, net, gross, delivery, freeFrom, deliveryKnown,
                leadDays, qty, cheapest, sharesLabel, match);
    }

    @Test
    void rendersWarehouseAndSuppliersAsRowsOfOneSourceList() {
        // when
        String html = engine.process(RESULTS, context(found(), true));

        // then
        assertThat(html).doesNotContain("??");
        assertThat(html).contains("data-inventory-fragment=\"results\"").contains("data-announce=\"Offers found: 4\"");
        assertThat(html).contains("Logitech MX Master 3S").contains("Matched by: EAN");
        assertThat(html.split("data-inventory-sortable", -1)).hasSize(2);
        assertThat(html.split("<tbody", -1)).hasSize(2);
        assertThat(html.indexOf("is-warehouse")).isLessThan(html.indexOf("Hurtownia Nowak"));
        assertThat(html).contains("data-sort-price=\"288.78\"").contains("data-sort-qty=\"3\"").doesNotContain("data-sort-source");
        assertThat(html).contains("Source</th>").contains("Purchase price").contains(">Available<");
        assertThat(html).doesNotContain("Your warehouse").doesNotContain("purchase cost")
                .doesNotContain("Manufacturer code").doesNotContain("%");
        assertThat(html).contains("in transit").contains("Damaged").contains("288,78 PLN").contains("355,20 gross").contains("80 pcs")
                .contains("cl-status is-neutral\">none<");
        assertThat(html).contains("<colgroup>").doesNotContain("cl-inv-figure");
        assertThat(html).contains("global").contains("manual");
    }

    /** Two offers a few zloty apart are decided by shipping, so the table has to show it next to the price. */
    @Test
    void everyOfferShowsNetAndGrossPriceDeliveryTotalPerUnitAndLeadTime() {
        // when
        String html = engine.process(RESULTS, context(found(), true));

        // then
        assertThat(html).contains(">Delivery<").contains(">Total / pc<").contains(">Time<");
        assertThat(html).contains("389,00 PLN").contains("478,47 gross")
                .contains("18,00 PLN").contains("free above 1 000 PLN").contains("free")
                .contains("423,00 PLN")
                .contains("3 days").contains("2 days");
        assertThat(html).contains("data-sort-delivery=\"18.0\"").contains("data-sort-total=\"423.0\"")
                .contains("data-sort-lead=\"2\"");
        // the warehouse is already here: nothing to ship and nothing to wait for
        assertThat(html).contains("already yours").contains("on hand");
    }

    /**
     * The registry answers for an unknown supplier with a placeholder policy; printing that as a cost would
     * be inventing a number the operator could act on.
     */
    @Test
    void deliveryAndLeadTimeStayBlankForASupplierWithoutKnownTerms() {
        // when
        String html = engine.process(RESULTS, context(found(), true));

        // then
        String elko = html.substring(html.indexOf("Elko"));
        assertThat(elko).contains("no data");
        assertThat(html).contains("data-sort-delivery=\"999999999\"").contains("data-sort-lead=\"999999999\"");
    }

    @Test
    void cheapestOfferCarriesACheckWithScreenReaderTextButNoVisibleLabel() {
        // when
        String html = engine.process(RESULTS, context(found(), true));

        // then
        assertThat(html.split("is-cheapest", -1)).hasSize(2);
        assertThat(html.split("cl-inv-check", -1)).hasSize(2);
        assertThat(html).contains("cl-visually-hidden\">lowest delivered cost<").doesNotContain("Cheapest");
    }

    @Test
    void summaryLineReplacesTheFiguresAndOmitsTheSupplierName() {
        // when
        String html = engine.process(RESULTS, context(found(), true));

        // then
        String summary = html.substring(html.indexOf("data-inventory-price-summary"), html.indexOf("data-inventory-offers"));
        assertThat(summary).contains("From").contains("478,47 PLN").contains("at suppliers").contains("96 pcs")
                .contains("3 pcs").contains("in the warehouse").contains("2 pcs").contains("in transit")
                .doesNotContain("Nowak").doesNotContain("median");
    }

    /** Shipping only earns a second figure in the headline when it actually moves the number. */
    @Test
    void summaryAddsTheDeliveredPriceOnlyWhenDeliveryCostsAnything() {
        // given
        InventorySearchResult.Found shipped = new InventorySearchResult.Found(MatchedBy.EAN, PRODUCT, List.of(), List.of(),
                new PriceSummary(478.47, 500.61, 0, 1, 10, 0, 0, false), true);

        // when
        String withShipping = engine.process(RESULTS, context(shipped, true));
        String withoutShipping = engine.process(RESULTS, context(found(), true));

        // then
        assertThat(withShipping).contains("with delivery").contains("500,61 PLN");
        assertThat(withoutShipping).doesNotContain("with delivery");
    }

    /**
     * The headline figure names one offer. When that offer was matched on different codes the caveat has to
     * travel with it, or the number is read as a firm price.
     */
    @Test
    void summaryWarnsWhenTheCheapestOfferIsADoubtfulMatch()  {
        // given
        InventorySearchResult.Found doubtful = new InventorySearchResult.Found(MatchedBy.EAN, PRODUCT, List.of(), List.of(),
                new PriceSummary(290.03, 290.03, 0, 1, 50, 0, 0, true), true);

        // when
        String warned = engine.process(RESULTS, context(doubtful, true));
        String plain = engine.process(RESULTS, context(found(), true));

        // then
        assertThat(warned).contains("cl-inv-summary-warning").contains("matched on a different EAN and code");
        assertThat(plain).doesNotContain("cl-inv-summary-warning");
    }

    /** Two connections of one supplier render the same name; then only the codes tell the rows apart. */
    @Test
    void offersSharingALabelPrintTheirOwnCodes() {
        // given
        InventorySearchResult.Found twins = new InventorySearchResult.Found(MatchedBy.EAN, PRODUCT,
                List.of(offer("AcmeB", "AcmeB", ConnectionMode.OWN, "5901234123457", "910-006559",
                                290.03, 356.74, 0, 0, true, 2, 50, true, true, CodeMatch.SAME),
                        offer("AcmeB-k7f3a9c2", "AcmeB", ConnectionMode.OWN, "5901234123457", "910-006559",
                                393.47, 483.97, 0, 0, true, 2, 10, false, true, CodeMatch.SAME)),
                List.of(), new PriceSummary(356.74, 356.74, 0, 2, 60, 0, 0, false), true);

        // when
        String html = engine.process(RESULTS, context(twins, true));
        String plain = engine.process(RESULTS, context(found(), true));

        // then
        assertThat(html.split("Codes of this offer:", -1)).hasSize(3);
        assertThat(plain).doesNotContain("Codes of this offer:");
    }

    @Test
    void summaryShowsTheMedianFromFourOffersAndNoPriceWhenNothingIsInStock() {
        // given
        InventorySearchResult.Found withMedian = new InventorySearchResult.Found(MatchedBy.EAN, PRODUCT, List.of(), List.of(),
                new PriceSummary(389.0, 389.0, 410.5, 4, 40, 0, 0, false), true);
        InventorySearchResult.Found withoutStock = new InventorySearchResult.Found(MatchedBy.EAN, PRODUCT, List.of(), List.of(),
                new PriceSummary(0, 0, 0, 0, 0, 0, 0, false), true);

        // when
        String median = engine.process(RESULTS, context(withMedian, true));
        String empty = engine.process(RESULTS, context(withoutStock, true));

        // then
        assertThat(median).contains("median").contains("410,50 PLN");
        assertThat(empty).contains("No supplier offers in stock").doesNotContain("From").doesNotContain("at suppliers");
    }

    @Test
    void codeNotesAppearOnlyForRowsWhoseCodesDiffer() {
        // when
        String html = engine.process(RESULTS, context(found(), true));

        // then
        assertThat(html).contains("Source EAN:").contains("5903000000000")
                .contains("Source code:").contains("910-OTHER")
                .contains("Different EAN and code at source:").contains("5900000000099").contains("MXM3S-BOX")
                .contains("check it is the same product");
        assertThat(html.split("cl-inv-code-info", -1)).hasSize(3);
        assertThat(html.split("cl-inv-code-warning", -1)).hasSize(2);
        assertThat(html).doesNotContain(">5901234123457</code>");
    }

    @Test
    void globalSearchSummaryLeavesOutTheWarehouse() {
        // given
        Context context = context(found(true), true);
        context.setVariable("superAdmin", true);

        // when
        String html = engine.process(RESULTS, context);

        // then
        assertThat(html).doesNotContain("in the warehouse");
    }

    @Test
    void summaryLeavesOutTheWarehouseWhenTheBuiltInWarehouseWasNotQueried() {
        // when
        String html = engine.process(RESULTS, context(found(false), true));

        // then
        assertThat(html).doesNotContain("??").doesNotContain("in the warehouse");
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
                .contains("href=\"/dashboard/store/suppliers\"");
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
