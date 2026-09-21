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

class StoreSupplierConnectionLabelPersistenceTest {

    @Test
    void labelAndBillingShortcutSurviveTheTableModelRoundTrip() {
        // given
        StoreSupplierConnection connection = new StoreSupplierConnection("Kosatec-k7f3a9c2", ConnectionMode.OWN);
        connection.setLabel("Kosatec konto B2B");
        connection.setBillingShortcut("KOSATEC-B");
        Store store = storeWith(connection);

        // when
        DynamoDBMapperTableModel<Store> model = storeModel();
        Map<String, AttributeValue> attributes = model.convert(store);
        Store restored = model.unconvert(attributes);

        // then
        Map<String, AttributeValue> stored = firstConnection(attributes);
        assertThat(stored.get("label").getS()).isEqualTo("Kosatec konto B2B");
        assertThat(stored.get("billingShortcut").getS()).isEqualTo("KOSATEC-B");
        StoreSupplierConnection back = restored.getFulfilmentConfiguration().getSupplierConnections().get(0);
        assertThat(back.getLabel()).isEqualTo("Kosatec konto B2B");
        assertThat(back.getBillingShortcut()).isEqualTo("KOSATEC-B");
    }

    @Test
    void aConnectionWithoutLabelIsStoredExactlyAsBeforeThisFeature() {
        // given -- the shape every production connection has today
        Store store = storeWith(new StoreSupplierConnection("Kosatec", ConnectionMode.OWN));

        // when
        Map<String, AttributeValue> attributes = storeModel().convert(store);

        // then
        assertThat(firstConnection(attributes)).doesNotContainKeys("label", "billingShortcut");
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
