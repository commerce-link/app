package pl.commercelink.stores;

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

class StoreEnabledGroupsPersistenceTest {

    @Test
    void enabledProductGroupsPersistAsListOfGroupNameStrings() {
        // given
        FulfilmentConfiguration configuration = new FulfilmentConfiguration();
        configuration.setEnabledProductGroups(List.of("PcComponents", "Peripherals"));
        Store store = new Store();
        store.setStoreId("store-1");
        store.setFulfilmentConfiguration(configuration);

        // when
        DynamoDBMapperTableModel<Store> model = storeModel();
        Map<String, AttributeValue> attributes = model.convert(store);
        Map<String, AttributeValue> fulfilment = attributes.get("fulfilment").getM();
        Store restored = model.unconvert(attributes);

        // then
        assertThat(fulfilment.get("enabledProductGroups").getL())
                .extracting(AttributeValue::getS)
                .containsExactly("PcComponents", "Peripherals");
        assertThat(restored.getFulfilmentConfiguration().getEnabledProductGroups())
                .extracting(String::valueOf)
                .containsExactly("PcComponents", "Peripherals");
    }

    @Test
    void emptyEnabledProductGroupsSurviveTheRoundTrip() {
        // given
        FulfilmentConfiguration configuration = new FulfilmentConfiguration();
        configuration.setEnabledProductGroups(List.of());
        Store store = new Store();
        store.setStoreId("store-1");
        store.setFulfilmentConfiguration(configuration);

        // when
        DynamoDBMapperTableModel<Store> model = storeModel();
        Store restored = model.unconvert(model.convert(store));

        // then
        assertThat(restored.getFulfilmentConfiguration().getEnabledProductGroups()).isEmpty();
    }

    private DynamoDBMapperTableModel<Store> storeModel() {
        DynamoDBMapper mapper = new DynamoDBMapper(mock(AmazonDynamoDB.class));
        return mapper.getTableModel(Store.class, DynamoDBMapperConfig.DEFAULT);
    }
}
