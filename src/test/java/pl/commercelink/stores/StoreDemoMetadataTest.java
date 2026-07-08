package pl.commercelink.stores;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class StoreDemoMetadataTest {

    @Test
    void regularStoreIsNeverExpired() {
        // given
        Store store = new Store();

        // when / then
        assertFalse(store.isDemoExpired(Instant.parse("2026-07-08T12:00:00Z")));
    }

    @Test
    void demoStoreIsExpiredAfterExpiryInstant() {
        // given
        Store store = new Store();
        store.setDemo(new DemoStoreMetadata("user@example.com", "2026-07-01T00:00:00Z", "2026-07-08T00:00:00Z"));

        // when / then
        assertTrue(store.isDemoExpired(Instant.parse("2026-07-08T00:00:01Z")));
        assertFalse(store.isDemoExpired(Instant.parse("2026-07-07T23:59:59Z")));
    }
}
