package pl.commercelink.carriers;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CarrierDictionariesTest {

    @Test
    void marketplacesWithTwoVocabulariesHaveTwoDictionaries() {
        // when / then
        assertThat(CarrierDictionaries.marketplaceImport("Allegro")).isEqualTo("global#marketplace#allegro-methods");
        assertThat(CarrierDictionaries.marketplaceStatus("Allegro")).contains("global#marketplace#allegro");
        assertThat(CarrierDictionaries.marketplaceImport("Ceneo")).isEqualTo("global#marketplace#ceneo-forms");
        assertThat(CarrierDictionaries.marketplaceStatus("Ceneo")).contains("global#marketplace#ceneo");
    }

    @Test
    void empikAndMoreleGetNoStatusCode() {
        // when / then
        assertThat(CarrierDictionaries.marketplaceImport("Empik")).isEqualTo("global#marketplace#empik");
        assertThat(CarrierDictionaries.marketplaceStatus("Empik")).isEmpty();
        assertThat(CarrierDictionaries.marketplaceStatus("Morele")).isEmpty();
    }

    @Test
    void supplierConnectionsAreScopedToTheStore() {
        // when / then
        assertThat(CarrierDictionaries.supplier("uma2dqukxr", "Shoper-k7f3a9c2"))
                .isEqualTo("uma2dqukxr#supplier#Shoper-k7f3a9c2");
        assertThat(CarrierDictionaries.matchesByFragment("uma2dqukxr#supplier#Shoper-k7f3a9c2")).isFalse();
        assertThat(CarrierDictionaries.matchesByFragment(CarrierDictionaries.shipping("Furgonetka"))).isTrue();
    }

    @Test
    void onlyCeneoRequiresAStatusCode() {
        // when / then
        assertThat(CarrierDictionaries.statusRequiresCode("Ceneo")).isTrue();
        assertThat(CarrierDictionaries.statusRequiresCode("Allegro")).isFalse();
    }
}
