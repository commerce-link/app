package pl.commercelink.pricelist;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceDataFanOutTest {

    private static final String QUEUE = "supplier-daily-price-snapshot-queue";
    private static final LocalDate DAY = LocalDate.of(2026, 10, 7);

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private StoreActivity storeActivity;
    @Mock
    private SqsTemplate sqsTemplate;

    @InjectMocks
    private PriceDataFanOut fanOut;

    @Test
    void everyActiveStoreGetsItsOwnMessage() {
        // given
        Store first = store("store-1");
        Store second = store("store-2");
        when(storesRepository.findAll()).thenReturn(List.of(first, second));
        when(storeActivity.isActive(first)).thenReturn(true);
        when(storeActivity.isActive(second)).thenReturn(true);

        // when
        fanOut.toActiveStores(QUEUE, DAY);

        // then
        verify(sqsTemplate).send(QUEUE, PriceDataMessage.forStore("store-1", DAY));
        verify(sqsTemplate).send(QUEUE, PriceDataMessage.forStore("store-2", DAY));
    }

    @Test
    void inactiveStoreGetsNoMessage() {
        // given
        Store inactive = store("store-1");
        when(storesRepository.findAll()).thenReturn(List.of(inactive));
        when(storeActivity.isActive(inactive)).thenReturn(false);

        // when
        fanOut.toActiveStores(QUEUE, DAY);

        // then
        verifyNoInteractions(sqsTemplate);
    }

    @Test
    void failedSendDoesNotStopTheOtherStores() {
        // given
        Store first = store("store-1");
        Store second = store("store-2");
        when(storesRepository.findAll()).thenReturn(List.of(first, second));
        when(storeActivity.isActive(first)).thenReturn(true);
        when(storeActivity.isActive(second)).thenReturn(true);
        doThrow(new IllegalStateException("queue unavailable")).when(sqsTemplate).send(eq(QUEUE), eq(PriceDataMessage.forStore("store-1", DAY)));

        // when / then
        assertDoesNotThrow(() -> fanOut.toActiveStores(QUEUE, DAY));
        verify(sqsTemplate).send(QUEUE, PriceDataMessage.forStore("store-2", DAY));
    }

    @Test
    void storesThatCannotBeListedDoNotFailTheRun() {
        // given
        when(storesRepository.findAll()).thenThrow(new IllegalStateException("dynamodb unavailable"));

        // when / then
        assertDoesNotThrow(() -> fanOut.toActiveStores(QUEUE, DAY));
        verify(sqsTemplate, never()).send(eq(QUEUE), any(PriceDataMessage.class));
    }

    private Store store(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        return store;
    }
}
