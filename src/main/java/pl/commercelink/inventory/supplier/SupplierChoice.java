package pl.commercelink.inventory.supplier;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;

import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class SupplierChoice {

    public static final String CUSTOM = "__custom__";
    public static final String CUSTOM_NAME_PATTERN = "[\\p{L}\\p{N}_\\-]+";

    private static final Pattern CUSTOM_NAME = Pattern.compile("^" + CUSTOM_NAME_PATTERN + "$");

    private static final String UNKNOWN = "order.item.assign.supplier.unknown";
    private static final String REQUIRED = "order.item.assign.supplier.custom.required";
    private static final String INVALID = "order.item.assign.supplier.custom.invalid";
    private static final String INTEGRATED = "order.item.assign.supplier.integrated";
    private static final String RESERVED = "order.item.assign.supplier.reserved";

    private final SupplierRegistry supplierRegistry;

    public record Resolution(String identity, String errorCode, Object[] errorArgs) {
        static Resolution accepted(String identity) {
            return new Resolution(identity, null, null);
        }

        static Resolution rejected(String errorCode, Object... errorArgs) {
            return new Resolution(null, errorCode, errorArgs);
        }

        public boolean accepted() {
            return identity != null;
        }
    }

    public Resolution resolve(Store store, String choice, String customName) {
        String name = StringUtils.trimToNull(CUSTOM.equals(choice) ? customName : choice);
        if (name == null) {
            return Resolution.rejected(REQUIRED);
        }
        StoreSupplierConnection connection = store.getSupplierConnections().stream()
                .filter(candidate -> candidate.getSupplierName().equals(name))
                .findFirst().orElse(null);
        if (connection != null) {
            return connection.isEnabled() ? Resolution.accepted(name) : Resolution.rejected(UNKNOWN);
        }
        if (SupplierIdentity.hasToken(name) || SupplierIdentity.isManual(name)) {
            return Resolution.rejected(UNKNOWN);
        }
        if (!CUSTOM_NAME.matcher(name).matches()) {
            return Resolution.rejected(INVALID, name);
        }
        if (name.equalsIgnoreCase(SupplierRegistry.WAREHOUSE)) {
            return Resolution.rejected(RESERVED, name);
        }
        if (supplierRegistry.exists(name) && !name.equalsIgnoreCase(SupplierRegistry.OTHER)) {
            return Resolution.rejected(INTEGRATED, name);
        }
        return Resolution.accepted(name);
    }
}
