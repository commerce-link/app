package pl.commercelink.orders.rma;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoresRepository;

import java.util.List;

@Component
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
@RequiredArgsConstructor
public class RMALifecycleCron {

    private final StoresRepository storesRepository;
    private final RMARepository rmaRepository;
    private final RMALifecycle rmaLifecycle;
    private final StoreActivity storeActivity;

    @SqsListener(
            value = "rma-lifecycle-queue",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void processDeliveredRma(String message) {
        List<Store> stores = storesRepository.findAll();

        for (Store store : stores) {
            if (!storeActivity.isActive(store)) {
                continue;
            }
            rmaRepository.findAllByStoreIdAndStatus(store.getStoreId(), RMAStatus.ItemsReceived)
                    .forEach(rma -> rmaLifecycle.update(rma));
        }
    }

}
