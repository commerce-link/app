package pl.commercelink.orders;

import java.util.Arrays;
import java.util.List;

/** A serial-number field holds one number, or a comma-separated list when the item's quantity is above one. */
public final class SerialNumbers {

    private SerialNumbers() {
    }

    public static List<String> parse(String field) {
        if (field == null) {
            return List.of();
        }
        return Arrays.stream(field.split(",")).map(String::trim).filter(sn -> !sn.isEmpty()).distinct().toList();
    }

    public static boolean contains(String field, String serialNo) {
        String wanted = serialNo == null ? "" : serialNo.trim();
        return !wanted.isEmpty() && parse(field).contains(wanted);
    }
}
