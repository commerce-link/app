package pl.commercelink.web.deliveries.pending;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import pl.commercelink.inventory.deliveries.DeliveriesPlanningService;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.deliveries.pending.PendingDeliveriesPageModel.*;
import pl.commercelink.web.deliveries.pending.PendingDeliveriesQuery.Kind;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;

/** The pending deliveries page: a view over the delivery planning, filtered, counted and sorted in memory (spec §4). */
@Service
@RequiredArgsConstructor
public class PendingDeliveriesService {

    private static final Comparator<PendingDeliveryRow> ORDER = Comparator
            .comparing(PendingDeliveryRow::due, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(PendingDeliveryRow::providerLabel, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(PendingDeliveryRow::key, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));

    private final DeliveriesPlanningService planningService;
    private final StoresRepository storesRepository;
    private final SupplierLabels supplierLabels;
    private final MessageSource messages;

    public PendingDeliveriesPageModel page(String storeId, boolean superAdmin, PendingDeliveriesQuery requested,
                                           LocalDate today, Locale locale) {
        DeliveriesPlanningService.Planning planning = planningService.plan(storeId);
        Set<String> providers = new LinkedHashSet<>();
        planning.deliveries().forEach(d -> providers.add(d.getProvider()));
        planning.dropshipCandidates().forEach(c -> providers.add(c.provider()));
        // A supplier that has nothing pending any more (e.g. its delivery was just created) is ignored, not an empty filter
        PendingDeliveriesQuery query = requested.withProviders(
                requested.providers().stream().filter(providers::contains).toList());
        Store store = storesRepository.findById(storeId);
        // Approval is configuration on the store (a GLOBAL supplier), so unlike API ordering it needs no secret read
        Set<String> approvalProviders = new HashSet<>();
        if (store != null) {
            providers.stream().filter(Objects::nonNull).filter(store::isGlobalSupplier).forEach(approvalProviders::add);
        }
        PendingDeliveryRowMapper mapper = new PendingDeliveryRowMapper(messages, locale, supplierLabels.forStore(store),
                planning.orders(), approvalProviders, storeId, superAdmin);

        List<PendingDeliveryRow> all = Stream.concat(
                        planning.deliveries().stream().map(d -> mapper.warehouse(d, today)),
                        planning.dropshipCandidates().stream().map(c -> mapper.dropship(c, today)))
                .sorted(ORDER).toList();
        List<PendingDeliveryRow> filtered = all.stream().filter(r -> matches(r, query)).toList();
        long warehouse = filtered.stream().filter(r -> r.kind() == Kind.WAREHOUSE).count();
        long dropship = filtered.size() - warehouse;
        Kind active = query.kind() != null ? query.kind() : warehouse == 0 && dropship > 0 ? Kind.DROPSHIP : Kind.WAREHOUSE;
        List<PendingDeliveryRow> rows = filtered.stream().filter(r -> r.kind() == active).toList();

        // Links that remove a filter keep the tab in view, also when it was chosen by default; narrowing links drop it
        PendingDeliveriesQuery widening = query.kind() != null ? query : query.withKind(active);
        String path = superAdmin ? "/dashboard/store/" + storeId + "/deliveries/preview" : "/dashboard/deliveries/preview";
        Map<String, String> providerLabels = new LinkedHashMap<>();
        all.forEach(r -> providerLabels.putIfAbsent(r.provider(), r.providerLabel()));

        return new PendingDeliveriesPageModel(query, superAdmin, path, path + "/fragment", "/dashboard/deliveries",
                List.of(tab(Kind.WAREHOUSE, warehouse, active, query, path, locale), tab(Kind.DROPSHIP, dropship, active, query, path, locale)),
                active, text(locale, "deliveries.pending.kind." + active.param()),
                text(locale, "deliveries.pending.kind." + active.param() + ".desc"),
                providerOptions(all, query, path, providerLabels), providerSummary(query, providerLabels, locale),
                chips(query, widening, path, providerLabels, locale), widening.cleared().href(path), widening.withoutQ().href(path),
                text(locale, "deliveries.pending.results", rows.size()), rows,
                emptyState(all, rows, query, widening, path, active, locale), all.isEmpty(), query.activeFilterCount());
    }

    private static boolean matches(PendingDeliveryRow row, PendingDeliveriesQuery query) {
        return (query.providers().isEmpty() || query.providers().contains(row.provider()))
                && row.matches(query.q());
    }

    private KindTab tab(Kind kind, long count, Kind active, PendingDeliveriesQuery query, String path, Locale locale) {
        return new KindTab(text(locale, "deliveries.pending.kind." + kind.param()), count, query.withKind(kind).href(path), kind == active);
    }

    private static List<Option> providerOptions(List<PendingDeliveryRow> all, PendingDeliveriesQuery query, String path,
                                                Map<String, String> labels) {
        return labels.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(e -> new Option(e.getKey(), e.getValue(),
                        all.stream().filter(r -> e.getKey().equals(r.provider())).count(),
                        query.providers().contains(e.getKey()), query.toggleProvider(e.getKey()).href(path)))
                .toList();
    }

    private String providerSummary(PendingDeliveriesQuery query, Map<String, String> labels, Locale locale) {
        List<String> chosen = query.providers();
        if (chosen.isEmpty()) return text(locale, "deliveries.pending.menu.all");
        if (chosen.size() == 1) return labels.getOrDefault(chosen.getFirst(), chosen.getFirst());
        return text(locale, "deliveries.pending.menu.selected", chosen.size());
    }

    private List<Chip> chips(PendingDeliveriesQuery query, PendingDeliveriesQuery widening, String path, Map<String, String> labels, Locale locale) {
        List<Chip> chips = new ArrayList<>();
        query.providers().forEach(p -> chip(chips, text(locale, "deliveries.pending.chip.provider", labels.getOrDefault(p, p)),
                widening.withoutProvider(p).href(path), locale));
        if (query.q() != null) {
            chip(chips, text(locale, "deliveries.pending.chip.search", query.q()), widening.withoutQ().href(path), locale);
        }
        return chips;
    }

    private void chip(List<Chip> chips, String label, String clearHref, Locale locale) {
        chips.add(new Chip(label, clearHref, text(locale, "deliveries.pending.chip.clearLabel", label)));
    }

    private EmptyState emptyState(List<PendingDeliveryRow> all, List<PendingDeliveryRow> rows, PendingDeliveriesQuery query,
                                  PendingDeliveriesQuery widening, String path, Kind active, Locale locale) {
        if (all.isEmpty()) {
            return new EmptyState(text(locale, "deliveries.pending.empty.nothing"),
                    text(locale, "deliveries.pending.empty.nothing.action"), "/dashboard/deliveries", false);
        }
        if (!rows.isEmpty()) {
            return null;
        }
        if (query.isFiltered()) {
            return new EmptyState(text(locale, "deliveries.pending.empty.filtered"), text(locale, "general.clear.filters"),
                    widening.cleared().href(path), false);
        }
        return new EmptyState(text(locale, "deliveries.pending.empty." + active.param()), null, null, true);
    }

    private String text(Locale locale, String key, Object... args) {
        return messages.getMessage(key, args, locale);
    }
}
