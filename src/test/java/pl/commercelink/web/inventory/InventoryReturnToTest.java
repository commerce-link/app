package pl.commercelink.web.inventory;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryReturnToTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "/dashboard/inventory",
            "/dashboard/inventory?view=browse",
            "/dashboard/inventory?view=browse&cat=11&supplier=AB&page=3",
            "  /dashboard/inventory?view=browse&q2=kabel%2Fhdmi  "})
    void acceptsTheInventoryPage(String value) {
        // when / then
        assertThat(InventoryReturnTo.safe(value)).contains(value.strip());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "", "//evil.com", "https://evil.com/dashboard/inventory", "/dashboard/inventory/../catalogs",
            "/dashboard/inventoryX", "/dashboard/catalogs", "/dashboard/inventory?next=//evil.com",
            "/dashboard/inventory\\evil", "/dashboard/inventory?view=browse x"})
    void refusesEverythingElse(String value) {
        // when / then
        assertThat(InventoryReturnTo.safe(value)).isEmpty();
    }
}
