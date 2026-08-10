package pl.commercelink.shipping;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CarrierDictionaryTest {

    private final CarrierDictionary dictionary = configured();

    private static CarrierDictionary configured() {
        CarrierDictionary dictionary = new CarrierDictionary();
        dictionary.setCarriers(Map.of(
                "INPOST", List.of("InPost", "Paczkomat", "Paczkomaty"),
                "POCZTA_POLSKA", List.of("Poczta Polska", "Pocztex", "Poczta"),
                "ORLEN", List.of("Orlen Paczka", "Orlen", "RUCH")));
        return dictionary;
    }

    @Test
    void resolvesAConfiguredAliasIgnoringCase() {
        // when / then
        assertEquals(Optional.of("INPOST"), dictionary.resolve("inpost"));
        assertEquals(Optional.of("ORLEN"), dictionary.resolve("RUCH"));
    }

    @Test
    void treatsTheCarrierKeyItselfAsItsOwnAlias() {
        // when / then
        assertEquals(Optional.of("POCZTA_POLSKA"), dictionary.resolve("POCZTA_POLSKA"));
        assertEquals(Optional.of("POCZTA_POLSKA"), dictionary.resolve("Poczta Polska"));
    }

    @Test
    void knowsOnlyTheCarriersThatConfigurationDeclares() {
        // when / then
        assertEquals(Optional.empty(), dictionary.resolve("DHL"));
    }

    @Test
    void resolvesAnAliasEmbeddedInALongerName() {
        // when / then
        assertEquals(Optional.of("INPOST"), dictionary.resolve("InPost Paczkomaty 24/7"));
        assertEquals(Optional.of("POCZTA_POLSKA"), dictionary.resolve("Kurier Pocztex Expres24"));
    }

    @Test
    void returnsNothingForUnknownOrBlankNames() {
        // when / then
        assertEquals(Optional.empty(), dictionary.resolve("Meest Express"));
        assertEquals(Optional.empty(), dictionary.resolve(null));
        assertEquals(Optional.empty(), dictionary.resolve("   "));
    }

    @Test
    void describesTellsWhetherANameBelongsToTheCarrier() {
        // when / then
        assertTrue(dictionary.describes("INPOST", "InPost Paczkomaty"));
        assertFalse(dictionary.describes("DPD", "InPost Paczkomaty"));
        assertFalse(dictionary.describes(null, "InPost Paczkomaty"));
    }
}
