package pl.commercelink.orders.rma;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.SupplierIdentity;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class RMACentersRepository extends DynamoDbRepository<RMACenter> {

    public RMACentersRepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    public RMACenter findById(String storeId, String rmaCenterId) {
        return dynamoDBMapper.load(RMACenter.class, storeId, rmaCenterId);
    }

    public List<RMACenter> findByProviderName(String storeId, String providerName) {
        return dynamoDBMapper.scan(RMACenter.class, providerScan(storeId, providerName));
    }

    // Store centres are keyed by the exact connection identity, but platform-wide (default) centres
    // are keyed by supplier type, so a delivery on `Elko-k7f3a9c2` must also match a default `Elko`
    // centre. Manual identities resolve to type "manual", which no default centre uses.
    static DynamoDBScanExpression providerScan(String storeId, String providerName) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));
        eav.put(":storeIdDefault", new AttributeValue().withS(RMACenter.MANAGED_RMA_CENTER_STORE_ID));
        eav.put(":provider", new AttributeValue().withS(providerName));
        eav.put(":type", new AttributeValue().withS(SupplierIdentity.typeOf(providerName)));

        return new DynamoDBScanExpression()
                .withFilterExpression("(storeId = :storeId and provider = :provider)"
                        + " or (storeId = :storeIdDefault and (provider = :provider or provider = :type))")
                .withExpressionAttributeValues(eav);
    }

    public List<RMACenter> findByStoreId(String storeId) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));
        eav.put(":storeIdDefault", new AttributeValue().withS("default"));

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression("storeId = :storeId or storeId = :storeIdDefault")
                .withExpressionAttributeValues(eav);

        return dynamoDBMapper.scan(RMACenter.class, scanExpression);
    }

}
