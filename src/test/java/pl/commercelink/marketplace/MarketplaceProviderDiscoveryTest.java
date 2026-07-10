package pl.commercelink.marketplace;

import org.junit.jupiter.api.Test;
import pl.commercelink.marketplace.api.MarketplaceProviderDescriptor;

import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketplaceProviderDiscoveryTest {

    @Test
    void allegroDescriptorIsDiscoverable() {
        // when
        boolean found = ServiceLoader.load(MarketplaceProviderDescriptor.class).stream()
                .anyMatch(p -> "Allegro".equals(p.get().name()));

        // then
        assertTrue(found);
    }
}
