package pl.commercelink.inventory.supplier;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.csv.CSVLoader;
import pl.commercelink.inventory.supplier.api.CsvRowParser;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.InventoryRepository;

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedList;
import java.util.List;

@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class CsvProductFeedLoader {

    private final InventoryRepository inventoryRepository;
    private final StoreFeedRepository storeFeedRepository;
    private final DataCleanup dataCleanup;
    private final FeedProcessor feedProcessor;

    public List<InventoryItem> fetch(CsvRowParser parser, Character separator, SupplierInfo supplierInfo) {
        String supplierName = supplierInfo.name();
        if (!inventoryRepository.canRead(supplierName)) {
            System.out.println("Skipping feed file for " + supplierName + " as it does not exist or is unreadable.");
            return new LinkedList<>();
        }
        try (Reader reader = inventoryRepository.read(supplierName)) {
            return parseRows(parser, separator, reader, supplierInfo, 0);
        } catch (IOException e) {
            System.out.println("Error reading feed file for " + supplierName + ": " + e.getMessage());
            return new LinkedList<>();
        }
    }

    public List<InventoryItem> fetch(CsvRowParser parser, Character separator, String storeId, SupplierInfo supplierInfo, int taxonomyPenalty) {
        String supplierName = supplierInfo.name();
        if (!storeFeedRepository.canRead(storeId, supplierName, "csv")) {
            System.out.println("Skipping store feed file for " + storeId + "/" + supplierName + " as it does not exist or is unreadable.");
            return new LinkedList<>();
        }
        try (Reader reader = storeFeedRepository.read(storeId, supplierName, "csv")) {
            return parseRows(parser, separator, reader, supplierInfo, taxonomyPenalty);
        } catch (IOException e) {
            System.out.println("Error reading store feed file for " + storeId + "/" + supplierName + ": " + e.getMessage());
            return new LinkedList<>();
        }
    }

    private List<InventoryItem> parseRows(CsvRowParser parser, Character separator, Reader reader, SupplierInfo supplierInfo, int taxonomyPenalty) {
        FeedProcessing processing = feedProcessor.start(supplierInfo.name(), taxonomyPenalty);
        new CSVLoader(reader).readRows(separator, row ->
                ParsedRow.tryParse(parser, row, supplierInfo).ifPresentOrElse(processing::add, processing::skipInvalid));
        return dataCleanup.run(processing.finish());
    }

}
