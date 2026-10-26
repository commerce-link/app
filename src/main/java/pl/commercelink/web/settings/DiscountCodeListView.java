package pl.commercelink.web.settings;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import pl.commercelink.discounts.DiscountCode;
import pl.commercelink.discounts.DiscountCodeStatus;
import pl.commercelink.discounts.DiscountTarget;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public record DiscountCodeListView(List<DiscountCodeView> rows, List<Segment> segments, String query, String status,
                                   String target, int total) {

    public static final String WHOLE_BASKET = "basket";

    private static final List<DiscountCodeStatus> SEGMENT_ORDER = List.of(DiscountCodeStatus.Active,
            DiscountCodeStatus.Scheduled, DiscountCodeStatus.Exhausted, DiscountCodeStatus.Expired,
            DiscountCodeStatus.Disabled);

    public record Segment(String param, String labelKey, long count, String href, boolean active) {
    }

    public static DiscountCodeListView of(List<DiscountCode> codes, String query, String status, String target,
                                          LocalDateTime now, Map<String, String> gatewayNames, String basePath) {
        String q = StringUtils.trimToNull(query);
        DiscountCodeStatus wantedStatus = statusOf(status);
        String targetParam = targetParamOf(target);
        List<DiscountCode> searched = codes.stream()
                .filter(code -> matchesQuery(code, q) && matchesTarget(code, targetParam))
                .sorted(Comparator.comparing(DiscountCode::getCode))
                .toList();
        List<DiscountCodeView> rows = searched.stream()
                .filter(code -> wantedStatus == null || code.statusAt(now) == wantedStatus)
                .map(code -> DiscountCodeView.of(code, now, gatewayNames, basePath))
                .toList();
        String statusParam = wantedStatus == null ? "" : param(wantedStatus);
        List<Segment> segments = new ArrayList<>();
        segments.add(new Segment("", "store.discounts.filter.all", searched.size(),
                href(basePath, q, targetParam, null), wantedStatus == null));
        for (DiscountCodeStatus candidate : SEGMENT_ORDER) {
            long count = searched.stream().filter(code -> code.statusAt(now) == candidate).count();
            segments.add(new Segment(param(candidate), "store.discounts.filter." + param(candidate), count,
                    href(basePath, q, targetParam, param(candidate)), candidate == wantedStatus));
        }
        return new DiscountCodeListView(rows, segments, q, statusParam, targetParam, codes.size());
    }

    public boolean filtered() {
        return query != null || !status.isEmpty() || !target.isEmpty();
    }

    private static boolean matchesQuery(DiscountCode code, String q) {
        return q == null || StringUtils.containsIgnoreCase(code.getCode(), q)
                || StringUtils.containsIgnoreCase(code.getName(), q);
    }

    private static boolean matchesTarget(DiscountCode code, String targetParam) {
        if (targetParam.isEmpty()) {
            return true;
        }
        if (targetParam.equals(WHOLE_BASKET)) {
            return code.getTarget() == null;
        }
        return code.getTarget() != null && param(code.getTarget()).equals(targetParam);
    }

    private static DiscountCodeStatus statusOf(String status) {
        for (DiscountCodeStatus candidate : DiscountCodeStatus.values()) {
            if (param(candidate).equals(status)) {
                return candidate;
            }
        }
        return null;
    }

    private static String targetParamOf(String target) {
        if (WHOLE_BASKET.equals(target)) {
            return WHOLE_BASKET;
        }
        for (DiscountTarget candidate : DiscountTarget.values()) {
            if (param(candidate).equals(target)) {
                return target;
            }
        }
        return "";
    }

    private static String param(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static String href(String basePath, String q, String target, String status) {
        return UriComponentsBuilder.fromPath(basePath)
                .queryParamIfPresent("q", Optional.ofNullable(q))
                .queryParamIfPresent("target", Optional.of(target).filter(value -> !value.isEmpty()))
                .queryParamIfPresent("status", Optional.ofNullable(status))
                .build()
                .encode()
                .toUriString();
    }
}
