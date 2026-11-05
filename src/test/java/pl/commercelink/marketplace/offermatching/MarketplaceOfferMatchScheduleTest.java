package pl.commercelink.marketplace.offermatching;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.testsupport.OptimisticLockingExecutorMocks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketplaceOfferMatchScheduleTest {

    private static final String STORE_ID = "store-1";
    private static final String EMPIK = "Empik";

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private OptimisticLockingExecutor optimisticLockingExecutor;

    @InjectMocks
    private MarketplaceOfferMatchSchedule schedule;

    @BeforeEach
    void setUp() {
        doAnswer(OptimisticLockingExecutorMocks.retryingModifyAndSave(3))
                .when(optimisticLockingExecutor).modifyAndSave(any(), any(), any());
    }

    private static Store storeConnectedTo(String marketplace, Boolean scheduled) {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        MarketplaceIntegration integration = new MarketplaceIntegration(marketplace);
        integration.setOfferMatchingScheduled(scheduled);
        store.getMarketplaces().add(integration);
        return store;
    }

    @Test
    void switchingOnSavesTheConnectionAsRunningDaily() {
        // given
        Store store = storeConnectedTo(EMPIK, null);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);

        // when
        boolean changed = schedule.set(STORE_ID, EMPIK, true);

        // then
        assertThat(changed).isTrue();
        verify(storesRepository).save(store);
        assertThat(store.getMarketplaceIntegration(EMPIK).runsOfferMatchingDaily()).isTrue();
    }

    @Test
    void switchingOffSavesTheConnectionAsNotRunningDaily() {
        // given
        Store store = storeConnectedTo(EMPIK, true);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);

        // when
        schedule.set(STORE_ID, EMPIK, false);

        // then
        verify(storesRepository).save(store);
        assertThat(store.getMarketplaceIntegration(EMPIK).getOfferMatchingScheduled()).isFalse();
    }

    @Test
    void anUnchangedSettingIsNotSavedAgain() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(storeConnectedTo(EMPIK, true));

        // when
        boolean changed = schedule.set(STORE_ID, EMPIK, true);

        // then
        assertThat(changed).isFalse();
        verify(storesRepository, never()).save(any());
    }

    @Test
    void switchingOffAConnectionThatNeverRanDailyIsNotSaved() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(storeConnectedTo(EMPIK, null));

        // when
        schedule.set(STORE_ID, EMPIK, false);

        // then
        verify(storesRepository, never()).save(any());
    }

    @Test
    void aStoreWithoutTheConnectionIsLeftAsItIs() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(storeConnectedTo("Allegro", null));

        // when
        schedule.set(STORE_ID, EMPIK, true);

        // then
        verify(storesRepository, never()).save(any());
    }

    @Test
    void aMissingStoreIsLeftAsItIs() {
        // when
        schedule.set(STORE_ID, EMPIK, true);

        // then
        verify(storesRepository, never()).save(any());
    }

    @Test
    void aConcurrentChangeOfTheStoreIsReadAgainSoItIsNotOverwritten() {
        // given
        Store stale = storeConnectedTo(EMPIK, null);
        Store fresh = storeConnectedTo(EMPIK, null);
        fresh.setName("renamed meanwhile");
        when(storesRepository.findById(STORE_ID)).thenReturn(stale, fresh);
        doThrow(new ConditionalCheckFailedException("version changed")).when(storesRepository).save(stale);

        // when
        schedule.set(STORE_ID, EMPIK, true);

        // then
        verify(storesRepository).save(fresh);
        assertThat(fresh.getName()).isEqualTo("renamed meanwhile");
        assertThat(fresh.getMarketplaceIntegration(EMPIK).runsOfferMatchingDaily()).isTrue();
    }
}
