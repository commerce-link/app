package pl.commercelink.inventory.supplier.manual;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.commercelink.inventory.StoreInventoryCache;
import pl.commercelink.inventory.supplier.StoreFeedRepository;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.CsvRowParser;
import pl.commercelink.starter.csv.CSVLoader;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ManualSupplierService {

    private static final Pattern VALID_LABEL = Pattern.compile("^[A-Za-z0-9 _-]{1,60}$");

    private final StoresRepository storesRepository;
    private final StoreFeedRepository storeFeedRepository;
    private final SupplierRegistry supplierRegistry;
    private final StoreInventoryCache storeInventoryCache;

    public record Result(boolean ok, String messageCode) {
        public static Result success() {
            return new Result(true, null);
        }

        public static Result error(String messageCode) {
            return new Result(false, messageCode);
        }
    }

    public record ManualSupplierView(String identity, String label, boolean enabled, boolean includeInPricing,
                                     boolean includeInFulfilment, boolean hasFeed) {
    }

    public record ManualSelection(String identity, boolean enabled, boolean includeInPricing,
                                  boolean includeInFulfilment) {
    }

    public Result create(String storeId, String label) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return Result.error("store.manual.error.store.notfound");
        }
        String trimmed = label == null ? "" : label.trim();
        if (!VALID_LABEL.matcher(trimmed).matches()) {
            return Result.error("store.manual.error.name.invalid");
        }
        String identity = ManualSupplierInfos.identityFor(trimmed);
        if (collidesWithStatic(trimmed) || alreadyExists(store, identity)) {
            return Result.error("store.manual.error.name.taken");
        }
        StoreSupplierConnection connection = new StoreSupplierConnection(identity, ConnectionMode.MANUAL, true, true);
        connection.setEnabled(false);
        connections(store).add(connection);
        storesRepository.save(store);
        storeInventoryCache.evict(storeId);
        return Result.success();
    }

    public Result delete(String storeId, String identity) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return Result.error("store.manual.error.supplier.notfound");
        }
        boolean removed = connections(store).removeIf(connection ->
                connection.getMode() == ConnectionMode.MANUAL && connection.getSupplierName().equals(identity));
        if (!removed) {
            return Result.error("store.manual.error.supplier.notfound");
        }
        storeFeedRepository.delete(storeId, identity);
        storesRepository.save(store);
        storeInventoryCache.evict(storeId);
        return Result.success();
    }

    public Result uploadFeed(String storeId, String identity, byte[] csvBytes) {
        Store store = storesRepository.findById(storeId);
        if (store == null || !alreadyExists(store, identity)) {
            return Result.error("store.manual.error.supplier.notfound");
        }
        if (!hasAtLeastOneLoadableRow(identity, csvBytes)) {
            return Result.error("store.manual.error.csv.invalid");
        }
        storeFeedRepository.store(storeId, identity, csvBytes, "csv");
        storeInventoryCache.evict(storeId);
        return Result.success();
    }

    public void applySelections(String storeId, List<ManualSelection> selections) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return;
        }
        for (ManualSelection selection : selections) {
            for (StoreSupplierConnection connection : connections(store)) {
                if (connection.getMode() == ConnectionMode.MANUAL
                        && connection.getSupplierName().equals(selection.identity())) {
                    boolean hasFeed = storeFeedRepository.canRead(storeId, selection.identity(), "csv");
                    connection.setEnabled(selection.enabled() && hasFeed);
                    connection.setIncludeInPricing(selection.includeInPricing());
                    connection.setIncludeInFulfilment(selection.includeInFulfilment());
                }
            }
        }
        storesRepository.save(store);
        storeInventoryCache.evict(storeId);
    }

    public List<ManualSupplierView> list(Store store) {
        List<ManualSupplierView> views = new ArrayList<>();
        if (store == null) {
            return views;
        }
        for (StoreSupplierConnection connection : connections(store)) {
            if (connection.getMode() == ConnectionMode.MANUAL) {
                String identity = connection.getSupplierName();
                views.add(new ManualSupplierView(
                        identity,
                        ManualSupplierInfos.label(identity),
                        connection.isEnabled(),
                        connection.isIncludeInPricing(),
                        connection.isIncludeInFulfilment(),
                        storeFeedRepository.canRead(store.getStoreId(), identity, "csv")));
            }
        }
        return views;
    }

    private boolean collidesWithStatic(String label) {
        return supplierRegistry.getAllSupplierNames().stream().anyMatch(label::equalsIgnoreCase);
    }

    private boolean alreadyExists(Store store, String identity) {
        return connections(store).stream().anyMatch(connection ->
                connection.getMode() == ConnectionMode.MANUAL
                        && connection.getSupplierName().equalsIgnoreCase(identity));
    }

    private boolean hasAtLeastOneLoadableRow(String identity, byte[] csvBytes) {
        CsvRowParser parser = new ManualCsvRowParser(identity);
        AtomicInteger loadable = new AtomicInteger();
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(csvBytes), StandardCharsets.UTF_8)) {
            new CSVLoader(reader).readRows(';', row -> parser.tryParse(row).ifPresent(parsed -> {
                if (parsed.taxonomy().isProcessable() && parsed.item().isSellable()) {
                    loadable.incrementAndGet();
                }
            }));
        } catch (Exception e) {
            return false;
        }
        return loadable.get() > 0;
    }

    private List<StoreSupplierConnection> connections(Store store) {
        FulfilmentConfiguration config = store.getFulfilmentConfiguration();
        if (config == null) {
            config = new FulfilmentConfiguration();
            store.setFulfilmentConfiguration(config);
        }
        return config.getSupplierConnections();
    }
}
