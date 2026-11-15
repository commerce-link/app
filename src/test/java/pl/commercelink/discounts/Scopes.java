package pl.commercelink.discounts;

import java.util.Arrays;
import java.util.List;

public final class Scopes {

    private Scopes() {
    }

    public static DiscountScope categories(String... names) {
        return new DiscountScope(List.of(names), List.of(), List.of());
    }

    public static DiscountScope brands(String... names) {
        return new DiscountScope(List.of(), List.of(names), List.of());
    }

    public static DiscountScope products(String... ids) {
        return new DiscountScope(List.of(), List.of(), Arrays.stream(ids).map(id -> new TargetValue(id, id)).toList());
    }
}
