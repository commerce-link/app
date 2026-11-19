package pl.commercelink.inventory;

import java.util.Optional;

/** The average prices of the shared offers are too far apart for one product. */
final class PriceTooFarVeto implements LinkVeto {

    @Override
    public Optional<OwnToGlobalLinks.Outcome> refuse(LinkCandidate candidate) {
        boolean tooFar = PriceSplitThresholds.areTooFarApart(candidate.ownAveragePrice(), candidate.globalAveragePrice());
        return tooFar ? Optional.of(OwnToGlobalLinks.Outcome.PRICE_TOO_FAR) : Optional.empty();
    }
}
