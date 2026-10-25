package pl.commercelink.payments.statements;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The ways orders taken one after another add up to a payout, each order less one of the fees its payer may have kept
 * (a bank taking 2% or 4% of each loan). Per sum at most two ways are kept: enough to tell a single fitting way from
 * several. Sums above the payout are dropped, since every further order only adds to them; a search that still grows
 * past {@link #MAX_SUMS} sums is given up as undecided - a payer's batch is a handful of orders, anything larger is the
 * operator's to pick.
 */
final class PayoutSums {

    static final int MAX_SUMS = 2000;
    private static final int WAYS_KEPT = 2;

    private final BigDecimal amount;
    private Map<BigDecimal, List<List<Allocation>>> ways = Map.of(BigDecimal.ZERO.setScale(2), List.of(List.of()));
    private boolean undecided;

    PayoutSums(BigDecimal amount) {
        // the sums are keys: the same scale as the orders' amounts, or 100.0 and 100.00 would be two sums
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    /** The next order, owing owed, paid out less one of fees. */
    void add(String orderId, BigDecimal owed, List<BigDecimal> fees) {
        Map<BigDecimal, List<List<Allocation>>> next = new HashMap<>();
        ways.forEach((sum, waysToSum) -> {
            for (BigDecimal fee : fees) {
                BigDecimal reached = sum.add(owed.subtract(fee)).setScale(2, RoundingMode.HALF_UP);
                if (reached.compareTo(amount) > 0) {
                    continue;
                }
                List<List<Allocation>> kept = next.computeIfAbsent(reached, key -> new ArrayList<>());
                for (List<Allocation> way : waysToSum) {
                    if (kept.size() == WAYS_KEPT) {
                        break;
                    }
                    List<Allocation> longer = new ArrayList<>(way);
                    longer.add(new Allocation(orderId, owed, fee));
                    kept.add(List.copyOf(longer));
                }
            }
        });
        ways = next;
        undecided = undecided || next.size() > MAX_SUMS;
    }

    /** The ways the orders added so far make the payout exactly: none, one, or two standing for several. */
    List<List<Allocation>> exact() {
        return ways.getOrDefault(amount, List.of());
    }

    /** Whether every sum is past the payout, so no further order can make it. */
    boolean exhausted() {
        return ways.isEmpty();
    }

    boolean undecided() {
        return undecided;
    }
}
