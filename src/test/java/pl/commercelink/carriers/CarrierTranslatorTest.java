package pl.commercelink.carriers;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CarrierTranslatorTest {

    private static final String ALLEGRO_METHODS = CarrierDictionary.marketplaceImport("Allegro");
    private static final String ALLEGRO = CarrierDictionary.marketplaceStatus("Allegro");
    private static final String MORELE = CarrierDictionary.marketplaceImport("Morele");
    private static final String FURGONETKA = CarrierDictionary.shipping("furgonetka");
    private static final String SHOPER = CarrierDictionary.supplier("store-1", "Shoper-k7f3a9c2");

    private final CarrierTranslator translator = new InMemoryCarrierDictionaries()
            .with(ALLEGRO_METHODS, "Paczkomat=InPost", "Kurier24 InPost=InPost Kurier", "InPost=InPost", "DPD=DPD")
            .with(ALLEGRO, "INPOST=InPost", "INPOST=InPost Kurier", "DPD=DPD")
            .with(MORELE, "2=InPost", "4=Orlen", "7=Poczta Polska")
            .with(FURGONETKA, "inpost=InPost", "inpost_courier=InPost Kurier")
            .with(SHOPER, "5=DPD", "Kurier DPD pobranie=DPD", "7=InPost")
            .translator();

    @Test
    void recognizesATextExactlyRegardlessOfCaseAndSpaces() {
        // when / then
        assertThat(translator.carrierFor(FURGONETKA, "  INPOST_COURIER ")).isEqualTo("InPost Kurier");
        assertThat(translator.carrierFor(SHOPER, "kurier  dpd pobranie")).isEqualTo("DPD");
    }

    @Test
    void recognizesAWholeMarketplaceNameByItsLongestFragment() {
        // when / then
        assertThat(translator.carrierFor(ALLEGRO_METHODS, "Allegro Paczkomaty InPost 24/7")).isEqualTo("InPost");
        assertThat(translator.carrierFor(ALLEGRO_METHODS, "Allegro Kurier24 InPost pobranie")).isEqualTo("InPost Kurier");
    }

    @Test
    void matchesDigitsOnlyExactly() {
        // when / then
        assertThat(translator.carrierFor(MORELE, "14")).isEqualTo("14");
        assertThat(translator.carrierFor(MORELE, "4")).isEqualTo("Orlen");
    }

    @Test
    void supplierMethodsMatchOnlyExactly() {
        // when / then
        assertThat(translator.carrierFor(SHOPER, "Kurier DPD pobranie 2")).isEqualTo("Kurier DPD pobranie 2");
    }

    @Test
    void firstPairDecidesInBothDirections() {
        // when / then
        assertThat(translator.carrierFor(ALLEGRO, "INPOST")).isEqualTo("InPost");
        assertThat(translator.textFor(ALLEGRO, "InPost Kurier")).contains("INPOST");
        assertThat(translator.textFor(SHOPER, "DPD")).contains("5");
    }

    @Test
    void keepsAnUnknownTextAsItCame() {
        // when / then
        assertThat(translator.carrierFor(MORELE, "99")).isEqualTo("99");
        assertThat(translator.carrierFor(MORELE, "7")).isEqualTo("Poczta Polska");
    }

    @Test
    void readsAnOlderLabelCodeThroughTheGivenDictionaries() {
        // when
        Optional<String> code = translator.textFor(ALLEGRO, "inpost_courier", List.of(FURGONETKA));

        // then
        assertThat(code).contains("INPOST");
    }

    @Test
    void missingDictionaryRecognizesNothing() {
        // when / then
        assertThat(translator.carrierFor(CarrierDictionary.shipping("apaczka"), "inpost")).isEqualTo("inpost");
        assertThat(translator.textFor(CarrierDictionary.shipping("apaczka"), "InPost")).isEmpty();
    }
}
