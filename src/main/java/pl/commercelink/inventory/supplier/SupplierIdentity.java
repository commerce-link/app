package pl.commercelink.inventory.supplier;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * Identity of a store supplier connection. The identity is an opaque string carried through every
 * `provider`/`deliveryId`/S3/secret/schedule key; the adapter type is derived from it here and
 * nowhere else. Shapes: `Kosatec` (type only, every connection created before multi-instance
 * support and every GLOBAL connection), `Kosatec-k7f3a9c2` (own instance), `manual-k7f3a9c2`
 * (manual instance) and the legacy `manual:Label`.
 */
public final class SupplierIdentity {

    public static final String MANUAL_TYPE = "manual";

    private static final String LEGACY_MANUAL_PREFIX = "manual:";
    private static final char SEPARATOR = '-';
    private static final String TOKEN_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int TOKEN_LENGTH = 8;
    private static final Pattern TOKEN = Pattern.compile("^[a-z0-9]{" + TOKEN_LENGTH + "}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private SupplierIdentity() {
    }

    public static String typeOf(String identity) {
        if (identity == null) {
            return null;
        }
        if (identity.startsWith(LEGACY_MANUAL_PREFIX)) {
            return MANUAL_TYPE;
        }
        return hasToken(identity) ? identity.substring(0, identity.indexOf(SEPARATOR)) : identity;
    }

    public static boolean isManual(String identity) {
        return identity != null && MANUAL_TYPE.equalsIgnoreCase(typeOf(identity));
    }

    // A dash suffix counts as a token only in the generated shape: legacy provider names may carry
    // a dash themselves ("ACME-FV") and must stay whole, or the registry would hand back another
    // type's policies for them.
    public static boolean hasToken(String identity) {
        if (identity == null || identity.startsWith(LEGACY_MANUAL_PREFIX)) {
            return false;
        }
        int separator = identity.indexOf(SEPARATOR);
        return separator >= 0 && TOKEN.matcher(identity.substring(separator + 1)).matches();
    }

    public static String newInstance(String type) {
        return of(type, randomToken());
    }

    public static String of(String type, String token) {
        if (!isValidTypeName(type)) {
            throw new IllegalArgumentException("Invalid supplier type name: " + type);
        }
        if (token == null || !TOKEN.matcher(token).matches()) {
            throw new IllegalArgumentException("Invalid supplier identity token: " + token);
        }
        return type + SEPARATOR + token;
    }

    /** Label to show for a connection that has no label of its own. */
    public static String legacyLabel(String identity) {
        if (identity != null && identity.startsWith(LEGACY_MANUAL_PREFIX)) {
            return identity.substring(LEGACY_MANUAL_PREFIX.length());
        }
        return identity;
    }

    public static boolean isValidTypeName(String name) {
        return name != null && !name.isBlank() && name.indexOf(SEPARATOR) < 0 && name.indexOf(':') < 0;
    }

    private static String randomToken() {
        StringBuilder token = new StringBuilder(TOKEN_LENGTH);
        for (int i = 0; i < TOKEN_LENGTH; i++) {
            token.append(TOKEN_ALPHABET.charAt(RANDOM.nextInt(TOKEN_ALPHABET.length())));
        }
        return token.toString();
    }
}
