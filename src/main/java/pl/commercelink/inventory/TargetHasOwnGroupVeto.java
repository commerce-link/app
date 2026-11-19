package pl.commercelink.inventory;

import java.util.Optional;

/** The store already has an own group for the target, by EAN or by PIM id. */
final class TargetHasOwnGroupVeto implements LinkVeto {

    @Override
    public Optional<OwnToGlobalLinks.Outcome> refuse(LinkCandidate candidate) {
        boolean hasOwnGroup = !candidate.ownIndex().findMatching(candidate.global().getInventoryKey()).isEmpty();
        return hasOwnGroup ? Optional.of(OwnToGlobalLinks.Outcome.TARGET_HAS_OWN_GROUP) : Optional.empty();
    }
}
