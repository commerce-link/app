package pl.commercelink.stores;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StoreCreatedAtTest {

    @Test
    void storesCreatedAtTimestamp() {
        // given
        Store store = new Store();

        // when
        store.setCreatedAt("2026-07-13T10:00:00Z");

        // then
        assertEquals("2026-07-13T10:00:00Z", store.getCreatedAt());
    }

    @Test
    void createdAtDefaultsToNull() {
        // given
        Store store = new Store();

        // when / then
        assertNull(store.getCreatedAt());
    }
}
