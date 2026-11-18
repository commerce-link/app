package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.pim.api.PIMEntryAddedEvent;
import pl.commercelink.pim.api.PIMEntryDeletedEvent;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimEntryIndex;
import pl.commercelink.pim.api.PimEntryRequest;
import pl.commercelink.pim.api.PimIdentifier;
import pl.commercelink.pim.api.PimIdentifierType;
import pl.commercelink.pim.api.PimView;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EanOnlyCodesTest {

    @Mock
    private PimCatalog pimCatalog;

    @Test
    void computesOnFirstUse() {
        // given
        when(pimCatalog.withGlobalData()).thenReturn(indexOf(flagged("SHINE10")).global());
        EanOnlyCodes eanOnlyCodes = new EanOnlyCodes(pimCatalog, InventoryMatchingProperties.allEnabled());
        verify(pimCatalog, never()).withGlobalData();

        // when
        VariantCodes first = eanOnlyCodes.current();
        VariantCodes second = eanOnlyCodes.current();

        // then
        assertThat(first.contains("mova", "SHINE10")).isTrue();
        assertThat(second).isSameAs(first);
        verify(pimCatalog, times(1)).withGlobalData();
    }

    @Test
    void refreshPicksUpAChangedIndex() {
        // given
        when(pimCatalog.withGlobalData())
                .thenReturn(indexOf(flagged("SHINE10")).global())
                .thenReturn(indexOf(flagged("SHINE11")).global());
        EanOnlyCodes eanOnlyCodes = new EanOnlyCodes(pimCatalog, InventoryMatchingProperties.allEnabled());
        eanOnlyCodes.current();

        // when
        VariantCodes refreshed = eanOnlyCodes.refresh();

        // then
        assertThat(refreshed.contains("mova", "SHINE11")).isTrue();
        assertThat(refreshed.contains("mova", "SHINE10")).isFalse();
        assertThat(eanOnlyCodes.current()).isSameAs(refreshed);
    }

    @Test
    void switchOffAlwaysGivesNone() {
        // given
        EanOnlyCodes eanOnlyCodes = new EanOnlyCodes(pimCatalog, switchedOff());

        // when
        VariantCodes current = eanOnlyCodes.current();
        VariantCodes refreshed = eanOnlyCodes.refresh();

        // then
        assertThat(current).isSameAs(VariantCodes.none());
        assertThat(refreshed).isSameAs(VariantCodes.none());
        verify(pimCatalog, never()).withGlobalData();
    }

    @Test
    void emptyCatalogGivesNone() {
        // given
        when(pimCatalog.withGlobalData()).thenReturn(indexOf().global());

        // when
        VariantCodes current = new EanOnlyCodes(pimCatalog, InventoryMatchingProperties.allEnabled()).current();

        // then
        assertThat(current).isSameAs(VariantCodes.none());
    }

    @Test
    void refreshIsNotOverwrittenByAnEarlierFirstUse() throws Exception {
        // given -- hand-written stubs, so the only lock the refresh thread can wait on is the one of EanOnlyCodes
        CountDownLatch firstUseReading = new CountDownLatch(1);
        CountDownLatch releaseFirstUse = new CountDownLatch(1);
        PimView slowBefore = new BlockingView(indexOf(flagged("SHINE10")).global(), firstUseReading, releaseFirstUse);
        PimCatalog catalog = new SwappingCatalog(slowBefore, indexOf(flagged("SHINE11")).global());
        EanOnlyCodes eanOnlyCodes = new EanOnlyCodes(catalog, InventoryMatchingProperties.allEnabled());
        Thread firstUse = new Thread(eanOnlyCodes::current);
        firstUse.start();
        assertThat(firstUseReading.await(5, TimeUnit.SECONDS)).isTrue();

        // when -- the first use is released only once the refresh either finished (no exclusion) or waits for the
        // lock held by the first use (exclusion)
        CountDownLatch refreshCalled = new CountDownLatch(1);
        Thread refresh = new Thread(() -> {
            refreshCalled.countDown();
            eanOnlyCodes.refresh();
        });
        refresh.start();
        assertThat(refreshCalled.await(5, TimeUnit.SECONDS)).isTrue();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (refresh.getState() != Thread.State.BLOCKED && refresh.getState() != Thread.State.TERMINATED) {
            assertThat(System.nanoTime()).as("refresh neither waiting nor finished").isLessThan(deadline);
            Thread.onSpinWait();
        }
        releaseFirstUse.countDown();
        firstUse.join(5000);
        refresh.join(5000);

        // then
        assertThat(firstUse.isAlive()).isFalse();
        assertThat(refresh.isAlive()).isFalse();
        assertThat(eanOnlyCodes.current().contains("mova", "SHINE11")).isTrue();
        assertThat(eanOnlyCodes.current().contains("mova", "SHINE10")).isFalse();
    }

    /** The first global view asked for is the given one, every later one the other. */
    private static final class SwappingCatalog implements PimCatalog {

        private final AtomicBoolean firstServed = new AtomicBoolean();
        private final PimView first;
        private final PimView later;

        private SwappingCatalog(PimView first, PimView later) {
            this.first = first;
            this.later = later;
        }

        @Override
        public PimView withGlobalData() {
            return firstServed.compareAndSet(false, true) ? first : later;
        }

        @Override
        public PimView withStoreData(String storeId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void submit(PimEntryRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void refresh() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onEntryAdded(Consumer<PIMEntryAddedEvent> handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onEntryDeleted(Consumer<PIMEntryDeletedEvent> handler) {
            throw new UnsupportedOperationException();
        }
    }

    /** A view whose findAll signals that it started and returns only once released. */
    private static final class BlockingView implements PimView {

        private final PimView delegate;
        private final CountDownLatch reading;
        private final CountDownLatch release;

        private BlockingView(PimView delegate, CountDownLatch reading, CountDownLatch release) {
            this.delegate = delegate;
            this.reading = reading;
            this.release = release;
        }

        @Override
        public List<PimEntry> findAll() {
            reading.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("never released");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return delegate.findAll();
        }

        @Override
        public Optional<PimEntry> findByPimId(String pimId) {
            return delegate.findByPimId(pimId);
        }

        @Override
        public Optional<PimEntry> findByGtin(String gtin) {
            return delegate.findByGtin(gtin);
        }

        @Override
        public Optional<PimEntry> findByMpnAndBrand(String mpn, String brand) {
            return delegate.findByMpnAndBrand(mpn, brand);
        }

        @Override
        public Optional<PimEntry> findByGtinOrMpnAndBrand(String gtin, String mpn, String brand) {
            return delegate.findByGtinOrMpnAndBrand(gtin, mpn, brand);
        }
    }

    private static InventoryMatchingProperties switchedOff() {
        return new InventoryMatchingProperties(true, true, true, true, false);
    }

    private static PimEntryIndex indexOf(PimEntry... entries) {
        return new PimEntryIndex(List.of(entries), UnaryOperator.identity(), UnaryOperator.identity());
    }

    private static PimEntry flagged(String mpn) {
        return new PimEntry("pim-" + mpn, List.of(
                new PimIdentifier("5901234567801", PimIdentifierType.GTIN),
                new PimIdentifier(mpn, PimIdentifierType.MPN)),
                "Mova", "Name", null, null, true, null, null, null, null, null, true);
    }
}
