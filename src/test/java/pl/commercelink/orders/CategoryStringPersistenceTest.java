package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperTableModel;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.junit.jupiter.api.Test;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCustomAttributeFilter;
import pl.commercelink.warehouse.builtin.WarehouseItem;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CategoryStringPersistenceTest {

    @Test
    void categoryPersistsAsRawStringAttribute() {
        // given
        OrderItem item = new OrderItem();
        item.setOrderId("order-1");
        item.setItemId("item-1");
        item.setCategory("Laptop");

        // when
        DynamoDBMapperTableModel<OrderItem> model = orderItemModel();
        Map<String, AttributeValue> attributes = model.convert(item);
        OrderItem restored = model.unconvert(attributes);

        // then
        assertThat(attributes.get("category").getS()).isEqualTo("Laptop");
        assertThat(restored.getCategory()).isEqualTo("Laptop");
    }

    @Test
    void marshallingDoesNotLeakInterfaceDefaultAttributes() {
        // given
        OrderItem item = new OrderItem();
        item.setOrderId("order-1");
        item.setItemId("item-1");
        item.setCategory("Services");

        // when
        DynamoDBMapperTableModel<OrderItem> model = orderItemModel();
        Map<String, AttributeValue> attributes = model.convert(item);
        OrderItem restored = model.unconvert(attributes);

        // then
        assertThat(attributes.get("category").getS()).isEqualTo("Services");
        assertThat(attributes.get("category").getN()).isNull();
        assertThat(attributes.get("service").getN()).isEqualTo("1");
        assertThat(restored.isService()).isTrue();
        assertThat(attributes).doesNotContainKeys("sequenceNumber", "categoryKey", "product", "serviceGroup");
    }

    @Test
    void warehouseItemCategoryPersistsAsStringAttribute() {
        // given
        WarehouseItem item = new WarehouseItem();
        item.setStoreId("store-1");
        item.setItemId("item-1");
        item.setCategory("Services");

        // when
        DynamoDBMapper mapper = new DynamoDBMapper(mock(AmazonDynamoDB.class));
        Map<String, AttributeValue> attributes =
                mapper.getTableModel(WarehouseItem.class, DynamoDBMapperConfig.DEFAULT).convert(item);

        // then
        assertThat(attributes.get("category").getS()).isEqualTo("Services");
        assertThat(attributes.get("category").getN()).isNull();
        assertThat(attributes.get("service").getN()).isEqualTo("1");
        assertThat(attributes).doesNotContainKeys("sequenceNumber", "categoryKey", "product", "serviceGroup");
    }

    @Test
    void basketItemCategoryPersistsAsStringInNestedDocument() {
        // given
        BasketItem basketItem = new BasketItem();
        basketItem.setCategory("Laptop");
        Basket basket = new Basket();
        basket.setStoreId("store-1");
        basket.setBasketItems(List.of(basketItem));

        // when
        DynamoDBMapper mapper = new DynamoDBMapper(mock(AmazonDynamoDB.class));
        Map<String, AttributeValue> attributes =
                mapper.getTableModel(Basket.class, DynamoDBMapperConfig.DEFAULT).convert(basket);
        Map<String, AttributeValue> nested = attributes.get("basketItems").getL().get(0).getM();

        // then
        assertThat(nested.get("category").getS()).isEqualTo("Laptop");
        assertThat(nested.get("category").getN()).isNull();
        assertThat(nested.get("service").getN()).isEqualTo("0");
        assertThat(nested).doesNotContainKeys("sequenceNumber", "categoryKey", "product", "serviceGroup");
    }

    @Test
    void productPersistsFilterCategoryButNoTopLevelCategoryAttribute() {
        // given
        Product product = new Product("cat-def-1");
        ProductCustomAttributeFilter filter = new ProductCustomAttributeFilter();
        filter.setCategory("CPU");
        product.setCustomAttributesFilters(List.of(filter));

        // when
        DynamoDBMapper mapper = new DynamoDBMapper(mock(AmazonDynamoDB.class));
        DynamoDBMapperTableModel<Product> model = mapper.getTableModel(Product.class, DynamoDBMapperConfig.DEFAULT);
        Map<String, AttributeValue> attributes = model.convert(product);
        Product restored = model.unconvert(attributes);

        // then
        assertThat(attributes).doesNotContainKey("category");
        assertThat(attributes.get("customAttributesFilters").getL().get(0).getM().get("category").getS()).isEqualTo("CPU");
        assertThat(restored.getCustomAttributesFilters().get(0).getCategory()).isEqualTo("CPU");
    }

    @Test
    void categoryDefinitionCategoryPersistsAsStringInCatalogDocumentAfterConverterRemoval() {
        // given
        CategoryDefinition definition = new CategoryDefinition();
        definition.setCategoryId("cat-def-1");
        definition.setCategory("CPU");
        ProductCatalog catalog = new ProductCatalog("store-1", "catalog");
        catalog.setCategories(List.of(definition));

        // when
        DynamoDBMapper mapper = new DynamoDBMapper(mock(AmazonDynamoDB.class));
        DynamoDBMapperTableModel<ProductCatalog> model = mapper.getTableModel(ProductCatalog.class, DynamoDBMapperConfig.DEFAULT);
        Map<String, AttributeValue> attributes = model.convert(catalog);
        ProductCatalog restored = model.unconvert(attributes);

        // then
        Map<String, AttributeValue> nested = attributes.get("categories").getL().get(0).getM();
        assertThat(nested.get("category").getS()).isEqualTo("CPU");
        assertThat(nested.get("category").getN()).isNull();
        assertThat(restored.getCategories().get(0).getCategory()).isEqualTo("CPU");
    }

    private DynamoDBMapperTableModel<OrderItem> orderItemModel() {
        DynamoDBMapper mapper = new DynamoDBMapper(mock(AmazonDynamoDB.class));
        return mapper.getTableModel(OrderItem.class, DynamoDBMapperConfig.DEFAULT);
    }
}
