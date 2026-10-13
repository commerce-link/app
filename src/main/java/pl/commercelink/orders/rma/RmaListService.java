package pl.commercelink.orders.rma;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import pl.commercelink.web.orders.ArchivePager;
import pl.commercelink.web.orders.OrderArchivePage.Action;
import pl.commercelink.web.orders.OrderArchivePage.Chip;
import pl.commercelink.web.orders.OrderArchivePage.DateMenu;
import pl.commercelink.web.orders.OrderArchivePage.EmptyState;
import pl.commercelink.web.orders.OrderArchivePage.Preset;
import pl.commercelink.web.orders.OrderArchivePage.SearchScope;
import pl.commercelink.web.orders.OrderArchiveQuery.Period;
import pl.commercelink.web.rma.RmaListPage;
import pl.commercelink.web.rma.RmaListPage.*;
import pl.commercelink.web.rma.RmaListQuery;
import pl.commercelink.web.rma.RmaListQuery.Scope;
import pl.commercelink.web.rma.RmaRowMapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static pl.commercelink.orders.rma.RmaListSortKey.*;

/**
 * Builds the RMA list (spec §5): the RMAs being handled read whole from the list index (their number follows the work
 * at hand, not the history) and paged in memory with a total; the closed ones read 25 at a time by cursor, newest
 * first; a search goes through both, at most SEARCH_BATCH closed RMAs per request, and offers to search older ones.
 */
@Service
@RequiredArgsConstructor
public class RmaListService {

    static final int SEARCH_BATCH = 500;
    private static final int PAGE = RmaListQuery.PAGE_SIZE;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final List<TileDef> TILES = List.of(
            new TileDef("new", Set.of(RMAStatus.New)),
            new TileDef("waiting", Set.of(RMAStatus.Approved, RMAStatus.WaitingForItems)),
            new TileDef("received", Set.of(RMAStatus.ItemsReceived)),
            new TileDef("processing", Set.of(RMAStatus.Processing)));

    private final RMARepository rmas;
    private final MessageSource messages;

    private record TileDef(String key, Set<RMAStatus> statuses) { }

    private record Result(List<RMA> rmas, int offset, String previousHref, String nextHref, Long total,
                          LocalDate scannedUntil, String moreHref) {
        static Result of(List<RMA> rmas, int offset, String previousHref, String nextHref, Long total) {
            return new Result(rmas, offset, previousHref, nextHref, total, null, null);
        }
    }

    public RmaListPage page(String storeId, RmaListQuery q, LocalDate today, Locale locale) {
        List<RMA> allActive = drain(rmas.listRange(storeId, ACTIVE, upperBound(ACTIVE, null), true));
        Result result = q.q() != null ? byId(storeId, q, today).orElseGet(() -> search(storeId, q, allActive, today))
                : q.effectiveScope() == Scope.CLOSED ? browseClosed(storeId, q, today)
                : browseActive(q, allActive, today);
        RmaRowMapper mapper = new RmaRowMapper(messages, locale);
        var rows = result.rmas().stream().map(rma -> mapper.map(rma, today)).toList();
        ArchivePager pager = new ArchivePager(result.offset(), result.offset() + rows.size(),
                result.previousHref(), result.nextHref(), result.total());
        // nothing matched in the batch that was spent: say so, otherwise the page would show only the offer to search further
        SearchScope scope = result.scannedUntil() == null ? null : new SearchScope(
                rows.isEmpty() ? text(locale, "rma.list.scope.none", q.q(), DAY.format(result.scannedUntil()))
                        : text(locale, "rma.list.scope", DAY.format(result.scannedUntil())),
                text(locale, "rma.list.scope.more"), result.moreHref());
        boolean storeEmpty = allActive.isEmpty() && !q.isFiltered() && rows.isEmpty() && q.offset() == 0
                && !rmas.hasAnyInRange(storeId, lowerBound(CLOSED, null), upperBound(CLOSED, null));
        EmptyState empty = rows.isEmpty() && scope == null ? emptyState(q, storeEmpty, locale) : null;
        String results = rows.isEmpty() ? ""
                : pager.openEnded() ? text(locale, "rma.list.results.range", pager.fromIndex() + 1, pager.toIndex())
                : text(locale, "rma.list.results", pager.totalItems());
        return new RmaListPage(q, storeEmpty ? List.of() : tiles(q, allActive, locale), segments(q, allActive.size(), locale),
                statusMenu(q, allActive, locale), dates(q, locale), chips(q, locale), results, rows, pager, empty, scope);
    }

    private Result browseActive(RmaListQuery q, List<RMA> allActive, LocalDate today) {
        List<RMA> matching = allActive.stream().filter(r -> q.accepts(r.getStatus()) && withinDates(r, q, today)).toList();
        int total = matching.size();
        int offset = total == 0 ? 0 : Math.min(q.offset(), (total - 1) / PAGE * PAGE);
        List<RMA> shown = matching.subList(offset, Math.min(total, offset + PAGE));
        String previous = offset > 0 ? q.atOffset(offset - PAGE).href() : null;
        String next = offset + PAGE < total ? q.atOffset(offset + PAGE).href() : null;
        return Result.of(shown, offset, previous, next, (long) total);
    }

    private Result browseClosed(String storeId, RmaListQuery q, LocalDate today) {
        String lo = lowerBound(CLOSED, q.effectiveFrom(today));
        String hi = upperBound(CLOSED, q.effectiveTo());
        Predicate<RMA> shown = r -> q.accepts(r.getStatus());
        if (q.before() != null) {
            String cursor = q.before().position();
            List<RMA> newer = take(rmas.listRange(storeId, max(lo, CLOSED + cursor), hi, false), notAt(cursor).and(shown), PAGE + 1);
            if (newer.size() <= PAGE) {
                // walked back to the newest RMA: show the first page as a fresh visit shows it
                return browseClosed(storeId, q.first(), today);
            }
            List<RMA> page = new ArrayList<>(newer.subList(0, PAGE));
            Collections.reverse(page);
            int offset = Math.max(PAGE, q.offset());
            return Result.of(page, offset, q.before(RmaCursor.of(page.get(0)), offset - PAGE).href(),
                    q.after(RmaCursor.of(page.get(PAGE - 1)), offset + PAGE).href(), null);
        }
        String cursor = q.after() == null ? null : q.after().position();
        String top = cursor == null ? hi : min(hi, CLOSED + cursor);
        List<RMA> read = take(rmas.listRange(storeId, lo, top, true), notAt(cursor).and(shown), PAGE + 1);
        boolean hasNext = read.size() > PAGE;
        List<RMA> page = hasNext ? read.subList(0, PAGE) : read;
        String previous = q.offset() <= 0 || page.isEmpty() ? null
                : q.offset() - PAGE <= 0 ? q.first().href() : q.before(RmaCursor.of(page.get(0)), q.offset() - PAGE).href();
        String next = hasNext ? q.after(RmaCursor.of(page.get(PAGE - 1)), q.offset() + PAGE).href() : null;
        return Result.of(page, q.offset(), previous, next, null);
    }

    /**
     * A search that looks like an RMA id is first a key query on the table, so an RMA is found whatever its age and not
     * only within the SEARCH_BATCH window (same idea as OrderArchiveService.byNumber). Nothing found falls back to the
     * text search, which still finds an order number or a reference.
     */
    private Optional<Result> byId(String storeId, RmaListQuery q, LocalDate today) {
        String prefix = q.idPrefix();
        if (prefix == null || q.offset() > 0 || q.after() != null) {
            return Optional.empty();
        }
        // q.accepts also applies the scope: an active search never lists a closed RMA and the other way round
        List<RMA> found = rmas.findByIdPrefix(storeId, prefix).stream()
                .filter(r -> q.accepts(r.getStatus()) && withinDates(r, q, today))
                .sorted(Comparator.comparing(RmaListService::position).reversed())
                .limit(PAGE)
                .toList();
        return found.isEmpty() ? Optional.empty() : Optional.of(Result.of(found, 0, null, null, (long) found.size()));
    }

    /**
     * Merges the active RMAs (already in memory) with the closed ones read newest first, by position, so the result is
     * newest first across both. When SEARCH_BATCH closed RMAs have been looked at, it stops before anything older than
     * the last one read: the next request continues from there.
     */
    private Result search(String storeId, RmaListQuery q, List<RMA> allActive, LocalDate today) {
        String cursor = q.after() == null ? null : q.after().position();
        Iterator<RMA> active = q.searchesActive()
                ? allActive.stream().filter(r -> cursor == null || position(r).compareTo(cursor) < 0).iterator()
                : Collections.emptyIterator();
        String hi = upperBound(CLOSED, q.effectiveTo());
        Iterator<RMA> closed = q.searchesClosed()
                ? rmas.listRange(storeId, lowerBound(CLOSED, q.effectiveFrom(today)), cursor == null ? hi : min(hi, CLOSED + cursor), true)
                : Collections.emptyIterator();
        Peeking a = new Peeking(active);
        Peeking c = new Peeking(closed);
        List<RMA> matched = new ArrayList<>();
        RMA last = null;
        int scannedClosed = 0;
        boolean batchSpent = false;
        while (matched.size() < PAGE) {
            boolean closedLeft = c.hasNext();
            if (closedLeft && scannedClosed >= SEARCH_BATCH) {
                batchSpent = true;
                break;
            }
            if (!a.hasNext() && !closedLeft) {
                break;
            }
            boolean takeClosed = closedLeft && (!a.hasNext() || position(c.peek()).compareTo(position(a.peek())) > 0);
            RMA next = takeClosed ? c.next() : a.next();
            if (takeClosed) {
                scannedClosed++;
            }
            if (cursor != null && position(next).equals(cursor)) {
                continue;
            }
            last = next;
            if (q.accepts(next.getStatus()) && withinDates(next, q, today) && RmaSearch.matches(next, q.q())) {
                matched.add(next);
            }
        }
        // a search pages forward only; "previous" starts it again from the newest RMA
        String previous = q.offset() > 0 ? q.first().href() : null;
        boolean more = a.hasNext() || c.hasNext();
        if (batchSpent && last != null) {
            LocalDate until = last.getCreatedAt() == null ? today : last.getCreatedAt().toLocalDate();
            return new Result(matched, q.offset(), previous, null, null, until,
                    q.after(RmaCursor.of(last), q.offset() + matched.size()).href());
        }
        if (matched.size() == PAGE && more) {
            return Result.of(matched, q.offset(), previous, q.after(RmaCursor.of(last), q.offset() + PAGE).href(), null);
        }
        Long total = q.offset() == 0 ? (long) matched.size() : null;
        return Result.of(matched, q.offset(), previous, null, total);
    }

    private List<Tile> tiles(RmaListQuery q, List<RMA> allActive, Locale locale) {
        return TILES.stream().map(def -> {
            long count = allActive.stream().filter(r -> def.statuses().contains(statusOf(r))).count();
            boolean active = q.effectiveScope() == Scope.ACTIVE && q.statuses().equals(def.statuses());
            String href = active ? q.withStatuses(Set.of()).href() : q.forTile(def.statuses()).href();
            return new Tile(text(locale, "rma.list.tile." + def.key()), text(locale, "rma.list.tile." + def.key() + ".hint"),
                    count, href, active);
        }).toList();
    }

    private List<SegmentLink> segments(RmaListQuery q, int activeCount, Locale locale) {
        Scope current = q.effectiveScope();
        return List.of(
                new SegmentLink(text(locale, "rma.list.segment.active"), (long) activeCount, q.withScope(Scope.ACTIVE).href(), current == Scope.ACTIVE),
                new SegmentLink(text(locale, "rma.list.segment.closed"), null, q.withScope(Scope.CLOSED).href(), current == Scope.CLOSED));
    }

    private StatusMenu statusMenu(RmaListQuery q, List<RMA> allActive, Locale locale) {
        boolean counted = q.effectiveScope() == Scope.ACTIVE;
        Map<RMAStatus, Long> counts = allActive.stream()
                .collect(Collectors.groupingBy(RmaListService::statusOf, Collectors.counting()));
        List<StatusOption> options = q.allowedStatuses().stream()
                .map(s -> new StatusOption(s.name(), text(locale, "RMAStatus." + s.name()),
                        counted ? counts.getOrDefault(s, 0L) : null, q.statuses().contains(s)))
                .toList();
        String value = q.statuses().isEmpty() ? text(locale, "rma.list.status.all")
                : q.statuses().size() == 1 ? text(locale, "RMAStatus." + q.statuses().iterator().next().name()).toLowerCase(locale)
                : text(locale, "rma.list.status.many", q.statuses().size());
        return new StatusMenu(value, options);
    }

    private DateMenu dates(RmaListQuery q, Locale locale) {
        List<Preset> presets = new ArrayList<>();
        presets.add(new Preset(text(locale, "rma.list.dates.all"), q.withoutDates().href(), !q.hasDates()));
        for (Period p : Period.values()) {
            presets.add(new Preset(text(locale, "rma.list.dates." + p.param()), q.withPeriod(p).href(), q.period() == p));
        }
        return new DateMenu(datesValue(q, locale), presets, q.from() == null ? null : q.from().toString(),
                q.to() == null ? null : q.to().toString());
    }

    private String datesValue(RmaListQuery q, Locale locale) {
        if (q.period() != null) return text(locale, "rma.list.dates." + q.period().param()).toLowerCase(locale);
        String from = q.from() == null ? null : DAY.format(q.from());
        String to = q.to() == null ? null : DAY.format(q.to());
        if (from != null && to != null) return text(locale, "rma.list.dates.range", from, to);
        if (from != null) return text(locale, "rma.list.dates.since", from);
        if (to != null) return text(locale, "rma.list.dates.until", to);
        return text(locale, "rma.list.dates.all").toLowerCase(locale);
    }

    private List<Chip> chips(RmaListQuery q, Locale locale) {
        List<Chip> chips = new ArrayList<>();
        if (q.q() != null) {
            chips.add(chip(text(locale, "rma.list.chip.search", q.q()), q.withQ(null).href(), locale));
        }
        for (RMAStatus status : q.statuses()) {
            chips.add(chip(text(locale, "rma.list.chip.status", text(locale, "RMAStatus." + status.name())), q.withoutStatus(status).href(), locale));
        }
        if (q.hasDates()) {
            chips.add(chip(text(locale, "rma.list.chip.dates", datesValue(q, locale)), q.withoutDates().href(), locale));
        }
        return chips;
    }

    private Chip chip(String label, String clearHref, Locale locale) {
        return new Chip(label, clearHref, text(locale, "rma.list.chip.clear", label));
    }

    private EmptyState emptyState(RmaListQuery q, boolean storeEmpty, Locale locale) {
        if (storeEmpty) {
            return new EmptyState(text(locale, "rma.list.empty.store"),
                    List.of(new Action(text(locale, "rma.list.empty.store.create"), RmaListQuery.PATH + "/new")));
        }
        if (q.q() != null && q.offset() > 0) {
            return new EmptyState(text(locale, "rma.list.empty.searchMore", q.q()),
                    List.of(new Action(text(locale, "rma.list.empty.first"), q.first().href())));
        }
        if (q.q() != null) {
            return new EmptyState(text(locale, "rma.list.empty.search", q.q()),
                    List.of(new Action(text(locale, "rma.list.search.clear"), q.withQ(null).href())));
        }
        if (q.isFiltered() || q.offset() > 0) {
            return new EmptyState(text(locale, "rma.list.empty.filtered"),
                    List.of(new Action(text(locale, "general.clear.filters"), q.cleared().href())));
        }
        if (q.effectiveScope() == Scope.ACTIVE) {
            return new EmptyState(text(locale, "rma.list.empty.active"),
                    List.of(new Action(text(locale, "rma.list.empty.active.closed"), q.withScope(Scope.CLOSED).href())));
        }
        return new EmptyState(text(locale, "rma.list.empty.closed"), List.of());
    }

    private static boolean withinDates(RMA rma, RmaListQuery q, LocalDate today) {
        LocalDate from = q.effectiveFrom(today);
        LocalDate to = q.effectiveTo();
        if (from == null && to == null) return true;
        if (rma.getCreatedAt() == null) return false;
        LocalDate day = rma.getCreatedAt().toLocalDate();
        return (from == null || !day.isBefore(from)) && (to == null || !day.isAfter(to));
    }

    // a record without a status counts as New, like RmaListQuery.accepts does
    private static RMAStatus statusOf(RMA rma) {
        return rma.getStatus() == null ? RMAStatus.New : rma.getStatus();
    }

    private static String position(RMA rma) {
        return RmaListSortKey.position(rma.getCreatedAt(), rma.getRmaId());
    }

    private static Predicate<RMA> notAt(String cursor) {
        return r -> cursor == null || !position(r).equals(cursor);
    }

    private static List<RMA> take(Iterator<RMA> it, Predicate<RMA> keep, int n) {
        List<RMA> out = new ArrayList<>();
        while (out.size() < n && it.hasNext()) {
            RMA next = it.next();
            if (keep.test(next)) {
                out.add(next);
            }
        }
        return out;
    }

    private static List<RMA> drain(Iterator<RMA> it) {
        List<RMA> out = new ArrayList<>();
        it.forEachRemaining(out::add);
        return out;
    }

    private static String min(String a, String b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static String max(String a, String b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    private String text(Locale locale, String key, Object... args) {
        return messages.getMessage(key, args, locale);
    }

    private static final class Peeking {
        private final Iterator<RMA> it;
        private RMA head;

        Peeking(Iterator<RMA> it) {
            this.it = it;
        }

        boolean hasNext() {
            return head != null || it.hasNext();
        }

        RMA peek() {
            if (head == null) head = it.next();
            return head;
        }

        RMA next() {
            RMA out = peek();
            head = null;
            return out;
        }
    }
}
