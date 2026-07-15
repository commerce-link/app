package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.stores.DemoStoreMetadata;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoEnvironmentBannerAdviceTest {

    private static final Instant NOW = Instant.parse("2026-07-08T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private StoresRepository storesRepository;

    @Test
    void bannerDisabledOutsideDemoEnvironment() {
        // given
        DemoEnvironmentBannerAdvice advice = new DemoEnvironmentBannerAdvice(storesRepository, FIXED_CLOCK, false);

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
        store.setDemo(new DemoStoreMetadata("a@b.pl", "2026-07-08T00:00:00Z", "2026-07-10T12:01:00Z"));
        when(storesRepository.findById("s-1")).thenReturn(store);
        DemoEnvironmentBannerAdvice advice = adviceWithStoreId("s-1", true);

        // when / then
        assertTrue(advice.demoEnvironment());
        assertNotNull(advice.demoExpiresAt());
        assertEquals(3L, advice.demoDaysLeft());
    }

    @Test
    void bannerRoundsExactDayCountDown() {
        // given
        Store store = new Store();
        store.setDemo(new DemoStoreMetadata("a@b.pl", "2026-07-08T00:00:00Z", "2026-07-10T12:00:00Z"));
        when(storesRepository.findById("s-1")).thenReturn(store);
        DemoEnvironmentBannerAdvice advice = adviceWithStoreId("s-1", true);

        // when / then
        assertEquals(2L, advice.demoDaysLeft());
    }

    @Test
    void expiredStoreClampsDaysLeftToZero() {
        // given
        Store store = new Store();
        store.setDemo(new DemoStoreMetadata("a@b.pl", "2026-07-01T00:00:00Z", "2026-07-07T12:00:00Z"));
        when(storesRepository.findById("s-1")).thenReturn(store);
        DemoEnvironmentBannerAdvice advice = adviceWithStoreId("s-1", true);

        // when / then
        assertEquals(0L, advice.demoDaysLeft());
    }

    @Test
    void bannerHiddenWhenExpiresAtMissing() {
        // given
        Store store = new Store();
        store.setDemo(new DemoStoreMetadata("a@b.pl", "2026-07-08T00:00:00Z", null));
        when(storesRepository.findById("s-1")).thenReturn(store);
        DemoEnvironmentBannerAdvice advice = adviceWithStoreId("s-1", true);

        // when / then
        assertNull(advice.demoExpiresAt());
        assertNull(advice.demoDaysLeft());
    }

    @Test
    void bannerHiddenWhenStoreNotFound() {
        // given
        when(storesRepository.findById("s-1")).thenReturn(null);
        DemoEnvironmentBannerAdvice advice = adviceWithStoreId("s-1", true);

        // when / then
        assertNull(advice.demoExpiresAt());
        assertNull(advice.demoDaysLeft());
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
        return new DemoEnvironmentBannerAdvice(storesRepository, FIXED_CLOCK, demoEnvironment) {
            @Override
            String currentStoreId() {
                return storeId;
            }
        };
    }
}
