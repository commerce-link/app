package pl.commercelink.orders.rma;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoresRepository;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RMALifecycleCronTest {

    @Mock private StoresRepository storesRepository;
    @Mock private RMARepository rmaRepository;
    @Mock private RMALifecycle rmaLifecycle;
    @Mock private StoreActivity storeActivity;

    @InjectMocks
    private RMALifecycleCron cron;

    private static Store store(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        return store;
    }

    @Test
    void updatesReturnsOfActiveStoresOnly() {
        // given
        Store active = store("active");
        Store inactive = store("inactive");
        RMA rma = new RMA();
        when(storesRepository.findAll()).thenReturn(List.of(active, inactive));
        when(storeActivity.isActive(active)).thenReturn(true);
        when(storeActivity.isActive(inactive)).thenReturn(false);
        when(rmaRepository.findAllByStoreIdAndStatus("active", RMAStatus.ItemsReceived)).thenReturn(List.of(rma));

        // when
        cron.processDeliveredRma("tick");

        // then
        verify(rmaLifecycle).update(rma);
        verify(rmaRepository, never()).findAllByStoreIdAndStatus(eq("inactive"), eq(RMAStatus.ItemsReceived));
    }
}
