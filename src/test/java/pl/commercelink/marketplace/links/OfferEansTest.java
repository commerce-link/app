package pl.commercelink.marketplace.links;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class OfferEansTest {

    @ParameterizedTest
    @CsvSource({
            "'5901234567890', '5901234567890'",
            "' 5901234567890 ', '5901234567890'",
            "'05901234567890', '5901234567890'",
            "' 05901234567890\t', '5901234567890'",
            "'012345678905', '012345678905'"
    })
    void trimsAndUnifiesTheEan(String raw, String expected) {
        // when
        String unified = OfferEans.unify(raw);

        // then
        assertThat(unified).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void aMissingOrBlankEanIsNull(String raw) {
        // when
        String unified = OfferEans.unify(raw);

        // then
        assertThat(unified).isNull();
    }

    @Test
    void unifyingTwiceChangesNothing() {
        // given
        String once = OfferEans.unify(" 05901234567890 ");

        // when
        String twice = OfferEans.unify(once);

        // then
        assertThat(twice).isEqualTo(once);
    }
}
