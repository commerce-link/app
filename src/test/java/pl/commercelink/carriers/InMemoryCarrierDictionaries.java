package pl.commercelink.carriers;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.mock;

/** Dictionaries kept in memory, for tests of the code that reads them through {@link CarrierMappings}. */
public class InMemoryCarrierDictionaries extends CarrierDictionaryRepository {

    private final Map<String, CarrierDictionary> dictionaries = new HashMap<>();

    public InMemoryCarrierDictionaries() {
        super(mock(AmazonDynamoDB.class));
    }

    public static CarrierMappings none() {
        return new CarrierMappings(new InMemoryCarrierDictionaries());
    }

    public InMemoryCarrierDictionaries with(String dictionary, String... pairs) {
        dictionaries.put(dictionary, new CarrierDictionary(dictionary, Arrays.stream(pairs)
                .map(pair -> pair.split("=", 2))
                .map(parts -> new CarrierMapping(parts[0], parts[1]))
                .toList()));
        return this;
    }

    public CarrierMappings mappings() {
        return new CarrierMappings(this);
    }

    @Override
    public Optional<CarrierDictionary> find(String dictionary) {
        return Optional.ofNullable(dictionaries.get(dictionary));
    }

    @Override
    public void save(CarrierDictionary dictionary) {
        dictionaries.put(dictionary.getDictionary(), dictionary);
    }
}
