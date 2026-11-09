package pl.commercelink.inventory.supplier;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EanTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "0", "556", "1234567", "123456789012345", "6941607330487a", "ABEAN-CP74167",
            "1111111111111", "9999999999999", "0000000000000", "88888888"})
    void codeThatIsNotAnEanIsInvalid(String ean) {
        // when / then
        assertFalse(Ean.isValid(ean));
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345670", "123456789012", "1234567890123", "12345678901234", "0012345678905",
            " 1234567890123 "})
    void eanOfEightToFourteenDigitsIsValid(String ean) {
        // when / then
        assertTrue(Ean.isValid(ean));
    }
}
