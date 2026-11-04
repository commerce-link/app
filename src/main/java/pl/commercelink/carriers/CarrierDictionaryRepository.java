package pl.commercelink.carriers;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class CarrierDictionaryRepository {

    private final DynamoDBMapper mapper;

    public CarrierDictionaryRepository(AmazonDynamoDB amazonDynamoDB) {
        this.mapper = new DynamoDBMapper(amazonDynamoDB);
    }

    /**
     * An eventually consistent GetItem, also on the dashboard: an edit read a moment late changes nothing for an order,
     * and the editor shows the version it saved itself.
     */
    public Optional<CarrierDictionary> find(String dictionary) {
        return load(dictionary);
    }

    public CarrierDictionary get(String dictionary) {
        return find(dictionary).orElseGet(() -> CarrierDictionary.empty(dictionary));
    }

    protected Optional<CarrierDictionary> load(String dictionary) {
        return Optional.ofNullable(mapper.load(CarrierDictionary.class, dictionary));
    }

    /** The whole dictionary in one PutItem, conditional on its version: a concurrent edit fails instead of being lost. */
    public void save(CarrierDictionary dictionary) {
        mapper.save(dictionary, DynamoDBMapperConfig.SaveBehavior.PUT.config());
    }
}
