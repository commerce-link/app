package pl.commercelink.orders.filters;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.TransactionWriteRequest;
import org.springframework.stereotype.Repository;
import pl.commercelink.orders.filters.model.OwnedOrderFilters;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;

import java.util.Optional;

@Repository
public class OrderFiltersRepository extends DynamoDbRepository<OwnedOrderFilters> {

    public OrderFiltersRepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    public Optional<OwnedOrderFilters> findByOwner(String storeId, String userId) {
        return Optional.ofNullable(dynamoDBMapper.load(OwnedOrderFilters.class, storeId, userId));
    }

    public void saveBoth(OwnedOrderFilters first, OwnedOrderFilters second) {
        TransactionWriteRequest request = new TransactionWriteRequest();
        request.addPut(first);
        request.addPut(second);
        dynamoDBMapper.transactionWrite(request);
    }
}
