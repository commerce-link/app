package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperTableModel;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class StoreApiKeyPersistenceTest {

    @Test
    void persistsHashButNeverThePlaintextKey() {
        // given
        Store store = new Store();
        store.setStoreId("store-1");
        store.setApiKeyHash("deadbeef");
        store.setPlaintextApiKey("super-secret-plaintext");

        // when
        Map<String, AttributeValue> attributes = storeModel().convert(store);

        // then
        assertThat(attributes).containsKey("apiKeyHash");
        assertThat(attributes.get("apiKeyHash").getS()).isEqualTo("deadbeef");
        assertThat(attributes).doesNotContainKey("plaintextApiKey");
    }

    @Test
    void hashSurvivesTheTableModelRoundTrip() {
        // given
        Store store = new Store();
        store.setStoreId("store-1");
        store.setApiKeyHash("deadbeef");

        // when
        DynamoDBMapperTableModel<Store> model = storeModel();
        Store restored = model.unconvert(model.convert(store));

        // then
        assertThat(restored.getApiKeyHash()).isEqualTo("deadbeef");
        assertThat(restored.getPlaintextApiKey()).isNull();
    }

    private DynamoDBMapperTableModel<Store> storeModel() {
        DynamoDBMapper mapper = new DynamoDBMapper(mock(AmazonDynamoDB.class));
        return mapper.getTableModel(Store.class, DynamoDBMapperConfig.DEFAULT);
    }
}
