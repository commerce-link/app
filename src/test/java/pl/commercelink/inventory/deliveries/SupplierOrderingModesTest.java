package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.SupplierProviderResolver;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupplierOrderingModesTest {

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private SupplierProviderResolver resolver;
    @Mock
    private Store store;

    private SupplierOrderingModes modes() {
        return new SupplierOrderingModes(storesRepository, resolver, Duration.ofMillis(300));
    }

    @Test
    void aSupplierThatSupportsOrderingIsApiAndAGlobalOneNeedsApproval() {
        // given
        when(storesRepository.findById("store-1")).thenReturn(store);
        SupplierProvider api = mock(SupplierProvider.class);
        when(api.supportsOrdering()).thenReturn(true);
        when(resolver.resolve(store, "Acme")).thenReturn(api);
        when(resolver.resolve(store, "Incom")).thenReturn(api);
        when(store.isGlobalSupplier("Acme")).thenReturn(false);
        when(store.isGlobalSupplier("Incom")).thenReturn(true);

        // when
        Map<String, SupplierOrderingModes.OrderingMode> result = modes().of("store-1", List.of("Acme", "Incom", "Acme"));

        // then
        assertThat(result).containsEntry("Acme", new SupplierOrderingModes.OrderingMode(true, false))
                .containsEntry("Incom", new SupplierOrderingModes.OrderingMode(true, true));
        verify(storesRepository, times(1)).findById("store-1");
        verify(resolver, times(1)).resolve(store, "Acme");
    }

    @Test
    void aThrowingAdapterMeansManualAndDoesNotHideTheOthers() {
        // given
        when(storesRepository.findById("store-1")).thenReturn(store);
        SupplierProvider api = mock(SupplierProvider.class);
        when(api.supportsOrdering()).thenReturn(true);
        when(resolver.resolve(store, "Acme")).thenReturn(api);
        when(resolver.resolve(store, "Broken")).thenThrow(new IllegalStateException("secret missing"));
        when(resolver.resolve(store, "Feed")).thenReturn(null);
        when(store.isGlobalSupplier("Acme")).thenReturn(false);
        when(store.isGlobalSupplier("Broken")).thenReturn(false);
        when(store.isGlobalSupplier("Feed")).thenReturn(false);

        // when
        Map<String, SupplierOrderingModes.OrderingMode> result = modes().of("store-1", List.of("Acme", "Broken", "Feed"));

        // then
        assertThat(result.get("Acme").api()).isTrue();
        assertThat(result.get("Broken").api()).isFalse();
        assertThat(result.get("Feed").api()).isFalse();
    }

    @Test
    void aHangingCheckTimesOutToManual() {
        // given
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(resolver.resolve(store, "Slow")).thenAnswer(invocation -> {
            Thread.sleep(5_000);
            return null;
        });
        when(store.isGlobalSupplier("Slow")).thenReturn(false);

        // when
        long start = System.nanoTime();
        Map<String, SupplierOrderingModes.OrderingMode> result = modes().of("store-1", List.of("Slow"));

        // then
        assertThat(result.get("Slow").api()).isFalse();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void aMissingStoreGivesManualForEverySupplier() {
        // given
        when(storesRepository.findById("gone")).thenReturn(null);

        // when
        Map<String, SupplierOrderingModes.OrderingMode> result = modes().of("gone", List.of("Acme"));

        // then
        assertThat(result).containsEntry("Acme", SupplierOrderingModes.OrderingMode.MANUAL);
        verifyNoInteractions(resolver);
    }
}
