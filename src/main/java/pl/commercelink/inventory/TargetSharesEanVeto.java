package pl.commercelink.inventory;

import java.util.Optional;

/** Another global group matches the target, by EAN or by PIM id (findMatching matches both). */
final class TargetSharesEanVeto implements LinkVeto {

    @Override
    public Optional<OwnToGlobalLinks.Outcome> refuse(LinkCandidate candidate) {
        MatchedInventory global = candidate.global();
        boolean shared = candidate.globalIndex().findMatching(global.getInventoryKey()).stream()
                .anyMatch(other -> other != global);
        return shared ? Optional.of(OwnToGlobalLinks.Outcome.TARGET_SHARES_EAN) : Optional.empty();
    }
}
