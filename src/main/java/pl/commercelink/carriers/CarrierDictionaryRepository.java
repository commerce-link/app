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

    /** A consistent GetItem: a change made on the dashboard counts from the next read on every instance. */
    public Optional<CarrierDictionary> find(String dictionary) {
        return Optional.ofNullable(mapper.load(CarrierDictionary.class, dictionary, CONSISTENT));
    }

    public CarrierDictionary get(String dictionary) {
        return find(dictionary).orElseGet(() -> CarrierDictionary.empty(dictionary));
    }

    /** The whole dictionary in one PutItem, conditional on its version: a concurrent edit fails instead of being lost. */
    public void save(CarrierDictionary dictionary) {
        mapper.save(dictionary, DynamoDBMapperConfig.SaveBehavior.PUT.config());
    }
}
