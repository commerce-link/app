package pl.commercelink.pricelist;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.stereotype.Repository;
import pl.commercelink.starter.storage.FileStorage;
import pl.commercelink.starter.storage.TimeOrderedFileName;
import pl.commercelink.starter.csv.CSVLoader;
import pl.commercelink.starter.csv.CSVWriter;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@EnableCaching
@Repository
public class PricelistRepository {

    public static final int DEFAULT_ESTIMATED_DELIVERY_DAYS = 1;
    private static final int IDX_SERVICE = 11;

    private final FileStorage fileStorage;
    private final String bucketName;
    private final Clock clock;

    @Autowired
    public PricelistRepository(FileStorage fileStorage, @Value("${s3.bucket.stores}") String bucketName) {
        this(fileStorage, bucketName, Clock.systemUTC());
    }

    PricelistRepository(FileStorage fileStorage, String bucketName, Clock clock) {
        this.fileStorage = fileStorage;
        this.bucketName = bucketName;
        this.clock = clock;
    }

    @Cacheable(value = "pricelists", key = "#storeId + '-' + #catalogId + '-' + #pricelistId")
    public Pricelist find(String storeId, String catalogId, String pricelistId) {
        String key = getKey(storeId, catalogId, pricelistId);
        if(fileStorage.canRead(bucketName, key)){
            Reader reader = fileStorage.get(bucketName, key);
            return getPricelist(pricelistId, reader);
        }
        return null;
    }

    public String save(String storeId, String catalogId, List<AvailabilityAndPrice> availabilityAndPrices) throws IOException {
        String pricelistId = TimeOrderedFileName.of(clock.instant()) + "_" + UUID.randomUUID();
        String s3Key = getKey(storeId, catalogId, pricelistId);
        byte[] bytes = new CSVWriter().writeAllRowsToBytes(availabilityAndPrices, AvailabilityAndPrice.HEADERS);
        fileStorage.put(bucketName, s3Key, bytes);
        return pricelistId;
    }

    private Pricelist getPricelist(String pricelistId, Reader reader) {
        CSVLoader loader = new CSVLoader(reader);
        List<AvailabilityAndPrice> availabilityAndPrices = new ArrayList<>();
            loader.readRows(CSVLoader.DEFAULT_SEPARATOR, row -> {
                AvailabilityAndPrice availabilityAndPrice = mapFieldsToObject(row);
                availabilityAndPrices.add(availabilityAndPrice);
            });
        return new Pricelist(pricelistId, availabilityAndPrices);
    }


    private static String getKey(String storeId, String catalogId, String pricelistId) {
        return prefix(storeId, catalogId) + pricelistId + ".csv";
    }

    private static String prefix(String storeId, String catalogId) {
        return storeId + "/pricelists/" + catalogId + "/";
    }

    public String findNewestPricelistId(String storeId, String catalogId) {
        String prefix = prefix(storeId, catalogId);
        return newestTimeOrderedFileName(prefix)
                .or(() -> fileStorage.findNewestFileNameByLastModified(bucketName, prefix))
                .map(PricelistRepository::extractPricelistId)
                .orElse(null);
    }

    @Cacheable(value = "newestPricelistId", key = "#storeId + '-' + #catalogId")
    public String findNewestPricelistIdCached(String storeId, String catalogId) {
        return findNewestPricelistId(storeId, catalogId);
    }

    public Pricelist findNewestPricelist(String storeId, String catalogId) {
        String prefix = prefix(storeId, catalogId);
        Pair<String, InputStreamReader> reader = newestTimeOrderedFileName(prefix).isPresent()
                ? fileStorage.findNewestByKeyOrder(bucketName, prefix)
                : fileStorage.findNewestByLastModified(bucketName, prefix);
        if (reader == null) {
            return null;
        }
        return getPricelist(extractPricelistId(reader.getLeft()), reader.getRight());
    }

    public List<Pricelist> findTopNPricelist(String storeId, String catalogId, int n) {
        String prefix = prefix(storeId, catalogId);
        List<Pair<String, String>> byKeyOrder = fileStorage.findTopNByKeyOrder(bucketName, prefix, n);
        List<Pair<String, String>> newest = byKeyOrder.stream().allMatch(pair -> isTimeOrdered(pair.getLeft()))
                ? byKeyOrder
                : fileStorage.findTopNByLastModified(bucketName, prefix, n);
        return newest.stream()
                .map(pair -> new Pricelist(extractPricelistId(pair.getLeft()), pair.getRight()))
                .collect(Collectors.toList());
    }

    private static String extractPricelistId(String fileName) {
        return fileName.replace(".csv", "");
    }

    private Optional<String> newestTimeOrderedFileName(String prefix) {
        return fileStorage.findNewestFileNameByKeyOrder(bucketName, prefix)
                .filter(PricelistRepository::isTimeOrdered);
    }

    private static boolean isTimeOrdered(String fileName) {
        return TimeOrderedFileName.instantOf(fileName).isPresent();
    }

    public byte[] findNewestPricelistAsBytes(String storeId, String catalogId) {
        String prefix = prefix(storeId, catalogId);
        return newestTimeOrderedFileName(prefix).isPresent()
                ? fileStorage.findNewestAsBytesByKeyOrder(bucketName, prefix)
                : fileStorage.findNewestAsBytesByLastModified(bucketName, prefix);
    }

    private AvailabilityAndPrice mapFieldsToObject(String[] fields) {
        long price = Long.parseLong(fields[7]);
        long lowest30DaysPrice = parseLongField(fields, 10);
        if (lowest30DaysPrice == 0) {
            lowest30DaysPrice = price;
        }
        return new AvailabilityAndPrice(
                fields[0],                            // pimId
                fields[1],                            // ean
                fields[2],                            // manufacturerCode
                fields[3],                            // brand
                fields[4],                            // label
                fields[5],                            // name
                "OS".equals(fields[6]) ? "Software" : fields[6],   // category
                price,                                // price
                Long.parseLong(fields[8]),             // qty
                parseEstimatedDeliveryDays(fields, 9),  // estimatedDeliveryDays
                lowest30DaysPrice,                       // lowest30DaysPrice
                fields.length > IDX_SERVICE && Boolean.parseBoolean(fields[IDX_SERVICE])  // service
        );
    }

    private int parseEstimatedDeliveryDays(String[] fields, int index) {
        try {
            return (index < fields.length) ? Integer.parseInt(fields[index]) : DEFAULT_ESTIMATED_DELIVERY_DAYS;
        } catch (NumberFormatException e) {
            return DEFAULT_ESTIMATED_DELIVERY_DAYS;
        }
    }

    private long parseLongField(String[] fields, int index) {
        try {
            return (index < fields.length) ? Long.parseLong(fields[index]) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

}
