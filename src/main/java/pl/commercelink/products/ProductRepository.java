package pl.commercelink.products;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ProductRepository extends DynamoDbRepository<Product> {

    private static final String TABLE = "ProductsV2";

    public ProductRepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    public Product findByProductId(String categoryId, String productId) {
        return dynamoDBMapper.load(Product.class, categoryId, productId);
    }

    public Product findByPimId(String categoryId, String pimId) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":categoryId", new AttributeValue().withS(categoryId));
        eav.put(":pimId", new AttributeValue().withS(pimId));

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression("categoryId = :categoryId AND pimId = :pimId")
                .withExpressionAttributeValues(eav);

        return dynamoDBMapper.scan(Product.class, scanExpression)
                .stream()
                .findFirst()
                .orElse(null);
    }

    public List<Product> findAll(String categoryId) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":categoryId", new AttributeValue().withS(categoryId));

        QueryRequest queryRequest = new QueryRequest()
                .withTableName(TABLE)
                .withKeyConditionExpression("categoryId = :categoryId")
                .withExpressionAttributeValues(eav);

        return query(queryRequest, Product.class);
    }

    public List<Product> findAll(ProductCatalog catalog) {
        List<String> categoryIds = getCategoryIds(catalog);
        List<Product> result = new ArrayList<>();
        for (String categoryId : categoryIds) {
            Map<String, AttributeValue> eav = new HashMap<>();
            eav.put(":categoryId", new AttributeValue().withS(categoryId));

            QueryRequest queryRequest = new QueryRequest()
                    .withTableName(TABLE)
                    .withKeyConditionExpression("categoryId = :categoryId")
                    .withExpressionAttributeValues(eav);
            result.addAll(query(queryRequest, Product.class));
        }
        return result;
    }

    public List<Product> findAllByPimId(String pimId) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":pimId", new AttributeValue().withS(pimId));

        QueryRequest queryRequest = new QueryRequest()
                .withTableName(TABLE)
                .withIndexName("PimIdIndex")
                .withKeyConditionExpression("pimId = :pimId")
                .withExpressionAttributeValues(eav);

        return query(queryRequest, Product.class);
    }

    public void detachPimFromProducts(String pimId) {
        List<Product> products = findAllByPimId(pimId);
        for (Product product : products) {
            Product fullProduct = findByProductId(product.getCategoryId(), product.getProductId());
            if (fullProduct != null) {
                fullProduct.setPimId(null);
                dynamoDBMapper.save(fullProduct);
            }
        }
    }

    public List<Product> findAllProductsThatQualifiesForRestock(ProductCatalog catalog) {
        List<String> categoryIds = getCategoryIds(catalog);
        List<Product> result = new ArrayList<>();
        for (String categoryId : categoryIds) {
            Map<String, AttributeValue> eav = new HashMap<>();
            eav.put(":categoryId", new AttributeValue().withS(categoryId));
            eav.put(":stockExpectedQty", new AttributeValue().withN("0"));

            QueryRequest queryRequest = new QueryRequest()
                    .withTableName(TABLE)
                    .withKeyConditionExpression("categoryId = :categoryId")
                    .withFilterExpression("stockExpectedQty > :stockExpectedQty")
                    .withExpressionAttributeValues(eav);
            result.addAll(query(queryRequest, Product.class));
        }
        return result;
    }

    private static List<String> getCategoryIds(ProductCatalog catalog) {
        return catalog.getCategories()
                .stream()
                .map(CategoryDefinition::getCategoryId)
                .collect(Collectors.toList());
    }

    public List<Product> findAllProductsWithPimId(String categoryId, boolean enabled) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":categoryId", new AttributeValue().withS(categoryId));
        eav.put(":enabled", new AttributeValue().withN(enabled ? "1" : "0"));

        QueryRequest queryRequest = new QueryRequest()
                .withTableName(TABLE)
                .withKeyConditionExpression("categoryId = :categoryId")
                .withFilterExpression("enabled = :enabled AND attribute_exists(pimId)")
                .withExpressionAttributeValues(eav);
        return query(queryRequest, Product.class);
    }

    public List<Product> scanAllKeys() {
        List<Product> result = new ArrayList<>();
        Map<String, AttributeValue> lastEvaluatedKey = null;

        do {
            ScanRequest scanRequest = new ScanRequest()
                    .withTableName(TABLE)
                    .withProjectionExpression("categoryId, productId");

            if (lastEvaluatedKey != null) {
                scanRequest.withExclusiveStartKey(lastEvaluatedKey);
            }

            ScanResult scanResult = amazonDynamoDB.scan(scanRequest);
            for (Map<String, AttributeValue> item : scanResult.getItems()) {
                Product product = new Product();
                product.setCategoryId(item.get("categoryId").getS());
                product.setProductId(item.get("productId").getS());
                result.add(product);
            }

            lastEvaluatedKey = scanResult.getLastEvaluatedKey();
        } while (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty());

        return result;
    }

    public List<Product> findAllWithoutPimIdByEanOrMfn(List<String> eans, List<String> mfnCodes) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":nullValue", new AttributeValue().withNULL(true));

        List<String> conditions = new ArrayList<>();

        List<String> eanPlaceholders = new ArrayList<>();
        for (int i = 0; i < eans.size(); i++) {
            String key = ":ean" + i;
            eanPlaceholders.add(key);
            eav.put(key, new AttributeValue().withS(eans.get(i)));
        }
        if (!eanPlaceholders.isEmpty()) {
            conditions.add("ean IN (" + String.join(", ", eanPlaceholders) + ")");
        }

        List<String> mfnPlaceholders = new ArrayList<>();
        for (int i = 0; i < mfnCodes.size(); i++) {
            String key = ":mfn" + i;
            mfnPlaceholders.add(key);
            eav.put(key, new AttributeValue().withS(mfnCodes.get(i)));
        }
        if (!mfnPlaceholders.isEmpty()) {
            conditions.add("mfn IN (" + String.join(", ", mfnPlaceholders) + ")");
        }

        if (conditions.isEmpty()) {
            return new ArrayList<>();
        }

        String filter = "(attribute_not_exists(pimId) OR pimId = :nullValue) AND (" + String.join(" OR ", conditions) + ")";

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression(filter)
                .withExpressionAttributeValues(eav);

        return dynamoDBMapper.scan(Product.class, scanExpression);
    }

    public List<Product> findAllProductsPaginated(String categoryId, Boolean enabled, String brand, String label, String pimId, String ean, String mfn, Boolean pimIdExists, Integer minStockExpectedQty, Boolean marketplacesNotEmpty, Integer minSuggestedRetailPrice, int page, int pageSize) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":categoryId", new AttributeValue().withS(categoryId));

        StringBuilder filterExpression = new StringBuilder();

        if (enabled != null) {
            filterExpression.append("enabled = :enabled");
            eav.put(":enabled", new AttributeValue().withN(enabled ? "1" : "0"));
        }

        if (StringUtils.isNotBlank(brand)) {
            if (filterExpression.length() > 0) filterExpression.append(" AND ");
            filterExpression.append("brand = :brand");
            eav.put(":brand", new AttributeValue().withS(brand));
        }

        if (StringUtils.isNotBlank(label)) {
            if (filterExpression.length() > 0) filterExpression.append(" AND ");
            filterExpression.append("label = :label");
            eav.put(":label", new AttributeValue().withS(label));
        }

        if (StringUtils.isNotBlank(pimId)) {
            if (filterExpression.length() > 0) filterExpression.append(" AND ");
            filterExpression.append("pimId = :pimId");
            eav.put(":pimId", new AttributeValue().withS(pimId));
        }

        if (StringUtils.isNotBlank(ean)) {
            if (filterExpression.length() > 0) filterExpression.append(" AND ");
            filterExpression.append("ean = :ean");
            eav.put(":ean", new AttributeValue().withS(ean));
        }

        if (StringUtils.isNotBlank(mfn)) {
            if (filterExpression.length() > 0) filterExpression.append(" AND ");
            filterExpression.append("mfn = :mfn");
            eav.put(":mfn", new AttributeValue().withS(mfn));
        }

        if (pimIdExists != null) {
            if (filterExpression.length() > 0) filterExpression.append(" AND ");
            if (pimIdExists) {
                filterExpression.append("attribute_exists(pimId)");
            } else {
                filterExpression.append("attribute_not_exists(pimId) OR pimId = :nullValue");
                eav.put(":nullValue", new AttributeValue().withNULL(true));
            }
        }

        if (minStockExpectedQty != null) {
            if (filterExpression.length() > 0) filterExpression.append(" AND ");
            filterExpression.append("stockExpectedQty >= :minStockExpectedQty");
            eav.put(":minStockExpectedQty", new AttributeValue().withN(String.valueOf(minStockExpectedQty)));
        }

        if (Boolean.TRUE.equals(marketplacesNotEmpty)) {
            if (filterExpression.length() > 0) filterExpression.append(" AND ");
            filterExpression.append("attribute_exists(marketplaces) AND size(marketplaces) > :zero");
            eav.put(":zero", new AttributeValue().withN("0"));
        }

        if (minSuggestedRetailPrice != null) {
            if (filterExpression.length() > 0) filterExpression.append(" AND ");
            filterExpression.append("suggestedRetailPrice >= :minSuggestedRetailPrice");
            eav.put(":minSuggestedRetailPrice", new AttributeValue().withN(String.valueOf(minSuggestedRetailPrice)));
        }

        DynamoDBQueryExpression<Product> queryExpression = new DynamoDBQueryExpression<Product>()
                .withKeyConditionExpression("categoryId = :categoryId")
                .withExpressionAttributeValues(eav);

        if (filterExpression.length() > 0) {
            queryExpression.withFilterExpression(filterExpression.toString());
        }

        return queryWithPagination(queryExpression, page, pageSize, Product.class);
    }

}
