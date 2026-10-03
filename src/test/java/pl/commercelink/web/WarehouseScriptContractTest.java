package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseScriptContractTest {

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    @Test
    void selectionActionsScriptKeepsItsContractAndNeverAlerts() throws Exception {
        // given
        String js = read("src/main/resources/static/js/selection-actions.js");

        // when / then
        assertThat(js).contains("data-cl-action-path").contains("data-cl-action-for").contains("data-cl-action-same-source")
                .contains("cl-quantity-dialog").contains("selectedItemIds").contains("quantities").contains("CL_confirmBulk")
                .contains("cl-list:swapped").contains("cl:selection-changed")
                .doesNotContain("alert(").doesNotContain("innerHTML").doesNotContain("setTimeout");
    }

    @Test
    void quantityDialogRowsShowTheUnitsWithTheirServerRenderedUnitWord() throws Exception {
        // given
        String js = read("src/main/resources/static/js/selection-actions.js");

        // when / then
        // "Na stanie · 3 szt.": the unit word comes from the selection row's data-template, the script knows no texts
        assertThat(js).contains("unitsText(box.getAttribute('data-qty'))")
                .doesNotContain("box.getAttribute('data-status-label') + ' · ' + box.getAttribute('data-qty')");
    }

    @Test
    void tableSelectAnnouncesEverySelectionChangeToTheSelectionActions() throws Exception {
        // given
        String js = read("src/main/resources/static/js/table-select.js");

        // when / then
        assertThat(js).contains("cl:selection-changed");
    }

    @Test
    void warehouseTemplatesHaveNoInlineScriptsOrBulma() throws Exception {
        for (String t : new String[]{"warehouse.html", "warehouse-item-new.html", "warehouse-restock.html", "fragments/warehouse-restock.html"}) {
            // given
            Path path = Path.of("src/main/resources/templates/" + t);
            if (!Files.exists(path)) continue;

            // when
            String html = read(path.toString());

            // then
            assertThat(html).as(t).doesNotContain("<script>").doesNotContain("onclick=").doesNotContain("style=")
                    .doesNotContain("class=\"button").doesNotContain("class=\"box").doesNotContain("class=\"modal")
                    .doesNotContain("th:utext");
        }
    }
}
