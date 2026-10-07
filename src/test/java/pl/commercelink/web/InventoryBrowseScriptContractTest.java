package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryBrowseScriptContractTest {

    private static final List<String> HOOKS = List.of(
            "data-browse-dialog-url", "data-browse-dialog-slot", "data-browse-add", "data-browse-add-selected",
            "data-browse-add-error", "data-browse-other-select", "data-browse-other-radio", "data-browse-other-field",
            "data-cl-select-row", "data-browse-building");

    @Test
    void everyHookOfTheScriptExistsInTheTemplates() throws IOException {
        // given
        String script = read("src/main/resources/static/js/inventory-browse.js");
        String templates = read("src/main/resources/templates/inventory.html")
                + read("src/main/resources/templates/fragments/inventory-browse.html");

        // when / then
        for (String hook : HOOKS) {
            String jsName = toDatasetSelector(hook);
            assertThat(script).as("script uses " + hook).containsAnyOf(hook, jsName);
            assertThat(templates).as("templates carry " + hook).contains(hook);
        }
    }

    @Test
    void onlyTheBrowsePageLoadsTheAddToCatalogScript() throws IOException {
        // when
        String browse = read("src/main/resources/templates/inventory.html");
        String prices = read("src/main/resources/templates/inventory-prices.html");

        // then
        assertThat(browse).contains("@{/js/inventory-browse.js}", "data-browse-dialog-url");
        assertThat(prices).doesNotContain("inventory-browse.js", "data-browse-dialog-url",
                "data-browse-dialog-slot", "data-browse-add");
    }

    @Test
    void longCategoryPathIsCollapsedByAScriptEveryVisitorLoadsAlsoAfterAListSwap() throws IOException {
        // when
        String script = read("src/main/resources/static/js/collapse-path.js");
        String page = read("src/main/resources/templates/inventory.html");
        String fragment = read("src/main/resources/templates/fragments/inventory-browse.html");

        // then
        assertThat(page).containsPattern("<script th:src=\"@\\{/js/collapse-path.js}\" defer></script>");
        assertThat(page).doesNotContain("th:if=\"${canManageSuppliers}\" th:src=\"@{/js/collapse-path.js}\"");
        assertThat(fragment).contains("data-cl-collapse-path", "data-cl-collapse-label=#{inventory.browse.path.expand}", "data-cl-path-crumb");
        assertThat(script).contains("[data-cl-collapse-path]", "[data-cl-path-crumb]", "data-cl-collapse-label", "MAX_VISIBLE = 4",
                "crumbs.slice(1, crumbs.length - 2)", "'cl-list:swapped'", "'DOMContentLoaded'", ".focus()");
        assertThat(script).as("the button disappears once clicked, so an expanded state is never heard").doesNotContain("aria-expanded");
    }

    @Test
    void inventoryPageLoadsTheMenuScriptForTheRowMenu() throws IOException {
        // when
        String page = read("src/main/resources/templates/inventory.html");

        // then
        assertThat(page).contains("@{/js/menu.js}");
    }

    @Test
    void addDialogClosesAndGuardsItsSubmitThroughTheSharedDialogScript() throws IOException {
        // when
        String page = read("src/main/resources/templates/inventory.html");
        String script = read("src/main/resources/static/js/inventory-browse.js");

        // then
        assertThat(page).contains("th:src=\"@{/js/dialog.js}\"");
        assertThat(page.indexOf("/js/dialog.js")).isLessThan(page.indexOf("/js/inventory-browse.js"));
        assertThat(script).doesNotContain("dialog-close", "pointerdown");
    }

    @Test
    void addFromTheRowMenuClosesTheMenuAndReturnsTheFocusToItsTrigger() throws IOException {
        // when
        String script = read("src/main/resources/static/js/inventory-browse.js");

        // then
        assertThat(script).contains("closest('details.cl-menu')", "menu.open = false", "querySelector(':scope > summary')");
    }

    private static String toDatasetSelector(String hook) {
        String[] parts = hook.substring("data-".length()).split("-");
        StringBuilder name = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            name.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return name.toString();
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
