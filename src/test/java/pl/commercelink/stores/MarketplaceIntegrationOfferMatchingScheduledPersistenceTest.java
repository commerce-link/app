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

class MarketplaceIntegrationOfferMatchingScheduledPersistenceTest {

    @Test
    void scheduledOfferMatchingIsWrittenOnTheIntegrationAndSurvivesTheTableModelRoundTrip() {
        // given
        MarketplaceIntegration integration = new MarketplaceIntegration("Empik");
        integration.setOfferMatchingScheduled(true);
        Store store = storeWith(integration);

        // when
        DynamoDBMapperTableModel<Store> model = storeModel();
        Map<String, AttributeValue> attributes = model.convert(store);
        Store restored = model.unconvert(attributes);

        // then
        assertThat(firstMarketplace(attributes).get("offerMatchingScheduled").getN()).isEqualTo("1");
        assertThat(restored.getMarketplaceIntegration("Empik").runsOfferMatchingDaily()).isTrue();
    }

    @Test
    void anIntegrationSavedBeforeTheFieldExistedDoesNotRunOfferMatchingDaily() {
        // given
        Store store = storeWith(new MarketplaceIntegration("Empik"));

        // when
        DynamoDBMapperTableModel<Store> model = storeModel();
        Map<String, AttributeValue> attributes = model.convert(store);
        Store restored = model.unconvert(attributes);

        // then
        assertThat(firstMarketplace(attributes)).doesNotContainKey("offerMatchingScheduled");
        assertThat(restored.getMarketplaceIntegration("Empik").getOfferMatchingScheduled()).isNull();
        assertThat(restored.getMarketplaceIntegration("Empik").runsOfferMatchingDaily()).isFalse();
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
