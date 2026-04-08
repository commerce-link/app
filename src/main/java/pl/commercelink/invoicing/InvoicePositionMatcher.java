package pl.commercelink.invoicing;

import pl.commercelink.invoicing.api.InvoicePosition;

import java.util.*;

public class InvoicePositionMatcher {

    private static final double PRICE_TOLERANCE = 10.00;

    private final List<InvoicePosition> positions;
    private final Set<String> usedPositionIds;

    public InvoicePositionMatcher(List<InvoicePosition> positions) {
        this.positions = positions;
        this.usedPositionIds = new HashSet<>();
    }

    public String findIdByCost(double cost) {
        return findIdByCostAndQty(cost, -1);
    }

    public String findIdByCostAndQty(double cost, int qty) {
        if (positions == null || cost <= 0) {
            return null;
        }

        List<Candidate> qtyMatched = new ArrayList<>();
        List<Candidate> all = new ArrayList<>();
        for (InvoicePosition pos : positions) {
            if (usedPositionIds.contains(pos.id())) {
                continue;
            }
            double abs = Math.abs(pos.price().netValue() - cost);
            if (abs < PRICE_TOLERANCE) {
                Candidate candidate = new Candidate(pos.id(), abs);
                all.add(candidate);
                if (qty > 0 && pos.qty() == qty) {
                    qtyMatched.add(candidate);
                }
            }
        }

        List<Candidate> candidates = qtyMatched.isEmpty() ? all : qtyMatched;
        if (!candidates.isEmpty()) {
            Candidate best = candidates.stream().min(Comparator.comparingDouble(c -> c.difference)).get();
            usedPositionIds.add(best.id);
            return best.id;
        }
        return null;
    }

    private record Candidate(String id, double difference) {}

}
