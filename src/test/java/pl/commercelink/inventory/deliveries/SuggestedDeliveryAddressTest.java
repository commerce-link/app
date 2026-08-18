package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.SupplierDeliveryAddress;
import pl.commercelink.orders.ShippingDetails;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SuggestedDeliveryAddressTest {

    private ShippingDetails storeAddress(String street, String postalCode) {
        ShippingDetails details = new ShippingDetails();
        details.setStreetAndNumber(street);
        details.setPostalCode(postalCode);
        details.setCity("Kraków");
        details.setCountry("PL");
        return details;
    }

    private SupplierDeliveryAddress supplierAddress(String id, String line, String postalCode) {
        return new SupplierDeliveryAddress(id, line, "Kraków", postalCode, "PL");
    }

    @Test
    void matchesIgnoringPunctuationCaseAndPostalCodeSeparator() {
        // given
        ShippingDetails storeDefault = storeAddress("Łobzowska 22/1", "31140");
        List<SupplierDeliveryAddress> supplier = List.of(
                supplierAddress("17200617", "ul. Łobzowska 22/1", "31-140"));

        // when
        Optional<String> match = SuggestedDeliveryAddress.match(storeDefault, supplier);

        // then
        assertThat(match).contains("17200617");
    }

    @Test
    void matchesWhenOnlyOneSideCarriesAStreetTypePrefix() {
        // given
        ShippingDetails storeDefault = storeAddress("Łobzowska 22/1", "31-140");
        List<SupplierDeliveryAddress> supplier = List.of(
                supplierAddress("17200617", "ul. Łobzowska 22/1", "31-140"));

        // when / then
        assertThat(SuggestedDeliveryAddress.match(storeDefault, supplier)).contains("17200617");
    }

    @Test
    void doesNotCollapseStreetsThatDifferOnlyInPolishLetters() {
        // given
        ShippingDetails storeDefault = storeAddress("ul. Świdnicka 5", "31-140");
        List<SupplierDeliveryAddress> supplier = List.of(
                supplierAddress("1", "ul. widnicka 5", "31-140"));

        // when / then
        assertThat(SuggestedDeliveryAddress.match(storeDefault, supplier)).isEmpty();
    }

    @Test
    void doesNotMatchWhenThePostalCodeDiffers() {
        // given
        ShippingDetails storeDefault = storeAddress("ul. Łobzowska 22/1", "30-418");
        List<SupplierDeliveryAddress> supplier = List.of(
                supplierAddress("17200617", "ul. Łobzowska 22/1", "31-140"));

        // when / then
        assertThat(SuggestedDeliveryAddress.match(storeDefault, supplier)).isEmpty();
    }

    @Test
    void suggestsNothingWhenTwoSupplierAddressesMatch() {
        // given
        ShippingDetails storeDefault = storeAddress("ul. Łobzowska 22/1", "31-140");
        List<SupplierDeliveryAddress> supplier = List.of(
                supplierAddress("1", "ul. Łobzowska 22/1", "31-140"),
                supplierAddress("2", "Łobzowska 22/1", "31140"));

        // when / then
        assertThat(SuggestedDeliveryAddress.match(storeDefault, supplier)).isEmpty();
    }

    @Test
    void suggestsNothingWithoutAStoreDefault() {
        // when / then
        assertThat(SuggestedDeliveryAddress.match(null,
                List.of(supplierAddress("1", "ul. Łobzowska 22/1", "31-140")))).isEmpty();
    }

    @Test
    void suggestsNothingWhenTheStoreDefaultIsIncomplete() {
        // given
        ShippingDetails storeDefault = storeAddress("  ", "31-140");

        // when / then
        assertThat(SuggestedDeliveryAddress.match(storeDefault,
                List.of(supplierAddress("1", "ul. Łobzowska 22/1", "31-140")))).isEmpty();
    }

    @Test
    void doesNotMatchDifferentBuildingsThatCollapseUnderPunctuationRemoval() {
        // given
        ShippingDetails storeDefault = storeAddress("Kwiatowa 1/2", "31-140");
        List<SupplierDeliveryAddress> supplier = List.of(
                supplierAddress("1", "Kwiatowa 12", "31-140"));

        // when / then
        assertThat(SuggestedDeliveryAddress.match(storeDefault, supplier)).isEmpty();
    }

    @Test
    void suggestsNothingWhenTheSupplierHasNoAddresses() {
        // given
        ShippingDetails storeDefault = storeAddress("ul. Łobzowska 22/1", "31-140");

        // when / then
        assertThat(SuggestedDeliveryAddress.match(storeDefault, List.of())).isEmpty();
    }
}
