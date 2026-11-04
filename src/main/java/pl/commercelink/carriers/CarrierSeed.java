package pl.commercelink.carriers;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Loads carriers and dictionaries from a file in the format of local-init/carriers/carriers.example.json, keeping
 * whatever already exists: a carrier or dictionary edited on the dashboard is never overwritten. Used for local
 * development and tests only; production data is loaded from outside this repository.
 */
@Component
@RequiredArgsConstructor
public class CarrierSeed {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final CarriersRepository carriers;
    private final CarrierDictionaryRepository dictionaries;

    record SeedFile(List<Carrier> carriers, List<Entry> dictionaries) {
    }

    record Entry(String dictionary, List<CarrierMapping> mappings) {
    }

    public void load(InputStream json) throws IOException {
        SeedFile file = MAPPER.readValue(json, SeedFile.class);
        if (file.carriers() != null) {
            file.carriers().forEach(carriers::create);
        }
        if (file.dictionaries() != null) {
            file.dictionaries().stream()
                    .filter(entry -> dictionaries.find(entry.dictionary()).isEmpty())
                    .forEach(entry -> dictionaries.save(new CarrierDictionary(entry.dictionary(), entry.mappings())));
        }
    }
}
