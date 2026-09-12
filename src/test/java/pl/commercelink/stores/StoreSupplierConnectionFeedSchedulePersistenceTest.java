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

class StoreSupplierConnectionFeedSchedulePersistenceTest {

    @Test
    void feedScheduleIsWrittenOnTheConnectionAndSurvivesTheTableModelRoundTrip() {
        // given
        StoreSupplierConnection connection = new StoreSupplierConnection("AcmeB", ConnectionMode.OWN);
        connection.setFeedSchedule("0/30 9-17 * * ? *");
        Store store = storeWith(connection);

        // when
        DynamoDBMapperTableModel<Store> model = storeModel();
        Map<String, AttributeValue> attributes = model.convert(store);
        Store restored = model.unconvert(attributes);

        // then
        Map<String, AttributeValue> stored = firstConnection(attributes);
        assertThat(stored.get("feedSchedule").getS()).isEqualTo("0/30 9-17 * * ? *");
        assertThat(stored.get("mode").getS()).isEqualTo("OWN");
        assertThat(restored.getFulfilmentConfiguration().getSupplierConnections().get(0).getFeedSchedule())
                .isEqualTo("0/30 9-17 * * ? *");
    }

    @Test
    void missingFeedScheduleIsOmittedFromTheItemAndUnconvertsToNull() {
        // given
        Store store = storeWith(new StoreSupplierConnection("Acme", ConnectionMode.GLOBAL));

        // when
        DynamoDBMapperTableModel<Store> model = storeModel();
        Map<String, AttributeValue> attributes = model.convert(store);
        Store restored = model.unconvert(attributes);

        // then
        assertThat(firstConnection(attributes)).doesNotContainKey("feedSchedule");
        assertThat(restored.getFulfilmentConfiguration().getSupplierConnections().get(0).getFeedSchedule()).isNull();
    }

    private Store storeWith(StoreSupplierConnection connection) {
        FulfilmentConfiguration configuration = new FulfilmentConfiguration();
        configuration.setSupplierConnections(new ArrayList<>(List.of(connection)));
        Store store = new Store();
        store.setStoreId("store-1");
        store.setFulfilmentConfiguration(configuration);
        return store;
    }

    private Map<String, AttributeValue> firstConnection(Map<String, AttributeValue> attributes) {
        return attributes.get("fulfilment").getM().get("supplierConnections").getL().get(0).getM();
    }

    private DynamoDBMapperTableModel<Store> storeModel() {
        DynamoDBMapper mapper = new DynamoDBMapper(mock(AmazonDynamoDB.class));
        return mapper.getTableModel(Store.class, DynamoDBMapperConfig.DEFAULT);
    }
}
