package pl.commercelink.web.deliveries.details;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryDetailsScriptsContractTest {

    private static final Pattern OPENING_TAG =
            Pattern.compile("<[a-zA-Z0-9:]+(?:\\s+[a-zA-Z0-9:_.-]+(?:=\"[^\"]*\")?)*\\s*/?>", Pattern.DOTALL);

    static List<Path> templates() throws Exception {
        try (Stream<Path> files = Files.walk(Path.of("src/main/resources/templates/deliveries/details"))) {
            return Stream.concat(Stream.of(Path.of("src/main/resources/templates/deliveries/details.html")),
                    files.filter(Files::isRegularFile).toList().stream()).toList();
        }
    }

    static String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void templatesCarryNoInlineBehaviourNoBulmaLookAndNoUnsafeText() throws Exception {
        for (Path template : templates()) {
            // when
            String html = read(template);

            // then
            assertThat(html).as(template.toString())
                    .doesNotContain("style=\"").doesNotContain("<style").doesNotContain("onclick=")
                    .doesNotContain("onchange=").doesNotContain("oninput=").doesNotContain("onsubmit=")
                    .doesNotContain("<script>").doesNotContain("th:utext").doesNotContain("th:field")
                    .doesNotContain("class=\"button").doesNotContain("class=\"box").doesNotContain("class=\"columns")
                    .doesNotContain("class=\"notification").doesNotContain("class=\"tag").doesNotContain("class=\"select")
                    .doesNotContain("class=\"input").doesNotContain("class=\"table").doesNotContain("class=\"level")
                    .doesNotContain("class=\"modal").doesNotContain("confirmSave");
        }
    }

    @Test
    void guardsAreNeverCombinedWithThReplaceOrThWithOnTheSameElement() throws Exception {
        for (Path template : templates()) {
            // when
            Matcher matcher = OPENING_TAG.matcher(read(template));

            // then
            while (matcher.find()) {
                String tag = matcher.group();
                assertThat(tag.contains("th:if") && (tag.contains("th:replace") || tag.contains("th:with")))
                        .as(template + ": " + tag).isFalse();
            }
        }
    }

    @Test
    void thePageDecoratesTheLayoutAndLoadsItsModules() throws Exception {
        // when
        String page = read(Path.of("src/main/resources/templates/deliveries/details.html"));

        // then
        assertThat(page).contains("layout:decorate=\"~{layout}\"")
                .contains("/js/menu.js").contains("/js/dialog.js").contains("/js/collapse.js").contains("/js/timeline.js")
                .contains("/js/copy-field.js").contains("/js/confirm-dialog.js");
    }

    @Test
    void theItemsScriptTogglesDestinationsDocksTheBarAndOwnsEnterInsideDialogs() throws Exception {
        // when
        String js = read(Path.of("src/main/resources/static/js/delivery-details.js"));
        String page = read(Path.of("src/main/resources/templates/deliveries/details.html"));

        // then
        assertThat(js).contains("'use strict'").doesNotContain("innerHTML").doesNotContainPattern("\\.style\\.(?!setProperty\\('--)")
                .contains("IntersectionObserver").contains("--cl-docked-bar").contains("data-cl-alloc-toggle")
                .contains("data-cl-select-pending").contains("data-cl-selection-list").contains("cl:dialog-open")
                .contains("event.key !== 'Enter'").contains("data-cl-dialog-submit").contains("data-cl-submitted");
        assertThat(page).contains("/js/table-select.js").contains("/js/delivery-details.js");
    }

    @Test
    void theDockedBarIsMeasuredOnlyWhileShownAndCollapsingAProductUnchecksItsDestinations() throws Exception {
        // when
        String js = read(Path.of("src/main/resources/static/js/delivery-details.js"));

        // then
        assertThat(js).contains("entries[entries.length - 1].isIntersecting").contains("if (height === 0) {")
                .contains("form.addEventListener('change', measure)")
                .containsPattern("if \\(!open\\) \\{\\s*row\\.querySelectorAll\\('input\\[data-cl-select-row\\]'\\)[\\s\\S]*?box\\.checked = false;")
                .containsPattern("if \\(unchecked\\) \\{\\s*refreshSelection\\(\\);")
                .containsPattern("before = checkedBoxes\\(\\);[\\s\\S]*?expandAll\\(\\);");
    }
}
