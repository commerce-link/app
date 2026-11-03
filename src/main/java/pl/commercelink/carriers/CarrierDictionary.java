package pl.commercelink.carriers;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * One set of names or codes of an external system, as an ordered list of {text, carrier} pairs. Within a dictionary
 * the mapping works both ways, and the order settles both ambiguities: of several texts of one carrier the first goes
 * out, of several carriers of one text the first comes in.
 */
@DynamoDBTable(tableName = CarrierDictionary.TABLE_NAME)
@Getter
@Setter
@NoArgsConstructor
public class CarrierDictionary {

    public static final String TABLE_NAME = "CarrierMappings";

    @DynamoDBHashKey(attributeName = "dictionary")
    private String dictionary;
    @DynamoDBAttribute(attributeName = "mappings")
    private List<CarrierMapping> mappings = new ArrayList<>();
    @DynamoDBVersionAttribute(attributeName = "version")
    private Long version;

    public CarrierDictionary(String dictionary, List<CarrierMapping> mappings) {
        this.dictionary = dictionary;
        this.mappings = new ArrayList<>(mappings);
    }

    public static CarrierDictionary empty(String dictionary) {
        return new CarrierDictionary(dictionary, List.of());
    }

    public Optional<String> inboundMappingFor(String text) {
        String normalized = normalize(text);
        if (normalized == null) {
            return Optional.empty();
        }
        return mappings().stream()
                .filter(mapping -> normalized.equalsIgnoreCase(mapping.getText()))
                .map(CarrierMapping::getCarrier)
                .findFirst();
    }

    /**
     * The carrier of the longest text of the list contained in the given one, for systems that send whole names
     * ("Allegro Paczkomaty InPost 24/7"). A text made of digits only matches exactly, so Morele's "14" is not "4".
     */
    public Optional<String> fragmentMappingFor(String text) {
        String normalized = normalize(text);
        if (normalized == null) {
            return Optional.empty();
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        return mappings().stream()
                .filter(mapping -> mapping.getText() != null && !StringUtils.isNumeric(mapping.getText()))
                .filter(mapping -> upper.contains(mapping.getText().toUpperCase(Locale.ROOT)))
                .max(Comparator.comparingInt(mapping -> mapping.getText().length()))
                .map(CarrierMapping::getCarrier);
    }

    public Optional<String> outboundMappingFor(String carrier) {
        if (StringUtils.isBlank(carrier)) {
            return Optional.empty();
        }
        String name = carrier.trim();
        return mappings().stream()
                .filter(mapping -> name.equalsIgnoreCase(mapping.getCarrier()))
                .map(CarrierMapping::getText)
                .findFirst();
    }

    public static String normalize(String text) {
        return StringUtils.trimToNull(StringUtils.normalizeSpace(text));
    }

    private List<CarrierMapping> mappings() {
        return mappings == null ? List.of() : mappings;
    }
}
