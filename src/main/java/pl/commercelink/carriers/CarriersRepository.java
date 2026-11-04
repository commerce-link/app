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

    /** A Scan, but of a dozen or so rows: only for the lists of the super admin page and the shipment forms. */
    public List<Carrier> findAll() {
        return scan(false);
    }

    public List<String> names() {
        return findAll().stream().map(Carrier::getName).toList();
    }

    /** A consistent Scan for the super admin page, so a carrier just added is listed and refused a second time. */
    public List<Carrier> findAllForEditing() {
        return scan(true);
    }

    /**
     * @return false when a carrier of that name exists already, also written in other case: the name is the identifier
     *         and dictionaries look carriers up regardless of case, so "inpost" next to "InPost" would be ambiguous
     */
    public boolean create(Carrier carrier) {
        if (findAllForEditing().stream().anyMatch(existing -> existing.getName().equalsIgnoreCase(carrier.getName()))) {
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

    private List<Carrier> scan(boolean consistent) {
        return mapper.scan(Carrier.class, new DynamoDBScanExpression().withConsistentRead(consistent)).stream()
                .sorted(Comparator.comparing(Carrier::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
