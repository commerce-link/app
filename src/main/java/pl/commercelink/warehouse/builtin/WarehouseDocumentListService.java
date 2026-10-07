package pl.commercelink.warehouse.builtin;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import pl.commercelink.documents.DocumentReason;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.orders.Pagination;
import pl.commercelink.web.warehousedocuments.DocumentKind;
import pl.commercelink.web.warehousedocuments.DocumentRowMapper;
import pl.commercelink.web.warehousedocuments.WarehouseDocumentListPage;
import pl.commercelink.web.warehousedocuments.WarehouseDocumentListPage.*;
import pl.commercelink.web.warehousedocuments.WarehouseDocumentListQuery;

import java.time.LocalTime;
import java.util.*;

/** Builds the warehouse documents list (spec §2): one index query per request, newest first, 25 per page. */
@Service
@RequiredArgsConstructor
public class WarehouseDocumentListService {

    static final String SETTINGS_HREF = "/dashboard/store/warehouse";

    private final WarehouseDocumentSearchService search;
    private final StoresRepository stores;
    private final MessageSource messages;

    public WarehouseDocumentListPage page(String storeId, boolean superAdmin, WarehouseDocumentListQuery q, Locale locale) {
        String fragmentPath = q.path() + "/list";
        Store store = stores.findById(storeId);
        if (!store.hasDocumentsGenerationEnabled()) {
            return new WarehouseDocumentListPage(q, superAdmin, false, fragmentPath, List.of(), List.of(), null,
                    null, List.of(), null, List.of(), null, null, SETTINGS_HREF);
        }

        WarehouseDocumentCriteria criteria = new WarehouseDocumentCriteria(storeId,
                q.kind() == null ? null : q.kind().type(), new LinkedHashSet<>(q.reasons()),
                q.from() == null ? null : q.from().atStartOfDay(),
                q.to() == null ? null : q.to().atTime(LocalTime.MAX),
                q.numberFragment());
        List<WarehouseDocument> found = search.search(criteria, q.productCode(), q.page(), WarehouseDocumentListQuery.PAGE_SIZE);
        boolean hasNext = found.size() > WarehouseDocumentListQuery.PAGE_SIZE;
        List<WarehouseDocument> shown = hasNext ? found.subList(0, WarehouseDocumentListQuery.PAGE_SIZE) : found;

        String detailsBase = superAdmin ? "/dashboard/store/" + storeId : "/dashboard";
        DocumentRowMapper mapper = new DocumentRowMapper(messages, locale, superAdmin, detailsBase);
        List<DocumentRow> rows = shown.stream().map(mapper::row).toList();
        // a page past the end (hand-typed page=9) is an empty list, not "201-200" with a link back
        Pagination pagination = rows.isEmpty()
                ? Pagination.openEnded(1, WarehouseDocumentListQuery.PAGE_SIZE, 0, false, n -> q.withPage(n).href())
                : Pagination.openEnded(q.page(), WarehouseDocumentListQuery.PAGE_SIZE, rows.size(), hasNext,
                n -> q.withPage(n).href());

        return new WarehouseDocumentListPage(q, superAdmin, true, fragmentPath,
                segments(q, locale), reasonOptions(q, locale), reasonSummary(q, locale), dates(q, locale),
                chips(q, locale),
                text(locale, "warehouse.documents.list.results", pagination.fromIndex() + (rows.isEmpty() ? 0 : 1), pagination.toIndex()),
                rows, pagination, rows.isEmpty() ? emptyState(q, locale) : null, SETTINGS_HREF);
    }

    private List<SegmentLink> segments(WarehouseDocumentListQuery q, Locale locale) {
        List<SegmentLink> segments = new ArrayList<>();
        segments.add(new SegmentLink(text(locale, "warehouse.documents.list.kind.all"), null, q.withKind(null).href(), q.kind() == null));
        for (DocumentKind kind : DocumentKind.values()) {
            segments.add(new SegmentLink(kind.code(), text(locale, "warehouse.documents.kind." + kind.code()),
                    q.withKind(kind).href(), q.kind() == kind));
        }
        return segments;
    }

    private List<Option> reasonOptions(WarehouseDocumentListQuery q, Locale locale) {
        return q.allowedReasons().stream()
                .map(r -> new Option(r.name(), text(locale, "DocumentReason." + r.name()), q.reasons().contains(r)))
                .toList();
    }

    private String reasonSummary(WarehouseDocumentListQuery q, Locale locale) {
        if (q.reasons().isEmpty()) return text(locale, "warehouse.documents.list.reason.all");
        if (q.reasons().size() == 1) return text(locale, "DocumentReason." + q.reasons().get(0).name()).toLowerCase(locale);
        return text(locale, "warehouse.documents.list.reason.some", q.reasons().size());
    }

    private DateMenu dates(WarehouseDocumentListQuery q, Locale locale) {
        return new DateMenu(datesValue(q, locale), q.from() == null ? null : q.from().toString(), q.to() == null ? null : q.to().toString());
    }

    private String datesValue(WarehouseDocumentListQuery q, Locale locale) {
        String from = q.from() == null ? null : q.from().format(DocumentRowMapper.DATE);
        String to = q.to() == null ? null : q.to().format(DocumentRowMapper.DATE);
        if (from != null && to != null) return text(locale, "warehouse.documents.list.dates.range", from, to);
        if (from != null) return text(locale, "warehouse.documents.list.dates.since", from);
        if (to != null) return text(locale, "warehouse.documents.list.dates.until", to);
        return text(locale, "warehouse.documents.list.dates.all");
    }

    private List<Chip> chips(WarehouseDocumentListQuery q, Locale locale) {
        List<Chip> chips = new ArrayList<>();
        if (q.q() != null) {
            String label = text(locale, "warehouse.documents.list.chip.search", q.q());
            chips.add(new Chip(label, q.withQ(null).href(), text(locale, "warehouse.documents.list.chip.clear", label)));
        }
        for (DocumentReason r : q.reasons()) {
            String label = text(locale, "DocumentReason." + r.name());
            chips.add(new Chip(label, q.withoutReason(r).href(), text(locale, "warehouse.documents.list.chip.clear", label)));
        }
        if (q.from() != null || q.to() != null) {
            String label = text(locale, "warehouse.documents.list.chip.dates", datesValue(q, locale));
            chips.add(new Chip(label, q.withoutDates().href(), text(locale, "warehouse.documents.list.chip.clear", label)));
        }
        return chips;
    }

    private EmptyState emptyState(WarehouseDocumentListQuery q, Locale locale) {
        if (!q.isFiltered()) {
            return new EmptyState(text(locale, "warehouse.documents.list.empty.none"), null, null);
        }
        String text = q.q() != null && q.reasons().isEmpty() && q.from() == null && q.to() == null
                ? text(locale, "warehouse.documents.list.empty.search", q.q())
                : text(locale, "warehouse.documents.list.empty.filtered");
        return new EmptyState(text, text(locale, "general.clear.filters"), q.cleared().href());
    }

    private String text(Locale locale, String key, Object... args) {
        return messages.getMessage(key, args, locale);
    }
}
