package pl.commercelink.starter.dynamodb;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;

import java.util.List;
import java.util.Map;

public record QueryPageResult<T>(List<T> items, Map<String, AttributeValue> lastEvaluatedKey) {
}
