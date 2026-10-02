package pl.commercelink.orders.rma;

import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;

@Component
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
public class RMALifecycleCron {

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private RMARepository rmaRepository;

    @Autowired
    private RMALifecycle rmaLifecycle;

    @SqsListener(
            value = "rma-lifecycle-queue",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void processDeliveredRma(String message) {
        List<Store> stores = storesRepository.findAll();

        for (Store store : stores) {
            rmaRepository.findAllByStoreIdAndStatus(store.getStoreId(), RMAStatus.ItemsReceived)
                    .forEach(rma -> rmaLifecycle.update(rma));
        }
    }

}
