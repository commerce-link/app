package pl.commercelink.pricelist;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.stereotype.Repository;
import pl.commercelink.starter.storage.FileStorage;
import pl.commercelink.starter.csv.CSVLoader;
import pl.commercelink.starter.csv.CSVWriter;
import pl.commercelink.taxonomy.ProductCategory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@EnableCaching
@Repository
public class PricelistRepository {

    public static final int DEFAULT_ESTIMATED_DELIVERY_DAYS = 1;

    private final FileStorage fileStorage;
    private final String bucketName;

    public PricelistRepository(FileStorage fileStorage, @Value("${s3.bucket.pricelists}") String bucketName) {
        this.fileStorage = fileStorage;
        this.bucketName = bucketName;
    }

    @Cacheable(value = "pricelists", key = "#catalogId + '-' + #pricelistId")
    public Pricelist find(String catalogId, String pricelistId) {
        String key = getKey(catalogId, pricelistId);
        if(fileStorage.canRead(bucketName, key)){
            Reader reader = fileStorage.get(bucketName, key);
            return getPricelist(pricelistId, reader);
        }
        return null;
    }

    public String save(String catalogId, List<AvailabilityAndPrice> availabilityAndPrices) throws IOException {
        String pricelistId = UUID.randomUUID().toString();
        String s3Key = getKey(catalogId, pricelistId);
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


    private static String getKey(String catalogId, String pricelistId) {
        return  catalogId + "/" + pricelistId + ".csv";
    }

    public String findNewestPricelistId(String catalogId) {
        return extractPricelistId(fileStorage.findNewest(bucketName, catalogId + "/").getLeft());
    }

    @Cacheable(value = "newestPricelistId", key = "#catalogId")
    public String findNewestPricelistIdCached(String catalogId) {
        return findNewestPricelistId(catalogId);
    }

    public Pricelist findNewestPricelist(String catalogId) {
        Pair<String, InputStreamReader> reader = fileStorage.findNewest(bucketName, catalogId + "/");
        return getPricelist(extractPricelistId(reader.getLeft()), reader.getRight());
    }

    public List<Pricelist> findTopNPricelist(String catalogId, int n) {
        return  fileStorage.findTopN(bucketName, catalogId + "/", n).stream()
                .map(pair -> new Pricelist(extractPricelistId(pair.getLeft()), pair.getRight()))
                .collect(Collectors.toList());
    }

    private static String extractPricelistId(String fileName) {
        return fileName.replace(".csv", "");
    }

    public byte[] findNewestPricelistAsBytes(String catalogId) {
        return fileStorage.findNewestAsBytes(bucketName, catalogId + "/");
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
                "OS".equals(fields[6]) ? ProductCategory.Software : ProductCategory.valueOf(fields[6]),   // category
                price,                                // price
                Long.parseLong(fields[8]),             // qty
                parseEstimatedDeliveryDays(fields, 9),  // estimatedDeliveryDays
                lowest30DaysPrice                       // lowest30DaysPrice
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
