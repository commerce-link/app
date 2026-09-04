package pl.commercelink.marketplace;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pl.commercelink.starter.storage.FileStorage;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MarketplaceExportRunService {

    private static final String RUNS_PREFIX = "marketplace-export-runs";
    private static final String RUN_EXTENSION = ".csv";
    private static final String FAILED_RUN_EXTENSION = "-failed.csv";
    private static final int UNREADABLE_RUN_ATTEMPTS = 5;

    private final FileStorage fileStorage;
    private final String bucketName;
    private final Clock clock;

    @Autowired
    public MarketplaceExportRunService(FileStorage fileStorage,
                                       @Value("${s3.bucket.stores}") String bucketName) {
        this(fileStorage, bucketName, Clock.systemUTC());
    }

    MarketplaceExportRunService(FileStorage fileStorage, String bucketName, Clock clock) {
        this.fileStorage = fileStorage;
        this.bucketName = bucketName;
        this.clock = clock;
    }

    public List<MarketplaceOfferSnapshot> loadPreviousExport(String storeId, String catalogId, String marketplace) {
        String prefix = catalogPrefix(storeId, marketplace, catalogId);
        List<String> keysNewestFirst;
        try {
            keysNewestFirst = fileStorage.getAllObjectLastModified(bucketName, prefix).keySet().stream()
                    .filter(this::isSucceededRunKey)
                    .flatMap(key -> runInstantOfKey(key).map(instant -> Map.entry(key, instant)).stream())
                    .sorted(Map.Entry.<String, Instant>comparingByValue(Comparator.reverseOrder()))
                    .map(Map.Entry::getKey)
                    .toList();
        } catch (Exception exception) {
            System.err.println("Failed to load previous marketplace export: " + exception.getMessage());
            return List.of();
        }

        int attempts = 0;
        for (String key : keysNewestFirst) {
            if (attempts == UNREADABLE_RUN_ATTEMPTS) {
                System.err.println("Gave up looking for a readable marketplace export run after "
                        + UNREADABLE_RUN_ATTEMPTS + " unreadable files under prefix " + prefix);
                return List.of();
            }
            attempts++;
            try {
                return readOffers(key);
            } catch (Exception exception) {
                System.err.println("Skipping unreadable marketplace export run " + key + ": " + exception.getMessage());
            }
        }
        return List.of();
    }

    public void saveRun(MarketplaceExportRun run) {
        try {
            String runId = MarketplaceExportRunId.of(clock.instant());
            String key = runKey(run.getStoreId(), run.getMarketplace(), run.getCatalogId(), runId, run.isFailed());
            List<MarketplaceOfferSnapshot> rows = run.toRows();
            fileStorage.put(bucketName, key, MarketplaceExportRunCsv.toBytes(rows));
        } catch (Exception exception) {
            System.err.println("Failed to save marketplace export run: " + exception.getMessage());
        }
    }

    public List<MarketplaceExportRunHeader> findRuns(String storeId) {
        try {
            return fileStorage.getAllObjectLastModified(bucketName, storePrefix(storeId))
                    .entrySet()
                    .stream()
                    .filter(entry -> isRunKey(entry.getKey()))
                    .flatMap(entry -> toHeader(entry.getKey(), entry.getValue()).stream())
                    .flatMap(header -> MarketplaceExportRunId.instantOf(header.runId())
                            .map(instant -> Map.entry(instant, header))
                            .stream())
                    .sorted(Map.Entry.<Instant, MarketplaceExportRunHeader>comparingByKey(Comparator.reverseOrder()))
                    .map(Map.Entry::getValue)
                    .toList();
        } catch (Exception exception) {
            System.err.println("Failed to list marketplace export runs: " + exception.getMessage());
            return List.of();
        }
    }

    public Optional<MarketplaceExportRunFile> findRun(String storeId,
                                                      String marketplace,
                                                      String catalogId,
                                                      String runId) {
        String keyWithoutExtension = catalogPrefix(storeId, marketplace, catalogId) + runId;
        for (String key : List.of(keyWithoutExtension + RUN_EXTENSION, keyWithoutExtension + FAILED_RUN_EXTENSION)) {
            Optional<MarketplaceExportRunFile> runFile = readRunFile(key);
            if (runFile.isPresent()) {
                return runFile;
            }
        }
        return Optional.empty();
    }

    private Optional<MarketplaceExportRunFile> readRunFile(String key) {
        try {
            if (!fileStorage.canRead(bucketName, key)) {
                return Optional.empty();
            }
            byte[] data = fileStorage.getBytes(bucketName, key);
            return Optional.of(new MarketplaceExportRunFile(
                    MarketplaceExportRunCsv.runIdFrom(key),
                    isFailedRunKey(key),
                    MarketplaceExportRunCsv.parse(data),
                    data));
        } catch (Exception exception) {
            System.err.println("Failed to read marketplace export run: " + exception.getMessage());
            return Optional.empty();
        }
    }

    private List<MarketplaceOfferSnapshot> readOffers(String key) {
        return MarketplaceExportRunCsv.parse(fileStorage.getBytes(bucketName, key)).stream()
                .filter(snapshot -> snapshot.pimId() != null && !snapshot.pimId().isBlank())
                .toList();
    }

    private Optional<Instant> runInstantOfKey(String key) {
        return MarketplaceExportRunId.instantOf(MarketplaceExportRunCsv.runIdFrom(key));
    }

    private boolean isRunKey(String key) {
        return key.endsWith(RUN_EXTENSION);
    }

    private boolean isFailedRunKey(String key) {
        return key.endsWith(FAILED_RUN_EXTENSION);
    }

    private boolean isSucceededRunKey(String key) {
        return isRunKey(key) && !isFailedRunKey(key);
    }

    private Optional<MarketplaceExportRunHeader> toHeader(String key, LocalDateTime storedAt) {
        String[] segments = key.split("/");
        if (segments.length != 5) {
            return Optional.empty();
        }
        return Optional.of(new MarketplaceExportRunHeader(
                segments[2], segments[3], MarketplaceExportRunCsv.runIdFrom(key), storedAt, isFailedRunKey(key)));
    }

    private String storePrefix(String storeId) {
        return String.format("%s/%s/", RUNS_PREFIX, storeId);
    }

    private String catalogPrefix(String storeId, String marketplace, String catalogId) {
        return String.format("%s/%s/%s/%s/", RUNS_PREFIX, storeId, marketplace, catalogId);
    }

    private String runKey(String storeId, String marketplace, String catalogId, String runId, boolean failed) {
        return catalogPrefix(storeId, marketplace, catalogId) + runId + (failed ? FAILED_RUN_EXTENSION : RUN_EXTENSION);
    }
}
