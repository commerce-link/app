package pl.commercelink.marketplace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.marketplace.api.MarketplaceExportReport;
import pl.commercelink.marketplace.api.MarketplaceOffer;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketplaceOfferWithdrawalTest {

    private static final String STORE_ID = "store-1";
    private static final String MARKETPLACE = "Allegro";

    @Mock private ProductCatalogRepository productCatalogRepository;
    @Mock private MarketplaceProviderFactory providerFactory;
    @Mock private MarketplaceExportRunService marketplaceExportRunService;
    @Mock private MarketplaceProvider provider;

    @InjectMocks
    private MarketplaceOfferWithdrawal withdrawal;

    private final ProductCatalog catalog = new ProductCatalog(STORE_ID, "Main");
    private Store store;

    @BeforeEach
    void setUp() {
        store = new Store();
        store.setStoreId(STORE_ID);
        MarketplaceIntegration integration = new MarketplaceIntegration(MARKETPLACE);
        integration.setLoggedIn(true);
        store.getMarketplaces().add(integration);
        when(productCatalogRepository.findAll(STORE_ID)).thenReturn(List.of(catalog));
    }

    private void lastExport(MarketplaceOfferSnapshot... offers) {
        when(marketplaceExportRunService.loadPreviousExport(STORE_ID, catalog.getCatalogId(), MARKETPLACE))
                .thenReturn(List.of(offers));
    }

    private MarketplaceExportRun savedRun() {
        ArgumentCaptor<MarketplaceExportRun> run = ArgumentCaptor.forClass(MarketplaceExportRun.class);
        verify(marketplaceExportRunService).saveRun(run.capture());
        return run.getValue();
    }

    @Test
    void sendsEveryOfferOfTheLastExportAgainWithNoStock() {
        // given
        when(providerFactory.get(store, MARKETPLACE)).thenReturn(provider);
        lastExport(MarketplaceOfferSnapshot.published("pim-1", 100, 5),
                MarketplaceOfferSnapshot.removalPending("pim-2", 50, 1));

        // when
        withdrawal.withdrawAll(store);

        // then
        ArgumentCaptor<List<MarketplaceOffer>> current = listCaptor();
        ArgumentCaptor<List<MarketplaceOffer>> removals = listCaptor();
        verify(provider).exportOffers(current.capture(), removals.capture(), any(MarketplaceExportReport.class));
        assertThat(current.getValue()).isEmpty();
        assertThat(removals.getValue()).containsExactly(
                new MarketplaceOffer("pim-1", null, null, null, null, null, 100, 0, 0),
                new MarketplaceOffer("pim-2", null, null, null, null, null, 50, 0, 0));
    }

    @Test
    void savesTheWithdrawalAsRunThatTheNextExportStartsFrom() {
        // given
        when(providerFactory.get(store, MARKETPLACE)).thenReturn(provider);
        lastExport(MarketplaceOfferSnapshot.published("pim-1", 100, 5),
                MarketplaceOfferSnapshot.removalPending("pim-2", 50, 1));

        // when
        withdrawal.withdrawAll(store);

        // then
        MarketplaceExportRun run = savedRun();
        assertThat(run.getStoreId()).isEqualTo(STORE_ID);
        assertThat(run.getMarketplace()).isEqualTo(MARKETPLACE);
        assertThat(run.getCatalogId()).isEqualTo(catalog.getCatalogId());
        assertThat(run.isFailed()).isFalse();
        assertThat(run.toRows()).containsExactly(
                MarketplaceOfferSnapshot.removalPending("pim-1", 100, 1),
                MarketplaceOfferSnapshot.removalPending("pim-2", 50, 2));
    }

    @Test
    void recordsOffersTheMarketplaceRejected() {
        // given
        when(providerFactory.get(store, MARKETPLACE)).thenReturn(provider);
        lastExport(MarketplaceOfferSnapshot.published("pim-1", 100, 5));
        doAnswer(invocation -> {
            MarketplaceExportReport report = invocation.getArgument(2);
            report.rejected("pim-1", "NOT_FOUND", "no such offer");
            return null;
        }).when(provider).exportOffers(anyList(), anyList(), any(MarketplaceExportReport.class));

        // when
        withdrawal.withdrawAll(store);

        // then
        assertThat(savedRun().toRows()).extracting(MarketplaceOfferSnapshot::outcome)
                .containsExactly(MarketplaceOfferSnapshot.OUTCOME_REJECTED);
    }

    @Test
    void failedWithdrawalIsSavedAsFailedRunAndReported() {
        // given
        when(providerFactory.get(store, MARKETPLACE)).thenReturn(provider);
        lastExport(MarketplaceOfferSnapshot.published("pim-1", 100, 5));
        RuntimeException outage = new RuntimeException("marketplace down");
        doThrow(outage).when(provider).exportOffers(anyList(), anyList(), any(MarketplaceExportReport.class));

        // when
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> withdrawal.withdrawAll(store));

        // then
        assertSame(outage, thrown);
        assertThat(savedRun().isFailed()).isTrue();
    }

    @Test
    void catalogNeverExportedToTheMarketplaceIsSkipped() {
        // given
        when(providerFactory.get(store, MARKETPLACE)).thenReturn(provider);
        lastExport();

        // when
        withdrawal.withdrawAll(store);

        // then
        verifyNoInteractions(provider);
        verify(marketplaceExportRunService, never()).saveRun(any());
    }

    @Test
    void marketplaceWithLostConnectionIsSkipped() {
        // given
        store.getMarketplaces().getFirst().setLoggedIn(false);

        // when
        withdrawal.withdrawAll(store);

        // then
        verifyNoInteractions(providerFactory, marketplaceExportRunService);
    }

    @Test
    void marketplaceWithoutProviderIsSkipped() {
        // given
        when(providerFactory.get(store, MARKETPLACE)).thenReturn(null);

        // when
        withdrawal.withdrawAll(store);

        // then
        verifyNoInteractions(marketplaceExportRunService);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<MarketplaceOffer>> listCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }
}
