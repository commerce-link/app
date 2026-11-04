package pl.commercelink.baskets;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperTableModel;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BasketItemPersistenceTest {

    @Test
    void itemIsStoredUnderSku() {
        // given
        Basket basket = new Basket();
        basket.setStoreId("store-1");
        basket.setBasketId("basket-1");
        basket.setBasketItems(List.of(new BasketItem("pim-1", "Dysk", "5901234123457", "Dyski", 100, 0, 1, null, 3, false)));

        // when
        Map<String, AttributeValue> attributes = basketModel().convert(basket);

        // then
        Map<String, AttributeValue> item = attributes.get("basketItems").getL().get(0).getM();
        assertThat(item.get("sku").getS()).isEqualTo("5901234123457");
        assertThat(item).doesNotContainKey("mfn");
    }

    @Test
    void itemStoredBeforeTheRenameReadsItsMfnAsSku() {
        // given: a basket written by an instance of the previous version
        Map<String, AttributeValue> attributes = Map.of(
                "storeId", new AttributeValue("store-1"),
                "basketId", new AttributeValue("basket-1"),
                "basketItems", new AttributeValue().withL(new AttributeValue().withM(Map.of(
                        "name", new AttributeValue("Dysk"),
                        "mfn", new AttributeValue("MZ-V9P1T0BW")))));

        // when
        Basket basket = basketModel().unconvert(attributes);

        // then
        assertThat(basket.getBasketItems()).extracting(BasketItem::getSku).containsExactly("MZ-V9P1T0BW");
    }

    private static DynamoDBMapperTableModel<Basket> basketModel() {
        DynamoDBMapper mapper = new DynamoDBMapper(mock(AmazonDynamoDB.class));
        return mapper.getTableModel(Basket.class, DynamoDBMapperConfig.DEFAULT);
    }
}
