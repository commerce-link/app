package pl.commercelink.shipping;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CarrierDictionaryTest {

    private final CarrierDictionary dictionary = configured();

    private static CarrierDictionary configured() {
        CarrierDictionary dictionary = new CarrierDictionary();
        dictionary.setCarriers(Map.of(
                "furgonetka", Map.of(
                        "Ceneo", "{\"InPost\":\"1\",\"DPD\":\"3\"}",
                        "Allegro", "{\"InPost\":\"INPOST\"}"),
                "Allegro", Map.of("furgonetka", "{\"Paczkomat\":\"InPost\",\"RUCH\":\"Orlen\"}"),
                "Morele", Map.of("furgonetka", "{\"2\":\"InPost\",\"6\":\"Zabka\"}")));
        return dictionary;
    }

    @Test
    void translatesTheSameNameDifferentlyPerTargetSystem() {
        // when / then
        assertEquals(Optional.of("1"), dictionary.translate("furgonetka", "Ceneo", "InPost"));
        assertEquals(Optional.of("INPOST"), dictionary.translate("furgonetka", "Allegro", "InPost"));
    }

    @Test
    void translatesFromTheMarketplaceBackToTheShippingProvider() {
        // when / then
        assertEquals(Optional.of("InPost"), dictionary.translate("Morele", "furgonetka", "2"));
        assertEquals(Optional.of("Zabka"), dictionary.translate("Morele", "furgonetka", "6"));
    }

    @Test
    void matchesAFragmentEmbeddedInFreeText() {
        // when / then
        assertEquals(Optional.of("InPost"), dictionary.translate("Allegro", "furgonetka", "InPost Paczkomat 24/7"));
        assertEquals(Optional.of("Orlen"), dictionary.translate("Allegro", "furgonetka", "Punkt RUCH"));
    }

    @Test
    void ignoresTheCaseOfSystemNames() {
        // when / then
        assertEquals(Optional.of("1"), dictionary.translate("FURGONETKA", "ceneo", "InPost"));
    }

    @Test
    void returnsNothingWhenThePairOrValueIsUnknown() {
        // when / then
        assertEquals(Optional.empty(), dictionary.translate("furgonetka", "Empik", "InPost"));
        assertEquals(Optional.empty(), dictionary.translate("furgonetka", "Ceneo", "Meest"));
        assertEquals(Optional.empty(), dictionary.translate("furgonetka", "Ceneo", null));
    }

    @Test
    void describesComparesTheTranslatedValue() {
        // when / then
        assertTrue(dictionary.describes("furgonetka", "Ceneo", "InPost", "1"));
        assertFalse(dictionary.describes("furgonetka", "Ceneo", "InPost", "3"));
        assertFalse(dictionary.describes("furgonetka", "Ceneo", "InPost", null));
    }

    @Test
    void malformedMappingFailsAtStartup() {
        // given
        CarrierDictionary broken = new CarrierDictionary();
        broken.setCarriers(Map.of("furgonetka", Map.of("Ceneo", "{\"InPost\": }")));

        // when / then
        assertThrows(IllegalStateException.class, broken::validate);
    }
}
