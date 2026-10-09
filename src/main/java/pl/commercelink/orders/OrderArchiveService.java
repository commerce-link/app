package pl.commercelink.orders;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.orders.ArchivePager;
import pl.commercelink.web.orders.OrderArchivePage;
import pl.commercelink.web.orders.OrderArchivePage.*;
import pl.commercelink.web.orders.OrderArchiveQuery;
import pl.commercelink.web.orders.OrderArchiveQuery.Period;
import pl.commercelink.web.orders.OrderArchiveQuery.SearchMode;
import pl.commercelink.web.orders.OrderListQuery;
import pl.commercelink.web.orders.OrderRow;
import pl.commercelink.web.orders.OrderRowMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Builds the orders archive (spec §5): closed orders of the store from StoreIdOrderedAtIndex, newest first, 25 a page.
 * A number is looked up by key, an e-mail on the index alone; any other text needs whole orders, so it reads at most
 * {@link #TEXT_BATCH} index entries per request and offers to search older ones.
 */
@Service
@RequiredArgsConstructor
public class OrderArchiveService {

    static final int TEXT_BATCH = 500;
    private static final int PAGE = OrderArchiveQuery.PAGE_SIZE;
    private static final int LOAD_CHUNK = 100;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final OrdersRepository orders;
    private final StoresRepository stores;
    private final MessageSource messages;

    public OrderArchivePage page(String storeId, OrderArchiveQuery q, LocalDate today, Locale locale) {
        Store store = stores.findById(storeId);
        OrderRowMapper mapper = new OrderRowMapper(messages, locale, store != null && store.hasDocumentsGenerationEnabled());
        Result result = switch (q.searchMode() == null ? SearchMode.TEXT : q.searchMode()) {
            case NUMBER -> byNumber(storeId, q, today).orElseGet(() -> byText(storeId, q, today));
            case EMAIL -> byEmail(storeId, q, today);
            case TEXT -> q.q() == null ? browse(storeId, q, today) : byText(storeId, q, today);
        };
        List<OrderRow> rows = result.orders().stream().map(o -> mapper.mapArchived(o, q.href())).toList();
        int from = result.offset();
        ArchivePager pager = new ArchivePager(from, from + rows.size(), result.previousHref(), result.nextHref());
        SearchScope scope = result.scannedUntil() == null ? null : new SearchScope(
                text(locale, "orders.archive.scope", DAY.format(parse(result.scannedUntil()))),
                text(locale, "orders.archive.scope.more"), result.moreHref());
        OpenNotice openNotice = result.openOrders().isEmpty() ? null : openNotice(result.openOrders(), q, locale);
        EmptyState empty = rows.isEmpty() && scope == null && openNotice == null ? emptyState(q, locale) : null;
        String results = rows.isEmpty() ? "" : text(locale, "orders.archive.results", from + 1, from + rows.size());
        return new OrderArchivePage(q, segments(q, locale), dates(q, locale), chips(q, locale), results, rows, pager,
                empty, openNotice, scope);
    }

    private record Result(List<Order> orders, int offset, String previousHref, String nextHref,
                          String scannedUntil, String moreHref, List<Order> openOrders) {
        static Result of(List<Order> orders, int offset, String previousHref, String nextHref) {
            return new Result(orders, offset, previousHref, nextHref, null, null, List.of());
        }
    }

    private ArchiveIndexRequest request(OrderArchiveQuery q, LocalDate today, ArchiveCursor start, boolean newestFirst, int pageLimit) {
        return new ArchiveIndexRequest(q.statuses(), q.effectiveFrom(today), q.effectiveTo(), start, newestFirst, pageLimit);
    }

    private Result browse(String storeId, OrderArchiveQuery q, LocalDate today) {
        if (q.before() != null) {
            List<ArchiveIndexEntry> newer = take(orders.archiveIndex(storeId, request(q, today, q.before(), false, PAGE + 1)), PAGE + 1);
            if (newer.size() <= PAGE) {
                // walked back to the newest order: show the first page as a fresh visit shows it
                return browse(storeId, q.first(), today);
            }
            List<ArchiveIndexEntry> shown = new ArrayList<>(newer.subList(0, PAGE));
            Collections.reverse(shown);
            // every paging link carries the offset of the page it leads to; more newer orders exist, so it is not the first
            int offset = Math.max(PAGE, q.offset());
            return Result.of(load(storeId, shown), offset,
                    q.before(shown.get(0).cursor(), offset - PAGE).href(),
                    q.after(shown.get(PAGE - 1).cursor(), offset + PAGE).href());
        }
        List<ArchiveIndexEntry> page = take(orders.archiveIndex(storeId, request(q, today, q.after(), true, PAGE + 1)), PAGE + 1);
        boolean hasNext = page.size() > PAGE;
        List<ArchiveIndexEntry> shown = hasNext ? page.subList(0, PAGE) : page;
        String previous = q.offset() > 0 && !shown.isEmpty() ? q.before(shown.get(0).cursor(), q.offset() - PAGE).href() : null;
        String next = hasNext ? q.after(shown.get(PAGE - 1).cursor(), q.offset() + PAGE).href() : null;
        return Result.of(load(storeId, shown), q.offset(), previous, next);
    }

    private Optional<Result> byNumber(String storeId, OrderArchiveQuery q, LocalDate today) {
        List<Order> found = orders.findByShortId(storeId, q.numberPrefix());
        if (found.isEmpty()) {
            return Optional.empty();
        }
        LocalDate from = q.effectiveFrom(today);
        LocalDate to = q.effectiveTo();
        List<Order> closed = found.stream()
                .filter(o -> q.statuses().contains(o.getStatus()))
                .filter(o -> o.getOrderedAt() == null || ((from == null || !o.getOrderedAt().toLocalDate().isBefore(from))
                        && (to == null || !o.getOrderedAt().toLocalDate().isAfter(to))))
                .sorted(Comparator.comparing(Order::getOrderedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(PAGE)
                .toList();
        if (!closed.isEmpty()) {
            return Optional.of(Result.of(closed, 0, null, null));
        }
        List<Order> open = found.stream().filter(o -> !o.isClosed()).toList();
        if (!open.isEmpty()) {
            return Optional.of(new Result(List.of(), 0, null, null, null, null, open));
        }
        return Optional.empty();
    }

    private Result byEmail(String storeId, OrderArchiveQuery q, LocalDate today) {
        String needle = OrderSearch.normalize(q.q());
        Iterator<ArchiveIndexEntry> index = orders.archiveIndex(storeId, request(q, today, q.after(), true, 1000));
        List<ArchiveIndexEntry> matched = new ArrayList<>();
        while (index.hasNext() && matched.size() <= PAGE) {
            ArchiveIndexEntry entry = index.next();
            if (entry.email() != null && entry.email().toLowerCase(Locale.ROOT).contains(needle)) {
                matched.add(entry);
            }
        }
        boolean hasNext = matched.size() > PAGE;
        List<ArchiveIndexEntry> shown = hasNext ? matched.subList(0, PAGE) : matched;
        // a search pages forward only; "previous" starts it again from the newest order
        return Result.of(load(storeId, shown), q.offset(), q.offset() > 0 ? q.first().href() : null,
                hasNext ? q.after(shown.get(PAGE - 1).cursor(), q.offset() + PAGE).href() : null);
    }

    private Result byText(String storeId, OrderArchiveQuery q, LocalDate today) {
        Iterator<ArchiveIndexEntry> index = orders.archiveIndex(storeId, request(q, today, q.after(), true, LOAD_CHUNK));
        List<Order> matched = new ArrayList<>();
        ArchiveIndexEntry lastScanned = null;
        boolean leftInChunk = false;
        int scanned = 0;
        while (index.hasNext() && scanned < TEXT_BATCH && matched.size() < PAGE) {
            List<ArchiveIndexEntry> chunk = new ArrayList<>();
            while (index.hasNext() && chunk.size() < LOAD_CHUNK && scanned + chunk.size() < TEXT_BATCH) {
                chunk.add(index.next());
            }
            Map<String, Order> loaded = new HashMap<>();
            load(storeId, chunk).forEach(o -> loaded.put(o.getOrderId(), o));
            for (int i = 0; i < chunk.size() && matched.size() < PAGE; i++) {
                ArchiveIndexEntry entry = chunk.get(i);
                scanned++;
                lastScanned = entry;
                Order order = loaded.get(entry.orderId());
                if (order != null && OrderSearch.matches(order, q.q())) {
                    matched.add(order);
                }
                leftInChunk = i < chunk.size() - 1;
            }
        }
        // a search pages forward only; "previous" starts it again from the newest order
        String previous = q.offset() > 0 ? q.first().href() : null;
        boolean more = leftInChunk || index.hasNext();
        if (matched.size() == PAGE) {
            // the 25th match is the last entry looked at, so the next page starts right after it
            return Result.of(matched, q.offset(), previous, more ? q.after(lastScanned.cursor(), q.offset() + PAGE).href() : null);
        }
        if (scanned >= TEXT_BATCH && more) {
            return new Result(matched, q.offset(), previous, null, lastScanned.orderedAt(),
                    q.after(lastScanned.cursor(), q.offset() + matched.size()).href(), List.of());
        }
        return Result.of(matched, q.offset(), previous, null);
    }

    private List<Order> load(String storeId, List<ArchiveIndexEntry> entries) {
        if (entries.isEmpty()) {
            return List.of();
        }
        return orders.findByIds(storeId, entries.stream().map(ArchiveIndexEntry::orderId).toList());
    }

    private static List<ArchiveIndexEntry> take(Iterator<ArchiveIndexEntry> it, int n) {
        List<ArchiveIndexEntry> out = new ArrayList<>();
        while (out.size() < n && it.hasNext()) {
            out.add(it.next());
        }
        return out;
    }

    private static LocalDateTime parse(String orderedAt) {
        return orderedAt.length() < 12 ? java.time.LocalDate.parse(orderedAt).atStartOfDay() : LocalDateTime.parse(orderedAt);
    }

    private OpenNotice openNotice(List<Order> open, OrderArchiveQuery q, Locale locale) {
        if (open.size() == 1) {
            Order order = open.get(0);
            return new OpenNotice(text(locale, "orders.archive.open.one", order.getShortenedOrderId()),
                    text(locale, "orders.archive.open.one.action"), OrderListQuery.PATH + "/" + order.getOrderId());
        }
        return new OpenNotice(text(locale, "orders.archive.open.many"), text(locale, "orders.archive.open.many.action"),
                OrderListQuery.PATH + "?filterId=&q=" + URLEncoder.encode(q.q(), StandardCharsets.UTF_8));
    }

    private EmptyState emptyState(OrderArchiveQuery q, Locale locale) {
        if (q.offset() > 0 && q.q() != null) {
            return new EmptyState(text(locale, "orders.archive.empty.searchMore", q.q()),
                    List.of(new Action(text(locale, "orders.archive.empty.first"), q.first().href())));
        }
        if (!q.isFiltered() && q.status() == null) {
            return new EmptyState(text(locale, "orders.archive.empty.none"), List.of());
        }
        if (q.q() != null) {
            List<Action> actions = new ArrayList<>();
            if (q.status() != null) {
                actions.add(new Action(text(locale, "orders.archive.empty.searchAll"), q.withStatus(null).href()));
            }
            actions.add(new Action(text(locale, "orders.archive.search.clear"), q.withQ(null).href()));
            return new EmptyState(text(locale, "orders.archive.empty.search", q.q()), actions);
        }
        return new EmptyState(text(locale, "orders.archive.empty.filtered"),
                List.of(new Action(text(locale, "general.clear.filters"), q.cleared().href())));
    }

    private List<SegmentLink> segments(OrderArchiveQuery q, Locale locale) {
        return List.of(
                new SegmentLink(text(locale, "orders.archive.segment.all"), q.withStatus(null).href(), q.status() == null),
                new SegmentLink(text(locale, "orders.archive.segment.Completed"), q.withStatus(OrderStatus.Completed).href(),
                        q.status() == OrderStatus.Completed),
                new SegmentLink(text(locale, "orders.archive.segment.Cancelled"), q.withStatus(OrderStatus.Cancelled).href(),
                        q.status() == OrderStatus.Cancelled));
    }

    private DateMenu dates(OrderArchiveQuery q, Locale locale) {
        List<Preset> presets = new ArrayList<>();
        presets.add(new Preset(text(locale, "orders.archive.dates.all"), q.withoutDates().href(), !q.hasDates()));
        for (Period p : Period.values()) {
            presets.add(new Preset(text(locale, "orders.archive.dates." + p.param()), q.withPeriod(p).href(), q.period() == p));
        }
        return new DateMenu(datesValue(q, locale), presets, q.from() == null ? null : q.from().toString(),
                q.to() == null ? null : q.to().toString());
    }

    private String datesValue(OrderArchiveQuery q, Locale locale) {
        if (q.period() != null) return text(locale, "orders.archive.dates." + q.period().param()).toLowerCase(locale);
        String from = q.from() == null ? null : DAY.format(q.from());
        String to = q.to() == null ? null : DAY.format(q.to());
        if (from != null && to != null) return text(locale, "orders.archive.dates.range", from, to);
        if (from != null) return text(locale, "orders.archive.dates.since", from);
        if (to != null) return text(locale, "orders.archive.dates.until", to);
        return text(locale, "orders.archive.dates.all").toLowerCase(locale);
    }

    private List<Chip> chips(OrderArchiveQuery q, Locale locale) {
        List<Chip> chips = new ArrayList<>();
        if (q.q() != null) {
            String label = text(locale, "orders.archive.chip.search", q.q());
            chips.add(new Chip(label, q.withQ(null).href(), text(locale, "orders.archive.chip.clear", label)));
        }
        if (q.hasDates()) {
            String label = text(locale, "orders.archive.chip.dates", datesValue(q, locale));
            chips.add(new Chip(label, q.withoutDates().href(), text(locale, "orders.archive.chip.clear", label)));
        }
        return chips;
    }

    private String text(Locale locale, String key, Object... args) {
        return messages.getMessage(key, args, locale);
    }
}
