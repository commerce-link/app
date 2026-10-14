package pl.commercelink.payments.statements;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.documents.Document;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.stores.PayoutChannel;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Proposes the orders of each incoming operation, trying in turn: the operation's number already on a payment (booked
 * before), an order number in the title, a document or payment reference number in the title, a payout channel, and
 * the amount. Candidates are the store's open orders that still owe money; an amount proposed for one operation is
 * no longer owed for the next, so two transfers never settle the same debt twice. Only numbers found in the title for
 * exactly what is owed are {@link MatchKind#MATCHED}; everything else waits for the operator.
 * <p>
 * An order named in the title that is not open any more (Completed is not among the orders read) is read by its number
 * through shortIdLookup, so a repeated upload of an operation booked on it is recognised.
 */
public class StatementMatcher {

    private static final Pattern SHORT_ID = Pattern.compile("(?<![0-9a-fA-F])[0-9a-fA-F]{8}(?![0-9a-fA-F])");
    private static final int MAX_LOOKUPS = 3;
    private static final int MIN_NUMBER_LENGTH = 6;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final List<Order> openOrders;
    private final List<PayoutChannel> channels;
    private final Function<String, List<Order>> shortIdLookup;
    private final Map<String, Receivable> receivables = new LinkedHashMap<>();
    private final Map<String, List<String>> bookedNumbers = new HashMap<>();

    public StatementMatcher(List<Order> openOrders, List<PayoutChannel> channels,
                            Function<String, List<Order>> shortIdLookup) {
        this.openOrders = openOrders;
        this.channels = channels;
        this.shortIdLookup = shortIdLookup;
        openOrders.stream()
                .sorted(Comparator.comparing(Order::getOrderedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(order -> {
                    BigDecimal unpaid = StatementAmounts.of(order.getUnpaidAmount());
                    if (unpaid.signum() > 0) {
                        receivables.put(order.getOrderId(), new Receivable(order, order.getPendingPayment(), unpaid));
                    }
                    order.getPayments().stream()
                            .map(Payment::getBankTransactionNo)
                            .filter(StringUtils::isNotBlank)
                            .forEach(number -> bookedNumbers.computeIfAbsent(key(number), k -> new ArrayList<>())
                                    .add(order.getOrderId()));
                });
    }

    /** The proposals for the incoming operations, oldest first; the operations going out are not matched. */
    public List<OperationMatch> match(List<BankOperation> operations) {
        return operations.stream()
                .filter(BankOperation::isIncoming)
                .sorted(Comparator.comparing(BankOperation::date).thenComparing(BankOperation::line))
                .map(this::match)
                .toList();
    }

    private OperationMatch match(BankOperation operation) {
        List<String> booked = bookedNumbers.get(key(operation.number()));
        if (booked != null) {
            return result(operation, MatchKind.BOOKED, null, List.of(), booked, List.of(), EnumSet.noneOf(MatchWarning.class), null, List.of());
        }
        // a payer's transfer is a payout even when its title names the order (a gateway paying out each payment on
        // its own, less its fee): taken as a customer's transfer, it would leave the order underpaid by the fee and turn
        // its online payment into a bank transfer
        List<PayoutChannel> payer = PayoutChannels.payer(operation, channels);
        if (!payer.isEmpty()) {
            return payout(operation, payer);
        }
        List<String> texts = new ArrayList<>();
        List<Receivable> named = byOrderNumber(operation, texts);
        if (!named.isEmpty()) {
            return allocate(operation, MatchRule.ORDER_NUMBER, named, texts);
        }
        OperationMatch closed = namedClosedOrder(operation);
        if (closed != null) {
            return closed;
        }
        named = byDocumentNumber(operation, texts);
        if (!named.isEmpty()) {
            return allocate(operation, MatchRule.DOCUMENT_NUMBER, named, texts);
        }
        named = byReferenceNumber(operation, texts);
        if (!named.isEmpty()) {
            return allocate(operation, MatchRule.REFERENCE_NUMBER, named, texts);
        }
        return byAmount(operation);
    }

    private List<Receivable> byOrderNumber(BankOperation operation, List<String> texts) {
        String title = StringUtils.defaultString(operation.title());
        String lower = title.toLowerCase(Locale.ROOT);
        String compact = lower.replaceAll("[^0-9a-z]", "");
        List<Receivable> found = new ArrayList<>();
        for (Receivable receivable : open()) {
            Order order = receivable.order();
            String id = order.getOrderId().toLowerCase(Locale.ROOT);
            String shortId = StringUtils.defaultString(order.getShortenedOrderId()).toLowerCase(Locale.ROOT);
            String external = StringUtils.trimToNull(order.getExternalOrderId());
            if (compact.contains(id.replace("-", ""))) {
                found.add(receivable);
                texts.add(verbatim(title, order.getOrderId()).orElse(order.getShortenedOrderId()));
            } else if (shortId.length() == 8 && hasToken(lower, shortId)) {
                found.add(receivable);
                texts.add(verbatim(title, shortId).orElse(shortId));
            } else if (external != null && external.length() >= MIN_NUMBER_LENGTH
                    && lower.contains(external.toLowerCase(Locale.ROOT))) {
                found.add(receivable);
                texts.add(verbatim(title, external).orElse(external));
            }
        }
        return found;
    }

    /**
     * An order named in the title that owes nothing: open but paid, or read by its number because it is closed. The
     * operation is booked on it already, or it is money for an order that does not wait for any.
     */
    private OperationMatch namedClosedOrder(BankOperation operation) {
        String title = StringUtils.defaultString(operation.title());
        Set<String> tokens = new LinkedHashSet<>();
        Matcher matcher = SHORT_ID.matcher(title);
        while (matcher.find()) {
            String token = matcher.group().toLowerCase(Locale.ROOT);
            // an order number has letters and digits; eight digits alone are a payout or account number far more often
            if (token.matches(".*[a-f].*") && token.matches(".*\\d.*")) {
                tokens.add(token);
            }
        }
        if (tokens.isEmpty()) {
            return null;
        }
        List<Order> named = new ArrayList<>();
        int lookups = 0;
        for (String token : tokens) {
            List<Order> open = openOrders.stream()
                    .filter(order -> token.equalsIgnoreCase(order.getShortenedOrderId()))
                    .toList();
            if (!open.isEmpty()) {
                named.addAll(open);
            } else if (lookups++ < MAX_LOOKUPS) {
                named.addAll(shortIdLookup.apply(token));
            }
        }
        if (named.isEmpty()) {
            return null;
        }
        List<String> ids = named.stream().map(Order::getOrderId).distinct().toList();
        boolean holdsOperation = named.stream().flatMap(order -> order.getPayments().stream())
                .anyMatch(payment -> key(operation.number()).equals(key(payment.getBankTransactionNo())));
        List<String> texts = named.stream().map(Order::getShortenedOrderId).toList();
        if (holdsOperation) {
            return result(operation, MatchKind.BOOKED, null, List.of(), ids, List.of(), EnumSet.noneOf(MatchWarning.class), null, texts);
        }
        return result(operation, MatchKind.UNMATCHED, MatchRule.ORDER_NUMBER, List.of(), ids, List.of(),
                EnumSet.of(MatchWarning.ORDER_ALREADY_PAID), null, texts);
    }

    private List<Receivable> byDocumentNumber(BankOperation operation, List<String> texts) {
        String title = StringUtils.defaultString(operation.title());
        String compact = title.toUpperCase(Locale.ROOT).replaceAll("\\s", "");
        List<Receivable> found = new ArrayList<>();
        for (Receivable receivable : open()) {
            List<Document> documents = receivable.order().getDocuments() == null ? List.of() : receivable.order().getDocuments();
            documents.stream()
                    .map(Document::getNumber)
                    .filter(StringUtils::isNotBlank)
                    .filter(number -> numberIn(compact, number))
                    .findFirst()
                    .ifPresent(number -> {
                        found.add(receivable);
                        texts.add(verbatim(title, number).orElse(number));
                    });
        }
        return found;
    }

    private List<Receivable> byReferenceNumber(BankOperation operation, List<String> texts) {
        String title = StringUtils.defaultString(operation.title());
        String compact = title.toUpperCase(Locale.ROOT).replaceAll("\\s", "");
        List<Receivable> found = new ArrayList<>();
        for (Receivable receivable : open()) {
            String reference = receivable.pending() == null ? null : StringUtils.trimToNull(receivable.pending().getReferenceNo());
            if (reference != null && reference.replaceAll("\\s", "").length() >= MIN_NUMBER_LENGTH
                    && compact.contains(reference.toUpperCase(Locale.ROOT).replaceAll("\\s", ""))) {
                found.add(receivable);
                texts.add(verbatim(title, reference).orElse(reference));
            }
        }
        return found;
    }

    /** One transfer for the named orders: their debts in turn, any surplus on the last one. */
    private OperationMatch allocate(BankOperation operation, MatchRule rule, List<Receivable> named, List<String> texts) {
        BigDecimal left = operation.amount();
        BigDecimal owed = named.stream().map(Receivable::remaining).reduce(ZERO, BigDecimal::add);
        List<Allocation> allocations = new ArrayList<>();
        for (int index = 0; index < named.size() && left.signum() > 0; index++) {
            Receivable receivable = named.get(index);
            boolean last = index == named.size() - 1;
            BigDecimal amount = last ? left : left.min(receivable.remaining());
            allocations.add(new Allocation(receivable.order().getOrderId(), amount, ZERO));
            left = left.subtract(amount);
        }
        Set<MatchWarning> warnings = EnumSet.noneOf(MatchWarning.class);
        int comparison = operation.amount().compareTo(owed);
        BigDecimal difference = null;
        if (comparison < 0) {
            warnings.add(MatchWarning.UNDERPAID);
            difference = owed.subtract(operation.amount());
        } else if (comparison > 0) {
            warnings.add(MatchWarning.OVERPAID);
            difference = operation.amount().subtract(owed);
        }
        if (allocations.stream().anyMatch(allocation -> waitsForAnotherMethod(receivables.get(allocation.orderId())))) {
            warnings.add(MatchWarning.METHOD_MISMATCH);
        }
        settle(allocations);
        MatchKind kind = warnings.isEmpty() ? MatchKind.MATCHED : MatchKind.CHECK;
        return result(operation, kind, rule, allocations, List.of(), List.of(), warnings, difference, texts);
    }

    /**
     * A payout: proposed only when exactly one set of the payer's orders adds up to the transfer, a run of them by order
     * date - payers settle a continuous period. The run is looked for among the orders of each row of the payer, and
     * among all of them together (a bank paying for loans of the shop and of the call centre in one transfer). Each
     * order's fee counts as its first row of the payer says. Anything less certain is left to the operator.
     */
    private OperationMatch payout(BankOperation operation, List<PayoutChannel> payer) {
        Map<String, PayoutChannel> rowOf = new LinkedHashMap<>();
        List<Receivable> orders = new ArrayList<>();
        for (Receivable receivable : open()) {
            PayoutChannels.rowOf(payer, receivable.order()).ifPresent(row -> {
                rowOf.put(receivable.order().getOrderId(), row);
                orders.add(receivable);
            });
        }
        Set<List<Receivable>> proposals = new LinkedHashSet<>();
        for (PayoutChannel row : payer) {
            proposals.addAll(runs(orders.stream().filter(receivable -> rowOf.get(receivable.order().getOrderId()) == row).toList(),
                    operation.amount(), rowOf));
        }
        if (payer.size() > 1) {
            proposals.addAll(runs(orders, operation.amount(), rowOf));
        }
        List<String> candidates = orders.stream().map(receivable -> receivable.order().getOrderId()).toList();
        OperationMatch named = namedPayout(operation, payer, rowOf, candidates);
        if (named != null) {
            return named;
        }
        if (proposals.size() != 1) {
            return result(operation, MatchKind.PAYOUT, MatchRule.PAYOUT, List.of(), candidates, payer,
                    EnumSet.of(MatchWarning.NO_PAYOUT_PROPOSAL), null, List.of());
        }
        List<Allocation> allocations = proposals.iterator().next().stream()
                .map(receivable -> new Allocation(receivable.order().getOrderId(), receivable.remaining(),
                        PayoutChannels.fee(rowOf.get(receivable.order().getOrderId()), receivable.pending())))
                .toList();
        settle(allocations);
        return result(operation, MatchKind.PAYOUT, MatchRule.PAYOUT, allocations, candidates, payer,
                EnumSet.noneOf(MatchWarning.class), null, List.of());
    }

    /**
     * A payout whose title names its orders by their order, document or reference number (a gateway that pays out each
     * payment on its own names the payment). Those orders are the proposal, whether the payer's rows describe them or
     * not. One named order that owes more than a payer deducting its fees sent has had the difference kept as the fee;
     * otherwise a payout that does not add up is proposed with the difference to check.
     */
    private OperationMatch namedPayout(BankOperation operation, List<PayoutChannel> payer, Map<String, PayoutChannel> rowOf,
                                       List<String> payerOrders) {
        List<String> texts = new ArrayList<>();
        MatchRule rule = MatchRule.ORDER_NUMBER;
        List<Receivable> named = byOrderNumber(operation, texts);
        if (named.isEmpty()) {
            rule = MatchRule.DOCUMENT_NUMBER;
            named = byDocumentNumber(operation, texts);
        }
        if (named.isEmpty()) {
            rule = MatchRule.REFERENCE_NUMBER;
            named = byReferenceNumber(operation, texts);
        }
        if (named.isEmpty()) {
            return null;
        }
        List<Allocation> allocations = new ArrayList<>();
        BigDecimal paidOut = ZERO;
        for (Receivable receivable : named) {
            PayoutChannel row = rowOf.getOrDefault(receivable.order().getOrderId(), payer.get(0));
            BigDecimal fee = PayoutChannels.fee(row, receivable.pending());
            allocations.add(new Allocation(receivable.order().getOrderId(), receivable.remaining(), fee));
            paidOut = paidOut.add(receivable.remaining().subtract(fee));
        }
        Set<MatchWarning> warnings = EnumSet.noneOf(MatchWarning.class);
        BigDecimal difference = null;
        int comparison = operation.amount().compareTo(paidOut);
        if (comparison != 0) {
            Receivable only = named.get(0);
            PayoutChannel row = rowOf.getOrDefault(only.order().getOrderId(), payer.get(0));
            if (named.size() == 1 && row.isNetOfFees() && comparison < 0) {
                allocations = List.of(new Allocation(only.order().getOrderId(), only.remaining(),
                        only.remaining().subtract(operation.amount())));
            } else {
                warnings.add(comparison < 0 ? MatchWarning.UNDERPAID : MatchWarning.OVERPAID);
                difference = paidOut.subtract(operation.amount()).abs();
            }
        }
        settle(allocations);
        List<String> candidates = new ArrayList<>(payerOrders);
        named.stream().map(receivable -> receivable.order().getOrderId())
                .filter(id -> !candidates.contains(id)).forEach(candidates::add);
        return result(operation, MatchKind.PAYOUT, rule, allocations, candidates, payer, warnings, difference, texts);
    }

    /** At most two runs of the orders, taken by order date, whose payouts add up to the amount: two are already too many. */
    private static List<List<Receivable>> runs(List<Receivable> orders, BigDecimal amount, Map<String, PayoutChannel> rowOf) {
        List<List<Receivable>> runs = new ArrayList<>();
        for (int start = 0; start < orders.size() && runs.size() < 2; start++) {
            BigDecimal sum = ZERO;
            for (int end = start; end < orders.size(); end++) {
                Receivable receivable = orders.get(end);
                sum = sum.add(PayoutChannels.payout(rowOf.get(receivable.order().getOrderId()), receivable.remaining(),
                        receivable.pending()));
                int comparison = sum.compareTo(amount);
                if (comparison == 0) {
                    runs.add(List.copyOf(orders.subList(start, end + 1)));
                    break;
                }
                if (comparison > 0) {
                    break;
                }
            }
        }
        return runs;
    }

    private OperationMatch byAmount(BankOperation operation) {
        List<Receivable> same = open().stream()
                .filter(receivable -> receivable.remaining().compareTo(operation.amount()) == 0)
                .toList();
        List<Receivable> fromClient = same.stream()
                .filter(receivable -> senderIsClient(senderOf(operation), receivable.order()))
                .toList();
        if (same.isEmpty()) {
            return result(operation, MatchKind.UNMATCHED, null, List.of(), List.of(), List.of(),
                    EnumSet.noneOf(MatchWarning.class), null, List.of());
        }
        if (fromClient.size() == 1 || same.size() == 1) {
            Receivable chosen = fromClient.size() == 1 ? fromClient.get(0) : same.get(0);
            MatchRule rule = fromClient.size() == 1 ? MatchRule.AMOUNT_AND_SENDER : MatchRule.AMOUNT;
            Set<MatchWarning> warnings = EnumSet.noneOf(MatchWarning.class);
            if (waitsForAnotherMethod(chosen)) {
                warnings.add(MatchWarning.METHOD_MISMATCH);
            }
            if (same.size() > 1) {
                warnings.add(MatchWarning.AMBIGUOUS);
            }
            List<Allocation> allocations = List.of(new Allocation(chosen.order().getOrderId(), operation.amount(), ZERO));
            settle(allocations);
            return result(operation, MatchKind.CHECK, rule, allocations, ids(same, fromClient), List.of(), warnings, null, List.of());
        }
        return result(operation, MatchKind.CHECK, MatchRule.AMOUNT, List.of(), ids(same, fromClient), List.of(),
                EnumSet.of(MatchWarning.AMBIGUOUS), null, List.of());
    }

    /** The candidates of an ambiguous amount, the ones whose client sent the money first. */
    private static List<String> ids(List<Receivable> same, List<Receivable> fromClient) {
        return Stream.concat(fromClient.stream(), same.stream())
                .map(receivable -> receivable.order().getOrderId())
                .distinct()
                .toList();
    }

    private void settle(List<Allocation> allocations) {
        allocations.forEach(allocation -> receivables.computeIfPresent(allocation.orderId(),
                (id, receivable) -> receivable.less(allocation.amount())));
    }

    private List<Receivable> open() {
        return receivables.values().stream().filter(receivable -> receivable.remaining().signum() > 0).toList();
    }

    private static boolean waitsForAnotherMethod(Receivable receivable) {
        Payment pending = receivable == null ? null : receivable.pending();
        return pending != null && pending.getSource() != null && pending.getSource() != PaymentSource.BankTransfer;
    }

    /** Some exports (mBank's operations list) have no sender column: the sender is then somewhere in the description. */
    private static String senderOf(BankOperation operation) {
        return StringUtils.isNotBlank(operation.sender()) ? operation.sender() : operation.title();
    }

    /** The sender is the client when it holds the company name, or every part of the person's name. */
    static boolean senderIsClient(String sender, Order order) {
        if (StringUtils.isBlank(sender)) {
            return false;
        }
        String compactSender = PayoutChannels.compact(sender);
        Set<String> senderWords = words(sender);
        List<String[]> names = new ArrayList<>();
        BillingDetails billing = order.getBillingDetails();
        if (billing != null) {
            names.add(new String[]{billing.getCompanyName(), billing.getName(), billing.getSurname()});
        }
        ShippingDetails shipping = order.getShippingDetails();
        if (shipping != null) {
            names.add(new String[]{shipping.getCompanyName(), shipping.getName(), shipping.getSurname()});
        }
        for (String[] name : names) {
            String company = PayoutChannels.compact(name[0]);
            if (company.length() >= 4 && compactSender.contains(company)) {
                return true;
            }
            Set<String> person = words(StringUtils.defaultString(name[1]) + " " + StringUtils.defaultString(name[2]));
            if (person.size() >= 2 && senderWords.containsAll(person)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> words(String text) {
        Set<String> words = new HashSet<>();
        for (String word : StringUtils.defaultString(text).split("[^\\p{L}\\p{N}]+")) {
            String compact = PayoutChannels.compact(word);
            if (compact.length() >= 2) {
                words.add(compact);
            }
        }
        return words;
    }

    /**
     * A document number in the title, spaces aside: "FV 0412/10/2026" whole, or its numeric part "0412/10/2026"
     * standing on its own, so "Faktura 0412/10/2026" counts but "11/10/2026" does not count for "1/10/2026".
     */
    private static boolean numberIn(String compactTitle, String number) {
        String compact = number.toUpperCase(Locale.ROOT).replaceAll("\\s", "");
        if (compact.length() >= MIN_NUMBER_LENGTH && compactTitle.contains(compact)) {
            return true;
        }
        int firstDigit = StringUtils.indexOfAny(compact, "0123456789");
        if (firstDigit < 0) {
            return false;
        }
        String digits = compact.substring(firstDigit);
        return digits.length() >= MIN_NUMBER_LENGTH && digits.matches(".*[/\\-.].*")
                && hasNumberToken(compactTitle, digits);
    }

    private static boolean hasNumberToken(String text, String token) {
        int from = 0;
        while (true) {
            int at = text.indexOf(token, from);
            if (at < 0) {
                return false;
            }
            boolean before = at == 0 || !Character.isDigit(text.charAt(at - 1));
            int end = at + token.length();
            boolean after = end == text.length() || !Character.isDigit(text.charAt(end));
            if (before && after) {
                return true;
            }
            from = at + 1;
        }
    }

    private static boolean hasToken(String lowerTitle, String shortId) {
        Matcher matcher = SHORT_ID.matcher(lowerTitle);
        while (matcher.find()) {
            if (matcher.group().equals(shortId)) {
                return true;
            }
        }
        return false;
    }

    /** The text as written in the title, for highlighting; empty when the title spells it differently. */
    private static Optional<String> verbatim(String title, String text) {
        int at = StringUtils.indexOfIgnoreCase(title, text);
        return at < 0 ? Optional.empty() : Optional.of(title.substring(at, at + text.length()));
    }

    private static String key(String number) {
        return StringUtils.defaultString(number).trim().toUpperCase(Locale.ROOT);
    }

    private static OperationMatch result(BankOperation operation, MatchKind kind, MatchRule rule,
                                         List<Allocation> allocations, List<String> candidates, List<PayoutChannel> payer,
                                         Set<MatchWarning> warnings, BigDecimal difference, List<String> texts) {
        return new OperationMatch(operation, kind, rule, List.copyOf(allocations), List.copyOf(candidates), List.copyOf(payer),
                Collections.unmodifiableSet(warnings), difference, List.copyOf(texts));
    }

    private record Receivable(Order order, Payment pending, BigDecimal remaining) {

        Receivable less(BigDecimal amount) {
            return new Receivable(order, pending, remaining.subtract(amount).max(ZERO));
        }
    }
}
