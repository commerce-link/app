package pl.commercelink.marketplace;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pl.commercelink.starter.storage.FileStorage;
import pl.commercelink.starter.util.ConversionUtil;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MarketplaceExportRunService {

    private static final DateTimeFormatter RUN_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneOffset.UTC);
    private static final String RUNS_PREFIX = "marketplace-export-runs";
    private static final int LISTING_LIMIT = 1000;

    private final List<MarketplaceExportRunReader> readers =
            List.of(new JsonMarketplaceExportRunReader(), new CsvMarketplaceExportRunReader());

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
        try {
            List<String> runKeys = listRunKeysFromNewest(catalogPrefix(storeId, marketplace, catalogId));

            for (String key : runKeys) {
                MarketplaceExportRunDocument document = readDocument(key);
                if (document != null && document.wasSuccessful()) {
                    return List.copyOf(document.offersOrEmpty());
                }
            }
            return List.of();
        } catch (Exception exception) {
            System.err.println("Failed to load previous marketplace export: " + exception.getMessage());
            return List.of();
        }
    }

    public void saveRun(MarketplaceExportRun run) {
        try {
            String runId = RUN_TIMESTAMP.format(clock.instant());
            MarketplaceExportRunDocument document = run.toDocument(runId, clock.instant());
            String key = jsonRunKey(run.getStoreId(), run.getMarketplace(), run.getCatalogId(), runId);
            fileStorage.put(bucketName, key, ConversionUtil.fromJsonToBytes(document));
        } catch (Exception exception) {
            System.err.println("Failed to save marketplace export run: " + exception.getMessage());
        }
    }

    public List<MarketplaceExportRunHeader> findRuns(String storeId) {
        try {
            return fileStorage.getAllObjectLastModified(bucketName, storePrefix(storeId))
                    .entrySet()
                    .stream()
                    .filter(entry -> hasReaderFor(entry.getKey()))
                    .flatMap(entry -> toHeader(entry.getKey(), entry.getValue()).stream())
                    .sorted(Comparator.comparing(MarketplaceExportRunHeader::runId).reversed())
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
        for (String key : List.of(keyWithoutExtension + ".json", keyWithoutExtension + ".csv")) {
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
            return readerFor(key)
                    .map(reader -> new MarketplaceExportRunFile(reader.parse(key, data), data));
        } catch (Exception exception) {
            System.err.println("Failed to read marketplace export run: " + exception.getMessage());
            return Optional.empty();
        }
    }

    private List<String> listRunKeysFromNewest(String prefix) {
        Map<String, LocalDateTime> lastModifiedByKey = fileStorage.getAllObjectLastModified(bucketName, prefix);
        if (lastModifiedByKey.size() >= LISTING_LIMIT) {
            System.err.println("Marketplace export run listing hit the object limit for prefix " + prefix);
        }

        return lastModifiedByKey.entrySet().stream()
                .filter(entry -> hasReaderFor(entry.getKey()))
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .map(Map.Entry::getKey)
                .toList();
    }

    private MarketplaceExportRunDocument readDocument(String key) {
        return readerFor(key)
                .map(reader -> reader.parse(key, fileStorage.getBytes(bucketName, key)))
                .orElse(null);
    }

    private Optional<MarketplaceExportRunReader> readerFor(String key) {
        return readers.stream().filter(reader -> reader.handlesFileFormatOf(key)).findFirst();
    }

    private boolean hasReaderFor(String key) {
        return readerFor(key).isPresent();
    }

    private Optional<MarketplaceExportRunHeader> toHeader(String key, LocalDateTime storedAt) {
        String[] segments = key.split("/");
        if (segments.length != 5) {
            return Optional.empty();
        }
        return Optional.of(new MarketplaceExportRunHeader(
                segments[2], segments[3], MarketplaceExportRunReader.runIdFrom(key), storedAt));
    }

    private String storePrefix(String storeId) {
        return String.format("%s/%s/", RUNS_PREFIX, storeId);
    }

    private String catalogPrefix(String storeId, String marketplace, String catalogId) {
        return String.format("%s/%s/%s/%s/", RUNS_PREFIX, storeId, marketplace, catalogId);
    }

    private String jsonRunKey(String storeId, String marketplace, String catalogId, String runId) {
        return catalogPrefix(storeId, marketplace, catalogId) + runId + ".json";
    }
}
