package pl.commercelink.payments.statements;

import pl.commercelink.stores.PayoutChannel;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * What the matcher proposes for one incoming operation. allocations is the proposal (empty when there is none);
 * candidates are the orders to choose from when the proposal is ambiguous, or the orders that already hold the
 * operation when it is {@link MatchKind#BOOKED}. payout holds the rows of the payer that sent a payout, empty for a
 * transfer from a customer. matchedTexts are the parts of the title that named the orders, to be
 * highlighted; difference is the amount behind {@link MatchWarning#UNDERPAID} or {@link MatchWarning#OVERPAID}, or the
 * fee behind {@link MatchWarning#FEE_ABOVE_LIMIT}.
 */
public record OperationMatch(BankOperation operation, MatchKind kind, MatchRule rule, List<Allocation> allocations,
                             List<String> candidates, List<PayoutChannel> payout, Set<MatchWarning> warnings,
                             BigDecimal difference, List<String> matchedTexts) {

    public boolean hasProposal() {
        return !allocations.isEmpty();
    }
}
