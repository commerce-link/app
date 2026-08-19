package pl.commercelink.registration;

import org.junit.jupiter.api.Test;

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

    @Test
    void rejectsPasswordShorterThanEightCharacters() {
        // when / then
        assertFalse(PasswordPolicy.isValid("Ab1!cde"));
    }

    @Test
    void rejectsPasswordLongerThanCognitoAccepts() {
        // when / then
        assertFalse(PasswordPolicy.isValid("Ab1!" + "x".repeat(253)));
    }

    @Test
    void rejectsPasswordWithoutLowercase() {
        // when / then
        assertFalse(PasswordPolicy.isValid("ABC1!DEFG"));
    }

    @Test
    void rejectsPasswordWithoutUppercase() {
        // when / then
        assertFalse(PasswordPolicy.isValid("abc1!defg"));
    }

    @Test
    void rejectsPasswordWithoutDigit() {
        // when / then
        assertFalse(PasswordPolicy.isValid("Abcd!efgh"));
    }

    @Test
    void rejectsPasswordWithoutSymbol() {
        // when / then
        assertFalse(PasswordPolicy.isValid("Abcd1efgh"));
    }

    @Test
    void doesNotCountSpaceAsSymbol() {
        // when / then
        assertFalse(PasswordPolicy.isValid("Moje haslo 1"));
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
