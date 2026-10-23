package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalScreenTemplateTest {

    private static final Path APPROVAL = Path.of("src/main/resources/templates/deliveries/approval.html");
    private static final Pattern OPENING_TAG =
            Pattern.compile("<[a-zA-Z0-9:]+(?:\\s+[a-zA-Z0-9:_.-]+(?:=\"[^\"]*\")?)*\\s*/?>", Pattern.DOTALL);

    private String approval() throws Exception {
        return Files.readString(APPROVAL, StandardCharsets.UTF_8);
    }

    @Test
    void guardsAreNeverCombinedWithThReplaceOnTheSameElement() throws Exception {
        // then
        assertThat(hasElementWithBoth(approval(), "th:if", "th:replace")).isFalse();
    }

    @Test
    void localVariablesAreNeverDeclaredOnTheSameElementThatGuardsOnThem() throws Exception {
        // then
        assertThat(hasElementWithBoth(approval(), "th:if", "th:with")).isFalse();
    }

    private boolean hasElementWithBoth(String html, String first, String second) {
        Matcher matcher = OPENING_TAG.matcher(html);
        while (matcher.find()) {
            String tag = matcher.group();
            if (tag.contains(first) && tag.contains(second)) {
                return true;
            }
        }
        return false;
    }
}
