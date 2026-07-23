package pl.commercelink.inventory.supplier;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.csv.CSVLoader;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.CsvRowParser;
import pl.commercelink.inventory.InventoryRepository;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class CsvProductFeedLoader {

    private final InventoryRepository inventoryRepository;
    private final StoreFeedRepository storeFeedRepository;
    private final DataCleanup dataCleanup;
    private final FeedRowProcessor feedRowProcessor;

    public List<InventoryItem> fetch(CsvRowParser parser, Character separator, String supplierName) {
        if (!inventoryRepository.canRead(supplierName)) {
            System.out.println("Skipping feed file for " + supplierName + " as it does not exist or is unreadable.");
            return new LinkedList<>();
        }
        try (Reader reader = inventoryRepository.read(supplierName)) {
            return parseRows(parser, separator, reader, supplierName, 0);
        } catch (IOException e) {
            System.out.println("Error reading feed file for " + supplierName + ": " + e.getMessage());
            return new LinkedList<>();
        }
    }

    public List<InventoryItem> fetch(CsvRowParser parser, Character separator, String storeId, String supplierName, int taxonomyPenalty) {
        if (!storeFeedRepository.canRead(storeId, supplierName, "csv")) {
            System.out.println("Skipping store feed file for " + storeId + "/" + supplierName + " as it does not exist or is unreadable.");
            return new LinkedList<>();
        }
        try (Reader reader = storeFeedRepository.read(storeId, supplierName, "csv")) {
            return parseRows(parser, separator, reader, supplierName, taxonomyPenalty);
        } catch (IOException e) {
            System.out.println("Error reading store feed file for " + storeId + "/" + supplierName + ": " + e.getMessage());
            return new LinkedList<>();
        }
    }

    private List<InventoryItem> parseRows(CsvRowParser parser, Character separator, Reader reader, String supplierName, int taxonomyPenalty) {
        List<InventoryItem> res = new ArrayList<>();
        FeedParseStats stats = new FeedParseStats(supplierName);
        new CSVLoader(reader).readRows(separator, row ->
                parser.tryParse(row)
                        .flatMap(parsed -> feedRowProcessor.process(parsed, supplierName, taxonomyPenalty, stats))
                        .ifPresent(res::add));
        stats.log();
        return dataCleanup.run(res);
    }

}
