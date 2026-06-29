package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperTableModel;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.junit.jupiter.api.Test;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.taxonomy.ProductCategory;
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
        item.setCategoryKey("Laptop");

        // when
        DynamoDBMapperTableModel<OrderItem> model = orderItemModel();
        Map<String, AttributeValue> attributes = model.convert(item);
        OrderItem restored = model.unconvert(attributes);

        // then
        assertThat(attributes.get("category").getS()).isEqualTo("Laptop");
        assertThat(restored.getCategoryKey()).isEqualTo("Laptop");
    }

    @Test
    void marshallingDoesNotLeakInterfaceDefaultAttributes() {
        // given
        OrderItem item = new OrderItem();
        item.setOrderId("order-1");
        item.setItemId("item-1");
        item.setCategoryKey("Services");

        // when
        Map<String, AttributeValue> attributes = orderItemModel().convert(item);

        // then
        assertThat(attributes.get("category").getS()).isEqualTo("Services");
        assertThat(attributes.get("category").getN()).isNull();
        assertThat(attributes).doesNotContainKeys("sequenceNumber", "categoryKey");
    }

    @Test
    void enumBridgeWritesByteIdenticalString() {
        // given
        OrderItem item = new OrderItem();
        item.setOrderId("order-1");
        item.setItemId("item-1");
        item.setCategory(ProductCategory.Services);

        // when
        Map<String, AttributeValue> attributes = orderItemModel().convert(item);

        // then
        assertThat(attributes.get("category").getS()).isEqualTo("Services");
    }

    @Test
    void bridgeExposesEnumAndRawKey() {
        // given
        OrderItem item = new OrderItem();
        item.setCategoryKey("Services");

        // when
        ProductCategory category = item.getCategory();
        String categoryKey = item.getCategoryKey();

        // then
        assertThat(category).isEqualTo(ProductCategory.Services);
        assertThat(categoryKey).isEqualTo("Services");
    }

    @Test
    void basketItemWithNullCategoryReturnsNullWithoutException() {
        // given
        BasketItem basketItem = new BasketItem();

        // when
        ProductCategory category = basketItem.getCategory();
        String categoryKey = basketItem.getCategoryKey();

        // then
        assertThat(category).isNull();
        assertThat(categoryKey).isNull();
    }

    @Test
    void warehouseItemCategoryPersistsAsStringAttribute() {
        // given
        WarehouseItem item = new WarehouseItem();
        item.setStoreId("store-1");
        item.setItemId("item-1");
        item.setCategoryKey("Services");

        // when
        DynamoDBMapper mapper = new DynamoDBMapper(mock(AmazonDynamoDB.class));
        Map<String, AttributeValue> attributes =
                mapper.getTableModel(WarehouseItem.class, DynamoDBMapperConfig.DEFAULT).convert(item);

        // then
        assertThat(attributes.get("category").getS()).isEqualTo("Services");
        assertThat(attributes.get("category").getN()).isNull();
        assertThat(attributes).doesNotContainKeys("sequenceNumber", "categoryKey");
    }

    @Test
    void basketItemCategoryPersistsAsStringInNestedDocument() {
        // given
        BasketItem basketItem = new BasketItem();
        basketItem.setCategoryKey("Laptop");
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
        assertThat(nested).doesNotContainKeys("sequenceNumber", "categoryKey");
    }

    private DynamoDBMapperTableModel<OrderItem> orderItemModel() {
        DynamoDBMapper mapper = new DynamoDBMapper(mock(AmazonDynamoDB.class));
        return mapper.getTableModel(OrderItem.class, DynamoDBMapperConfig.DEFAULT);
    }
}
