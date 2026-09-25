package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperTableModel;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ReceiptConfigurationTest {

    private static final LocalDateTime T1 = LocalDateTime.of(2026, 9, 23, 10, 0);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, 9, 24, 10, 0);

    @Test
    void enablingStampsTheMomentOnlyOnTheTransition() {
        ReceiptConfiguration configuration = new ReceiptConfiguration();

        configuration.enable(T1);
        configuration.enable(T2);

        assertThat(configuration.getEnabledAt()).isEqualTo(T1);
    }

    @Test
    void reEnablingMovesTheMoment() {
        ReceiptConfiguration configuration = new ReceiptConfiguration();
        configuration.enable(T1);

        configuration.disable();
        configuration.enable(T2);

        assertThat(configuration.getEnabledAt()).isEqualTo(T2);
    }

    @Test
    void storeNeverReturnsNullConfiguration() {
        assertThat(new Store().getReceiptConfiguration()).isNotNull();
    }

    /**
     * A store saved before the per-channel selection was removed still carries a legacy {@code sources} attribute
     * in its stored {@code receipts} map. The table model must load it without failing and simply drop the
     * attribute nothing in the class reads anymore.
     */
    @Test
    void aStoredLegacySourcesAttributeIsIgnoredOnLoad() {
        // given
        Map<String, AttributeValue> receipts = Map.of(
                "enabled", new AttributeValue().withBOOL(true),
                "enabledAt", new AttributeValue().withS("2026-09-23T10:00:00"),
                "sources", new AttributeValue().withL(
                        new AttributeValue().withS("WebStore"),
                        new AttributeValue().withS("Marketplace")));
        Map<String, AttributeValue> item = Map.of(
                "storeId", new AttributeValue().withS("store-1"),
                "receipts", new AttributeValue().withM(receipts));

        // when
        Store restored = storeModel().unconvert(item);

        // then
        assertThat(restored.getReceiptConfiguration().isEnabled()).isTrue();
        assertThat(restored.getReceiptConfiguration().getEnabledAt()).isEqualTo(T1);
    }

    private static DynamoDBMapperTableModel<Store> storeModel() {
        DynamoDBMapper mapper = new DynamoDBMapper(mock(AmazonDynamoDB.class));
        return mapper.getTableModel(Store.class, DynamoDBMapperConfig.DEFAULT);
    }
}
