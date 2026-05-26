package pl.commercelink.invoicing;

import pl.commercelink.invoicing.api.InvoicePosition;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class InvoicePositionMatcher {

    private static final double EPS = 0.005;
    private static final double CLOSE_DELTA = 0.01;

    private final List<InvoicePosition> positions;
    private final Set<String> usedPositionIds;

    public InvoicePositionMatcher(List<InvoicePosition> positions) {
        this.positions = positions;
        this.usedPositionIds = new HashSet<>();
    }

    public Match match(double unitCost, int qty) {
        if (positions == null || unitCost <= 0 || qty <= 0) {
            return Match.none();
        }

        InvoicePosition best = null;
        double bestDelta = Double.MAX_VALUE;

        for (InvoicePosition pos : positions) {
            if (usedPositionIds.contains(pos.id())) {
                continue;
            }
            if (pos.qty() != qty) {
                continue;
            }
            double delta = Math.abs(pos.price().netValue() - unitCost);
            if (delta < bestDelta) {
                best = pos;
                bestDelta = delta;
            }
        }

        if (best == null) {
            return Match.none();
        }

        Quality quality = classify(bestDelta);
        if (quality == Quality.NO_MATCH) {
            return Match.none();
        }

        double signedDelta = best.price().netValue() - unitCost;
        usedPositionIds.add(best.id());
        return new Match(best.id(), quality, signedDelta);
    }

    public Optional<String> matchAuxiliary(double totalCost) {
        if (positions == null || totalCost <= 0) {
            return Optional.empty();
        }

        InvoicePosition best = null;
        double bestDelta = Double.MAX_VALUE;

        for (InvoicePosition pos : positions) {
            if (usedPositionIds.contains(pos.id())) {
                continue;
            }
            double delta = Math.abs(pos.totalPrice().netValue() - totalCost);
            if (delta < bestDelta) {
                best = pos;
                bestDelta = delta;
            }
        }

        if (best == null || bestDelta >= EPS) {
            return Optional.empty();
        }

        usedPositionIds.add(best.id());
        return Optional.of(best.id());
    }

    private static Quality classify(double absDelta) {
        if (absDelta < EPS) {
            return Quality.EXACT_MATCH;
        }
        if (Math.abs(absDelta - CLOSE_DELTA) < EPS) {
            return Quality.CLOSE_MATCH;
        }
        return Quality.NO_MATCH;
    }

    public enum Quality {
        EXACT_MATCH, CLOSE_MATCH, NO_MATCH
    }

    public record Match(String positionId, Quality quality, double priceDelta) {
        public static Match none() {
            return new Match(null, Quality.NO_MATCH, 0.0);
        }

        public boolean found() {
            return positionId != null;
        }
    }
}
