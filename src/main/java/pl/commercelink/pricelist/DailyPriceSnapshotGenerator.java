package pl.commercelink.pricelist;

import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.starter.csv.CSVWriter;
import pl.commercelink.starter.storage.FileStorage;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
class DailyPriceSnapshotGenerator {

    @Autowired
    private Inventory inventory;
    @Autowired
    private FileStorage fileStorage;
    @Value("${s3.bucket.datalake}")
    private String bucketName;

    @SqsListener(
            value = "supplier-daily-price-snapshot-queue",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    void handleMessage(String message) throws IOException {
        byte[] result;
        List<DailyPriceSnapshot> rows = getDailyPriceSnapshots();
        try {
            result = new CSVWriter().writeAllRowsToBytes(rows, DailyPriceSnapshot.COLUMNS);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String fileName = "daily-price-snapshot/" + LocalDate.now() + ".csv";
        fileStorage.put(bucketName, fileName, result);
    }

    private List<DailyPriceSnapshot> getDailyPriceSnapshots() {
        List<DailyPriceSnapshot> rows = new ArrayList<>();
        for (MatchedInventory matchedInventory : inventory.withGlobalData().findAllWithPimId()) {
            if (matchedInventory.hasOffersFromMultipleSuppliers(2) || matchedInventory.hasTotalMinQty(2)) {
                rows.add(createSnapshot(matchedInventory));
            }
        }
        return rows;
    }

    private DailyPriceSnapshot createSnapshot(MatchedInventory matchedInventory) {
        List<InventoryItem> items = matchedInventory.getInventoryItems();

        Price lowestPrice = matchedInventory.getLowestPrice();
        Price medianPrice = matchedInventory.getMedianPrice();
        Price highestPrice = matchedInventory.getHighestPrice();

        // lookup item inventory item that is connected with the lowest price
        InventoryItem lowestPricedItem = items.stream()
                .filter(i -> Price.fromNet(i.netPrice()).netValue() == lowestPrice.netValue())
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No inventory item found with lowest price"));

        // lookup three best value distributors starting with the lowest priced one
        int lowestIndex = items.indexOf(lowestPricedItem);
        List<InventoryItem> bestValueItems = items.subList(lowestIndex, Math.min(lowestIndex + 3, items.size()));

        String bestValueDistributorsPipeSeparated = bestValueItems.stream()
                .map(InventoryItem::supplier)
                .collect(Collectors.joining("|"));
        String bestValuePricesPipeSeparated = bestValueItems.stream()
                .map(i -> String.valueOf(i.netPrice()))
                .collect(Collectors.joining("|"));

        // // all available distributors starting with the lowest priced one
        String allAvailableDistributorsPipeSeparated = items.subList(lowestIndex, items.size()).stream()
                .map(InventoryItem::supplier)
                .distinct()
                .collect(Collectors.joining("|"));

        return new DailyPriceSnapshot(
                matchedInventory.getInventoryKey().getId(),
                lowestPrice.netValue(),
                medianPrice.netValue(),
                highestPrice.netValue(),
                lowestPricedItem.qty(),
                matchedInventory.getTotalAvailableQty(),
                lowestPricedItem.supplier(),
                bestValueDistributorsPipeSeparated,
                bestValuePricesPipeSeparated,
                allAvailableDistributorsPipeSeparated,
                LocalDate.now()
        );
    }

}