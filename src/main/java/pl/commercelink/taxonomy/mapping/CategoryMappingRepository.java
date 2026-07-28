package pl.commercelink.taxonomy.mapping;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import org.springframework.stereotype.Repository;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class CategoryMappingRepository extends DynamoDbRepository<CategoryMapping> {

    public CategoryMappingRepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    public CategoryMapping find(String supplier, String rawCategoryKey) {
        return dynamoDBMapper.load(CategoryMapping.class, supplier, rawCategoryKey);
    }

    public List<CategoryMapping> findAll() {
        return new ArrayList<>(dynamoDBMapper.scan(CategoryMapping.class, new DynamoDBScanExpression()));
    }
}
