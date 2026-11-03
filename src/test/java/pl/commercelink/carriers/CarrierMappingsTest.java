package pl.commercelink.carriers;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CarrierMappingsTest {

    private static final String ALLEGRO_METHODS = CarrierDictionaries.marketplaceImport("Allegro");
    private static final String ALLEGRO = CarrierDictionaries.marketplaceStatus("Allegro").orElseThrow();
    private static final String MORELE = CarrierDictionaries.marketplaceImport("Morele");
    private static final String FURGONETKA = CarrierDictionaries.shipping("furgonetka");
    private static final String SHOPER = CarrierDictionaries.supplier("store-1", "Shoper-k7f3a9c2");

    private final CarrierMappings mappings = new InMemoryCarrierDictionaries()
            .with(ALLEGRO_METHODS, "Paczkomat=InPost", "Kurier24 InPost=InPost Kurier", "InPost=InPost", "DPD=DPD")
            .with(ALLEGRO, "INPOST=InPost", "INPOST=InPost Kurier", "DPD=DPD")
            .with(MORELE, "2=InPost", "4=Orlen", "7=Poczta Polska")
            .with(FURGONETKA, "inpost=InPost", "inpost_courier=InPost Kurier")
            .with(SHOPER, "5=DPD", "Kurier DPD pobranie=DPD", "7=InPost")
            .mappings();

    @Test
    void recognizesATextExactlyRegardlessOfCaseAndSpaces() {
        // when / then
        assertThat(mappings.inboundMappingFor(FURGONETKA, "  INPOST_COURIER ")).contains("InPost Kurier");
        assertThat(mappings.inboundMappingFor(SHOPER, "kurier  dpd pobranie")).contains("DPD");
    }

    @Test
    void recognizesAWholeMarketplaceNameByItsLongestFragment() {
        // when / then
        assertThat(mappings.inboundMappingFor(ALLEGRO_METHODS, "Allegro Paczkomaty InPost 24/7")).contains("InPost");
        assertThat(mappings.inboundMappingFor(ALLEGRO_METHODS, "Allegro Kurier24 InPost pobranie")).contains("InPost Kurier");
    }

    @Test
    void matchesDigitsOnlyExactly() {
        // when / then
        assertThat(mappings.inboundMappingFor(MORELE, "14")).isEmpty();
        assertThat(mappings.inboundMappingFor(MORELE, "4")).contains("Orlen");
    }

    @Test
    void supplierMethodsMatchOnlyExactly() {
        // when / then
        assertThat(mappings.inboundMappingFor(SHOPER, "Kurier DPD pobranie 2")).isEmpty();
    }

    @Test
    void firstPairDecidesInBothDirections() {
        // when / then
        assertThat(mappings.inboundMappingFor(ALLEGRO, "INPOST")).contains("InPost");
        assertThat(mappings.outboundMappingFor(ALLEGRO, "InPost Kurier")).contains("INPOST");
        assertThat(mappings.outboundMappingFor(SHOPER, "DPD")).contains("5");
    }

    @Test
    void keepsAnUnknownTextAsItCame() {
        // when / then
        assertThat(mappings.carrierNameFor(MORELE, "99")).isEqualTo("99");
        assertThat(mappings.carrierNameFor(MORELE, "7")).isEqualTo("Poczta Polska");
    }

    @Test
    void readsAnOlderLabelCodeThroughTheGivenDictionaries() {
        // when
        Optional<String> code = mappings.outboundMappingFor(ALLEGRO, "inpost_courier", List.of(FURGONETKA));

        // then
        assertThat(code).contains("INPOST");
    }

    @Test
    void missingDictionaryRecognizesNothing() {
        // when / then
        assertThat(mappings.inboundMappingFor(CarrierDictionaries.shipping("apaczka"), "inpost")).isEmpty();
        assertThat(mappings.outboundMappingFor(CarrierDictionaries.shipping("apaczka"), "InPost")).isEmpty();
    }
}
