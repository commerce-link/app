package pl.commercelink.inventory.supplier.manual;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import pl.commercelink.inventory.StoreInventoryCache;
import pl.commercelink.inventory.supplier.StoreFeedRepository;
import pl.commercelink.inventory.supplier.SupplierIdentity;
import pl.commercelink.inventory.supplier.SupplierLabels;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class ManualSupplierService {

    private static final int MAX_LABEL_LENGTH = 60;
    private static final int IDENTITY_ATTEMPTS = 5;

    private final StoresRepository storesRepository;
    private final StoreFeedRepository storeFeedRepository;
    private final StoreInventoryCache storeInventoryCache;
    private final SupplierRegistry supplierRegistry;

    public record Result(boolean ok, String messageCode, String identity) {
        public static Result success() {
            return new Result(true, null, null);
        }

        public static Result created(String identity) {
            return new Result(true, null, identity);
        }

        public static Result error(String messageCode) {
            return new Result(false, messageCode, null);
        }
    }

    public record ManualSelection(String identity, boolean enabled, boolean includeInPricing,
                                  boolean includeInFulfilment, String externalSupplierId, String label) {
    }

    public Result create(String storeId, String label) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return Result.error("store.manual.error.store.notfound");
        }
        String trimmed = label == null ? "" : label.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_LABEL_LENGTH) {
            return Result.error("store.manual.error.name.invalid");
        }
        if (reservedName(trimmed)) {
            return Result.error("store.supplier.connection.error.label.reserved");
        }
        if (labelTaken(store, trimmed, null)) {
            return Result.error("store.manual.error.name.taken");
        }
        String identity = freshIdentity(store);
        if (identity == null) {
            return Result.error("store.supplier.connection.error.identity.exhausted");
        }
        StoreSupplierConnection connection = new StoreSupplierConnection(identity, ConnectionMode.MANUAL, true, true);
        connection.setLabel(trimmed);
        connection.setEnabled(false);
        connections(store).add(connection);
        storesRepository.save(store);
        storeInventoryCache.evict(storeId);
        return Result.created(identity);
    }

    // Bounded rather than an unbounded retry loop: a stuck token generator would otherwise spin
    // forever inside a request thread.
    private String freshIdentity(Store store) {
        for (int attempt = 0; attempt < IDENTITY_ATTEMPTS; attempt++) {
            String candidate = newIdentity();
            if (!alreadyExists(store, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** Seam for tests that need a deterministic collision. */
    String newIdentity() {
        return SupplierIdentity.newInstance(SupplierIdentity.MANUAL_TYPE);
    }

    // Labels are unique across every connection of the store, whatever its mode, so the operator
    // never sees two rows with the same name.
    private boolean labelTaken(Store store, String label, String exceptIdentity) {
        return connections(store).stream()
                .filter(connection -> !connection.getSupplierName().equals(exceptIdentity))
                .anyMatch(connection -> SupplierLabels.labelOf(connection).equalsIgnoreCase(label));
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

    /**
     * Every rename is validated before anything is written: a label the operator cannot have is a
     * rejected save, not a silently dropped field. Validation runs over the whole batch first so a
     * bad label in one selection never leaves the others half-applied.
     */
    public Result applySelections(String storeId, List<ManualSelection> selections) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return Result.error("store.manual.error.store.notfound");
        }
        for (ManualSelection selection : selections) {
            Result rejected = rejectLabel(store, selection);
            if (rejected != null) {
                return rejected;
            }
        }
        for (ManualSelection selection : selections) {
            for (StoreSupplierConnection connection : connections(store)) {
                if (connection.getMode() == ConnectionMode.MANUAL
                        && connection.getSupplierName().equals(selection.identity())) {
                    boolean hasFeed = storeFeedRepository.canRead(storeId, selection.identity(), "csv");
                    connection.setEnabled(selection.enabled() && hasFeed);
                    connection.setIncludeInPricing(selection.includeInPricing());
                    connection.setIncludeInFulfilment(selection.includeInFulfilment());
                    connection.setExternalSupplierId(StringUtils.trimToNull(selection.externalSupplierId()));
                    String label = submittedLabel(selection);
                    if (label != null) {
                        connection.setLabel(label);
                    }
                }
            }
        }
        storesRepository.save(store);
        storeInventoryCache.evict(storeId);
        return Result.success();
    }

    // An absent label field means "leave the name alone"; a present but unusable one is an error.
    private Result rejectLabel(Store store, ManualSelection selection) {
        if (selection.label() == null) {
            return null;
        }
        String label = submittedLabel(selection);
        if (label == null || label.length() > MAX_LABEL_LENGTH) {
            return Result.error("store.manual.error.name.invalid");
        }
        if (reservedName(label)) {
            return Result.error("store.supplier.connection.error.label.reserved");
        }
        if (labelTaken(store, label, selection.identity())) {
            return Result.error("store.manual.error.name.taken");
        }
        return null;
    }

    // A manual supplier named like a built-in entry or an external supplier type would show up as
    // a duplicate row in every supplier select, so every registry name is off limits here.
    private boolean reservedName(String label) {
        return supplierRegistry.getAllSupplierNames().stream().anyMatch(name -> name.equalsIgnoreCase(label));
    }

    private String submittedLabel(ManualSelection selection) {
        return StringUtils.trimToNull(selection.label());
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
                if (parsed.item().isSellable()) {
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
