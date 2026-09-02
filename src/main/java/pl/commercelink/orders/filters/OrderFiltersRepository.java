package pl.commercelink.orders.filters;

import pl.commercelink.orders.filters.model.OrderFilter;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import org.springframework.stereotype.Repository;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class OrderFiltersRepository extends DynamoDbRepository<OrderFilter> {

    public OrderFiltersRepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    public List<OrderFilter> findAllByStoreId(String storeId) {
        OrderFilter key = new OrderFilter();
        key.setStoreId(storeId);

        DynamoDBQueryExpression<OrderFilter> query = new DynamoDBQueryExpression<OrderFilter>()
                .withHashKeyValues(key);

        return new ArrayList<>(dynamoDBMapper.query(OrderFilter.class, query));
    }

    public Optional<OrderFilter> findById(String storeId, String filterKey) {
        return Optional.ofNullable(dynamoDBMapper.load(OrderFilter.class, storeId, filterKey));
    }
}
