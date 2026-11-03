package pl.commercelink.carriers;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CarrierDictionaryTest {

    @Test
    void everyMarketplaceHasAnImportAndAStatusDictionaryNamedAfterItsAdapter() {
        // when / then
        assertThat(CarrierDictionary.marketplaceImport(" Allegro ")).isEqualTo("global#marketplace#allegro#import");
        assertThat(CarrierDictionary.marketplaceStatus("Allegro")).isEqualTo("global#marketplace#allegro#status");
        assertThat(CarrierDictionary.marketplaceStatus("Erli")).isEqualTo("global#marketplace#erli#status");
    }

    @Test
    void marketplaceAndShippingDictionariesMatchByFragmentSupplierOnesOnlyExactly() {
        // given
        String supplier = CarrierDictionary.supplier("uma2dqukxr", "Shoper-k7f3a9c2");

        // when / then
        assertThat(supplier).isEqualTo("uma2dqukxr#supplier#Shoper-k7f3a9c2");
        assertThat(CarrierDictionary.isSupplier(supplier)).isTrue();
        assertThat(CarrierDictionary.matchesByFragment(supplier)).isFalse();
        assertThat(CarrierDictionary.matchesByFragment(CarrierDictionary.shipping("Furgonetka"))).isTrue();
        assertThat(CarrierDictionary.matchesByFragment(CarrierDictionary.marketplaceStatus("Ceneo"))).isTrue();
    }
}
