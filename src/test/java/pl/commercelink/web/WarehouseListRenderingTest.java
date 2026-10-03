package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.servlet.support.csrf.CsrfRequestDataValueProcessor;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.support.RequestContext;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.context.webmvc.SpringWebMvcThymeleafRequestContext;
import org.thymeleaf.spring6.naming.SpringContextVariableNames;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;
import pl.commercelink.warehouse.builtin.WarehouseItemRow;
import pl.commercelink.warehouse.builtin.WarehouseListQuery;
import pl.commercelink.warehouse.builtin.WarehousePageModel;
import pl.commercelink.warehouse.builtin.WarehousePageModel.BulkActionView;
import pl.commercelink.warehouse.builtin.WarehousePageModel.Chip;
import pl.commercelink.warehouse.builtin.WarehousePageModel.EmptyState;
import pl.commercelink.warehouse.builtin.WarehousePageModel.Option;
import pl.commercelink.warehouse.builtin.WarehousePageModel.SortHeader;
import pl.commercelink.warehouse.builtin.WarehousePageModel.Tile;
import pl.commercelink.web.orders.Pagination;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Renders the results fragment of the warehouse list through real Thymeleaf with English messages. */
class WarehouseListRenderingTest {

    private static WarehouseItemRow row(String id, String status, boolean selectable) {
        return row(id, status, selectable, "/dashboard/deliveries/details?deliveryId=d1", "d1", "Acme Polska");
    }

    private static WarehouseItemRow row(String id, String status, boolean selectable, String deliveryHref, String deliveryNumber,
                                        String supplier) {
        return new WarehouseItemRow(id, "/dashboard/warehouse/items/" + id, "RTX " + id, "EAN 590 · GV-1", "Damaged", "is-bad",
                "comment " + id, "GPU", false, 3, "1 243,00", "gross 1 528,89", null, null, deliveryHref,
                deliveryNumber, supplier, "S/N 1", status, status, "is-ok", selectable, "Acme");
    }

    private static String deliveryCell(String html) {
        Matcher cell = Pattern.compile("<td class=\"is-secondary-column\" data-label=\"Delivery\">.*?</td>", Pattern.DOTALL).matcher(html);
        assertThat(cell.find()).isTrue();
        return cell.group();
    }

    private static WarehousePageModel model(List<WarehouseItemRow> rows, boolean storeEmpty) {
        Map<WarehouseListQuery.Sort, SortHeader> headers = new EnumMap<>(WarehouseListQuery.Sort.class);
        for (WarehouseListQuery.Sort s : WarehouseListQuery.Sort.values()) {
            headers.put(s, new SortHeader("/dashboard/warehouse?sort=" + s.param(), "none"));
        }
        BulkActionView reserve = new BulkActionView("reserve", "/dashboard/warehouse/markAsReserved", "Reserve", "Delivered",
                true, false, false, false, "Only for: In stock", null, null, "Reserve");
        BulkActionView destroy = new BulkActionView("destroy", "/dashboard/warehouse/markAsDestroyed", "Destroy",
                "Delivered InRMA InExternalService", true, false, false, true, "Only for: …", null, null, "Destroy");
        return new WarehousePageModel(WarehouseListQuery.parse(new LinkedMultiValueMap<>(), false), true, false,
                List.of(new Tile("In stock", "3 pcs", "in 1 items", "/dashboard/warehouse", true),
                        new Tile("To receive", "2 pcs", "in allocation and ordered from suppliers", "/dashboard/warehouse?statuses=Allocation&statuses=Ordered", false),
                        new Tile("Reserved", "1 pcs", "in 1 items", "/dashboard/warehouse?statuses=Reserved", false),
                        new Tile("Needs attention", "0 pcs", "in claim and in service", "/dashboard/warehouse?statuses=InRMA&statuses=InExternalService", false)),
                List.of(new Option("Delivered", "In stock", 1, true, "/dashboard/warehouse?statuses=all")), "In stock",
                List.of(new Option("GPU", "GPU", 1, false, "/dashboard/warehouse?categories=GPU"),
                        new Option("none", "No category", 0, false, "/x")), "All",
                List.of(new Chip("Status: In stock", "/dashboard/warehouse?statuses=all", "Remove filter: Status: In stock")),
                "Items: 2 · 6 pcs", headers, rows, Pagination.of(1, rows.size(), 50, n -> "/p" + n),
                rows.isEmpty() ? new EmptyState(storeEmpty ? "The warehouse is empty." : "Nothing matches the filters.",
                        "Clear filters", "/dashboard/warehouse") : null,
                storeEmpty, 1, 7, List.of(reserve), destroy, List.of(new Option("Theft", "Theft", 0, false, null)));
    }

    private static String render(WarehousePageModel page) {
        Context context = new Context();
        context.setVariable("page", page);
        return EnglishFragmentTemplateEngine.create().process("<div th:replace=\"~{warehouse :: results}\"></div>", context);
    }

    @Test
    void rowsMenusChipsAndSelectionAreRendered() {
        // given
        WarehousePageModel page = model(List.of(row("a1", "Delivered", true), row("b2", "Ordered", false)), false);

        // when
        String html = render(page);

        // then
        assertThat(html).doesNotContain("??");
        assertThat(html).contains("data-cl-list-results").contains("data-cl-list-fragment=\"/dashboard/warehouse/list\"");
        assertThat(html).contains("class=\"cl-stat is-link\"").contains("aria-current=\"true\"");
        assertThat(html).contains("data-cl-filter-menu=\"statuses\"").contains("data-cl-filter-menu=\"categories\"").contains("No category");
        assertThat(html).contains("Status: In stock").contains("Items: 2 · 6 pcs");
        assertThat(html).contains("cl-table is-warehouse").contains("data-cl-select-table=\"true\"");
        assertThat(html).contains("value=\"a1\"").doesNotContain("value=\"b2\"");
        assertThat(html).contains("data-status=\"Delivered\"").contains("data-qty=\"3\"").contains("data-source=\"Acme\"");
        assertThat(html).contains("data-cl-action-path=\"/dashboard/warehouse/markAsReserved\"").contains("data-cl-action-for=\"Delivered\"");
        assertThat(html).contains("Destroyed items: 7").contains("is-secondary-column");
        assertThat(html).contains("id=\"warehouse-bulk-form\"").contains("id=\"cl-quantity-dialog\"");
    }

    @Test
    void everyTileIsALinkAndTheResultsLineIsVisibleWithoutChips() {
        // given
        WarehousePageModel withChips = model(List.of(row("a1", "Delivered", true)), false);
        WarehousePageModel noChips = new WarehousePageModel(withChips.query(), true, false, withChips.tiles(), withChips.statusOptions(),
                "All", withChips.categoryOptions(), "All", List.of(), "Items: 2 · 6 pcs · Net value: 1.00 PLN · gross: 1.23 PLN",
                withChips.sortHeaders(), withChips.rows(), withChips.pagination(), null, false, 0, 0,
                withChips.menuActions(), withChips.destroyAction(), withChips.destroyReasons());

        // when
        String html = render(noChips);

        // then
        assertThat(html).containsOnlyOnce("To receive");
        assertThat(Pattern.compile("<a class=\"cl-stat is-link\"").matcher(html).results().count()).isEqualTo(4);
        assertThat(html).doesNotContain("<li class=\"cl-stat\"");
        assertThat(html).contains("Net value: 1.00 PLN · gross: 1.23 PLN");
        assertThat(html).doesNotContain("cl-table-results cl-visually-hidden");
        assertThat(html).containsPattern("<p class=\"cl-table-results\" role=\"status\"");
        assertThat(html).contains("class=\"cl-list-meta is-warehouse\"");
    }

    @Test
    void categoryCellIsMarkedForHyphenation() {
        // given
        WarehousePageModel page = model(List.of(row("a1", "Delivered", true)), false);

        // when
        String html = render(page);

        // then
        assertThat(html).contains("<td class=\"is-hyphenated\" data-label=\"Category\">");
    }

    @Test
    void wholePageRendersPastTheResultsBlock() {
        // given
        Context context = new Context();
        context.setVariable("page", model(List.of(row("a1", "Delivered", true)), false));

        // when
        String html = EnglishFragmentTemplateEngine.create().process("warehouse", context);

        // then
        assertThat(html).doesNotContain("??").contains("id=\"cl-confirm-dialog\"").contains("/js/selection-actions.js");
    }

    @Test
    void emptyStoreRendersNoToolbarNoTable() {
        // given
        WarehousePageModel page = model(List.of(), true);

        // when
        String html = render(page);

        // then
        assertThat(html).contains("The warehouse is empty.").doesNotContain("<table").doesNotContain("data-cl-filter-menu");
    }

    @Test
    void noFilterMatchKeepsToolbarAndOffersClear() {
        // given
        WarehousePageModel page = model(List.of(), false);

        // when
        String html = render(page);

        // then
        assertThat(html).contains("Nothing matches the filters.").contains("data-cl-filter-menu=\"statuses\"").doesNotContain("<table");
    }

    /**
     * Renders like a real MVC view with Spring Security's CSRF processor in place, as it will be once CSRF is on for
     * /dashboard/** (PR app#217): every POST form with th:action gets the hidden _csrf field.
     */
    private static String renderWithCsrf(WarehousePageModel page) {
        MockServletContext servletContext = new MockServletContext();
        StaticWebApplicationContext applicationContext = new StaticWebApplicationContext();
        applicationContext.setServletContext(servletContext);
        applicationContext.registerSingleton("requestDataValueProcessor", CsrfRequestDataValueProcessor.class);
        applicationContext.refresh();
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, applicationContext);
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        request.setAttribute(CsrfToken.class.getName(), new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token-abc"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
        WebContext context = new WebContext(application.buildExchange(request, response), Locale.ENGLISH);
        context.setVariable("page", page);
        context.setVariable(SpringContextVariableNames.THYMELEAF_REQUEST_CONTEXT,
                new SpringWebMvcThymeleafRequestContext(new RequestContext(request, response, servletContext, null), request));
        return EnglishFragmentTemplateEngine.create().process("<div th:replace=\"~{warehouse :: results}\"></div>", context);
    }

    private static String formWithId(String html, String marker) {
        Matcher matcher = Pattern.compile("<form[^>]*" + Pattern.quote(marker) + "[^>]*>.*?</form>", Pattern.DOTALL).matcher(html);
        assertThat(matcher.find()).as(marker).isTrue();
        return matcher.group();
    }

    @Test
    void bothBulkPostFormsCarryTheCsrfTokenWhenCsrfIsOn() {
        // given
        WarehousePageModel page = model(List.of(row("a1", "Delivered", true)), false);

        // when
        String html = renderWithCsrf(page);

        // then
        assertThat(formWithId(html, "id=\"warehouse-bulk-form\"")).contains("name=\"_csrf\"").contains("value=\"test-token-abc\"");
        assertThat(formWithId(html, "data-cl-quantity-form")).contains("name=\"_csrf\"").contains("value=\"test-token-abc\"");
    }

    @Test
    void filterFormsStayFreeOfTheCsrfToken() {
        // given
        WarehousePageModel page = model(List.of(row("a1", "Delivered", true)), false);

        // when
        String html = renderWithCsrf(page);

        // then
        assertThat(formWithId(html, "role=\"search\"")).doesNotContain("_csrf");
    }

    @Test
    void statusMenuCarriesItsMarkerSoUntickingEverythingMeansAll() {
        // given
        WarehousePageModel page = model(List.of(row("a1", "Delivered", true)), false);

        // when
        String html = render(page);

        // then
        Matcher statusMenu = Pattern.compile("data-cl-filter-menu=\"statuses\".*?</details>", Pattern.DOTALL).matcher(html);
        assertThat(statusMenu.find()).isTrue();
        assertThat(statusMenu.group()).contains("<input type=\"hidden\" name=\"statusesMenu\" value=\"1\"/>");
        assertThat(html).containsOnlyOnce("name=\"statusesMenu\"").doesNotContain("statusesMenu=");
    }

    @Test
    void deliveryCellShowsTheSupplierUnderTheDeliveryLinkAndTheSerialNumberLast() {
        // given
        WarehousePageModel page = model(List.of(row("a1", "Delivered", true)), false);

        // when
        String cell = deliveryCell(render(page));

        // then
        assertThat(cell).contains("<a href=\"/dashboard/deliveries/details?deliveryId=d1\">d1</a>");
        assertThat(cell.indexOf("Acme Polska")).isGreaterThan(cell.indexOf(">d1</a>")).isLessThan(cell.indexOf("S/N 1"));
    }

    @Test
    void deliveryThatIsNotADeliveryOfTheStoreIsADashWithoutALink() {
        // given
        WarehousePageModel page = model(List.of(row("a1", "Delivered", true, null, null, null)), false);

        // when
        String cell = deliveryCell(render(page));

        // then
        assertThat(cell).doesNotContain("<a ").contains("—").contains("S/N 1");
    }

    @Test
    void destroyReasonStartsWithNoChoiceSoTheOperatorHasToPickOne() {
        // given
        WarehousePageModel page = model(List.of(row("a1", "Delivered", true)), false);

        // when
        String html = render(page);

        // then
        Matcher select = Pattern.compile("<select[^>]*id=\"destroy-reason\".*?</select>", Pattern.DOTALL).matcher(html);
        assertThat(select.find()).isTrue();
        assertThat(select.group()).contains("required")
                .containsPattern("<select[^>]*>\\s*<option value=\"\" selected>Choose a reason</option>\\s*<option value=\"Theft\">");
        assertThat(html).contains("Choose a reason from the list.");
    }

    @Test
    void categoryMenuScrollsItsChecksAndKeepsTheApplyButtonBelowThem() {
        // given
        WarehousePageModel page = model(List.of(row("a1", "Delivered", true)), false);

        // when
        String html = render(page);

        // then
        Matcher menu = Pattern.compile("data-cl-filter-menu=\"categories\".*?</details>", Pattern.DOTALL).matcher(html);
        assertThat(menu.find()).isTrue();
        String categories = menu.group();
        assertThat(categories).containsPattern("<div class=\"cl-filter-menu-scroll\">\\s*<div class=\"cl-filter-menu-group\"");
        assertThat(categories.indexOf("cl-filter-menu-actions")).isGreaterThan(categories.lastIndexOf("cl-filter-menu-check"));
    }
}
