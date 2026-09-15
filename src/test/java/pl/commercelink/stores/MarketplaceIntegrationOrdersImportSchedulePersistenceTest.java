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

class MarketplaceIntegrationOrdersImportSchedulePersistenceTest {

    @Test
    void ordersImportScheduleIsWrittenOnTheIntegrationAndSurvivesTheTableModelRoundTrip() {
        // given
        MarketplaceIntegration integration = new MarketplaceIntegration("CsCartMultiVendor");
        integration.setOrdersImportSchedule("0/15 * * * ? *");
        Store store = storeWith(integration);

        // when
        DynamoDBMapperTableModel<Store> model = storeModel();
        Map<String, AttributeValue> attributes = model.convert(store);
        Store restored = model.unconvert(attributes);

        // then
        assertThat(firstMarketplace(attributes).get("ordersImportSchedule").getS()).isEqualTo("0/15 * * * ? *");
        assertThat(restored.getMarketplaceIntegration("CsCartMultiVendor").getOrdersImportSchedule()).isEqualTo("0/15 * * * ? *");
    }

    @Test
    void missingOrdersImportScheduleIsOmittedFromTheItemAndUnconvertsToNull() {
        // given
        Store store = storeWith(new MarketplaceIntegration("Allegro"));

        // when
        DynamoDBMapperTableModel<Store> model = storeModel();
        Map<String, AttributeValue> attributes = model.convert(store);
        Store restored = model.unconvert(attributes);

        // then
        assertThat(firstMarketplace(attributes)).doesNotContainKey("ordersImportSchedule");
        assertThat(restored.getMarketplaceIntegration("Allegro").getOrdersImportSchedule()).isNull();
    }

    private Store storeWith(MarketplaceIntegration integration) {
        Store store = new Store();
        store.setStoreId("store-1");
        store.getMarketplaces().add(integration);
        return store;
    }

    private Map<String, AttributeValue> firstMarketplace(Map<String, AttributeValue> attributes) {
        return attributes.get("marketplaces").getL().get(0).getM();
    }

    private DynamoDBMapperTableModel<Store> storeModel() {
        DynamoDBMapper mapper = new DynamoDBMapper(mock(AmazonDynamoDB.class));
        return mapper.getTableModel(Store.class, DynamoDBMapperConfig.DEFAULT);
    }
}
