package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** The selection script reads its texts from the template and wires the A4 controls the template renders. */
class FulfilmentSelectScriptContractTest {

    private static final Path SCRIPT = Path.of("src/main/resources/static/js/fulfilment-select.js");
    private static final Path TEMPLATE = Path.of("src/main/resources/templates/fulfilment.html");

    private static String textsBlock(String html) {
        String from = html.substring(html.indexOf("data-cl-select-text"));
        return from.substring(0, from.indexOf("></div>"));
    }

    private static String dataAttribute(String datasetKey) {
        return "data-" + datasetKey.replaceAll("([A-Z])", "-$1").toLowerCase() + "=";
    }

    @Test
    void everyTextTheScriptWritesComesFromTheTemplate() throws Exception {
        // given
        String js = Files.readString(SCRIPT);
        String texts = textsBlock(Files.readString(TEMPLATE));

        // when
        Set<String> used = new TreeSet<>();
        Matcher m = Pattern.compile("texts\\.([a-zA-Z]+)").matcher(js);
        while (m.find()) {
            used.add(m.group(1));
        }

        // then
        assertThat(used).contains("dearer", "fold", "ratio", "money", "chipFilter", "chipFilterSplit", "categoryChosen",
                "selectVisible", "clearAll", "clearMatching", "coveredOff", "foldPlain", "foldLocked", "swapOrders", "swapItems",
                "liveItems", "liveCost", "liveProfit");
        assertThat(used).allSatisfy(key -> assertThat(texts).as(key).contains(dataAttribute(key)));
        assertThat(texts).doesNotContain("data-category=");
    }

    @Test
    void theListBarChipsAndFoldsAreWiredWithoutWritingHtml() throws Exception {
        // when
        String js = Files.readString(SCRIPT);

        // then
        assertThat(js).contains("button[data-cl-select-visible]").contains("button[data-cl-expand-all]")
                .contains("button[data-cl-collapse-all]").contains("[data-cl-coverage-fill]").contains("style.width")
                .contains("'cl-alt-fold'").contains("'cl-alt-fold-toggle'").contains("aria-controls")
                .contains("aria-labelledby").doesNotContain("data-cl-select-all");
        assertThat(js).doesNotContain("innerHTML").doesNotContain("insertAdjacentHTML").doesNotContain("outerHTML");
    }
}
