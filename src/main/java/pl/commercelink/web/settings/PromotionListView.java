package pl.commercelink.web.settings;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import pl.commercelink.discounts.DiscountCodeStatus;
import pl.commercelink.discounts.Promotion;
import pl.commercelink.web.settings.DiscountCodeListView.Segment;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public record PromotionListView(List<PromotionView> rows, List<Segment> segments, String query, String status,
                                int total) {

    private static final List<DiscountCodeStatus> SEGMENT_ORDER = List.of(DiscountCodeStatus.Active,
            DiscountCodeStatus.Scheduled, DiscountCodeStatus.Expired, DiscountCodeStatus.Disabled);

    public static PromotionListView of(List<Promotion> promotions, String query, String status, LocalDateTime now,
                                       String basePath) {
        String q = StringUtils.trimToNull(query);
        DiscountCodeStatus wantedStatus = statusOf(status);
        List<Promotion> searched = promotions.stream()
                .filter(promotion -> q == null || StringUtils.containsIgnoreCase(promotion.getName(), q))
                .sorted(Comparator.comparing(Promotion::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<PromotionView> rows = searched.stream()
                .filter(promotion -> wantedStatus == null || promotion.statusAt(now) == wantedStatus)
                .map(promotion -> PromotionView.of(promotion, now, basePath))
                .toList();
        List<Segment> segments = new ArrayList<>();
        segments.add(new Segment("", "store.promotions.filter.all", searched.size(), href(basePath, q, null),
                wantedStatus == null));
        for (DiscountCodeStatus candidate : SEGMENT_ORDER) {
            long count = searched.stream().filter(promotion -> promotion.statusAt(now) == candidate).count();
            segments.add(new Segment(param(candidate), "store.promotions.filter." + param(candidate), count,
                    href(basePath, q, param(candidate)), candidate == wantedStatus));
        }
        return new PromotionListView(rows, segments, q, wantedStatus == null ? "" : param(wantedStatus),
                promotions.size());
    }

    private static DiscountCodeStatus statusOf(String status) {
        for (DiscountCodeStatus candidate : SEGMENT_ORDER) {
            if (param(candidate).equals(status)) {
                return candidate;
            }
        }
        return null;
    }

    private static String param(DiscountCodeStatus status) {
        return status.name().toLowerCase(Locale.ROOT);
    }

    private static String href(String basePath, String q, String status) {
        return UriComponentsBuilder.fromPath(basePath)
                .queryParamIfPresent("q", Optional.ofNullable(q))
                .queryParamIfPresent("status", Optional.ofNullable(status))
                .build()
                .encode()
                .toUriString();
    }
}
