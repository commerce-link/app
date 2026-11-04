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
 * One set of names or codes of an external system, as an ordered list of {externalText, carrier} pairs. Within a
 * dictionary the mapping works both ways, and the order settles both ambiguities: of several external texts of one
 * carrier the first goes out, of several carriers of one external text the first comes in.
 * <p>
 * The key is {scope}#{type}#{name}. The caller always knows where an external text came from, so it names the
 * dictionary; there is no fallback from a store to the global scope. The separator is '#' because supplier
 * connection ids contain '-'.
 */
@DynamoDBTable(tableName = CarrierDictionary.TABLE_NAME)
@Getter
@Setter
@NoArgsConstructor
public class CarrierDictionary {

    public static final String TABLE_NAME = "CarrierDictionaries";

    private static final String GLOBAL = "global";
    private static final String MARKETPLACE = "marketplace";
    private static final String SHIPPING = "shipping";
    private static final String SUPPLIER = "supplier";
    private static final String SEPARATOR = "#";

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

    public static String marketplaceOrderCarrierTexts(String marketplace) {
        return key(GLOBAL, MARKETPLACE, lower(marketplace) + SEPARATOR + "import");
    }

    // also read for returns; left empty for a marketplace whose status takes no carrier code
    public static String marketplaceStatusCarrierCodes(String marketplace) {
        return key(GLOBAL, MARKETPLACE, lower(marketplace) + SEPARATOR + "status");
    }

    public static String shippingLabelCarrierCodes(String integration) {
        return key(GLOBAL, SHIPPING, lower(integration));
    }

    public static String supplierDeliveryMethods(String storeId, String connection) {
        return key(storeId, SUPPLIER, connection);
    }

    /** Names and supplier methods are free text that only matches exactly; marketplaces and labels also by fragment. */
    public static boolean matchesByFragment(String dictionary) {
        return type(dictionary).filter(type -> type.equals(MARKETPLACE) || type.equals(SHIPPING)).isPresent();
    }

    public static boolean isSupplier(String dictionary) {
        return type(dictionary).filter(SUPPLIER::equals).isPresent();
    }

    public Optional<String> carrierFor(String externalText) {
        String normalized = normalize(externalText);
        if (normalized == null) {
            return Optional.empty();
        }
        return mappings().stream()
                .filter(mapping -> normalized.equalsIgnoreCase(mapping.getExternalText()))
                .map(CarrierMapping::getCarrier)
                .findFirst();
    }

    /**
     * The carrier of the longest external text of the list contained in the given one, for systems that send whole
     * names ("Allegro Paczkomaty InPost 24/7"). An external text made of digits only matches exactly, so Morele's "14"
     * is not "4".
     */
    public Optional<String> carrierByFragmentFor(String externalText) {
        String normalized = normalize(externalText);
        if (normalized == null) {
            return Optional.empty();
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        return mappings().stream()
                .filter(mapping -> mapping.getExternalText() != null)
                .filter(mapping -> !StringUtils.isNumeric(mapping.getExternalText()))
                .filter(mapping -> upper.contains(mapping.getExternalText().toUpperCase(Locale.ROOT)))
                .max(Comparator.comparingInt(mapping -> mapping.getExternalText().length()))
                .map(CarrierMapping::getCarrier);
    }

    public Optional<String> externalTextFor(String carrier) {
        if (StringUtils.isBlank(carrier)) {
            return Optional.empty();
        }
        String name = carrier.trim();
        return mappings().stream()
                .filter(mapping -> name.equalsIgnoreCase(mapping.getCarrier()))
                .map(CarrierMapping::getExternalText)
                .findFirst();
    }

    public static String normalize(String text) {
        return StringUtils.trimToNull(StringUtils.normalizeSpace(text));
    }

    private static Optional<String> type(String dictionary) {
        String[] parts = StringUtils.split(dictionary, SEPARATOR);
        return parts != null && parts.length >= 3 ? Optional.of(parts[1]) : Optional.empty();
    }

    private static String key(String scope, String type, String name) {
        return scope + SEPARATOR + type + SEPARATOR + name;
    }

    private static String lower(String name) {
        return StringUtils.trimToEmpty(name).toLowerCase(Locale.ROOT);
    }

    private List<CarrierMapping> mappings() {
        return mappings == null ? List.of() : mappings;
    }
}
