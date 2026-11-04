package pl.commercelink.carriers;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;

@Repository
public class CarriersRepository {

    private final DynamoDBMapper mapper;

    public CarriersRepository(AmazonDynamoDB amazonDynamoDB) {
        this.mapper = new DynamoDBMapper(amazonDynamoDB);
    }

    /**
     * A Scan, but of a dozen or so rows: only for the lists of the super admin page and the shipment forms. Eventually
     * consistent, so a carrier added a moment ago may be missing from the next page for a while.
     */
    public List<Carrier> findAll() {
        return mapper.scan(Carrier.class, new DynamoDBScanExpression()).stream()
                .sorted(Comparator.comparing(Carrier::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<String> names() {
        return findAll().stream().map(Carrier::getName).toList();
    }

    /**
     * @return false when a carrier of that name exists already, also written in other case: the name is the identifier
     *         and dictionaries look carriers up regardless of case, so "inpost" next to "InPost" would be ambiguous
     */
    public boolean create(Carrier carrier) {
        if (findAll().stream().anyMatch(existing -> existing.getName().equalsIgnoreCase(carrier.getName()))) {
            return false;
        }
        try {
            mapper.save(carrier,
                    new DynamoDBSaveExpression().withExpectedEntry("name", new ExpectedAttributeValue(false)),
                    DynamoDBMapperConfig.SaveBehavior.PUT.config());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }
}
