package pl.commercelink.pricelist;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.starter.csv.CSVWriter;
import pl.commercelink.starter.storage.FileStorage;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.SupplierScope;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@Service
@Slf4j
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
class DailyPriceSnapshotGenerator {

    static final String QUEUE = "supplier-daily-price-snapshot-queue";

    private final Inventory inventory;
    private final FileStorage fileStorage;
    private final PriceDataFanOut fanOut;
    private final StoreActivity storeActivity;
    private final String datalakeBucket;
    private final String storesBucket;

    DailyPriceSnapshotGenerator(Inventory inventory,
                                FileStorage fileStorage,
                                PriceDataFanOut fanOut,
                                StoreActivity storeActivity,
                                @Value("${s3.bucket.datalake}") String datalakeBucket,
                                @Value("${s3.bucket.stores}") String storesBucket) {
        this.inventory = inventory;
        this.fileStorage = fileStorage;
        this.fanOut = fanOut;
        this.storeActivity = storeActivity;
        this.datalakeBucket = datalakeBucket;
        this.storesBucket = storesBucket;
    }

    @SqsListener(
            value = QUEUE,
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    void handleMessage(String body) throws IOException {
        PriceDataMessage message = PriceDataMessage.parse(body);
        LocalDate day = message.dayOrToday();

        if (message.isForStore()) {
            generateForStore(message.storeId(), day);
            return;
        }

        // Fan out first: a failing global snapshot must not leave the stores without theirs
        fanOut.toActiveStores(QUEUE, day);
        write(PriceDataLocation.global(datalakeBucket), DailyPriceSnapshotBuilder.build(inventory.withGlobalData(), day), day);
    }

    private void generateForStore(String storeId, LocalDate day) throws IOException {
        if (!storeActivity.isActive(storeId)) {
            log.warn("Daily price snapshot skipped for store {}: the store is inactive", storeId);
            return;
        }
        InventoryView view = inventory.withEnabledSuppliersAndWarehouseData(storeId, SupplierScope.PRICING);
        List<DailyPriceSnapshot> rows = DailyPriceSnapshotBuilder.build(view, day);
        if (rows.isEmpty()) {
            return;
        }
        write(PriceDataLocation.store(storesBucket, storeId), rows, day);
    }

    private void write(PriceDataLocation location, List<DailyPriceSnapshot> rows, LocalDate day) throws IOException {
        byte[] csv = new CSVWriter().writeAllRowsToBytes(rows, DailyPriceSnapshot.COLUMNS);
        fileStorage.put(location.bucket(), location.snapshotKey(day), csv);
    }
}
