package pl.commercelink.inventory;

import pl.commercelink.inventory.supplier.InventoryItem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Links own groups of a store to global groups that are the same product under other EANs: an own group without any
 * global counterpart by PIM id or EAN is linked to the one global group that shares its brand and manufacturer code,
 * unless something makes the link doubtful. A link is a join of two products, so every condition errs on the side
 * of leaving the groups apart.
 */
final class OwnToGlobalLinks {

    // TARGET_SHARES_EAN: another global group matches the target, by EAN or by PIM id (findMatching matches both)
    enum Outcome {
        LINKED, VARIANT_CODE, SEVERAL_TARGETS, CONFLICTING_PIM_IDS, TARGET_SHARES_EAN,
        TARGET_HAS_OWN_GROUP, PRICE_TOO_FAR, SEVERAL_OWN_GROUPS
    }

    /**
     * One own group that shares a brand and code with a global group, and what became of it. The averages are the net
     * prices of the offers of the shared pairs on each side; {@code global} is null and both averages are NaN when
     * there is no single target ({@link Outcome#VARIANT_CODE}, {@link Outcome#SEVERAL_TARGETS}).
     */
    record Decision(MatchedInventory own, MatchedInventory global, Outcome outcome,
                    double ownAveragePrice, double globalAveragePrice) {
    }

    private OwnToGlobalLinks() {
    }

    /**
     * Links own groups of the index to global groups and reports every candidate. Not thread-safe, like
     * {@link InventoryIndex#link}: call it before the own index is published.
     *
     * @param variantCodes variant codes of the own and the global pool together
     */
    static List<Decision> apply(InventoryIndex ownIndex, GlobalMatchedInventory.Generation global, VariantCodes variantCodes) {
        // keyed by the raw code, so the pass over the global pool allocates nothing for offers no own group shares;
        // one small Pair per distinct own pair, nothing per own group until a global offer hits it
        Map<String, Pair> pairsByCode = collectPairs(ownIndex, global.index());
        if (pairsByCode.isEmpty()) {
            return List.of();
        }
        Map<MatchedInventory, Candidate> hits = findTargets(global.all(), pairsByCode, variantCodes);

        // every condition is evaluated before the first link, so a link never makes a later candidate's target look taken
        List<Decision> evaluated = new ArrayList<>(hits.size());
        Map<MatchedInventory, Integer> claimsByTarget = new IdentityHashMap<>();
        for (MatchedInventory own : ownIndex.all()) {
            Candidate candidate = hits.get(own);
            if (candidate == null) {
                continue;
            }
            Decision decision = evaluate(candidate, pairsByCode, ownIndex, global.index());
            if (decision.outcome() == Outcome.LINKED) {
                claimsByTarget.merge(decision.global(), 1, Integer::sum);
            }
            evaluated.add(decision);
        }

        List<Decision> decisions = new ArrayList<>(evaluated.size());
        for (Decision decision : evaluated) {
            if (decision.outcome() != Outcome.LINKED) {
                decisions.add(decision);
            } else if (claimsByTarget.get(decision.global()) > 1) {
                decisions.add(new Decision(decision.own(), decision.global(), Outcome.SEVERAL_OWN_GROUPS,
                        decision.ownAveragePrice(), decision.globalAveragePrice()));
            } else {
                ownIndex.link(decision.own(), decision.global().getEans());
                decisions.add(decision);
            }
        }
        return decisions;
    }

    private static Map<String, Pair> collectPairs(InventoryIndex ownIndex, InventoryIndex globalIndex) {
        Map<String, Pair> pairsByCode = new HashMap<>();
        for (MatchedInventory own : ownIndex.all()) {
            if (globalIndex.contains(own.getInventoryKey())) {
                continue;
            }
            for (InventoryItem offer : own.offersView()) {
                if (!hasPair(offer)) {
                    continue;
                }
                String brandKey = offer.brandKey();
                Pair first = pairsByCode.get(offer.mfn());
                Pair pair = Pair.find(first, brandKey);
                if (pair == null) {
                    pair = new Pair(brandKey, offer.mfn(), first);
                    pairsByCode.put(offer.mfn(), pair);
                }
                pair.addOwn(own);
            }
        }
        return pairsByCode;
    }

    private static Map<MatchedInventory, Candidate> findTargets(Collection<MatchedInventory> globalGroups,
                                                               Map<String, Pair> pairsByCode, VariantCodes variantCodes) {
        Map<MatchedInventory, Candidate> hits = new IdentityHashMap<>();
        for (MatchedInventory global : globalGroups) {
            for (InventoryItem offer : global.offersView()) {
                Pair first = offer.mfn() == null ? null : pairsByCode.get(offer.mfn());
                if (first == null || !offer.hasMatchableBrand()) {
                    continue;
                }
                Pair pair = Pair.find(first, offer.brandKey());
                if (pair == null) {
                    continue;
                }
                boolean variant = pair.isVariant(variantCodes);
                pair.forEachOwn(own -> {
                    Candidate candidate = hits.computeIfAbsent(own, Candidate::new);
                    if (variant) {
                        candidate.variantHit = true;
                    } else {
                        candidate.hit(global, pair, offer.netPrice());
                    }
                });
            }
        }
        return hits;
    }

    private static Decision evaluate(Candidate candidate, Map<String, Pair> pairsByCode, InventoryIndex ownIndex,
                                     InventoryIndex globalIndex) {
        MatchedInventory own = candidate.own;
        if (candidate.targets.isEmpty()) {
            return new Decision(own, null, Outcome.VARIANT_CODE, Double.NaN, Double.NaN);
        }
        if (candidate.targets.size() > 1) {
            return new Decision(own, null, Outcome.SEVERAL_TARGETS, Double.NaN, Double.NaN);
        }
        Map.Entry<MatchedInventory, PriceSum> target = candidate.targets.entrySet().iterator().next();
        MatchedInventory global = target.getKey();
        double ownAverage = candidate.ownAveragePrice(pairsByCode);
        double globalAverage = target.getValue().average();

        Outcome outcome;
        if (haveConflictingPimIds(own.getInventoryKey(), global.getInventoryKey())) {
            outcome = Outcome.CONFLICTING_PIM_IDS;
        } else if (globalIndex.findMatching(global.getInventoryKey()).stream().anyMatch(other -> other != global)) {
            outcome = Outcome.TARGET_SHARES_EAN;
        } else if (!ownIndex.findMatching(global.getInventoryKey()).isEmpty()) {
            outcome = Outcome.TARGET_HAS_OWN_GROUP;
        } else if (PriceSplitThresholds.areTooFarApart(ownAverage, globalAverage)) {
            outcome = Outcome.PRICE_TOO_FAR;
        } else {
            outcome = Outcome.LINKED;
        }
        return new Decision(own, global, outcome, ownAverage, globalAverage);
    }

    private static boolean haveConflictingPimIds(InventoryKey own, InventoryKey global) {
        return own.getId() != null && global.getId() != null && !own.getId().equals(global.getId());
    }

    private static boolean hasPair(InventoryItem offer) {
        return offer.hasMatchableBrand() && offer.mfn() != null && !offer.mfn().isBlank();
    }

    /** A brand and code of the own pool and the own groups that list it; other brands of the same code are chained. */
    private static final class Pair {

        private final String brandKey;
        private final String mfn;
        private final Pair otherBrand;
        private MatchedInventory firstOwn;
        // more than one own group per pair is rare, so the list exists only then
        private List<MatchedInventory> moreOwns;
        private Boolean variant;

        private Pair(String brandKey, String mfn, Pair otherBrand) {
            this.brandKey = brandKey;
            this.mfn = mfn;
            this.otherBrand = otherBrand;
        }

        static Pair find(Pair first, String brandKey) {
            for (Pair pair = first; pair != null; pair = pair.otherBrand) {
                if (pair.brandKey.equals(brandKey)) {
                    return pair;
                }
            }
            return null;
        }

        void addOwn(MatchedInventory own) {
            if (firstOwn == null) {
                firstOwn = own;
                return;
            }
            // the offers of one group are walked together, so a repeat of its pair is always the last group
            MatchedInventory last = moreOwns == null ? firstOwn : moreOwns.get(moreOwns.size() - 1);
            if (last == own) {
                return;
            }
            if (moreOwns == null) {
                moreOwns = new ArrayList<>(2);
            }
            moreOwns.add(own);
        }

        void forEachOwn(Consumer<MatchedInventory> action) {
            action.accept(firstOwn);
            if (moreOwns != null) {
                moreOwns.forEach(action);
            }
        }

        // asked only on a global hit, so pairs no global offer shares never look the variant codes up
        boolean isVariant(VariantCodes variantCodes) {
            if (variant == null) {
                variant = variantCodes.contains(brandKey, mfn);
            }
            return variant;
        }
    }

    private static final class Candidate {

        private final MatchedInventory own;
        // created on the first unprotected hit
        private Map<MatchedInventory, PriceSum> targets = Map.of();
        private Set<Pair> sharedPairs = Set.of();
        private boolean variantHit;

        private Candidate(MatchedInventory own) {
            this.own = own;
        }

        void hit(MatchedInventory global, Pair pair, double netPrice) {
            if (targets.isEmpty()) {
                targets = new LinkedHashMap<>(2);
                sharedPairs = new HashSet<>(2);
            }
            PriceSum sum = targets.get(global);
            if (sum == null) {
                sum = new PriceSum();
                targets.put(global, sum);
            }
            sum.add(netPrice);
            sharedPairs.add(pair);
        }

        double ownAveragePrice(Map<String, Pair> pairsByCode) {
            PriceSum sum = new PriceSum();
            for (InventoryItem offer : own.offersView()) {
                if (hasPair(offer) && sharedPairs.contains(Pair.find(pairsByCode.get(offer.mfn()), offer.brandKey()))) {
                    sum.add(offer.netPrice());
                }
            }
            return sum.average();
        }
    }

    private static final class PriceSum {

        private double total;
        private int count;

        void add(double price) {
            total += price;
            count++;
        }

        double average() {
            return count == 0 ? Double.NaN : total / count;
        }
    }
}
