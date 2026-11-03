package pl.commercelink.carriers;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The keys of the dictionaries, {scope}#{type}#{dictionary}. The caller always knows where a text came from, so it
 * names the dictionary; there is no fallback from a store to the global scope. The separator is '#' because supplier
 * connection ids contain '-'.
 */
public final class CarrierDictionaries {

    public static final String GLOBAL = "global";
    public static final String MARKETPLACE = "marketplace";
    public static final String SHIPPING = "shipping";
    public static final String SUPPLIER = "supplier";

    private static final String SEPARATOR = "#";

    /**
     * Marketplaces whose order import and shipment status speak different vocabularies get two dictionaries: Allegro
     * sends delivery method names and takes /order/carriers ids, Ceneo sends the seller's delivery form names and
     * takes numbers. Empik and Morele get no status code, as before.
     */
    enum Marketplace {
        ALLEGRO("allegro-methods", "allegro", false),
        CENEO("ceneo-forms", "ceneo", true),
        EMPIK("empik", null, false),
        MORELE("morele", null, false);

        private final String importDictionary;
        private final String statusDictionary;
        private final boolean statusRequiresCode;

        Marketplace(String importDictionary, String statusDictionary, boolean statusRequiresCode) {
            this.importDictionary = importDictionary;
            this.statusDictionary = statusDictionary;
            this.statusRequiresCode = statusRequiresCode;
        }

        static Optional<Marketplace> of(String marketplace) {
            return Arrays.stream(values()).filter(m -> m.name().equalsIgnoreCase(StringUtils.trim(marketplace))).findFirst();
        }
    }

    private CarrierDictionaries() {
    }

    /** The dictionary of the carrier text an imported order brings; an unknown marketplace has one named after it. */
    public static String marketplaceImport(String marketplace) {
        String name = Marketplace.of(marketplace).map(m -> m.importDictionary).orElseGet(() -> lower(marketplace));
        return key(GLOBAL, MARKETPLACE, name);
    }

    /** The dictionary of the carrier code sent with the shipment status and received with returns, if the marketplace has one. */
    public static Optional<String> marketplaceStatus(String marketplace) {
        return Marketplace.of(marketplace)
                .map(m -> m.statusDictionary)
                .map(name -> key(GLOBAL, MARKETPLACE, name));
    }

    /** Ceneo refuses the shipment status without a carrier code, so a carrier without one is worth an alert at import. */
    public static boolean statusRequiresCode(String marketplace) {
        return Marketplace.of(marketplace).map(m -> m.statusRequiresCode).orElse(false);
    }

    public static String shipping(String integration) {
        return key(GLOBAL, SHIPPING, lower(integration));
    }

    public static String supplier(String storeId, String connection) {
        return key(storeId, SUPPLIER, connection);
    }

    /** The shared dictionaries, as listed on the super admin page: they follow the integrations, so they are fixed in code. */
    public static List<String> shared() {
        return List.of(
                key(GLOBAL, MARKETPLACE, Marketplace.ALLEGRO.importDictionary),
                key(GLOBAL, MARKETPLACE, Marketplace.ALLEGRO.statusDictionary),
                key(GLOBAL, MARKETPLACE, Marketplace.CENEO.importDictionary),
                key(GLOBAL, MARKETPLACE, Marketplace.CENEO.statusDictionary),
                key(GLOBAL, MARKETPLACE, Marketplace.EMPIK.importDictionary),
                key(GLOBAL, MARKETPLACE, Marketplace.MORELE.importDictionary),
                shipping("furgonetka"),
                shipping("allegro"));
    }

    /** Import and status dictionaries of the marketplaces that refuse a shipment status without a carrier code. */
    public static List<Map.Entry<String, String>> requiredStatusCodes() {
        return Arrays.stream(Marketplace.values())
                .filter(m -> m.statusRequiresCode)
                .map(m -> Map.entry(key(GLOBAL, MARKETPLACE, m.importDictionary), key(GLOBAL, MARKETPLACE, m.statusDictionary)))
                .toList();
    }

    /** Names and supplier methods are free text that only matches exactly; marketplaces and labels also by fragment. */
    public static boolean matchesByFragment(String dictionary) {
        return type(dictionary).filter(type -> type.equals(MARKETPLACE) || type.equals(SHIPPING)).isPresent();
    }

    public static Optional<String> type(String dictionary) {
        String[] parts = StringUtils.split(dictionary, SEPARATOR);
        return parts != null && parts.length >= 3 ? Optional.of(parts[1]) : Optional.empty();
    }

    private static String key(String scope, String type, String name) {
        return scope + SEPARATOR + type + SEPARATOR + name;
    }

    private static String lower(String name) {
        return StringUtils.trimToEmpty(name).toLowerCase(Locale.ROOT);
    }
}
