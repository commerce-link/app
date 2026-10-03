package pl.commercelink.web.offers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import pl.commercelink.baskets.*;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.orders.Pagination;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Builds the offers list page (spec §3, §5): one index query per request, the shown page loaded in full. */
@Service
public class OfferListService {

    private static final DateTimeFormatter SAME_YEAR = DateTimeFormatter.ofPattern("dd.MM");
    private static final DateTimeFormatter OTHER_YEAR = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final BasketsRepository baskets;
    private final StoresRepository stores;
    private final MessageSource messages;
    private final String appDomain;

    public OfferListService(BasketsRepository baskets, StoresRepository stores, MessageSource messages,
                            @Value("${app.domain}") String appDomain) {
        this.baskets = baskets;
        this.stores = stores;
        this.messages = messages;
        this.appDomain = appDomain;
    }

    public OfferListPage page(String storeId, OfferListQuery requested, LocalDateTime now, Locale locale) {
        OfferListQuery query = requested;
        List<Basket> rows;
        int total;
        int page;

        // a pasted full ID opens that record in its own segment, whatever segment and validity were chosen (spec §3.5)
        Optional<Basket> exact = OfferListQuery.looksLikeId(requested.q())
                ? baskets.findById(storeId, requested.q().toLowerCase(Locale.ROOT)) : Optional.empty();
        if (exact.isPresent()) {
            query = new OfferListQuery(OfferSegment.of(exact.get().getType()), requested.q(), List.of(), null, null, 1);
            rows = List.of(exact.get());
            total = 1;
            page = 1;
        } else {
            OfferListResult result = baskets.findForList(storeId, new OfferListCriteria(query.segment().type(), query.q(),
                    Set.copyOf(query.validity()), query.from(), query.to(), now), query.page(), OfferListQuery.PAGE_SIZE);
            rows = result.rows();
            total = result.total();
            page = result.page();
        }

        Store store = stores.findById(storeId);
        OfferRowMapper mapper = new OfferRowMapper(messages, locale, store, appDomain, now, query.href());
        OfferSegment segment = query.segment();
        List<OfferListPage.OfferRow> offers = segment == OfferSegment.OFFERS ? rows.stream().map(mapper::offer).toList() : List.of();
        List<OfferListPage.TemplateRow> templates = segment == OfferSegment.TEMPLATES ? rows.stream().map(mapper::template).toList() : List.of();
        List<OfferListPage.BasketRow> basketRows = segment == OfferSegment.BASKETS ? rows.stream().map(mapper::basket).toList() : List.of();

        OfferListQuery q = query;
        return new OfferListPage(query,
                Arrays.stream(OfferSegment.values()).map(s -> new OfferListPage.SegmentLink(
                        text(locale, "offers.list.segment." + s.param()), q.withSegment(s).href(), s == segment)).toList(),
                Arrays.stream(OfferValidity.values()).map(v -> new OfferListPage.Option(v.param(),
                        text(locale, "offers.list.validity." + v.param()), q.validity().contains(v))).toList(),
                validitySummary(q, locale),
                new OfferListPage.DateMenu(datesValue(q, now, locale),
                        q.from() == null ? null : q.from().toString(), q.to() == null ? null : q.to().toString()),
                chips(q, now, locale),
                text(locale, "offers.list.results." + segment.param(), String.valueOf(total)),
                offers, templates, basketRows,
                Pagination.of(page, total, OfferListQuery.PAGE_SIZE, n -> q.withPage(n).href()),
                rows.isEmpty() ? emptyState(q, locale) : null,
                q.activeFilterCount());
    }

    private String validitySummary(OfferListQuery q, Locale locale) {
        if (q.validity().isEmpty()) return text(locale, "offers.list.summary.all");
        if (q.validity().size() == 1) return text(locale, "offers.list.validity." + q.validity().get(0).param());
        return text(locale, "offers.list.summary.selected", String.valueOf(q.validity().size()));
    }

    private String datesValue(OfferListQuery q, LocalDateTime now, Locale locale) {
        if (q.from() != null && q.to() != null) return text(locale, "offers.list.dates.range", day(q.from(), now), day(q.to(), now));
        if (q.from() != null) return text(locale, "offers.list.dates.since", day(q.from(), now));
        if (q.to() != null) return text(locale, "offers.list.dates.until", day(q.to(), now));
        return text(locale, "offers.list.summary.all");
    }

    private List<OfferListPage.Chip> chips(OfferListQuery q, LocalDateTime now, Locale locale) {
        List<OfferListPage.Chip> chips = new ArrayList<>();
        if (q.q() != null) chips.add(chip(text(locale, "offers.list.chip.q", q.q()), q.withQ(null).href(), locale));
        for (OfferValidity v : q.validity()) {
            chips.add(chip(text(locale, "offers.list.chip.validity", text(locale, "offers.list.validity." + v.param())),
                    q.withoutValidity(v).href(), locale));
        }
        if (q.from() != null || q.to() != null) {
            chips.add(chip(text(locale, "offers.list.chip.dates", datesValue(q, now, locale)), q.withoutDates().href(), locale));
        }
        return chips;
    }

    private OfferListPage.Chip chip(String label, String clearHref, Locale locale) {
        return new OfferListPage.Chip(label, clearHref, text(locale, "offers.list.chip.clear", label));
    }

    private OfferListPage.EmptyState emptyState(OfferListQuery q, Locale locale) {
        if (q.isFiltered()) {
            return new OfferListPage.EmptyState(text(locale, "offers.list.empty.filtered"),
                    text(locale, "general.clear.filters"), q.cleared().href());
        }
        return new OfferListPage.EmptyState(text(locale, "offers.list.empty." + q.segment().param()), null, null);
    }

    private static String day(LocalDate date, LocalDateTime now) {
        return (date.getYear() == now.getYear() ? SAME_YEAR : OTHER_YEAR).format(date);
    }

    private String text(Locale locale, String key, Object... args) {
        return messages.getMessage(key, args, locale);
    }
}
