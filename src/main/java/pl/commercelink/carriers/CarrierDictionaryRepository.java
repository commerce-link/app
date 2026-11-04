package pl.commercelink.carriers;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class CarrierDictionaryRepository {

    private static final DynamoDBMapperConfig CONSISTENT = DynamoDBMapperConfig.builder()
            .withConsistentReads(DynamoDBMapperConfig.ConsistentReads.CONSISTENT)
            .build();

    private final DynamoDBMapper mapper;

    public CarrierDictionaryRepository(AmazonDynamoDB amazonDynamoDB) {
        this.mapper = new DynamoDBMapper(amazonDynamoDB);
    }

    /** An eventually consistent GetItem: a dashboard edit read a moment late changes nothing for an order. */
    public Optional<CarrierDictionary> find(String dictionary) {
        return load(dictionary, false);
    }

    public CarrierDictionary get(String dictionary) {
        return find(dictionary).orElseGet(() -> CarrierDictionary.empty(dictionary));
    }

    /**
     * A consistent GetItem for the dashboard editor: the page shown right after a save has to carry the new version,
     * otherwise the next save would be refused as a concurrent edit.
     */
    public CarrierDictionary getForEditing(String dictionary) {
        return load(dictionary, true).orElseGet(() -> CarrierDictionary.empty(dictionary));
    }

    protected Optional<CarrierDictionary> load(String dictionary, boolean consistent) {
        return Optional.ofNullable(mapper.load(CarrierDictionary.class, dictionary, consistent ? CONSISTENT : DynamoDBMapperConfig.DEFAULT));
    }

    /** The whole dictionary in one PutItem, conditional on its version: a concurrent edit fails instead of being lost. */
    public void save(CarrierDictionary dictionary) {
        mapper.save(dictionary, DynamoDBMapperConfig.SaveBehavior.PUT.config());
    }
}
