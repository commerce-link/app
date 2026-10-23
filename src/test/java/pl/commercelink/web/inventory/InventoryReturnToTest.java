package pl.commercelink.web.inventory;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryReturnToTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "/dashboard/inventory",
            "/dashboard/inventory?cat=11",
            "/dashboard/inventory?cat=11&supplier=AB&page=3",
            "  /dashboard/inventory?q2=kabel%2Fhdmi  "})
    void acceptsTheInventoryPage(String value) {
        // when / then
        assertThat(InventoryReturnTo.safe(value)).contains(value.strip());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "", "//evil.com", "https://evil.com/dashboard/inventory", "/dashboard/inventory/../catalogs",
            "/dashboard/inventoryX", "/dashboard/catalogs", "/dashboard/inventory?next=//evil.com",
            "/dashboard/inventory\\evil", "/dashboard/inventory?cat=11 x",
            "/dashboard/inventory?q={x}", "/dashboard/inventory?q=}", "/dashboard/inventory/prices?q=123"})
    void refusesEverythingElse(String value) {
        // when / then
        assertThat(InventoryReturnTo.safe(value)).isEmpty();
    }
}
