package pl.commercelink.web.payments;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.MultiValueMap;
import pl.commercelink.orders.PaymentSource;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * The state of the Payments page, read from and written back to the address (spec §6.3). Every link is built here so
 * that changing one parameter never loses the others; unknown values are ignored. A null side means "the side with
 * results" (spec §4.2): tile and search links leave it out on purpose.
 */
public record PaymentsQuery(PaymentSide side, PaymentFocus focus, List<String> providers, List<PaymentSource> methods, String q) {

    public static final String PATH = "/dashboard/payments";
    public static final int MAX_Q = 100;

    public PaymentsQuery {
        providers = providers == null ? List.of() : providers.stream().filter(StringUtils::isNotBlank).distinct().toList();
        methods = methods == null ? List.of() : methods.stream().filter(Objects::nonNull).distinct().toList();
        q = normalizeQ(q);
    }

    public static PaymentsQuery parse(MultiValueMap<String, String> params) {
        return new PaymentsQuery(
                PaymentSide.parse(params.getFirst("side")).orElse(null),
                PaymentFocus.parse(params.getFirst("focus")).orElse(null),
                values(params, "provider").stream().map(StringUtils::trimToNull).filter(Objects::nonNull).toList(),
                values(params, "method").stream().map(PaymentsQuery::method).flatMap(Optional::stream).toList(),
                params.getFirst("q"));
    }

    public boolean isFiltered() {
        return focus != null || !providers.isEmpty() || !methods.isEmpty() || q != null;
    }

    /** A tab link: the menu of the other side does not apply there, so its choices are dropped. */
    public PaymentsQuery withSide(PaymentSide s) {
        return new PaymentsQuery(s, focus, s == PaymentSide.PAYABLES ? providers : List.of(),
                s == PaymentSide.RECEIVABLES ? methods : List.of(), q);
    }

    /** A tile narrows to exactly what it counts, on whichever side has it (like the deliveries list's tiles). */
    public PaymentsQuery withFocus(PaymentFocus f) { return new PaymentsQuery(null, f, List.of(), List.of(), null); }
    public PaymentsQuery withoutFocus() { return new PaymentsQuery(side, null, providers, methods, q); }
    public PaymentsQuery toggleProvider(String p) { return new PaymentsQuery(side, focus, toggled(providers, p), methods, q); }
    public PaymentsQuery withoutProvider(String p) { return new PaymentsQuery(side, focus, without(providers, p), methods, q); }
    public PaymentsQuery toggleMethod(PaymentSource m) { return new PaymentsQuery(side, focus, providers, toggled(methods, m), q); }
    public PaymentsQuery withoutMethod(PaymentSource m) { return new PaymentsQuery(side, focus, providers, without(methods, m), q); }
    public PaymentsQuery withQ(String newQ) { return new PaymentsQuery(side, focus, providers, methods, newQ); }
    public PaymentsQuery cleared() { return new PaymentsQuery(side, null, List.of(), List.of(), null); }

    public String href() {
        List<String> parts = new ArrayList<>();
        if (side != null) parts.add("side=" + side.param());
        if (focus != null) parts.add("focus=" + focus.param());
        providers.forEach(p -> parts.add("provider=" + encode(p)));
        methods.forEach(m -> parts.add("method=" + m.name()));
        if (q != null) parts.add("q=" + encode(q));
        return parts.isEmpty() ? PATH : PATH + "?" + String.join("&", parts);
    }

    private static Optional<PaymentSource> method(String value) {
        return Arrays.stream(PaymentSource.values()).filter(m -> m.name().equalsIgnoreCase(StringUtils.trimToEmpty(value))).findFirst();
    }

    private static <T> List<T> toggled(List<T> list, T value) {
        List<T> next = new ArrayList<>(list);
        if (!next.remove(value)) next.add(value);
        return next;
    }

    private static <T> List<T> without(List<T> list, T value) {
        List<T> next = new ArrayList<>(list);
        next.remove(value);
        return next;
    }

    private static List<String> values(MultiValueMap<String, String> params, String name) {
        List<String> raw = params.get(name);
        return raw == null ? List.of() : raw.stream().filter(Objects::nonNull).toList();
    }

    private static String normalizeQ(String value) {
        String trimmed = StringUtils.trimToNull(value);
        return trimmed == null ? null : StringUtils.left(trimmed, MAX_Q);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
