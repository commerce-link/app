package pl.commercelink.products;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperTableModel;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ProductCatalogPricelistSchedulePersistenceTest {

    @Test
    void pricelistScheduleIsWrittenAndSurvivesTheTableModelRoundTrip() {
        // given
        ProductCatalog catalog = new ProductCatalog("store-1", "cat-1");
        catalog.setPricelistSchedule("0 6,14 * * ? *");

        // when
        DynamoDBMapperTableModel<ProductCatalog> model = catalogModel();
        Map<String, AttributeValue> attributes = model.convert(catalog);
        ProductCatalog restored = model.unconvert(attributes);

        // then
        assertThat(attributes.get("pricelistSchedule").getS()).isEqualTo("0 6,14 * * ? *");
        assertThat(restored.getPricelistSchedule()).isEqualTo("0 6,14 * * ? *");
    }

    @Test
    void missingPricelistScheduleIsOmittedFromTheItemAndUnconvertsToNull() {
        // given
        ProductCatalog catalog = new ProductCatalog("store-1", "cat-1");

        // when
        DynamoDBMapperTableModel<ProductCatalog> model = catalogModel();
        Map<String, AttributeValue> attributes = model.convert(catalog);
        ProductCatalog restored = model.unconvert(attributes);

        // then
        assertThat(attributes).doesNotContainKey("pricelistSchedule");
        assertThat(restored.getPricelistSchedule()).isNull();
    }

    private DynamoDBMapperTableModel<ProductCatalog> catalogModel() {
        DynamoDBMapper mapper = new DynamoDBMapper(mock(AmazonDynamoDB.class));
        return mapper.getTableModel(ProductCatalog.class, DynamoDBMapperConfig.DEFAULT);
    }
}
