package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.stores.DemoStoreMetadata;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoEnvironmentBannerAdviceTest {

    @Mock
    private StoresRepository storesRepository;

    @Test
    void bannerDisabledOutsideDemoEnvironment() {
        // given
        DemoEnvironmentBannerAdvice advice = new DemoEnvironmentBannerAdvice(storesRepository, false);

        // when / then
        assertFalse(advice.demoEnvironment());
        assertNull(advice.demoExpiresAt());
    }

    @Test
    void bannerEnabledOnDemoEnvironmentWithoutStore() {
        // given
        DemoEnvironmentBannerAdvice advice = adviceWithStoreId(null, true);

        // when / then
        assertTrue(advice.demoEnvironment());
        assertNull(advice.demoExpiresAt());
        assertNull(advice.demoDaysLeft());
    }

    @Test
    void bannerShowsExpiryForDemoStore() {
        // given
        Store store = new Store();
        store.setDemo(new DemoStoreMetadata("a@b.pl", Instant.now().toString(),
                Instant.now().plusSeconds(2 * 24 * 3600 + 60).toString()));
        when(storesRepository.findById("s-1")).thenReturn(store);
        DemoEnvironmentBannerAdvice advice = adviceWithStoreId("s-1", true);

        // when / then
        assertTrue(advice.demoEnvironment());
        assertNotNull(advice.demoExpiresAt());
        assertEquals(2L, advice.demoDaysLeft());
    }

    @Test
    void toleratesMalformedExpiresAt() {
        // given
        Store store = new Store();
        store.setDemo(new DemoStoreMetadata("a@b.pl", "x", "garbage"));
        when(storesRepository.findById("s-1")).thenReturn(store);
        DemoEnvironmentBannerAdvice advice = adviceWithStoreId("s-1", true);

        // when / then
        assertTrue(advice.demoEnvironment());
        assertNull(advice.demoExpiresAt());
        assertNull(advice.demoDaysLeft());
    }

    private DemoEnvironmentBannerAdvice adviceWithStoreId(String storeId, boolean demoEnvironment) {
        return new DemoEnvironmentBannerAdvice(storesRepository, demoEnvironment) {
            @Override
            String currentStoreId() {
                return storeId;
            }
        };
    }
}
