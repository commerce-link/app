package pl.commercelink.web.stores;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.stores.DeactivationReason;
import pl.commercelink.stores.DeactivationStatus;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoreTrialService;
import pl.commercelink.stores.TrialStatus;
import pl.commercelink.web.activity.AccountStatusView;

import java.text.Collator;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Builds the stores list from one scan of the stores. Trial and activity statuses are computed from the store itself,
 * so the list does no read per store.
 */
@Component
@RequiredArgsConstructor
public class StoresListPageFactory {

    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final int SHORT_ID = 8;

    private final StoreTrialService storeTrialService;
    private final StoreActivity storeActivity;

    public StoresListPage build(List<Store> stores, StoresListQuery query) {
        List<Listed> matching = stores.stream()
                .filter(Objects::nonNull)
                .map(store -> new Listed(store, row(store, query)))
                .filter(listed -> matches(listed, query.q()))
                .toList();

        Map<StoresListQuery.Segment, Long> counts = new EnumMap<>(StoresListQuery.Segment.class);
        matching.forEach(listed -> counts.merge(listed.row().state().segment(), 1L, Long::sum));

        List<StoreRow> rows = matching.stream()
                .filter(listed -> query.segment() == StoresListQuery.Segment.ALL || listed.row().state().segment() == query.segment())
                .sorted(order(query))
                .map(Listed::row)
                .toList();

        return new StoresListPage(query, rows, segments(query, counts, matching.size()), deleteDialog(stores, query),
                emptyState(query, rows.isEmpty(), stores.isEmpty(), !matching.isEmpty()));
    }

    private StoreRow row(Store store, StoresListQuery query) {
        Optional<DeactivationStatus> deactivation = storeActivity.status(store);
        Optional<TrialStatus> trial = storeTrialService.status(store);
        StoreState state = state(store, deactivation, trial);
        String noteDate = switch (state) {
            case TRIAL, TRIAL_ENDING -> trial.map(t -> format(t.endsOn())).orElse(null);
            case TRIAL_ENDED -> deactivation.map(d -> format(d.deletionOn())).orElse(null);
            case SWITCHED_OFF, DEMO_SWITCHED_OFF -> deactivation.map(d -> format(d.deactivatedOn())).orElse(null);
            case DEMO -> format(day(store.getDemo().getExpiresAt()));
            case FULL -> null;
        };
        long noteDays = trial.map(TrialStatus::daysLeft).orElse(0L);
        String shortId = StringUtils.left(store.getStoreId(), SHORT_ID);
        String company = trimmed(store.getBillingDetails() == null ? null : store.getBillingDetails().getCompanyName());
        String email = ownerEmail(store);
        boolean inactive = deactivation.isPresent();
        boolean trialEnded = state == StoreState.TRIAL_ENDED;
        return new StoreRow(store.getStoreId(), shortId,
                displayName(store),
                company != null ? company : email,
                company != null ? email : null,
                trialOwnerEmail(store),
                state, noteDate, noteDays, format(day(store.getCreatedAt())),
                store.getDemo() != null, store.getTrial() != null,
                store.getTrial() != null, !inactive, inactive && !trialEnded, trialEnded,
                query.hrefWithDelete(store.getStoreId()));
    }

    private static StoreState state(Store store, Optional<DeactivationStatus> deactivation, Optional<TrialStatus> trial) {
        if (deactivation.isPresent()) {
            if (deactivation.get().reason() == DeactivationReason.TRIAL_ENDED) {
                return StoreState.TRIAL_ENDED;
            }
            return store.getDemo() != null ? StoreState.DEMO_SWITCHED_OFF : StoreState.SWITCHED_OFF;
        }
        if (store.getDemo() != null) {
            return StoreState.DEMO;
        }
        if (store.getTrial() != null) {
            return trial.filter(t -> t.daysLeft() <= AccountStatusView.ENDING_SOON_DAYS).isPresent()
                    ? StoreState.TRIAL_ENDING : StoreState.TRIAL;
        }
        return StoreState.FULL;
    }

    /** The one name rule for every screen: a store without a name is shown by the first characters of its ID. */
    public static String displayName(Store store) {
        return StringUtils.isBlank(store.getName()) ? StringUtils.left(store.getStoreId(), SHORT_ID) : store.getName();
    }

    private static String ownerEmail(Store store) {
        return Stream.of(
                        store.getTrial() == null ? null : store.getTrial().getOwnerEmail(),
                        store.getDemo() == null ? null : store.getDemo().getOwnerEmail(),
                        store.getBillingDetails() == null ? null : store.getBillingDetails().getEmail())
                .map(StoresListPageFactory::trimmed)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static String trialOwnerEmail(Store store) {
        return store.getTrial() == null ? null : trimmed(store.getTrial().getOwnerEmail());
    }

    private static boolean matches(Listed listed, String q) {
        if (q == null) {
            return true;
        }
        String needle = fold(q);
        StoreRow row = listed.row();
        return Stream.of(row.name(), row.storeId(), row.ownerName(), row.ownerEmail())
                .filter(Objects::nonNull)
                .anyMatch(value -> fold(value).contains(needle));
    }

    // stripAccents leaves the Polish ł alone, so it is folded by hand
    private static String fold(String value) {
        return StringUtils.stripAccents(value.replace('ł', 'l').replace('Ł', 'L')).toLowerCase(Locale.ROOT);
    }

    private static Comparator<Listed> order(StoresListQuery query) {
        Comparator<Listed> byId = Comparator.comparing(listed -> listed.store().getStoreId(), Comparator.nullsLast(Comparator.naturalOrder()));
        if (query.sort() == StoresListQuery.Sort.NAME) {
            Collator collator = Collator.getInstance(Locale.forLanguageTag("pl"));
            Comparator<String> names = query.ascending() ? collator::compare : (a, b) -> collator.compare(b, a);
            return Comparator.comparing((Listed listed) -> listed.row().name(), names).thenComparing(byId);
        }
        Comparator<Instant> instants = query.ascending() ? Comparator.naturalOrder() : Comparator.reverseOrder();
        // a store without a readable date (a copy, an old record) stays at the end in both directions, as before
        return Comparator.comparing((Listed listed) -> instant(listed.store().getCreatedAt()), Comparator.nullsLast(instants))
                .thenComparing(byId);
    }

    private static List<StoresListPage.SegmentLink> segments(StoresListQuery query, Map<StoresListQuery.Segment, Long> counts,
                                                             long total) {
        List<StoresListPage.SegmentLink> links = new ArrayList<>();
        for (StoresListQuery.Segment segment : StoresListQuery.Segment.values()) {
            long count = segment == StoresListQuery.Segment.ALL ? total : counts.getOrDefault(segment, 0L);
            boolean active = segment == query.segment();
            if (segment == StoresListQuery.Segment.DEMO && count == 0 && !active) {
                continue;
            }
            links.add(new StoresListPage.SegmentLink("stores.list.segment." + segment.param(), count,
                    query.withSegment(segment).href(), active));
        }
        return links;
    }

    private static StoresListPage.DeleteDialog deleteDialog(List<Store> stores, StoresListQuery query) {
        if (query.delete() == null) {
            return null;
        }
        return stores.stream()
                .filter(store -> store != null && query.delete().equals(store.getStoreId()) && store.getDemo() == null)
                .findFirst()
                .map(store -> new StoresListPage.DeleteDialog(store.getStoreId(),
                        displayName(store),
                        store.getTrial() != null,
                        trialOwnerEmail(store),
                        "/dashboard/store/" + store.getStoreId() + "/delete"))
                .orElse(null);
    }

    private static StoresListPage.EmptyState emptyState(StoresListQuery query, boolean noRows, boolean noStores,
                                                        boolean matchesInOtherSegments) {
        if (!noRows) {
            return null;
        }
        if (noStores) {
            return new StoresListPage.EmptyState("stores.list.empty.none", null, "stores.list.create", "/dashboard/store/create");
        }
        String showAll = query.cleared().href();
        // e.g. right after converting a trial from the "Trial" segment: the store now matches only in another segment
        if (query.q() != null && matchesInOtherSegments) {
            return new StoresListPage.EmptyState("stores.list.empty.searchSegment", query.q(), "stores.list.empty.allSegments",
                    query.withSegment(StoresListQuery.Segment.ALL).href());
        }
        // as on the offers list: the action drops only the phrase, the segment and the sort stay
        if (query.q() != null) {
            return new StoresListPage.EmptyState("stores.list.empty.search", query.q(), "stores.list.search.clear", query.withQ(null).href());
        }
        return new StoresListPage.EmptyState("stores.list.empty.segment", null, "stores.list.empty.showAll", showAll);
    }

    private static Instant instant(String iso) {
        if (iso == null) {
            return null;
        }
        try {
            return Instant.parse(iso);
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static LocalDate day(String iso) {
        Instant instant = instant(iso);
        return instant == null ? null : instant.atZone(ZONE).toLocalDate();
    }

    private static String format(LocalDate date) {
        return date == null ? null : DATE.format(date);
    }

    private static String trimmed(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private record Listed(Store store, StoreRow row) {
    }
}
