package pl.commercelink.stores;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.ShippingDetails;

import java.util.LinkedList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StoreWarehouseAddressesTest {

    private ShippingDetails address(String id, String company, boolean isDefault) {
        ShippingDetails details = new ShippingDetails();
        details.setId(id);
        details.setCompanyName(company);
        details.set_default(isDefault);
        return details;
    }

    private Store storeWith(ShippingDetails... addresses) {
        Store store = new Store();
        store.setShippingDetails(new LinkedList<>(List.of(addresses)));
        return store;
    }

    @Test
    void theFirstAddressOfAStoreBecomesTheDefault() {
        // given
        Store store = storeWith();

        // when
        store.addShippingDetails(address(null, "Warehouse", false), false);

        // then
        assertThat(store.getShippingDetails()).singleElement().satisfies(added -> {
            assertThat(added.is_default()).isTrue();
            assertThat(added.getId()).isNotBlank();
        });
    }

    @Test
    void anAddressAddedAsDefaultTakesTheFlagFromThePreviousDefault() {
        // given
        Store store = storeWith(address("a-1", "Old", true));

        // when
        store.addShippingDetails(address(null, "New", false), true);

        // then
        assertThat(store.getShippingDetails()).extracting(ShippingDetails::is_default).containsExactly(false, true);
    }

    @Test
    void anAddressAddedWithoutTheDefaultFlagLeavesTheCurrentDefault() {
        // given
        Store store = storeWith(address("a-1", "Old", true));

        // when
        store.addShippingDetails(address(null, "New", true), false);

        // then
        assertThat(store.getShippingDetails()).extracting(ShippingDetails::is_default).containsExactly(true, false);
    }

    @Test
    void settingTheDefaultAddressClearsTheFlagOnTheOthers() {
        // given
        Store store = storeWith(address("a-1", "One", true), address("a-2", "Two", false));

        // when
        boolean found = store.makeDefaultShippingDetails("a-2");

        // then
        assertThat(found).isTrue();
        assertThat(store.getDefaultShippingDetails().getId()).isEqualTo("a-2");
        assertThat(store.getShippingDetails()).filteredOn(ShippingDetails::is_default).hasSize(1);
    }

    @Test
    void removingTheDefaultAddressMakesTheFirstRemainingOneTheDefault() {
        // given
        Store store = storeWith(address("a-1", "One", false), address("a-2", "Two", true), address("a-3", "Three", false));

        // when
        boolean removed = store.removeShippingDetails("a-2");

        // then
        assertThat(removed).isTrue();
        assertThat(store.getShippingDetails()).extracting(ShippingDetails::getId).containsExactly("a-1", "a-3");
        assertThat(store.getDefaultShippingDetails().getId()).isEqualTo("a-1");
    }

    @Test
    void removingAnUnknownAddressChangesNothing() {
        // given
        Store store = storeWith(address("a-1", "One", true));

        // when
        boolean removed = store.removeShippingDetails("missing");

        // then
        assertThat(removed).isFalse();
        assertThat(store.getShippingDetails()).hasSize(1);
    }

    @Test
    void givesAnIdToEveryAddressWithoutOneAndReportsWhetherAnythingChanged() {
        // given
        Store store = storeWith(address(null, "Legacy", true), address("a-2", "Two", false));

        // when
        boolean changed = store.assignMissingShippingDetailsIds();

        // then
        assertThat(changed).isTrue();
        assertThat(store.getShippingDetails()).allSatisfy(details -> assertThat(details.getId()).isNotBlank());
        assertThat(store.findShippingDetails("a-2")).isPresent();
        assertThat(store.assignMissingShippingDetailsIds()).isFalse();
    }
}
