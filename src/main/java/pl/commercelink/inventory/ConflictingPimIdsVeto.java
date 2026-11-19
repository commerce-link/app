package pl.commercelink.inventory;

import java.util.Optional;

/** Both groups are already identified by a PIM id, and the ids differ: they are two products. */
final class ConflictingPimIdsVeto implements LinkVeto {

    @Override
    public Optional<OwnToGlobalLinks.Outcome> refuse(LinkCandidate candidate) {
        InventoryKey own = candidate.own().getInventoryKey();
        InventoryKey global = candidate.global().getInventoryKey();
        boolean conflicting = own.getId() != null && global.getId() != null && !own.getId().equals(global.getId());
        return conflicting ? Optional.of(OwnToGlobalLinks.Outcome.CONFLICTING_PIM_IDS) : Optional.empty();
    }
}
