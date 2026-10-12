package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import pl.commercelink.products.StoreCategories;
import pl.commercelink.warehouse.builtin.WarehouseItemEditForm;
import pl.commercelink.warehouse.builtin.WarehouseItemPage;
import pl.commercelink.warehouse.builtin.WarehouseItemPage.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseItemPageRenderingTest {

    private static WarehouseItemEditForm form(String status) {
        WarehouseItemEditForm form = new WarehouseItemEditForm();
        form.setName("Samsung 990 PRO");
        form.setCategory("SSD drives");
        form.setCondition("Sealed");
        form.setComment("Checked");
        form.setStatus(status);
        form.setQty("3");
        form.setCost("689,00");
        form.setTax("23");
        form.setBack("/dashboard/warehouse?q=ssd");
        form.setVersion("5");
        return form;
    }

    private static WarehouseItemPage page(Mode mode) {
        String status = mode == Mode.NEW ? "New" : mode == Mode.DESTROYED ? "Destroyed" : "Delivered";
        return new WarehouseItemPage("7c1e5a20-1111", "Samsung 990 PRO", "In stock", "is-ok", "8806094215038", "MZ-V9P2T0BW",
                "7c1e5a20", "/dashboard/warehouse?q=ssd", "Warehouse", mode, mode == Mode.DESTROYED ? "Destroyed on 06.10.2026" : null,
                mode == Mode.NEW ? List.of() : List.of(new Fact("Quantity", "3 pcs", true)), mode == Mode.NEW ? null : "2 067,00 PLN",
                new Origin("/dashboard/deliveries/details?deliveryId=d-1", "3F2A9C1E", null,
                        mode == Mode.NEW ? null : "/dashboard/warehouse-documents/delivery-mfn-history?deliveryId=d-1&mfn=MZ&from=item&itemId=7c1e5a20-1111",
                        List.of(new Link("S1", "/dashboard/item/history?serialNo=S1"), new Link("S2", "/dashboard/item/history?serialNo=S2"))),
                form(status), Map.of(), null, List.of(new StoreCategories.Group("Computers", List.of("SSD drives"))), null,
                List.of(new Choice("Sealed", "Sealed", null, "is-ok"), new Choice("Damaged", "Damaged", null, "is-bad")),
                mode == Mode.NEW ? List.of(new Choice("New", "New", "Waits", null), new Choice("Allocation", "In allocation", "Pool", null)) : List.of(),
                mode == Mode.DESTROYED ? new Choice("Damaged", "Damaged", null, "is-bad") : null,
                mode == Mode.NEW ? new Delete("/dashboard/warehouse/items/7c1e5a20-1111/delete", "Delete item Samsung 990 PRO?", "Gone.", "Delete item") : null);
    }

    private static String render(WarehouseItemPage page) {
        Context context = new Context();
        context.setVariable("page", page);
        return EnglishFragmentTemplateEngine.create().process("<div th:replace=\"~{warehouse-item :: body}\"></div>", context);
    }

    @Test
    void itemInStockShowsTheFormFactsAndOrigin() {
        // when
        String html = render(page(Mode.EDIT));

        // then
        assertThat(html).doesNotContain("??").doesNotContain("confirmSave");
        assertThat(html).contains("<h1 class=\"cl-page-title\">Samsung 990 PRO</h1>");
        assertThat(html).contains("action=\"/dashboard/warehouse/items/7c1e5a20-1111\"").contains("name=\"back\" value=\"/dashboard/warehouse?q=ssd\"");
        assertThat(html).contains("name=\"version\" value=\"5\"");
        assertThat(html).contains("name=\"name\"").contains("name=\"condition\"").contains("name=\"serialNo\"").contains("name=\"unitSystemCost\"");
        assertThat(html).doesNotContain("name=\"qty\"").doesNotContain("name=\"status\"").doesNotContain("data-cl-confirm");
        assertThat(html).contains("2 067,00 PLN").contains("/dashboard/item/history?serialNo=S1").contains("/dashboard/item/history?serialNo=S2");
        assertThat(html).contains("delivery-mfn-history?deliveryId=d-1&amp;mfn=MZ&amp;from=item");
    }

    @Test
    void newItemHasEveryFieldAndADeleteConfirmation() {
        // when
        String html = render(page(Mode.NEW));

        // then
        assertThat(html).contains("name=\"qty\"").contains("name=\"cost\"").contains("name=\"tax\"").contains("name=\"status\"")
                .contains("name=\"ean\"").contains("name=\"manufacturerCode\"").contains("name=\"deliveryId\"");
        assertThat(html).contains("data-cl-confirm").contains("href=\"/dashboard/warehouse/items/7c1e5a20-1111/delete\"")
                .contains("id=\"cl-confirm-dialog\"");
        assertThat(html).doesNotContain("item-cost-title");
    }

    @Test
    void destroyedItemOffersOnlyTheNameAndComment() {
        // when
        String html = render(page(Mode.DESTROYED));

        // then
        assertThat(html).contains("cl-card is-status").contains("Destroyed on 06.10.2026").contains("you can change its name and comment");
        assertThat(html).contains(">Name and comment</h2>");
        assertThat(html).contains("name=\"comment\"").contains("name=\"name\"").doesNotContain("name=\"condition\"")
                .doesNotContain("name=\"category\"").doesNotContain("name=\"unitSystemCost\"")
                .doesNotContain("name=\"serialNo\"").doesNotContain("data-cl-confirm");
        assertThat(html).contains("cl-status is-bad");
    }

    @Test
    void commentIsAnOptionalFieldOfTheRecordsGroup() {
        // when
        String html = render(page(Mode.EDIT));

        // then
        String records = html.substring(html.indexOf(">Records</legend>"));
        records = records.substring(0, records.indexOf("</fieldset>"));
        assertThat(records).contains("name=\"comment\"");
        String commentLabel = records.substring(records.indexOf("for=\"comment\""), records.indexOf("<textarea"));
        assertThat(commentLabel).contains("cl-optional");
    }

    @Test
    void destroyedCommentSitsInsideTheCardPadding() {
        // when
        String html = render(page(Mode.DESTROYED));

        // then
        String form = html.substring(html.indexOf("id=\"item-form-title\""), html.indexOf("name=\"comment\""));
        assertThat(form).contains("<fieldset class=\"cl-fieldset\"");
        assertThat(form).doesNotContain("<legend");
    }

    @Test
    void itemNumberCopyButtonDrawsOneFocusRingAroundTheCodeAndItsIcon() {
        // when
        String html = render(page(Mode.EDIT));

        // then
        // the focus ring is drawn on each direct child, so the icon belongs inside the code (as on the order items)
        String button = html.substring(html.indexOf("<button type=\"button\" class=\"cl-copy-inline\""));
        button = button.substring(0, button.indexOf("</button>"));
        assertThat(button).containsPattern("<span class=\"cl-copy-code\"><span>7c1e5a20</span><span class=\"cl-copy-icon\" aria-hidden=\"true\">");
    }

    @Test
    void formWithoutAVersionPostsNone() {
        // given
        WarehouseItemPage page = page(Mode.EDIT);
        page.form().setVersion(null);

        // when
        String html = render(page);

        // then
        assertThat(html).doesNotContain("name=\"version\"");
    }

    @Test
    void errorsAreSummarisedAndMarkedOnTheirFields() {
        // given
        WarehouseItemPage page = page(Mode.NEW);
        WarehouseItemPage invalid = new WarehouseItemPage(page.itemId(), page.name(), page.statusLabel(), page.statusTone(), page.ean(), page.mfn(),
                page.shortId(), page.backHref(), page.backLabel(), page.mode(), page.destroyedTitle(), page.facts(), page.totalNet(), page.origin(),
                page.form(), Map.of("name", "warehouse.item.error.name", "status", "warehouse.item.error.status"), "Changed meanwhile",
                page.categoryGroups(), page.categoryOutsideCatalog(), page.conditions(), page.statuses(), page.readonlyCondition(), page.delete());

        // when
        String html = render(invalid);

        // then
        assertThat(html).doesNotContain("??");
        assertThat(html).contains("id=\"warehouse-item-errors\"").contains("href=\"#name\"").contains("href=\"#status\"");
        assertThat(html).contains("id=\"name-error\"").contains("aria-invalid=\"true\"").contains("Changed meanwhile");
    }

    @Test
    void storedCategoryOutsideTheCatalogStaysAnOptionWhileTheTypedChoiceIsSelected() {
        // given
        WarehouseItemPage page = page(Mode.EDIT);
        WarehouseItemPage outside = new WarehouseItemPage(page.itemId(), page.name(), page.statusLabel(), page.statusTone(), page.ean(), page.mfn(),
                page.shortId(), page.backHref(), page.backLabel(), page.mode(), page.destroyedTitle(), page.facts(), page.totalNet(), page.origin(),
                page.form(), Map.of("name", "warehouse.item.error.name"), null,
                page.categoryGroups(), "Old category", page.conditions(), page.statuses(), page.readonlyCondition(), page.delete());

        // when
        String html = render(outside);

        // then
        assertThat(html).contains("<option value=\"Old category\">Old category</option>");
        assertThat(html).contains("<option value=\"SSD drives\" selected=\"selected\">SSD drives</option>");
    }

    @Test
    void storedCategoryOutsideTheCatalogIsSelectedWhileItIsTheChoice() {
        // given
        WarehouseItemPage page = page(Mode.EDIT);
        page.form().setCategory("Old category");
        WarehouseItemPage outside = new WarehouseItemPage(page.itemId(), page.name(), page.statusLabel(), page.statusTone(), page.ean(), page.mfn(),
                page.shortId(), page.backHref(), page.backLabel(), page.mode(), page.destroyedTitle(), page.facts(), page.totalNet(), page.origin(),
                page.form(), Map.of(), null,
                page.categoryGroups(), "Old category", page.conditions(), page.statuses(), page.readonlyCondition(), page.delete());

        // when
        String html = render(outside);

        // then
        assertThat(html).contains("<option value=\"Old category\" selected=\"selected\">Old category</option>");
        assertThat(html).contains("<option value=\"SSD drives\">SSD drives</option>");
    }

    @Test
    void oldTemplateIsGone() {
        // when / then
        assertThat(Files.exists(Path.of("src/main/resources/templates/warehouseItem.html"))).isFalse();
        assertThat(Files.exists(Path.of("src/main/resources/templates/warehouse-item.html"))).isTrue();
    }

    private static WarehouseItemPage with(WarehouseItemPage p, Origin origin, Map<String, String> errors) {
        return new WarehouseItemPage(p.itemId(), p.name(), p.statusLabel(), p.statusTone(), p.ean(), p.mfn(), p.shortId(), p.backHref(),
                p.backLabel(), p.mode(), p.destroyedTitle(), p.facts(), p.totalNet(), origin, p.form(), errors, p.errorMessage(),
                p.categoryGroups(), p.categoryOutsideCatalog(), p.conditions(), p.statuses(), p.readonlyCondition(), p.delete());
    }

    @Test
    void itemWithoutCategoryHasTheEmptyChoiceSelected() {
        // given
        WarehouseItemPage page = page(Mode.EDIT);
        page.form().setCategory("");

        // when
        String html = render(page);

        // then
        String select = html.substring(html.indexOf("<select class=\"cl-select\" id=\"category\""));
        select = select.substring(0, select.indexOf("</select>"));
        assertThat(select).contains("<option value=\"\" selected=\"selected\">No category</option>");
        assertThat(select).doesNotContain("value=\"SSD drives\" selected");
    }

    @Test
    void itemWithACategoryKeepsItSelectedNextToTheEmptyChoice() {
        // when
        String html = render(page(Mode.EDIT));

        // then
        assertThat(html).contains("<option value=\"\">No category</option>").contains("<option value=\"SSD drives\" selected=\"selected\">SSD drives</option>");
    }

    @Test
    void deliveryNumberIsFollowedByItsSupplier() {
        // given
        WarehouseItemPage page = with(page(Mode.EDIT), new Origin("/dashboard/deliveries/details?deliveryId=d-1", "3F2A9C1E", "Action S.A.", null, List.of()), Map.of());

        // when
        String html = render(page);

        // then
        assertThat(html).containsPattern("<a href=\"/dashboard/deliveries/details\\?deliveryId=d-1\">3F2A9C1E</a>\\s*<span[^>]*>· Action S.A.</span>");
    }

    @Test
    void commentErrorIsTiedToTheField() {
        // given
        WarehouseItemPage page = with(page(Mode.EDIT), page(Mode.EDIT).origin(), Map.of("comment", "warehouse.item.error.comment"));

        // when
        String html = render(page);

        // then
        String textarea = html.substring(html.indexOf("<textarea"), html.indexOf("</textarea>"));
        assertThat(textarea).contains("aria-invalid=\"true\"").contains("aria-describedby=\"comment-error\"");
        assertThat(html).contains("class=\"cl-field-error\" id=\"comment-error\"");
    }

    @Test
    void statusErrorIsTiedToTheChoiceGroup() {
        // given
        WarehouseItemPage page = with(page(Mode.NEW), page(Mode.NEW).origin(), Map.of("status", "warehouse.item.error.status"));

        // when
        String html = render(page);

        // then
        assertThat(html).contains("<fieldset class=\"cl-choice-group\" id=\"status\" aria-describedby=\"status-error\" aria-invalid=\"true\">");
        assertThat(html).contains("class=\"cl-field-error\" id=\"status-error\"");
    }

    @Test
    void fieldsWithoutErrorsCarryNoErrorAttributes() {
        // when
        String html = render(page(Mode.NEW));

        // then
        assertThat(html).doesNotContain("aria-describedby=\"comment-error\"").doesNotContain("status-error");
    }

    @Test
    void destroyedCommentHasAVisibleOptionalLabel() {
        // when
        String html = render(page(Mode.DESTROYED));

        // then
        String commentLabel = html.substring(html.indexOf("for=\"comment\""), html.indexOf("<textarea"));
        assertThat(commentLabel).doesNotContain("cl-visually-hidden").contains("cl-optional");
    }
}
