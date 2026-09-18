package pl.commercelink.starter.email;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** The From header value SES accepts for a display name typed by a store admin or taken from the store name. */
final class FromHeader {

    private FromHeader() {
    }

    static String of(String displayName, String address) {
        String name = StringUtils.trimToNull(displayName == null ? null : displayName.replaceAll("[\\r\\n]+", " "));
        if (name == null) {
            return address;
        }
        // SES requires a display name with non-ASCII characters (Polish store names) as an RFC 2047 encoded word.
        if (!StandardCharsets.US_ASCII.newEncoder().canEncode(name)) {
            return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(name.getBytes(StandardCharsets.UTF_8)) + "?= <" + address + ">";
        }
        return "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\" <" + address + ">";
    }
}
