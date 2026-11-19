package pl.commercelink.inventory;

import java.util.Optional;

/** A condition that refuses linking an own group to a global group of the same brand and code. */
interface LinkVeto {

    /** @return the outcome that refuses the link, or empty when this rule has no objection */
    Optional<OwnToGlobalLinks.Outcome> refuse(LinkCandidate candidate);
}
