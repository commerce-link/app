package pl.commercelink.web.payments;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Payment;
import pl.commercelink.payments.statements.Allocation;
import pl.commercelink.payments.statements.BankOperation;
import pl.commercelink.payments.statements.MatchKind;
import pl.commercelink.payments.statements.MatchRule;
import pl.commercelink.payments.statements.MatchWarning;
import pl.commercelink.payments.statements.OperationMatch;
import pl.commercelink.payments.statements.PayoutChannels;
import pl.commercelink.payments.statements.StatementAmounts;
import pl.commercelink.payments.statements.StatementBooking;
import pl.commercelink.payments.statements.StatementMatcher;
import pl.commercelink.stores.BankAccount;
import pl.commercelink.stores.PayoutChannel;
import pl.commercelink.stores.Store;
import pl.commercelink.web.orders.Money;
import pl.commercelink.web.orders.OrderPageModelFactory;
import pl.commercelink.web.payments.StatementPage.*;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the reconciliation page: matches the statement's incoming operations against the store's open orders (the
 * same read as the Payments page) and words every proposal for the operator. Operations booked a moment ago are shown
 * as booked from the booking's outcome, not from a new read: an order completed by its payment is not open any more.
 */
@Service
@RequiredArgsConstructor
public class StatementPageFactory {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM");
    private static final DateTimeFormatter FULL_DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final List<MatchKind> GROUPS = List.of(MatchKind.MATCHED, MatchKind.CHECK, MatchKind.PAYOUT,
            MatchKind.UNMATCHED, MatchKind.BOOKED);

    private final OrdersRepository ordersRepository;
    private final StatementJson json;
    private final MessageSource messages;

    public record Statement(BankAccount account, String fileName, List<BankOperation> incoming, int outgoing,
                            int skipped, List<String> problems) {
    }

    public StatementPage page(Store store, Statement statement, Map<String, List<Allocation>> chosen,
                              List<StatementBooking.Outcome> outcomes, Locale locale) {
        String storeId = store.getStoreId();
        List<PayoutChannel> channels = statement.account().getStatementFormat() == null ? List.of()
                : statement.account().getStatementFormat().payoutChannelsOrEmpty();
        List<Order> openOrders = ordersRepository.findByStoreAndStatuses(storeId, ReceivableEntry.OPEN_STATUSES);
        Map<String, Order> orders = new HashMap<>();
        openOrders.forEach(order -> orders.put(order.getOrderId(), order));
        Function<String, List<Order>> lookup = token -> {
            List<Order> found = ordersRepository.findByShortId(storeId, token);
            found.forEach(order -> orders.putIfAbsent(order.getOrderId(), order));
            return found;
        };
        List<OperationMatch> matches = new StatementMatcher(openOrders, channels, lookup).match(statement.incoming());

        Map<String, List<String>> justBooked = new HashMap<>();
        outcomes.forEach(outcome -> {
            List<String> ids = outcome.orders().stream()
                    .filter(order -> order.result() != StatementBooking.Result.FAILED)
                    .map(StatementBooking.OrderOutcome::orderId).toList();
            if (!ids.isEmpty()) {
                justBooked.put(outcome.operation().number(), ids);
            }
        });
        List<String> missing = justBooked.values().stream().flatMap(List::stream)
                .filter(id -> !orders.containsKey(id)).distinct().toList();
        if (!missing.isEmpty()) {
            ordersRepository.findByIds(storeId, missing).forEach(order -> orders.put(order.getOrderId(), order));
        }
        matches = matches.stream().map(match -> justBooked.containsKey(match.operation().number())
                ? booked(match, justBooked.get(match.operation().number())) : match).toList();

        Map<String, List<Allocation>> proposals = new LinkedHashMap<>();
        List<Row> rows = new ArrayList<>();
        for (OperationMatch match : matches) {
            List<Allocation> allocations = match.allocations();
            boolean preserved = false;
            if (match.kind() != MatchKind.BOOKED && chosen.containsKey(match.operation().number())) {
                allocations = chosen.get(match.operation().number());
                preserved = true;
            }
            // an assignment the operator removed travels on empty, or the next booking would propose it again
            if (match.kind() != MatchKind.BOOKED && (preserved || !allocations.isEmpty())) {
                proposals.put(match.operation().number(), allocations);
            }
            boolean selected = outcomes.isEmpty() && !preserved && (match.kind() == MatchKind.MATCHED || namedPayout(match));
            rows.add(row(match, allocations, selected, groupOf(match, preserved, allocations), channels, orders, locale));
        }

        // the matcher's groups stay on the page when empty: the page script moves rows into them
        List<Group> groups = GROUPS.stream()
                .map(kind -> new Group(kind.name().toLowerCase(Locale.ROOT),
                        text(locale, "payments.statement.group." + kind.name()),
                        rows.stream().filter(row -> row.group().equals(kind.name())).toList()))
                .filter(group -> !group.rows().isEmpty() || !group.id().equals(MatchKind.BOOKED.name().toLowerCase(Locale.ROOT)))
                .toList();

        List<Row> selectedRows = rows.stream().filter(Row::selected).toList();
        long selectedGrosze = selectedRows.stream().mapToLong(Row::amountGrosze).sum();
        int selectedOrders = selectedRows.stream().mapToInt(row -> proposals.getOrDefault(row.number(), List.of()).size()).sum();
        BankAccount account = statement.account();
        return new StatementPage(account.getId(), statement.fileName(), tiles(rows, locale), groups, notices(outcomes, locale), statement.problems(),
                json.write(statement.problems()), statement.outgoing(),
                statement.skipped(), json.operations(statement.incoming()), json.allocations(proposals),
                receivables(openOrders, channels, locale), channels(channels, locale), selectedRows.size(),
                money(locale, selectedGrosze), selectedOrders);
    }

    private static OperationMatch booked(OperationMatch match, List<String> orderIds) {
        return new OperationMatch(match.operation(), MatchKind.BOOKED, null, List.of(), orderIds, List.of(),
                EnumSet.noneOf(MatchWarning.class), null, match.matchedTexts());
    }

    /**
     * The matcher's verdict until the operator's own choice says otherwise: an operation left without orders waits to
     * be assigned like one nothing was found for, one assigned by hand is checked like a proposal. A payout keeps its
     * group, its dialog never leaves it without orders.
     */
    private static String groupOf(OperationMatch match, boolean preserved, List<Allocation> allocations) {
        if (!preserved || match.kind() == MatchKind.PAYOUT) {
            return match.kind().name();
        }
        if (allocations.isEmpty()) {
            return MatchKind.UNMATCHED.name();
        }
        return match.kind() == MatchKind.UNMATCHED ? MatchKind.CHECK.name() : match.kind().name();
    }

    private Row row(OperationMatch match, List<Allocation> allocations, boolean selected, String group,
                    List<PayoutChannel> channels, Map<String, Order> orders, Locale locale) {
        BankOperation operation = match.operation();
        boolean payout = match.kind() == MatchKind.PAYOUT;
        boolean booked = match.kind() == MatchKind.BOOKED;
        List<OrderRef> refs = new ArrayList<>();
        String note = null;
        if (booked) {
            match.candidates().forEach(id -> refs.add(orderRef(id, orders, bookedDetail(orders.get(id), operation, locale))));
        } else if (payout && !allocations.isEmpty()) {
            allocations.forEach(allocation -> refs.add(orderRef(allocation.orderId(), orders, payoutDetail(allocation, locale))));
            if (allocations.size() > 1) {
                note = text(locale, "payments.statement.orders.payout", allocations.size(),
                        money(locale, allocations.stream().map(Allocation::amount).reduce(BigDecimal.ZERO, BigDecimal::add)));
            }
        } else if (payout) {
            note = text(locale, "payments.statement.orders.payout.none", match.candidates().size());
        } else if (!allocations.isEmpty()) {
            allocations.forEach(allocation -> refs.add(orderRef(allocation.orderId(), orders,
                    debtDetail(orders.get(allocation.orderId()), allocation, locale))));
        } else if (match.warnings().contains(MatchWarning.ORDER_ALREADY_PAID)) {
            match.candidates().forEach(id -> refs.add(orderRef(id, orders, text(locale, "payments.statement.orders.paid"))));
        } else if (!match.candidates().isEmpty()) {
            note = text(locale, "payments.statement.orders.choose", match.candidates().size());
        } else {
            note = text(locale, "payments.statement.orders.none");
        }

        String[] payment = payment(match, allocations, channels, orders, locale);
        List<Integer> payerRows = match.payout().stream().map(channels::indexOf).toList();
        String action = booked ? null
                : payout ? text(locale, "payments.statement.action.payout")
                : allocations.isEmpty() ? text(locale, "payments.statement.action.assign")
                : text(locale, "payments.statement.action.change");
        return new Row(operation.number(), operation.date().format(DAY), operation.sender(),
                titleParts(operation.title(), match.matchedTexts()), money(locale, operation.amount()),
                StatementJson.grosze(operation.amount()), group, rulePill(match, locale),
                warningPills(match, locale), refs, note, payment[0], payment[1],
                !booked && !allocations.isEmpty(), selected && !allocations.isEmpty(), payout, json.write(payerRows),
                json.write(match.candidates()), action);
    }

    private OrderRef orderRef(String orderId, Map<String, Order> orders, String detail) {
        Order order = orders.get(orderId);
        String shortId = order == null ? StringUtils.left(orderId, 8) : order.getShortenedOrderId();
        String client = order == null ? null : OrderPageModelFactory.clientName(order);
        return new OrderRef(orderId, shortId, "/dashboard/orders/" + orderId, client, detail);
    }

    /** What a payout pays for one of its orders, with the fee the payer kept when it kept one. */
    private String payoutDetail(Allocation allocation, Locale locale) {
        return allocation.fee().signum() > 0
                ? text(locale, "payments.statement.orders.payoutFee", money(locale, allocation.amount()), money(locale, allocation.fee()))
                : text(locale, "payments.statement.orders.owed", money(locale, allocation.amount()));
    }

    private String debtDetail(Order order, Allocation allocation, Locale locale) {
        if (order == null) {
            return null;
        }
        BigDecimal owed = StatementAmounts.of(order.getUnpaidAmount());
        BigDecimal left = owed.subtract(allocation.amount());
        String after = left.signum() == 0 ? text(locale, "payments.statement.orders.after.paid")
                : left.signum() > 0 ? text(locale, "payments.statement.orders.after.underpaid", money(locale, left))
                : text(locale, "payments.statement.orders.after.overpaid", money(locale, left.negate()));
        return text(locale, "payments.statement.orders.owed", money(locale, owed)) + " · " + after;
    }

    private String bookedDetail(Order order, BankOperation operation, Locale locale) {
        if (order == null) {
            return operation.number();
        }
        return order.getPayments().stream()
                .filter(payment -> operation.number().equalsIgnoreCase(StringUtils.trim(payment.getBankTransactionNo())))
                .findFirst()
                .map(payment -> payment.getBankTransactionNo() + (payment.getBankTransactionDate() == null ? ""
                        : " · " + payment.getBankTransactionDate().format(FULL_DAY)))
                .orElse(operation.number());
    }

    /** The type and reference number of the payment the operation fills, the operator compares them with the title. */
    private String[] payment(OperationMatch match, List<Allocation> allocations, List<PayoutChannel> channels,
                             Map<String, Order> orders, Locale locale) {
        String none = text(locale, "payments.statement.payment.none");
        if (match.kind() == MatchKind.PAYOUT) {
            if (allocations.isEmpty()) {
                return new String[]{none, null};
            }
            List<Payment> payments = allocations.stream().map(allocation -> pending(orders.get(allocation.orderId())))
                    .filter(Objects::nonNull).toList();
            String types = payments.stream().map(payment -> type(payment, locale)).distinct().collect(Collectors.joining(", "));
            long references = payments.stream().filter(payment -> StringUtils.isNotBlank(payment.getReferenceNo())).count();
            return new String[]{types.isEmpty() ? none : types, text(locale, "payments.statement.payment.references", references)};
        }
        if (match.kind() == MatchKind.BOOKED) {
            Order order = match.candidates().isEmpty() ? null : orders.get(match.candidates().get(0));
            Payment booked = order == null ? null : order.getPayments().stream()
                    .filter(payment -> match.operation().number().equalsIgnoreCase(StringUtils.trim(payment.getBankTransactionNo())))
                    .findFirst().orElse(null);
            return booked == null ? new String[]{none, null} : new String[]{type(booked, locale), reference(booked, locale)};
        }
        if (allocations.isEmpty()) {
            return new String[]{none, null};
        }
        Payment pending = pending(orders.get(allocations.get(0).orderId()));
        if (pending == null) {
            return new String[]{text(locale, "payments.statement.payment.new"), null};
        }
        String reference = reference(pending, locale);
        if (allocations.size() > 1) {
            reference = reference + " · " + text(locale, "payments.statement.payment.orders", allocations.size());
        }
        return new String[]{type(pending, locale), reference};
    }

    private static Payment pending(Order order) {
        return order == null ? null : order.getPendingPayment();
    }

    private String type(Payment payment, Locale locale) {
        String source = payment.getSource() == null ? text(locale, "payments.statement.payment.unknown")
                : text(locale, "PaymentSource." + payment.getSource().name());
        return StringUtils.isBlank(payment.getName()) ? source : source + " · " + payment.getName().trim();
    }

    private String reference(Payment payment, Locale locale) {
        return StringUtils.isBlank(payment.getReferenceNo())
                ? text(locale, "payments.statement.payment.noReference")
                : text(locale, "payments.statement.payment.reference", payment.getReferenceNo().trim());
    }

    /**
     * A payout that names its orders and adds up to the transfer (a gateway paying out one payment less its fee): as sure
     * as an order number in a customer's transfer, so it is ticked for booking.
     */
    private static boolean namedPayout(OperationMatch match) {
        return match.kind() == MatchKind.PAYOUT && match.rule() != MatchRule.PAYOUT && match.warnings().isEmpty()
                && match.hasProposal();
    }

    /** The payer as the format names it: the text its rows look for, "SANTANDER". */
    private static String payerLabel(List<PayoutChannel> rows) {
        return rows.stream().map(row -> StringUtils.trim(row.getMatch())).filter(StringUtils::isNotBlank).distinct()
                .collect(Collectors.joining(", "));
    }

    private Pill rulePill(OperationMatch match, Locale locale) {
        if (match.kind() == MatchKind.BOOKED) {
            return new Pill(text(locale, "payments.statement.rule.BOOKED"), "is-neutral");
        }
        if (match.rule() == null) {
            return new Pill(text(locale, "payments.statement.rule.NONE"), "is-neutral");
        }
        if (match.kind() == MatchKind.PAYOUT) {
            return new Pill(text(locale, "payments.statement.rule.PAYOUT", payerLabel(match.payout())),
                    namedPayout(match) ? "is-ok" : "is-info");
        }
        String tone = match.kind() == MatchKind.MATCHED ? "is-ok" : "is-info";
        return new Pill(text(locale, "payments.statement.rule." + match.rule().name()), tone);
    }

    private List<Pill> warningPills(OperationMatch match, Locale locale) {
        List<Pill> pills = new ArrayList<>();
        for (MatchWarning warning : match.warnings()) {
            String label = switch (warning) {
                case UNDERPAID, OVERPAID, FEE_ABOVE_LIMIT ->
                        text(locale, "payments.statement.warning." + warning.name(), money(locale, match.difference()));
                case AMBIGUOUS -> text(locale, "payments.statement.warning.AMBIGUOUS", match.candidates().size());
                default -> text(locale, "payments.statement.warning." + warning.name());
            };
            pills.add(new Pill(label, warning == MatchWarning.ORDER_ALREADY_PAID ? "is-bad" : "is-warn"));
        }
        return pills;
    }

    /** The title cut around the numbers the match was found by, so the page can mark them. */
    static List<TitlePart> titleParts(String title, List<String> hits) {
        String text = StringUtils.defaultString(title);
        List<TitlePart> parts = new ArrayList<>();
        int from = 0;
        while (from < text.length()) {
            int next = -1;
            String hit = null;
            for (String candidate : hits) {
                if (StringUtils.isBlank(candidate)) {
                    continue;
                }
                int at = StringUtils.indexOfIgnoreCase(text, candidate, from);
                if (at >= 0 && (next < 0 || at < next)) {
                    next = at;
                    hit = text.substring(at, at + candidate.length());
                }
            }
            if (next < 0) {
                parts.add(new TitlePart(text.substring(from), false));
                break;
            }
            if (next > from) {
                parts.add(new TitlePart(text.substring(from, next), false));
            }
            parts.add(new TitlePart(hit, true));
            from = next + hit.length();
        }
        return parts;
    }

    private List<Tile> tiles(List<Row> rows, Locale locale) {
        return List.of(MatchKind.MATCHED, MatchKind.CHECK, MatchKind.PAYOUT, MatchKind.UNMATCHED).stream()
                .map(kind -> {
                    List<Row> of = rows.stream().filter(row -> row.group().equals(kind.name())).toList();
                    return new Tile(kind.name().toLowerCase(Locale.ROOT), text(locale, "payments.statement.tile." + kind.name()),
                            of.size(), money(locale, of.stream().mapToLong(Row::amountGrosze).sum()),
                            text(locale, "payments.statement.tile." + kind.name() + ".hint"));
                })
                .toList();
    }

    private List<Notice> notices(List<StatementBooking.Outcome> outcomes, Locale locale) {
        if (outcomes.isEmpty()) {
            return List.of();
        }
        List<Notice> notices = new ArrayList<>();
        long booked = outcomes.stream().filter(StatementBooking.Outcome::booked).count();
        long ordersBooked = outcomes.stream().flatMap(outcome -> outcome.orders().stream())
                .filter(order -> order.result() == StatementBooking.Result.BOOKED).count();
        if (booked > 0) {
            notices.add(new Notice(text(locale, "payments.statement.booked", booked, ordersBooked), "is-ok", List.of()));
        }
        List<String> failures = new ArrayList<>();
        for (StatementBooking.Outcome outcome : outcomes) {
            BankOperation operation = outcome.operation();
            String prefix = operation.date().format(DAY) + " · " + money(locale, operation.amount());
            if (outcome.refusalKey() != null) {
                failures.add(prefix + ": " + text(locale, outcome.refusalKey()));
            }
            outcome.orders().stream()
                    .filter(order -> order.result() == StatementBooking.Result.FAILED)
                    .forEach(order -> failures.add(prefix + " · " + StringUtils.left(order.orderId(), 8) + ": "
                            + text(locale, order.messageKey())));
        }
        if (!failures.isEmpty()) {
            notices.add(new Notice(text(locale, "payments.statement.failed"), "is-bad", failures));
        }
        return notices;
    }

    /** The orders that still owe money, for the dialogs' search and the payout lists. */
    private String receivables(List<Order> openOrders, List<PayoutChannel> channels, Locale locale) {
        List<Map<String, Object>> list = openOrders.stream()
                .filter(order -> StatementAmounts.of(order.getUnpaidAmount()).signum() > 0)
                .sorted(Comparator.comparing(Order::getOrderedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(order -> {
                    Payment pending = order.getPendingPayment();
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", order.getOrderId());
                    item.put("shortId", order.getShortenedOrderId());
                    item.put("client", StringUtils.defaultString(OrderPageModelFactory.clientName(order)));
                    item.put("email", StringUtils.defaultString(order.getEmail()));
                    item.put("ordered", order.getOrderedAt() == null ? "" : order.getOrderedAt().format(FULL_DAY));
                    item.put("unpaid", StatementJson.grosze(StatementAmounts.of(order.getUnpaidAmount())));
                    item.put("fee", pending == null ? 0 : StatementJson.grosze(StatementAmounts.of(pending.getFee())));
                    item.put("type", pending == null ? text(locale, "payments.statement.payment.new") : type(pending, locale));
                    item.put("reference", pending == null ? text(locale, "payments.statement.payment.noReference") : reference(pending, locale));
                    item.put("channels", channels.stream()
                            .filter(channel -> PayoutChannels.pays(channel, order))
                            .map(channels::indexOf)
                            .collect(Collectors.toList()));
                    return item;
                })
                .toList();
        return json.write(list);
    }

    private String channels(List<PayoutChannel> channels, Locale locale) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int index = 0; index < channels.size(); index++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", index);
            item.put("label", payerLabel(List.of(channels.get(index))));
            item.put("net", channels.get(index).isNetOfFees());
            list.add(item);
        }
        return json.write(list);
    }

    private String money(Locale locale, BigDecimal amount) {
        return text(locale, "general.currency.amount", Money.format(amount.doubleValue())).replace(' ', '\u00a0');
    }

    private String money(Locale locale, long grosze) {
        return money(locale, StatementJson.money(grosze));
    }

    private String text(Locale locale, String key, Object... args) {
        return messages.getMessage(key, args, locale);
    }
}
