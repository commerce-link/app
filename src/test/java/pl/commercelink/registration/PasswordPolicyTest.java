package pl.commercelink.registration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {

    @Test
    void acceptsPasswordMeetingEveryRequirement() {
        // when / then
        assertTrue(PasswordPolicy.isValid("Tajne1!haslo"));
    }

    @Test
    void acceptsExactlyEightCharacters() {
        // when / then
        assertTrue(PasswordPolicy.isValid("Ab1!cdef"));
    }

    static Stream<Arguments> invalidPasswords() {
        return Stream.of(
                Arguments.of("shorter than eight characters", "Ab1!cde"),
                Arguments.of("longer than Cognito accepts", "Ab1!" + "x".repeat(253)),
                Arguments.of("without lowercase", "ABC1!DEFG"),
                Arguments.of("without uppercase", "abc1!defg"),
                Arguments.of("without digit", "Abcd!efgh"),
                Arguments.of("without symbol", "Abcd1efgh"),
                Arguments.of("space is not a symbol", "Moje haslo 1"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPasswords")
    void rejectsPasswordBreakingARequirement(String reason, String password) {
        // when / then
        assertFalse(PasswordPolicy.isValid(password));
    }

    @Test
    void rejectsNull() {
        // when / then
        assertFalse(PasswordPolicy.isValid(null));
    }

    @Test
    void acceptsEverySymbolAllowedByTheUserPool() {
        // given
        String symbols = "^$*.[]{}()?\"!@#%&/\\,><':;|_~`+=-";

        // when / then
        for (char symbol : symbols.toCharArray()) {
            assertTrue(PasswordPolicy.isValid("Abcd1efg" + symbol),
                    "symbol should be accepted: " + symbol);
        }
    }
}
