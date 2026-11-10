package pl.commercelink.carriers;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.mock;

/** Dictionaries kept in memory, for tests of the code that reads them through {@link CarrierTranslator}. */
public class InMemoryCarrierDictionaries extends CarrierDictionaryRepository {

    private final Map<String, CarrierDictionary> dictionaries = new HashMap<>();

    public InMemoryCarrierDictionaries() {
        super(mock(AmazonDynamoDB.class));
    }

    public static CarrierTranslator none() {
        return new CarrierTranslator(new InMemoryCarrierDictionaries());
    }

    public InMemoryCarrierDictionaries with(String dictionary, String... pairs) {
        dictionaries.put(dictionary, new CarrierDictionary(dictionary, Arrays.stream(pairs)
                .map(pair -> pair.split("=", 2))
                .map(parts -> new CarrierMapping(parts[0], parts[1]))
                .toList()));
        return this;
    }

    public CarrierTranslator translator() {
        return new CarrierTranslator(this);
    }

    @Override
    protected Optional<CarrierDictionary> load(String dictionary) {
        return Optional.ofNullable(dictionaries.get(dictionary));
    }

    @Override
    public void save(CarrierDictionary dictionary) {
        dictionaries.put(dictionary.getDictionary(), dictionary);
    }
}
