package pl.commercelink.web.offers;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** The basket view styles live in commercelink.css. */
class BasketViewStylesContractTest {

    @Test
    void priceIsTheLastColumnAndStaysUnlabelledAsACard() throws Exception {
        // given
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"));

        // when / then: the label rule of the removed market column would now name the price ("Ilość × cena (PLN): 1 × …")
        assertThat(css).doesNotContain("is-basket-items.is-wrap tbody tr > td.is-numeric:last-child::before")
                .doesNotContain("cl-col-market");
    }
}
