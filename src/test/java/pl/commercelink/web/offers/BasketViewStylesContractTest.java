package pl.commercelink.web.offers;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** The basket view styles live in commercelink.css; the phone cards would otherwise show two bare amounts. */
class BasketViewStylesContractTest {

    @Test
    void marketAmountKeepsItsColumnNameAsACard() throws Exception {
        // given
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"));

        // when
        String phone = css.substring(css.lastIndexOf("@media screen and (max-width: 719px)"));

        // then
        assertThat(phone).contains(".cl-page .cl-table.is-basket-items.is-wrap tbody tr > td.is-numeric:last-child::before { content: attr(data-label) \": \"; }");
    }
}
