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
            "data-browse-root", "data-browse-dialog-url", "data-browse-dialog-slot", "data-browse-add",
            "data-browse-add-selected", "data-browse-dialog-close", "data-browse-other-select",
            "data-browse-other-radio", "data-cl-select-row");

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
