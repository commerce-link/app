package pl.commercelink.orders;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SerialNumbersTest {

    @Test
    void parsesACommaSeparatedFieldTrimmedWithoutBlanksAndRepeats() {
        // when
        var numbers = SerialNumbers.parse(" SN-1, SN-2,,SN-1 , ");

        // then
        assertThat(numbers).containsExactly("SN-1", "SN-2");
    }

    @Test
    void parsesNothingFromANullField() {
        // then
        assertThat(SerialNumbers.parse(null)).isEmpty();
    }

    @Test
    void matchesAWholeNumberOnlyNotAFragment() {
        // then
        assertThat(SerialNumbers.contains("AB12345", "1234")).isFalse();
        assertThat(SerialNumbers.contains("X-1,AB12345", "AB12345")).isTrue();
    }

    @Test
    void matchesAPaddedSearchButNotACommaSeparatedOne() {
        // then
        assertThat(SerialNumbers.contains("SN-1,SN-2", "  SN-1 ")).isTrue();
        assertThat(SerialNumbers.contains("SN-1,SN-2", "SN-1, SN-2")).isFalse();
    }

    @Test
    void keepsTheCaseOfTheNumber() {
        // then
        assertThat(SerialNumbers.contains("S6B0NX", "s6b0nx")).isFalse();
    }

    @Test
    void matchesNothingForABlankSearch() {
        // then
        assertThat(SerialNumbers.contains("SN-1", " ")).isFalse();
        assertThat(SerialNumbers.contains("SN-1", null)).isFalse();
    }
}
