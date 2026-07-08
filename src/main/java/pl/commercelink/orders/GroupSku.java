package pl.commercelink.orders;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class GroupSku {

    public static final String PREFIX = "#";
    private static final String COMPONENT_SEPARATOR = "\\|";
    private static final Pattern COMPONENT_PATTERN = Pattern.compile("^(\\d+)[xX](.+)$");

    public record Component(int qty, String sku) {
    }

    public static boolean isGroup(String sku) {
        return isNotBlank(sku) && sku.startsWith(PREFIX);
    }

    public static List<Component> parse(String sku) {
        if (!isGroup(sku)) {
            return List.of();
        }

        return Arrays.stream(sku.substring(PREFIX.length()).split(COMPONENT_SEPARATOR))
                .map(String::trim)
                .filter(segment -> !segment.isEmpty())
                .map(GroupSku::parseComponent)
                .toList();
    }

    private static Component parseComponent(String segment) {
        Matcher matcher = COMPONENT_PATTERN.matcher(segment);
        if (matcher.matches()) {
            return new Component(Integer.parseInt(matcher.group(1)), matcher.group(2).trim());
        }
        return new Component(1, segment);
    }

}
