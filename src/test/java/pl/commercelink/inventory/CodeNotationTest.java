package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodeNotationTest {

    @Test
    void normalizeKeepsOnlyUpperCaseLettersAndDigits() {
        // when / then
        assertThat(CodeNotation.normalize("TL-SG3428X-M2")).isEqualTo("TLSG3428XM2");
        assertThat(CodeNotation.normalize(null)).isEmpty();
    }

    @Test
    void sameNotationAcceptsNotationVariants() {
        // when / then
        assertThat(CodeNotation.sameNotation("3JA26AE", "3ja26ae")).isTrue();
        assertThat(CodeNotation.sameNotation("7MD66F", "7MD66F#B19")).isTrue();
        assertThat(CodeNotation.sameNotation("SG3428X-M2", "TL-SG3428X-M2")).isTrue();
    }

    @Test
    void sameNotationRejectsDifferentOrTooShortCodes() {
        // when / then
        assertThat(CodeNotation.sameNotation("11324-01-20G", "11350-03-20G")).isFalse();
        assertThat(CodeNotation.sameNotation("AB", "ABCDEF")).isFalse();
        assertThat(CodeNotation.sameNotation("", "")).isFalse();
    }
}
