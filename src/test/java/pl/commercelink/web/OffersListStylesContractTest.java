package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** The offers list styles live in commercelink.css and keep the row menu above the row link. */
class OffersListStylesContractTest {

    @Test
    void actionsColumnSitsAboveTheRowLink() throws Exception {
        // given
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"));

        // then
        assertThat(css).contains(".cl-page .cl-table.is-offers .cl-table-actions")
                .contains("z-index: 1")
                .contains(".cl-page .cl-menu-item .cl-menu-desc")
                .contains(".cl-page .cl-table.is-offers .cl-copy-quick");
    }

    @Test
    void copyScriptCopiesAnyButtonWithACopyValue() throws Exception {
        // given
        String js = Files.readString(Path.of("src/main/resources/static/js/copy-field.js"));

        // then
        assertThat(js).contains("closest('button[data-cl-copy]')");
    }
}
