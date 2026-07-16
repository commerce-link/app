package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperTableModel;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class StoreEnabledCategoriesPersistenceTest {

    @Test
    void emptyEnabledCategoriesSurvivesTheTableModelRoundTrip() {
        // given
        FulfilmentConfiguration configuration = new FulfilmentConfiguration();
        configuration.setEnabledCategories(new ArrayList<>());
        Store store = new Store();
        store.setStoreId("store-1");
        store.setFulfilmentConfiguration(configuration);

        // when
        DynamoDBMapperTableModel<Store> model = storeModel();
        Map<String, AttributeValue> attributes = model.convert(store);
        Store restored = model.unconvert(attributes);

        // then
        Map<String, AttributeValue> fulfilment = attributes.get("fulfilment").getM();
        assertThat(fulfilment.get("enabledCategories").getL()).isEmpty();
        assertThat(restored.getFulfilmentConfiguration().getEnabledCategories()).isNotNull().isEmpty();
    }

    @Test
    void missingEnabledCategoriesUnconvertsToNull() {
        // given
        Store store = new Store();
        store.setStoreId("store-1");
        store.setFulfilmentConfiguration(new FulfilmentConfiguration());

        // when
        DynamoDBMapperTableModel<Store> model = storeModel();
        Map<String, AttributeValue> attributes = model.convert(store);
        Store restored = model.unconvert(attributes);

        // then
        assertThat(attributes.get("fulfilment").getM()).doesNotContainKey("enabledCategories");
        assertThat(restored.getFulfilmentConfiguration().getEnabledCategories()).isNull();
    }

    @Test
    void populatedEnabledCategoriesSurvivesTheTableModelRoundTrip() {
        // given
        FulfilmentConfiguration configuration = new FulfilmentConfiguration();
        configuration.setEnabledCategories(List.of("Komputery i urządzenia peryferyjne", "Biuro"));
        Store store = new Store();
        store.setStoreId("store-1");
        store.setFulfilmentConfiguration(configuration);

        // when
        DynamoDBMapperTableModel<Store> model = storeModel();
        Store restored = model.unconvert(model.convert(store));

        // then
        assertThat(restored.getFulfilmentConfiguration().getEnabledCategories())
                .containsExactly("Komputery i urządzenia peryferyjne", "Biuro");
    }

    private DynamoDBMapperTableModel<Store> storeModel() {
        DynamoDBMapper mapper = new DynamoDBMapper(mock(AmazonDynamoDB.class));
        return mapper.getTableModel(Store.class, DynamoDBMapperConfig.DEFAULT);
    }
}
