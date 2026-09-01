package pl.commercelink.orders.rma;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperTableModel;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.junit.jupiter.api.Test;
import pl.commercelink.marketplace.api.MarketplaceReturnStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RMAMarketplaceFieldsTest {

    @Test
    void manuallyCreatedRmaIsNotAMarketplaceReturn() {
        // given
        RMA rma = new RMA("store-1");

        // when / then
        assertFalse(rma.isMarketplaceReturn());
    }

    @Test
    void rmaWithExternalReturnIdIsAMarketplaceReturn() {
        // given
        RMA rma = new RMA("store-1");
        rma.setMarketplace("Allegro");
        rma.setExternalReturnId("r-1");
        rma.setExternalReturnReference("XGQX/2026");
        rma.setExternalReturnStatus(MarketplaceReturnStatus.IN_TRANSIT);

        // when / then
        assertTrue(rma.isMarketplaceReturn());
        assertEquals("Allegro", rma.getMarketplace());
        assertEquals(MarketplaceReturnStatus.IN_TRANSIT, rma.getExternalReturnStatus());
    }

    @Test
    void marketplaceReturnStatusIsStoredAsItsEnumName() {
        // given
        RMA rma = new RMA("store-1");
        rma.setExternalReturnStatus(MarketplaceReturnStatus.IN_TRANSIT);

        // when
        Map<String, AttributeValue> attributes = tableModel().convert(rma);

        // then
        assertEquals("IN_TRANSIT", attributes.get("externalReturnStatus").getS());
        assertNull(attributes.get("marketplaceReturn"));
        assertEquals(MarketplaceReturnStatus.IN_TRANSIT,
                tableModel().unconvert(attributes).getExternalReturnStatus());
    }

    private DynamoDBMapperTableModel<RMA> tableModel() {
        DynamoDBMapper mapper = new DynamoDBMapper(mock(AmazonDynamoDB.class));
        return mapper.getTableModel(RMA.class, DynamoDBMapperConfig.DEFAULT);
    }
}
