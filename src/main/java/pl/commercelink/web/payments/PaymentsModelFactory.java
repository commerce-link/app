package pl.commercelink.web.payments;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.orders.OrderPageModelFactory;
import pl.commercelink.web.payments.PaymentsPageModel.*;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The Payments page (spec docs/active/payments-redesign): reads the store's unpaid deliveries and its open orders
 * (StoreIdStatusIndex, not a scan), classifies them, then narrows, counts, sorts and sums in memory — a store has
 * a few dozen of each, so there is no paging.
 */
@Service
@RequiredArgsConstructor
public class PaymentsModelFactory {

    private final DeliveriesRepository deliveries;
    private final OrdersRepository orders;
    private final StoresRepository stores;
    private final SupplierLabels supplierLabels;
    private final MessageSource messages;

    public PaymentsPageModel page(String storeId, PaymentsQuery query, LocalDate today, Locale locale) {
        SupplierLabelMap labels = supplierLabels.forStoreId(storeId);
        PaymentsRowMapper mapper = new PaymentsRowMapper(messages, locale, labels);
        List<PayableEntry> payables = deliveries.findUnpaidDeliveries(storeId).stream()
                .map(PayableEntry::of).flatMap(Optional::stream).toList();
        List<ReceivableEntry> receivables = orders.findByStoreAndStatuses(storeId, ReceivableEntry.OPEN_STATUSES).stream()
                .map(o -> ReceivableEntry.of(o, today)).flatMap(Optional::stream).toList();

        Predicate<PayableEntry> payableBase = e -> (query.focus() == null || query.focus().matches(e, today))
                && matches(e, query.q(), mapper);
        Predicate<ReceivableEntry> receivableBase = e -> (query.focus() == null || query.focus().matches(e))
                && matches(e, query.q());
        List<PayableEntry> payableBaseList = payables.stream().filter(payableBase).toList();
        List<ReceivableEntry> receivableBaseList = receivables.stream().filter(receivableBase).toList();
        List<PayableEntry> shownPayables = payableBaseList.stream()
                .filter(e -> query.providers().isEmpty() || query.providers().contains(e.delivery().getProvider()))
                .sorted(payableOrder(mapper)).toList();
        List<ReceivableEntry> shownReceivables = receivableBaseList.stream()
                .filter(e -> query.methods().isEmpty() || query.methods().contains(e.method()))
                .sorted(receivableOrder()).toList();

        PaymentSide side = query.side() != null ? query.side()
                : shownPayables.isEmpty() && !shownReceivables.isEmpty() ? PaymentSide.RECEIVABLES : PaymentSide.PAYABLES;
        PaymentsQuery current = query.withSide(side);

        List<Tile> tiles = Arrays.stream(PaymentFocus.values()).map(f -> {
            long p = payables.stream().filter(e -> f.matches(e, today)).count();
            long r = receivables.stream().filter(f::matches).count();
            boolean active = f == query.focus();
            return new Tile(text(locale, "payments.tile." + f.param()), p + r,
                    text(locale, "payments.tile.hint.payables", p), text(locale, "payments.tile.hint.receivables", r),
                    active ? current.withoutFocus().href() : query.withFocus(f).href(), active);
        }).toList();

        List<SideTab> tabs = List.of(
                new SideTab(text(locale, "payments.side.payables"), shownPayables.size(),
                        current.withSide(PaymentSide.PAYABLES).href(), side == PaymentSide.PAYABLES),
                new SideTab(text(locale, "payments.side.receivables"), shownReceivables.size(),
                        current.withSide(PaymentSide.RECEIVABLES).href(), side == PaymentSide.RECEIVABLES));

        boolean payablesSide = side == PaymentSide.PAYABLES;
        List<Option> options = payablesSide ? providerOptions(payableBaseList, current, mapper)
                : methodOptions(receivableBaseList, current, locale);
        int selected = payablesSide ? current.providers().size() : current.methods().size();
        Map<String, String> optionLabels = options.stream().collect(Collectors.toMap(Option::value, Option::label, (a, b) -> a));

        List<PayableRow> payableRows = payablesSide ? shownPayables.stream().map(e -> mapper.map(e, today)).toList() : List.of();
        List<ReceivableRow> receivableRows = payablesSide ? List.of() : shownReceivables.stream().map(e -> mapper.map(e, today)).toList();

        double owed = payablesSide ? shownPayables.stream().filter(PayableEntry::owes).mapToDouble(PayableEntry::amount).sum()
                : shownReceivables.stream().filter(ReceivableEntry::owes).mapToDouble(ReceivableEntry::amount).sum();
        double owedNet = payablesSide
                ? shownPayables.stream().filter(PayableEntry::owes).mapToDouble(e -> e.delivery().getUnpaidAmountNet()).sum()
                : shownReceivables.stream().filter(ReceivableEntry::owes).mapToDouble(e -> e.order().getUnpaidAmountNet()).sum();
        double refunds = payablesSide
                ? shownPayables.stream().filter(e -> !e.owes()).mapToDouble(PayableEntry::amount).sum()
                : shownReceivables.stream().filter(e -> !e.owes()).mapToDouble(ReceivableEntry::amount).sum();
        String sideKey = payablesSide ? "payables" : "receivables";
        String tail = text(locale, "payments.results.tail", mapper.money(owedNet))
                + (PaymentsAmounts.isZero(refunds) ? "" : " " + text(locale, "payments.results.refund." + sideKey, mapper.money(refunds)));

        Store store = stores.findById(storeId);
        return new PaymentsPageModel(current, side, tiles, tabs,
                text(locale, payablesSide ? "payments.menu.provider" : "payments.menu.method"),
                payablesSide ? "provider" : "method",
                selected == 0 ? text(locale, "deliveries.list.menu.all") : text(locale, "deliveries.list.menu.selected", selected),
                options,
                text(locale, "payments.search.placeholder." + sideKey),
                chips(current, payablesSide, optionLabels, locale),
                text(locale, "payments.results." + sideKey, payablesSide ? shownPayables.size() : shownReceivables.size()),
                mapper.money(owed), tail,
                payableRows, receivableRows,
                emptyState(current, payables.isEmpty() && receivables.isEmpty(), payablesSide ? payableRows.isEmpty() : receivableRows.isEmpty(), sideKey, locale),
                store != null && store.hasIntegration(IntegrationType.INVOICING_PROVIDER),
                current.href());
    }

    private static boolean matches(PayableEntry e, String q, PaymentsRowMapper mapper) {
        if (q == null) return true;
        Delivery d = e.delivery();
        return StringUtils.startsWithIgnoreCase(d.getDeliveryId(), q)
                || StringUtils.containsIgnoreCase(d.getExternalDeliveryId(), q)
                || StringUtils.containsIgnoreCase(mapper.supplierLabel(d), q);
    }

    private static boolean matches(ReceivableEntry e, String q) {
        if (q == null) return true;
        Order o = e.order();
        String email = o.getBillingDetails() == null ? o.getEmail() : o.getBillingDetails().getEmail();
        return StringUtils.startsWithIgnoreCase(o.getOrderId(), q)
                || StringUtils.containsIgnoreCase(o.getExternalOrderId(), q)
                || StringUtils.containsIgnoreCase(OrderPageModelFactory.clientName(o), q)
                || StringUtils.containsIgnoreCase(email, q)
                || StringUtils.containsIgnoreCase(o.getEmail(), q);
    }

    /** Money owed by due date (undated last), then supplier and number; refunds at the end (spec §5.1). */
    private static Comparator<PayableEntry> payableOrder(PaymentsRowMapper mapper) {
        return Comparator.comparing((PayableEntry e) -> e.owes() ? 0 : 1)
                .thenComparing(PayableEntry::due, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(e -> StringUtils.defaultString(mapper.supplierLabel(e.delivery())), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(e -> e.delivery().getDeliveryId());
    }

    /** Urgent, then warned, then by date, cash on delivery on its way, refunds last (spec §5.2). */
    private static Comparator<ReceivableEntry> receivableOrder() {
        return Comparator.comparing(PaymentsModelFactory::rank)
                .thenComparing(ReceivableEntry::shipDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(e -> e.order().getOrderId());
    }

    private static int rank(ReceivableEntry e) {
        if (!e.owes()) return 4;
        if (e.urgency().isBad()) return 0;
        if (e.urgency().isWarn()) return 1;
        return e.standing() == PaymentsAmounts.Standing.COD ? 3 : 2;
    }

    private List<Option> providerOptions(List<PayableEntry> base, PaymentsQuery query, PaymentsRowMapper mapper) {
        Map<String, Long> counts = base.stream().filter(e -> e.delivery().getProvider() != null)
                .collect(Collectors.groupingBy(e -> e.delivery().getProvider(), Collectors.counting()));
        Map<String, String> names = new LinkedHashMap<>();
        base.forEach(e -> { if (e.delivery().getProvider() != null) names.putIfAbsent(e.delivery().getProvider(), mapper.supplierLabel(e.delivery())); });
        query.providers().forEach(p -> names.putIfAbsent(p, p));
        return names.entrySet().stream().sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER))
                .map(n -> new Option(n.getKey(), n.getValue(), counts.getOrDefault(n.getKey(), 0L), query.providers().contains(n.getKey())))
                .toList();
    }

    private List<Option> methodOptions(List<ReceivableEntry> base, PaymentsQuery query, Locale locale) {
        Map<PaymentSource, Long> counts = base.stream().filter(e -> e.method() != null)
                .collect(Collectors.groupingBy(ReceivableEntry::method, Collectors.counting()));
        return Arrays.stream(PaymentSource.values())
                .filter(m -> counts.containsKey(m) || query.methods().contains(m))
                .map(m -> new Option(m.name(), text(locale, "PaymentSource." + m.name()), counts.getOrDefault(m, 0L), query.methods().contains(m)))
                .toList();
    }

    private List<Chip> chips(PaymentsQuery query, boolean payablesSide, Map<String, String> optionLabels, Locale locale) {
        List<Chip> chips = new ArrayList<>();
        if (query.focus() != null) {
            chip(chips, text(locale, "payments.tile." + query.focus().param()), query.withoutFocus().href(), locale);
        }
        if (payablesSide) {
            query.providers().forEach(p -> chip(chips, text(locale, "payments.chip.provider", optionLabels.getOrDefault(p, p)),
                    query.withoutProvider(p).href(), locale));
        } else {
            query.methods().forEach(m -> chip(chips, text(locale, "payments.chip.method", text(locale, "PaymentSource." + m.name())),
                    query.withoutMethod(m).href(), locale));
        }
        if (query.q() != null) {
            chip(chips, text(locale, "payments.chip.search", query.q()), query.withQ(null).href(), locale);
        }
        return chips;
    }

    private void chip(List<Chip> chips, String label, String href, Locale locale) {
        chips.add(new Chip(label, href, text(locale, "deliveries.list.chip.clearLabel", label)));
    }

    private EmptyState emptyState(PaymentsQuery query, boolean nothingAtAll, boolean sideEmpty, String sideKey, Locale locale) {
        if (nothingAtAll && !query.isFiltered()) {
            return new EmptyState(text(locale, "payments.empty.all"), null, null);
        }
        if (!sideEmpty) {
            return null;
        }
        if (query.isFiltered()) {
            return new EmptyState(text(locale, "payments.empty.filtered"), text(locale, "general.clear.filters"), query.cleared().href());
        }
        return new EmptyState(text(locale, "payments.empty." + sideKey), null, null);
    }

    private String text(Locale locale, String key, Object... args) {
        return messages.getMessage(key, args, locale);
    }
}
