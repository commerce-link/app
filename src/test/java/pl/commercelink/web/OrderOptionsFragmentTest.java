package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class OrderOptionsFragmentTest {

    private static final Path FRAGMENT = Path.of("src/main/resources/templates/fragments/order-options.html");
    private static final Pattern OPENING_TAG =
            Pattern.compile("<[a-zA-Z0-9:]+(?:\\s+[a-zA-Z0-9:_.-]+(?:=\"[^\"]*\")?)*\\s*/?>", Pattern.DOTALL);

    private String fragment() throws Exception {
        return Files.readString(FRAGMENT, StandardCharsets.UTF_8);
    }

    @Test
    void declaresTheOrderOptionsFragmentWithTheExpectedSignature() throws Exception {
        // when
        String html = fragment();

        // then
        assertThat(html).contains("th:fragment=\"orderOptions(options, selected)\"");
    }

    @Test
    void rendersASelectPerOptionCarryingTheSupplierOptionsFieldName() throws Exception {
        // when
        String html = fragment();

        // then
        assertThat(html).contains("<select");
        assertThat(html).contains("th:name=\"'supplierOptions[' + ${option.key()} + ']'\"");
        assertThat(html).contains("data-order-option");
        assertThat(html).contains("th:attr=\"data-order-option=${option.key()},data-required=${option.required()}\"");
        // The HTML required attribute must reflect the option too, not just the JS-read data
        // attribute, so a required select is enforced even if the JS gating fails to run.
        assertThat(html).contains("th:required=\"${option.required()}\"");
    }

    @Test
    void guardsThePlaceholderOptionWithTheChooseMessageKey() throws Exception {
        // when
        String html = fragment();

        // then
        assertThat(html).contains("#{deliveries.options.choose}");
        assertThat(html).contains("th:selected");
    }

    @Test
    void guardsAreNeverCombinedWithThEachOnTheSameElement() throws Exception {
        // when
        String html = fragment();

        // then
        assertThat(hasElementWithBothThIfAndThEach(html)).isFalse();
    }

    private boolean hasElementWithBothThIfAndThEach(String html) {
        Matcher matcher = OPENING_TAG.matcher(html);
        while (matcher.find()) {
            String tag = matcher.group();
            if (tag.contains("th:if") && tag.contains("th:each")) {
                return true;
            }
        }
        return false;
    }
}
