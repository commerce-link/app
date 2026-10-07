package pl.commercelink.web.warehousedocuments;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.MultiValueMap;
import pl.commercelink.documents.DocumentReason;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * The state of the warehouse documents list, read from and written back to the address (spec §2.2). Every link of the
 * page is built here; unknown values are ignored so old bookmarks open. {@code path} is the list's own address
 * (a super admin works on /dashboard/store/{id}/warehouse-documents).
 */
public record WarehouseDocumentListQuery(String path, DocumentKind kind, List<DocumentReason> reasons,
                                         LocalDate from, LocalDate to, String q, int page) {

    public static final int PAGE_SIZE = 25;
    public static final int MAX_Q = 100;
    private static final Pattern NUMBER_DIGITS = Pattern.compile("\\d{1,6}");
    private static final Set<String> LEGACY = Set.of("dateFrom", "dateTo", "warehouseId", "ean", "mfn");

    public enum SearchMode { NUMBER, PRODUCT }

    public WarehouseDocumentListQuery {
        List<DocumentReason> allowed = kind == null ? DocumentKind.allReasons() : kind.reasons();
        reasons = reasons == null ? List.of() : reasons.stream().filter(allowed::contains).distinct().toList();
        if (from != null && to != null && from.isAfter(to)) {
            LocalDate earlier = to;
            to = from;
            from = earlier;
        }
        q = normalizeQ(q);
        page = Math.max(1, page);
    }

    public static WarehouseDocumentListQuery parse(String path, MultiValueMap<String, String> params) {
        return new WarehouseDocumentListQuery(path,
                DocumentKind.parse(params.getFirst("type")).orElse(null),
                values(params, "reason").stream().map(WarehouseDocumentListQuery::reason).flatMap(Optional::stream).toList(),
                date(firstNonBlank(params.getFirst("from"), params.getFirst("dateFrom"))),
                date(firstNonBlank(params.getFirst("to"), params.getFirst("dateTo"))),
                firstNonBlank(params.getFirst("q"), params.getFirst("ean"), params.getFirst("mfn"),
                        warehouseFragment(params.getFirst("warehouseId"))),
                parsePage(params.getFirst("page")));
    }

    /** The filter form of the old page (type as enum name, dateFrom/dateTo, warehouseId, ean, mfn) → the new address. */
    public static Optional<String> legacyRedirect(String path, MultiValueMap<String, String> params) {
        String type = params.getFirst("type");
        boolean oldType = StringUtils.isNotBlank(type) && DocumentKind.parse(type)
                .map(k -> !k.code().equalsIgnoreCase(type.trim())).orElse(false);
        if (!oldType && params.keySet().stream().noneMatch(LEGACY::contains)) {
            return Optional.empty();
        }
        return Optional.of(parse(path, params).href());
    }

    public List<DocumentReason> allowedReasons() {
        return kind == null ? DocumentKind.allReasons() : kind.reasons();
    }

    public SearchMode searchMode() {
        if (q == null) return null;
        return q.contains("/") || NUMBER_DIGITS.matcher(q).matches() ? SearchMode.NUMBER : SearchMode.PRODUCT;
    }

    /**
     * The alternatives a document number may contain, as typed and in capitals: the sequence is stored in capitals
     * ("PZ/MAG1/2026/000214") and the number search is case-sensitive. A warehouse id is free text from the store settings
     * and may carry lower-case letters ("PZ/MAG-uma2dqukxr/..."), so a query with "/" also gets a capitals variant with the
     * warehouse id in its stored casing ({@link #withWarehouseIdCase}).
     */
    public List<String> numberFragments(String warehouseId) {
        if (searchMode() != SearchMode.NUMBER) return List.of();
        List<String> variants = new ArrayList<>(List.of(q, q.toUpperCase(Locale.ROOT)));
        if (q.contains("/")) variants.add(withWarehouseIdCase(q, warehouseId));
        return variants.stream().distinct().toList();
    }

    /**
     * The query in capitals, split on "/", with the warehouse id given back its stored casing: a segment equal to it
     * (ignoring case) becomes the warehouse id, the last segment that starts it becomes that prefix and the first segment
     * that ends it becomes that suffix ("pz/mag-uma2" → "PZ/MAG-uma2", "dqukxr/2026" → "dqukxr/2026").
     */
    static String withWarehouseIdCase(String query, String warehouseId) {
        String[] segments = query.toUpperCase(Locale.ROOT).split("/", -1);
        String id = StringUtils.trimToNull(warehouseId);
        if (id == null) return String.join("/", segments);
        int last = segments.length - 1;
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            int length = segment.length();
            if (length == 0 || length > id.length()) continue;
            if (segment.equalsIgnoreCase(id)) {
                segments[i] = id;
            } else if (i == last && id.regionMatches(true, 0, segment, 0, length)) {
                segments[i] = id.substring(0, length);
            } else if (i == 0 && id.regionMatches(true, id.length() - length, segment, 0, length)) {
                segments[i] = id.substring(id.length() - length);
            }
        }
        return String.join("/", segments);
    }

    public String productCode() {
        return searchMode() == SearchMode.PRODUCT ? q : null;
    }

    public boolean isFiltered() {
        return !reasons.isEmpty() || from != null || to != null || q != null;
    }

    /** Menus in use (the "Filtry" button on phones counts them): reasons and dates. */
    public int activeFilterCount() {
        return (reasons.isEmpty() ? 0 : 1) + (from != null || to != null ? 1 : 0);
    }

    public WarehouseDocumentListQuery withKind(DocumentKind k) { return new WarehouseDocumentListQuery(path, k, reasons, from, to, q, 1); }
    public WarehouseDocumentListQuery toggleReason(DocumentReason r) { return new WarehouseDocumentListQuery(path, kind, toggled(reasons, r), from, to, q, 1); }
    public WarehouseDocumentListQuery withoutReason(DocumentReason r) { return new WarehouseDocumentListQuery(path, kind, without(reasons, r), from, to, q, 1); }
    public WarehouseDocumentListQuery withoutDates() { return new WarehouseDocumentListQuery(path, kind, reasons, null, null, q, 1); }
    public WarehouseDocumentListQuery withQ(String newQ) { return new WarehouseDocumentListQuery(path, kind, reasons, from, to, newQ, 1); }
    public WarehouseDocumentListQuery withPage(int n) { return new WarehouseDocumentListQuery(path, kind, reasons, from, to, q, n); }
    public WarehouseDocumentListQuery cleared() { return new WarehouseDocumentListQuery(path, kind, List.of(), null, null, null, 1); }

    public String href() {
        List<String> parts = new ArrayList<>();
        if (kind != null) parts.add("type=" + kind.code());
        reasons.forEach(r -> parts.add("reason=" + r.name()));
        if (from != null) parts.add("from=" + from);
        if (to != null) parts.add("to=" + to);
        if (q != null) parts.add("q=" + URLEncoder.encode(q, StandardCharsets.UTF_8));
        if (page > 1) parts.add("page=" + page);
        return parts.isEmpty() ? path : path + "?" + String.join("&", parts);
    }

    private static Optional<DocumentReason> reason(String value) {
        return Arrays.stream(DocumentReason.values()).filter(r -> r.name().equalsIgnoreCase(StringUtils.trimToEmpty(value))).findFirst();
    }

    private static String warehouseFragment(String warehouseId) {
        String w = StringUtils.trimToNull(warehouseId);
        return w == null ? null : "/" + w + "/";
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

    private static String firstNonBlank(String... values) {
        return Arrays.stream(values).map(StringUtils::trimToNull).filter(Objects::nonNull).findFirst().orElse(null);
    }
}
