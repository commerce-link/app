package pl.commercelink.products;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.*;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ProductRepository extends DynamoDbRepository<Product> {

    private static final String TABLE = "Products";

    public ProductRepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    /**
     * Deletes the product whatever version it has now. A plain delete of a versioned item is conditional on the
     * version it was read with, so a save landing between the read and the delete fails it with
     * ConditionalCheckFailedException. The deletions of the catalog screens (the bulk action, the product page, a
     * removed category) delete what the operator chose regardless of an edit made meanwhile; an item already gone is
     * no error.
     */
    public void deleteWhateverItsVersion(Product product) {
        dynamoDBMapper.delete(product, DynamoDBMapperConfig.builder()
                .withSaveBehavior(DynamoDBMapperConfig.SaveBehavior.CLOBBER)
                .build());
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

    /**
     * Every product of the category. One query page holds at most 1 MB, so a large category takes several pages, and
     * every one of them is read: a caller deciding "not in the catalog" or "one product per EAN" needs all products.
     */
    public List<Product> findAll(String categoryId) {
        return findAll(categoryId, null);
    }

    /** As {@link #findAll(String)}, at most pageSize items a query page (null: DynamoDB's 1 MB page). */
    List<Product> findAll(String categoryId, Integer pageSize) {
        return queryAllPages(byCategory(categoryId).withLimit(pageSize), this::toProduct);
    }

    /**
     * The label of every product of the category, one entry per product (null for a product without one): what the
     * catalog page needs to count the products and the ones whose label is off the category's list, without reading
     * the products. Only the label is projected, and every page of the query is followed.
     */
    public List<String> labelsOf(String categoryId) {
        return labelsOf(categoryId, null);
    }

    /** As {@link #labelsOf(String)}, at most pageSize items a query page (null: DynamoDB's 1 MB page). */
    List<String> labelsOf(String categoryId, Integer pageSize) {
        return queryAllPages(byCategory(categoryId).withProjectionExpression("label").withLimit(pageSize),
                item -> stringOf(item, "label"));
    }

    /**
     * The EAN of every product of the category, the only field the inventory's "W katalogu" column compares, for
     * {@link CatalogPlacement}. Read consistently: the inventory page shows a product as in the catalog right after
     * "Uzupełnij dane" saved it, possibly on another instance, and an eventually consistent read could miss the rows
     * written a moment ago.
     */
    public List<String> eansOf(String categoryId) {
        return eansOf(categoryId, null);
    }

    /** As {@link #eansOf(String)}, at most pageSize items a query page (null: DynamoDB's 1 MB page). */
    List<String> eansOf(String categoryId, Integer pageSize) {
        return queryAllPages(byCategory(categoryId).withProjectionExpression("ean").withConsistentRead(true)
                .withLimit(pageSize), item -> stringOf(item, "ean"));
    }

    private static QueryRequest byCategory(String categoryId) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":categoryId", new AttributeValue().withS(categoryId));
        return new QueryRequest()
                .withTableName(TABLE)
                .withKeyConditionExpression("categoryId = :categoryId")
                .withExpressionAttributeValues(eav);
    }

    /**
     * Every item the query matches. One page holds at most 1 MB (or the request's limit) and a filter is applied after
     * a page is read, so a page can come back empty while the next ones still hold matches: the pages are followed until
     * DynamoDB has none left, never until the first empty one.
     */
    private <T> List<T> queryAllPages(QueryRequest request, Function<Map<String, AttributeValue>, T> mapper) {
        List<T> result = new ArrayList<>();
        Map<String, AttributeValue> lastEvaluatedKey = null;
        do {
            QueryResult page = amazonDynamoDB.query(request.clone().withExclusiveStartKey(lastEvaluatedKey));
            for (Map<String, AttributeValue> item : page.getItems()) {
                result.add(mapper.apply(item));
            }
            lastEvaluatedKey = page.getLastEvaluatedKey();
        } while (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty());
        return result;
    }

    private Product toProduct(Map<String, AttributeValue> item) {
        return dynamoDBMapper.marshallIntoObject(Product.class, item);
    }

    private static String stringOf(Map<String, AttributeValue> item, String attribute) {
        AttributeValue value = item.get(attribute);
        return value == null ? null : value.getS();
    }

    public List<Product> findAll(ProductCatalog catalog) {
        List<String> categoryIds = getCategoryIds(catalog);
        List<Product> result = new ArrayList<>();
        for (String categoryId : categoryIds) {
            result.addAll(findAll(categoryId));
        }
        return result;
    }

    public List<Product> findAllByPimId(String pimId) {
        return findAllByPimId(pimId, null);
    }

    /** As {@link #findAllByPimId(String)}, at most pageSize items a query page (null: DynamoDB's 1 MB page). */
    List<Product> findAllByPimId(String pimId, Integer pageSize) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":pimId", new AttributeValue().withS(pimId));

        QueryRequest queryRequest = new QueryRequest()
                .withTableName(TABLE)
                .withIndexName("PimIdIndex")
                .withKeyConditionExpression("pimId = :pimId")
                .withExpressionAttributeValues(eav)
                .withLimit(pageSize);

        return queryAllPages(queryRequest, this::toProduct);
    }

    /** Clears {@code pimId} on every product pinned to it and returns those products, fully loaded. */
    public List<Product> detachPimFromProducts(String pimId) {
        List<Product> detached = new ArrayList<>();
        for (Product product : findAllByPimId(pimId)) {
            Product fullProduct = findByProductId(product.getCategoryId(), product.getProductId());
            if (fullProduct != null) {
                fullProduct.setPimId(null);
                dynamoDBMapper.save(fullProduct);
                detached.add(fullProduct);
            }
        }
        return detached;
    }

    private static List<String> getCategoryIds(ProductCatalog catalog) {
        return catalog.getCategories()
                .stream()
                .map(CategoryDefinition::getCategoryId)
                .collect(Collectors.toList());
    }

    /**
     * The products of the category with a PIM entry, enabled or disabled as asked: what the price list, the marketplace
     * export and the shop's product list of a manual category read, so every page of the category is followed.
     */
    public List<Product> findAllProductsWithPimId(String categoryId, boolean enabled) {
        return findAllProductsWithPimId(categoryId, enabled, null);
    }

    /** As {@link #findAllProductsWithPimId(String, boolean)}, at most pageSize items a query page (null: 1 MB). */
    List<Product> findAllProductsWithPimId(String categoryId, boolean enabled, Integer pageSize) {
        QueryRequest queryRequest = byCategory(categoryId)
                .withFilterExpression("enabled = :enabled AND attribute_exists(pimId)")
                .withLimit(pageSize);
        queryRequest.getExpressionAttributeValues().put(":enabled", new AttributeValue().withN(enabled ? "1" : "0"));
        return queryAllPages(queryRequest, this::toProduct);
    }

    public List<Product> scanAllKeys() {
        List<Product> result = new ArrayList<>();
        Map<String, AttributeValue> lastEvaluatedKey = null;

        do {
            ScanRequest scanRequest = new ScanRequest()
                    .withTableName(TABLE)
                    .withProjectionExpression("categoryId, productId, version");

            if (lastEvaluatedKey != null) {
                scanRequest.withExclusiveStartKey(lastEvaluatedKey);
            }

            ScanResult scanResult = amazonDynamoDB.scan(scanRequest);
            for (Map<String, AttributeValue> item : scanResult.getItems()) {
                Product product = new Product();
                product.setCategoryId(item.get("categoryId").getS());
                product.setProductId(item.get("productId").getS());
                product.setVersion(Long.parseLong(item.get("version").getN()));
                result.add(product);
            }

            lastEvaluatedKey = scanResult.getLastEvaluatedKey();
        } while (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty());

        return result;
    }

    public List<Product> findAllWithoutPimIdByEan(List<String> eans) {
        Map<String, AttributeValue> eav = new HashMap<>();
        String filter = withoutPimIdByEanFilter(eans, eav);
        if (filter == null) {
            return new ArrayList<>();
        }

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression(filter)
                .withExpressionAttributeValues(eav);

        return dynamoDBMapper.scan(Product.class, scanExpression);
    }

    /** Products of the given categories whose EAN is among the given ones, pinned to a PIM entry or not. */
    public List<Product> findAllByEanInCategories(Collection<String> categoryIds, List<String> eans) {
        return findAllInCategories(categoryIds, eav -> eanCondition(eans, eav));
    }

    private List<Product> findAllInCategories(Collection<String> categoryIds,
                                              Function<Map<String, AttributeValue>, String> filterBuilder) {
        List<Product> result = new ArrayList<>();
        for (String categoryId : categoryIds) {
            Map<String, AttributeValue> eav = new HashMap<>();
            String filter = filterBuilder.apply(eav);
            if (filter == null) {
                return result;
            }
            eav.put(":categoryId", new AttributeValue().withS(categoryId));

            DynamoDBQueryExpression<Product> queryExpression = new DynamoDBQueryExpression<Product>()
                    .withKeyConditionExpression("categoryId = :categoryId")
                    .withFilterExpression(filter)
                    .withExpressionAttributeValues(eav);

            result.addAll(dynamoDBMapper.query(Product.class, queryExpression));
        }
        return result;
    }

    /**
     * Builds the filter "pimId missing or null, and ean among the given ones", adding its values to {@code eav}.
     * Returns null when the list is empty, i.e. nothing can match.
     */
    private static String withoutPimIdByEanFilter(List<String> eans, Map<String, AttributeValue> eav) {
        String codes = eanCondition(eans, eav);
        if (codes == null) {
            return null;
        }
        eav.put(":nullValue", new AttributeValue().withNULL(true));
        return "(attribute_not_exists(pimId) OR pimId = :nullValue) AND " + codes;
    }

    /**
     * Builds the condition "ean among the given ones", adding its values to {@code eav}.
     * Returns null when the list is empty, i.e. nothing can match.
     */
    private static String eanCondition(List<String> eans, Map<String, AttributeValue> eav) {
        if (eans.isEmpty()) {
            return null;
        }
        List<String> placeholders = new ArrayList<>();
        for (int i = 0; i < eans.size(); i++) {
            String key = ":ean" + i;
            placeholders.add(key);
            eav.put(key, new AttributeValue().withS(eans.get(i)));
        }
        return "ean IN (" + String.join(", ", placeholders) + ")";
    }

}
