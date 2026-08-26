package pl.commercelink.web.dtos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts the {@code supplierOptions[<key>]} request parameters posted alongside a purchase/approval form. */
public final class SupplierOptionsParams {

    private static final Pattern KEY = Pattern.compile("^supplierOptions\\[(.+)]$");

    private SupplierOptionsParams() {
    }

    public static Map<String, String> fromRequest(Map<String, String> params) {
        Map<String, String> options = new LinkedHashMap<>();
        params.forEach((name, value) -> {
            Matcher matcher = KEY.matcher(name);
            if (matcher.matches() && value != null && !value.isBlank()) {
                options.put(matcher.group(1), value.trim());
            }
        });
        return options;
    }
}
