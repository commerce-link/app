package pl.commercelink.externaloffers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

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

    @ParameterizedTest
    @ValueSource(strings = {"12345670", "2000000001012", "20000000010120", "012345678905"})
    void anEanOfEightToFourteenDigitsIsValid(String unified) {
        // when
        boolean valid = OfferEans.isValid(unified);

        // then
        assertThat(valid).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"N/A", "123:456", "20070000001A", "#2000000001012", "2000000 001012", "1234567",
            "200000000101200", "２０００００００１０１２"})
    void anythingElseIsNotAnEan(String unified) {
        // when
        boolean valid = OfferEans.isValid(unified);

        // then
        assertThat(valid).isFalse();
    }

    @Test
    void theCheckDigitIsNotChecked() {
        // when
        boolean valid = OfferEans.isValid("2000000001013");

        // then
        assertThat(valid).isTrue();
    }

    @Test
    void keepsTheValidEansUnifiedOnceEachInTheirOrder() {
        // when
        List<String> eans = OfferEans.validUnified(Arrays.asList(" 2000000001029 ", "N/A", null, "02000000001012",
                "123:456", "2000000001029"));

        // then
        assertThat(eans).containsExactly("2000000001029", "2000000001012");
    }

    @Test
    void noEansGiveAnEmptyList() {
        // when
        List<String> eans = OfferEans.validUnified(null);

        // then
        assertThat(eans).isEmpty();
    }

    @Test
    void unifyValidGivesTheUnifiedFormOfAValidEan() {
        // when
        String unified = OfferEans.unifyValid(" 02000000001012 ");

        // then
        assertThat(unified).isEqualTo("2000000001012");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"brak kodu", "N/A", "123:456", "20070000001A"})
    void unifyValidGivesNullForAnythingThatIsNotAnEan(String raw) {
        // when
        String unified = OfferEans.unifyValid(raw);

        // then
        assertThat(unified).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"5901234123457", "4006381333931", "96385074", "730143315289", "15901234123454"})
    void unifyCheckedKeepsAnEanWithAValidCheckDigit(String ean) {
        // when
        String checked = OfferEans.unifyChecked(ean);

        // then
        assertThat(checked).isEqualTo(ean);
    }

    @Test
    void unifyCheckedReturnsTheUnifiedSpelling() {
        // when
        String checked = OfferEans.unifyChecked(" 05901234123457 ");

        // then
        assertThat(checked).isEqualTo(OfferEans.unifyValid(" 05901234123457 "));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  ", "5901234123458", "12345678", "2222222222222", "ABC-123", "#590123", "5901234123457:4006381333931"})
    void unifyCheckedRejectsWhatIsNotAnEanOfAProduct(String code) {
        // when
        String checked = OfferEans.unifyChecked(code);

        // then
        assertThat(checked).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"100000597", "1234567895", "12345678905"})
    void unifyCheckedRejectsANumberOfNoGtinLengthEvenWithAMatchingCheckDigit(String code) {
        // when
        String checked = OfferEans.unifyChecked(code);

        // then
        assertThat(checked).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2000000000015", "0200000000004", "0412345678903", "20123451", "01234565"})
    void unifyCheckedRejectsACodeAStoreAssignsItself(String code) {
        // when
        String checked = OfferEans.unifyChecked(code);

        // then
        assertThat(checked).isNull();
    }
}
