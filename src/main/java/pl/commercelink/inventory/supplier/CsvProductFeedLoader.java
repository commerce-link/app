package pl.commercelink.inventory.supplier;

import org.springframework.stereotype.Component;
import pl.commercelink.starter.csv.CSVLoader;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.CsvRowParser;
import pl.commercelink.inventory.InventoryRepository;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@Component
public class CsvProductFeedLoader {

    private final InventoryRepository inventoryRepository;
    private final DataCorrection dataCorrection;
    private final DataCleanup dataCleanup;
    private final TaxonomyCache taxonomyCache;

    CsvProductFeedLoader(InventoryRepository inventoryRepository, DataCorrection dataCorrection, DataCleanup dataCleanup, TaxonomyCache taxonomyCache) {
        this.inventoryRepository = inventoryRepository;
        this.dataCorrection = dataCorrection;
        this.dataCleanup = dataCleanup;
        this.taxonomyCache = taxonomyCache;
    }

    public List<InventoryItem> fetch(CsvRowParser parser, Character separator, String supplierName) {
        if (!inventoryRepository.canRead(supplierName)) {
            System.out.println("Skipping feed file for " + supplierName + " as it does not exist or is unreadable.");
            return new LinkedList<>();
        }
        try (Reader reader = inventoryRepository.read(supplierName)) {
            return parseRows(parser, separator, reader);
        } catch (IOException e) {
            System.out.println("Error reading feed file for " + supplierName + ": " + e.getMessage());
            return new LinkedList<>();
        }
    }

    public List<InventoryItem> fetch(CsvRowParser parser, Character separator, String storeId, String supplierName) {
        if (!inventoryRepository.canRead(storeId, supplierName)) {
            System.out.println("Skipping store feed file for " + storeId + "/" + supplierName + " as it does not exist or is unreadable.");
            return new LinkedList<>();
        }
        try (Reader reader = inventoryRepository.read(storeId, supplierName, "csv")) {
            return parseRows(parser, separator, reader);
        } catch (IOException e) {
            System.out.println("Error reading store feed file for " + storeId + "/" + supplierName + ": " + e.getMessage());
            return new LinkedList<>();
        }
    }

    private List<InventoryItem> parseRows(CsvRowParser parser, Character separator, Reader reader) {
        List<InventoryItem> res = new ArrayList<>();
        new CSVLoader(reader).readRows(separator, row -> {
            parser.tryParse(row).ifPresent(parsed -> {
                InventoryItem item = dataCorrection.run(parsed.item());
                Taxonomy taxonomy = dataCorrection.run(parsed.taxonomy());
                if (item != null && taxonomy != null && taxonomy.isProcessable() && item.isSellable()) {
                    taxonomyCache.add(taxonomy);
                    res.add(item);
                }
            });
        });
        return dataCleanup.run(res);
    }

}
