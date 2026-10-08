package pl.commercelink.pricelist;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDate;
import java.util.List;

/** Turns the global trigger of a queue into one message per active store, each processed (and retried) on its own. */
@Component
@Slf4j
@RequiredArgsConstructor
class PriceDataFanOut {

    private final StoresRepository storesRepository;
    private final StoreActivity storeActivity;
    private final SqsTemplate sqsTemplate;

    void toActiveStores(String queue, LocalDate date) {
        List<Store> stores;
        try {
            stores = storesRepository.findAll();
        } catch (Exception e) {
            log.error("Price data was not queued on {}: the stores could not be listed", queue, e);
            return;
        }
        for (Store store : stores) {
            if (!storeActivity.isActive(store)) {
                log.warn("Price data of store {} skipped: the store is inactive", store.getStoreId());
                continue;
            }
            try {
                sqsTemplate.send(queue, PriceDataMessage.forStore(store.getStoreId(), date));
            } catch (Exception e) {
                log.error("Price data of store {} was not queued on {}", store.getStoreId(), queue, e);
            }
        }
    }
}
