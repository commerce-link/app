package pl.commercelink.orders.filters;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

public final class OrderFilterConditions {

    private static final char FIELD_VALUE_SEPARATOR = '=';
    private static final String ENTRY_SEPARATOR = "|";

    private final List<String> entries;

    private OrderFilterConditions(List<String> entries) {
        this.entries = List.copyOf(entries);
    }

    public static OrderFilterConditions of(List<String> rawEntries) {
        if (rawEntries == null || rawEntries.isEmpty()) {
            throw new OrderFilterInvalidException("A filter needs at least one condition");
        }

        TreeSet<String> canonical = new TreeSet<>();
        for (String raw : rawEntries) {
            canonicalize(raw).ifPresent(canonical::add);
        }

        if (canonical.isEmpty()) {
            throw new OrderFilterInvalidException("A filter needs at least one condition");
        }
        return new OrderFilterConditions(new ArrayList<>(canonical));
    }

    public static OrderFilterConditions ofStored(List<String> storedEntries) {
        return new OrderFilterConditions(storedEntries == null ? List.of() : storedEntries);
    }

    private static Optional<String> canonicalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        int separator = raw.indexOf(FIELD_VALUE_SEPARATOR);
        if (separator <= 0) {
            throw new OrderFilterInvalidException("Malformed condition: " + raw);
        }

        String rawField = raw.substring(0, separator).trim();
        String rawValue = raw.substring(separator + 1).trim();

        OrderFilterField field = OrderFilterField.parse(rawField)
                .orElseThrow(() -> new OrderFilterInvalidException("Not a filterable field: " + rawField));

        String value = field.normalize(rawValue);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(field.name() + FIELD_VALUE_SEPARATOR + value);
    }

    public List<String> entries() {
        return entries;
    }

    public String canonicalForm() {
        return String.join(ENTRY_SEPARATOR, entries);
    }

    public String fingerprint() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalForm().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public static Optional<OrderFilterField> fieldOf(String entry) {
        int separator = entry.indexOf(FIELD_VALUE_SEPARATOR);
        return separator <= 0 ? Optional.empty() : OrderFilterField.parse(entry.substring(0, separator));
    }

    public static String valueOf(String entry) {
        int separator = entry.indexOf(FIELD_VALUE_SEPARATOR);
        return separator < 0 ? "" : entry.substring(separator + 1);
    }
}
