package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CardAlertSpacingStylesContractTest {

    @Test
    void anAlertFollowedByTheCardFooterKeepsItsDistanceFromIt() throws Exception {
        // given
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"), StandardCharsets.UTF_8);

        // then the offer matching card ends with a failure alert and then the form holding the footer
        assertThat(css).containsPattern("\\.cl-page \\.cl-card > \\.cl-alert \\+ \\.cl-card-footer,\\s*"
                + "\\.cl-page \\.cl-card > \\.cl-alert \\+ form:has\\(> \\.cl-card-footer\\)\\s*\\{\\s*"
                + "margin-top: 16px;");
    }
}
