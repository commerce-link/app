package pl.commercelink.stores;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IbanTest {

    @Test
    void acceptsAPolishNumberWithSpacesAndLowerCase() {
        // when
        String normalized = Iban.normalize("pl61 1090 1014 0000 0712 1981 2874");

        // then
        assertThat(normalized).isEqualTo("PL61109010140000071219812874");
        assertThat(Iban.isValid(normalized)).isTrue();
        assertThat(Iban.countryCode(normalized)).isEqualTo("PL");
    }

    /** Polish account numbers are usually copied without the country code. */
    @Test
    void addsTheCountryCodeToABarePolishAccountNumber() {
        // when
        String normalized = Iban.normalize("61 1090 1014 0000 0712 1981 2874");

        // then
        assertThat(normalized).isEqualTo("PL61109010140000071219812874");
        assertThat(Iban.isValid(normalized)).isTrue();
    }

    @Test
    void acceptsAForeignNumber() {
        // when / then
        assertThat(Iban.isValid(Iban.normalize("DE89 3704 0044 0532 0130 00"))).isTrue();
    }

    @Test
    void rejectsAMistypedDigitThroughTheCheckDigits() {
        // when / then
        assertThat(Iban.isValid(Iban.normalize("PL61 1090 1014 0000 0712 1981 2875"))).isFalse();
        assertThat(Iban.isValid(Iban.normalize("not an account"))).isFalse();
        assertThat(Iban.isValid(null)).isFalse();
    }

    @Test
    void groupsANumberInFoursForReading() {
        // when / then
        assertThat(Iban.grouped("PL61109010140000071219812874")).isEqualTo("PL61 1090 1014 0000 0712 1981 2874");
        assertThat(Iban.grouped(" ")).isNull();
    }
}
