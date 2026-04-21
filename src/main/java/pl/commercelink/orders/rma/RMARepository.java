package pl.commercelink.orders.rma;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.springframework.stereotype.Repository;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Repository
public class RMARepository extends DynamoDbRepository<RMA>  {

    public RMARepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    public RMA findById(String storeId, String rmaId) {
        return dynamoDBMapper.load(RMA.class, storeId, rmaId);
    }

    public List<RMA> findAll() {
        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression();
        return dynamoDBMapper.scan(RMA.class, scanExpression);
    }

    public List<RMA> searchRMAEntries(String storeId, RMAFilter filter, int page, int pageSize) {
        if (isNotBlank(filter.getRmaId())) {
            RMA rma = findById(storeId, filter.getRmaId());
            return rma != null ? Collections.singletonList(rma) : Collections.emptyList();
        } else {
            Map<String, AttributeValue> eav = new HashMap<>();
            Map<String, String> expressionAttributeNames = new HashMap<>();
            StringBuilder filterExpression = new StringBuilder();
            eav.put(":storeId", new AttributeValue().withS(storeId));

            if (isNotBlank(filter.getOrderId())) {
                eav.put(":orderId", new AttributeValue().withS(filter.getOrderId()));
                appendFilter(filterExpression, "orderId = :orderId");
            }

            if (isNotBlank(filter.getEmail())) {
                eav.put(":email", new AttributeValue().withS(filter.getEmail()));
                appendFilter(filterExpression, "email = :email");
            }

            if (filter.getCreatedAtStart() != null && filter.getCreatedAtEnd() != null) {
                eav.put(":createdAtStart", new AttributeValue().withS(filter.getCreatedAtStart().toString()));
                eav.put(":createdAtEnd", new AttributeValue().withS(filter.getCreatedAtEnd().toString()));
                appendFilter(filterExpression, "createdAt BETWEEN :createdAtStart AND :createdAtEnd");
            } else if (filter.getCreatedAtStart() != null) {
                eav.put(":createdAtStart", new AttributeValue().withS(filter.getCreatedAtStart().toString()));
                appendFilter(filterExpression, "createdAt >= :createdAtStart");
            } else if (filter.getCreatedAtEnd() != null) {
                eav.put(":createdAtEnd", new AttributeValue().withS(filter.getCreatedAtEnd().toString()));
                appendFilter(filterExpression, "createdAt <= :createdAtEnd");
            }

            // Exclude Rejected and Completed RMA by default
            if (!filter.hasAnyFilter()) {
                expressionAttributeNames.put("#status", "status");
                eav.put(":rejectedStatus", new AttributeValue().withS(RMAStatus.Rejected.name()));
                eav.put(":completedStatus", new AttributeValue().withS(RMAStatus.Completed.name()));
                appendFilter(filterExpression, "NOT (#status IN (:rejectedStatus, :completedStatus))");
            }

            DynamoDBQueryExpression<RMA> queryExpression = new DynamoDBQueryExpression<RMA>()
                    .withKeyConditionExpression("storeId = :storeId")
                    .withFilterExpression(filterExpression.length() > 0 ? filterExpression.toString() : null)
                    .withExpressionAttributeValues(eav)
                    .withExpressionAttributeNames(expressionAttributeNames.isEmpty() ? null : expressionAttributeNames);

            return queryWithPagination(queryExpression, page, pageSize, RMA.class);
        }


    }

    public List<RMA> findAllByStoreIdAndStatus(String storeId, RMAStatus... statuses) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));

        StringBuilder filterExpression = new StringBuilder("storeId = :storeId AND (");
        for (int i = 0; i < statuses.length; i++) {
            if (i > 0) {
                filterExpression.append(" OR ");
            }
            filterExpression.append("#status").append(i).append(" = :status").append(i);
            eav.put(":status" + i, new AttributeValue().withS(statuses[i].name()));
        }
        filterExpression.append(")");

        Map<String, String> expressionAttributeNames = new HashMap<>();
        for (int i = 0; i < statuses.length; i++) {
            expressionAttributeNames.put("#status" + i, "status");
        }

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression(filterExpression.toString())
                .withExpressionAttributeValues(eav)
                .withExpressionAttributeNames(expressionAttributeNames);

        return dynamoDBMapper.scan(RMA.class, scanExpression);
    }
}
