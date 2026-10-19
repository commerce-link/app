package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperTableModel;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Additional shipping integrations live in the shipping settings as plain names, so a version that does not know them
 * (a rollback) still reads the store: the mapper skips attributes it has no field for.
 */
class StoreAdditionalIntegrationsMappingTest {

    private final DynamoDBMapperTableModel<Store> model = new DynamoDBMapper(mock(AmazonDynamoDB.class))
            .getTableModel(Store.class);

    @Test
    void additionalIntegrationsAreWrittenInsideTheShippingSettings() {
        // given
        Store store = new Store();
        store.setStoreId("store-1");
        store.setConfigurationValue(IntegrationType.SHIPPING_PROVIDER, "furgonetka");
        store.addAdditionalShippingIntegration("allegro");

        // when
        Map<String, AttributeValue> item = model.convert(store);

        // then
        AttributeValue names = item.get("shipping").getM().get("additionalIntegrations");
        assertThat(names.getL()).extracting(AttributeValue::getS).containsExactly("allegro");
        // nothing new in Store#integrations: the older IntegrationType enum converts every entry there
        assertThat(item.get("integrations").getL()).hasSize(1);
    }

    @Test
    void aStoreRecordWithShippingAttributesTheClassDoesNotKnowIsStillRead() {
        // given: a record written by a newer version, read by a class without that field
        Store store = new Store();
        store.setStoreId("store-1");
        store.setConfigurationValue(IntegrationType.SHIPPING_PROVIDER, "furgonetka");
        Map<String, AttributeValue> item = new HashMap<>(model.convert(store));
        Map<String, AttributeValue> shipping = new HashMap<>(item.get("shipping") == null
                ? Map.of() : item.get("shipping").getM());
        shipping.put("attributeOfANewerVersion", new AttributeValue().withL(new AttributeValue("allegro")));
        item.put("shipping", new AttributeValue().withM(shipping));
        item.put("topLevelAttributeOfANewerVersion", new AttributeValue("x"));

        // when
        Store read = model.unconvert(item);

        // then
        assertThat(read.getStoreId()).isEqualTo("store-1");
        assertThat(read.defaultShippingIntegration()).isEqualTo("furgonetka");
    }
}
