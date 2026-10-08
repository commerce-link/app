package pl.commercelink.web.offers;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.MultiValueMap;
import pl.commercelink.baskets.BasketType;
import pl.commercelink.baskets.OfferValidity;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * The state of the offers list, read from and written back to the address (spec §5.1). Every link on the page is
 * built here, so changing one parameter never loses the others; unknown values are ignored so old bookmarks open.
 */
public record OfferListQuery(OfferSegment segment, String q, List<OfferValidity> validity, LocalDate from, LocalDate to, int page) {

    public static final String PATH = "/dashboard/offers";
    public static final int PAGE_SIZE = 25;
    public static final int MAX_Q = 100;
    private static final Pattern FULL_ID = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    public OfferListQuery {
        segment = segment == null ? OfferSegment.OFFERS : segment;
        // validity only narrows offers; templates and store baskets do not expire
        validity = segment != OfferSegment.OFFERS || validity == null || validity.isEmpty()
                ? List.of() : List.copyOf(EnumSet.copyOf(validity));
        if (from != null && to != null && from.isAfter(to)) {
            LocalDate earlier = to;
            to = from;
            from = earlier;
        }
        page = Math.max(1, page);
    }

    public static OfferListQuery parse(MultiValueMap<String, String> params) {
        List<String> rawValidity = params.get("validity");
        return new OfferListQuery(
                OfferSegment.parse(params.getFirst("segment")).orElse(OfferSegment.OFFERS),
                normalizeQ(params.getFirst("q")),
                rawValidity == null ? List.of() : rawValidity.stream().map(OfferValidity::parse).flatMap(Optional::stream).toList(),
                date(params.getFirst("from")), date(params.getFirst("to")),
                parsePage(params.getFirst("page")));
    }

    /** The search form of the old list (name, basketId, type, createdAtStart/End): bookmarks keep working. */
    public static Optional<String> legacyRedirect(MultiValueMap<String, String> params) {
        Set<String> legacy = Set.of("name", "basketId", "type", "createdAtStart", "createdAtEnd");
        if (params.keySet().stream().noneMatch(legacy::contains)) {
            return Optional.empty();
        }
        String q = Optional.ofNullable(StringUtils.trimToNull(params.getFirst("basketId")))
                .orElse(StringUtils.trimToNull(params.getFirst("name")));
        OfferSegment segment = Arrays.stream(BasketType.values())
                .filter(t -> t.name().equals(StringUtils.trimToEmpty(params.getFirst("type"))))
                .findFirst().map(OfferSegment::of).orElse(OfferSegment.OFFERS);
        OfferListQuery target = new OfferListQuery(segment, normalizeQ(q), List.of(),
                date(params.getFirst("createdAtStart")), date(params.getFirst("createdAtEnd")), 1);
        return Optional.of(target.href());
    }

    public static boolean looksLikeId(String value) {
        return value != null && FULL_ID.matcher(value).matches();
    }

    public boolean isFiltered() {
        return activeFilterCount() > 0;
    }

    /** Search, each validity value and the date range count once each ("Filtry: n" on a phone). */
    public int activeFilterCount() {
        return (q != null ? 1 : 0) + validity.size() + (from != null || to != null ? 1 : 0);
    }

    public OfferListQuery withSegment(OfferSegment s) { return new OfferListQuery(s, q, List.of(), from, to, 1); }
    public OfferListQuery withQ(String newQ) { return new OfferListQuery(segment, normalizeQ(newQ), validity, from, to, 1); }
    public OfferListQuery toggleValidity(OfferValidity v) { return new OfferListQuery(segment, q, toggled(validity, v), from, to, 1); }
    public OfferListQuery withoutValidity(OfferValidity v) { return new OfferListQuery(segment, q, without(validity, v), from, to, 1); }
    public OfferListQuery withoutDates() { return new OfferListQuery(segment, q, validity, null, null, 1); }
    public OfferListQuery withPage(int n) { return new OfferListQuery(segment, q, validity, from, to, n); }
    public OfferListQuery cleared() { return new OfferListQuery(segment, null, List.of(), null, null, 1); }

    public String href() {
        List<String> parts = new ArrayList<>();
        if (segment != OfferSegment.OFFERS) parts.add("segment=" + segment.param());
        if (q != null) parts.add("q=" + URLEncoder.encode(q, StandardCharsets.UTF_8));
        validity.forEach(v -> parts.add("validity=" + v.param()));
        if (from != null) parts.add("from=" + from);
        if (to != null) parts.add("to=" + to);
        if (page > 1) parts.add("page=" + page);
        return parts.isEmpty() ? PATH : PATH + "?" + String.join("&", parts);
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

    private static LocalDate date(String value) {
        try {
            return StringUtils.isBlank(value) ? null : LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String normalizeQ(String value) {
        String trimmed = StringUtils.trimToNull(value);
        return trimmed == null ? null : StringUtils.left(trimmed, MAX_Q);
    }

    private static int parsePage(String value) {
        try {
            return value == null ? 1 : Math.max(1, Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
