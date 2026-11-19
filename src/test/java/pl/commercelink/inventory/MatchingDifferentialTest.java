// Temporary: delete it, together with ReferenceAutoDiscovery, ReferenceOwnToGlobalLinks and MatchingPoolGenerator, in
// the first change that alters matching on purpose.
package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.OwnToGlobalLinks.Decision;
import pl.commercelink.inventory.OwnToGlobalLinks.Outcome;
import pl.commercelink.inventory.supplier.InventoryItem;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimView;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the frozen matching logic and the production one over the same seeded pools and requires identical results:
 * the guard of a refactor that must not change what is matched.
 */
@ExtendWith(MockitoExtension.class)
class MatchingDifferentialTest {

    private static final long SEED = 20261118L;
    private static final int SCENARIOS = 20_000;
    private static final int MIN_RULE_HITS = 50;

    @Mock
    private PimCatalog pimCatalog;
    @Mock
    private TaxonomyCache taxonomyCache;
    @Mock
    private SupplierRegistry supplierRegistry;

    @Test
    void groupingMatchesTheReferenceForEveryToggleCombination() {
        // given
        List<InventoryMatchingProperties> combinations = combinations();
        MatchingPoolGenerator generator = new MatchingPoolGenerator(SEED);

        for (int scenarioIndex = 0; scenarioIndex < SCENARIOS; scenarioIndex++) {
            MatchingPoolGenerator.Scenario scenario = generator.next();
            for (InventoryMatchingProperties properties : combinations) {
                String description = "scenario " + scenarioIndex + " with " + properties;

                // when
                Run reference = run(scenario, referenceGrouper(new ReferenceAutoDiscovery(pimCatalog, taxonomyCache,
                        supplierRegistry, properties)), null);
                Run actual = run(scenario, actualGrouper(new InventoryAutoDiscovery(pimCatalog, taxonomyCache,
                        supplierRegistry, properties)), null);

                // then
                assertThat(signaturesOf(actual.global().groups())).as(description)
                        .isEqualTo(signaturesOf(reference.global().groups()));
                assertThat(actual.global().variantCodes()).as(description).isEqualTo(reference.global().variantCodes());
                assertThat(signaturesOf(actual.own().groups())).as(description)
                        .isEqualTo(signaturesOf(reference.own().groups()));
                assertThat(actual.own().variantCodes()).as(description).isEqualTo(reference.own().variantCodes());
            }
        }
    }

    @Test
    void linksMatchTheReference() {
        // given
        List<InventoryMatchingProperties> combinations = combinations();
        MatchingPoolGenerator generator = new MatchingPoolGenerator(SEED);

        for (int scenarioIndex = 0; scenarioIndex < SCENARIOS; scenarioIndex++) {
            MatchingPoolGenerator.Scenario scenario = generator.next();
            for (InventoryMatchingProperties properties : combinations) {
                String description = "scenario " + scenarioIndex + " with " + properties;

                // when
                Run reference = run(scenario, referenceGrouper(new ReferenceAutoDiscovery(pimCatalog, taxonomyCache,
                        supplierRegistry, properties)), ReferenceOwnToGlobalLinks::apply);
                Run actual = run(scenario, actualGrouper(new InventoryAutoDiscovery(pimCatalog, taxonomyCache,
                        supplierRegistry, properties)), OwnToGlobalLinks::apply);

                // then
                assertThat(signaturesOf(actual.decisions())).as(description).isEqualTo(signaturesOf(reference.decisions()));
                assertThat(linkedEansByGroup(actual.ownIndex())).as(description)
                        .isEqualTo(linkedEansByGroup(reference.ownIndex()));
            }
        }
    }

    @Test
    void generatedPoolsExerciseEveryRule() {
        // given
        RuleHits hits = new RuleHits();
        MatchingPoolGenerator generator = new MatchingPoolGenerator(SEED);
        ReferenceAutoDiscovery reference = countingReference(hits);

        // when
        for (int scenarioIndex = 0; scenarioIndex < SCENARIOS; scenarioIndex++) {
            MatchingPoolGenerator.Scenario scenario = generator.next();
            Run run = run(scenario, referenceGrouper(reference), ReferenceOwnToGlobalLinks::apply);
            hits.countPools(scenario, offersOf(run.global().groups()));
            run.global().groups().forEach(hits::countPimId);
            run.own().groups().forEach(hits::countPimId);
            run.decisions().forEach(decision -> hits.count(decision.outcome().name()));
        }

        // then
        assertThat(hits.counts).allSatisfy((rule, count) -> assertThat(count)
                .as("%s; all hits: %s", rule, hits.counts)
                .isGreaterThanOrEqualTo(MIN_RULE_HITS));
    }

    private record Grouped(Collection<MatchedInventory> groups, VariantCodes variantCodes) {
    }

    private record Run(Grouped global, Grouped own, InventoryIndex ownIndex, List<Decision> decisions) {
    }

    @FunctionalInterface
    private interface Grouper {
        Grouped group(List<InventoryItem> offers, PimView pim, Supplier<? extends Collection<InventoryItem>> witnesses,
                      VariantCodes inherited, VariantCodes eanOnly);
    }

    @FunctionalInterface
    private interface Linker {
        List<Decision> apply(InventoryIndex ownIndex, GlobalMatchedInventory.Generation global, VariantCodes variantCodes);
    }

    private static Grouper referenceGrouper(ReferenceAutoDiscovery discovery) {
        return (offers, pim, witnesses, inherited, eanOnly) -> {
            ReferenceAutoDiscovery.Grouping grouping = discovery.group(offers, pim, witnesses, inherited, eanOnly);
            return new Grouped(grouping.groups(), grouping.variantCodes());
        };
    }

    private static Grouper actualGrouper(InventoryAutoDiscovery discovery) {
        return (offers, pim, witnesses, inherited, eanOnly) -> {
            InventoryAutoDiscovery.Grouping grouping = discovery.group(offers, pim, witnesses, inherited, eanOnly);
            return new Grouped(grouping.groups(), grouping.variantCodes());
        };
    }

    /** The global pool, then the store's own pool against it, then the links; every run on its own copies of the lists. */
    private static Run run(MatchingPoolGenerator.Scenario scenario, Grouper grouper, Linker linker) {
        Grouped global = grouper.group(new ArrayList<>(scenario.globalOffers()), pimViewOf(scenario.globalPim()),
                List::of, VariantCodes.none(), scenario.globalEanOnly());
        Grouped own = grouper.group(new ArrayList<>(scenario.ownOffers()), pimViewOf(scenario.storePim()),
                () -> offersOf(global.groups()), global.variantCodes(),
                scenario.globalEanOnly().plus(scenario.storeEanOnly()));
        if (linker == null) {
            return new Run(global, own, null, List.of());
        }
        InventoryIndex ownIndex = InventoryIndex.of(own.groups());
        List<MatchedInventory> globalGroups = new ArrayList<>(global.groups());
        GlobalMatchedInventory.Generation generation = new GlobalMatchedInventory.Generation(1, globalGroups,
                InventoryIndex.of(globalGroups), global.variantCodes(), scenario.globalEanOnly());
        List<Decision> decisions = linker.apply(ownIndex, generation, own.variantCodes());
        return new Run(global, own, ownIndex, decisions);
    }

    private static List<InventoryMatchingProperties> combinations() {
        List<InventoryMatchingProperties> combinations = new ArrayList<>();
        for (boolean excludeConflictingOffers : new boolean[]{true, false}) {
            for (boolean splitSingleCodeByPrice : new boolean[]{true, false}) {
                combinations.add(new InventoryMatchingProperties(excludeConflictingOffers, true, splitSingleCodeByPrice,
                        true, true, true));
            }
        }
        return combinations;
    }

    private static List<InventoryItem> offersOf(Collection<MatchedInventory> groups) {
        return groups.stream().flatMap(group -> group.getInventoryItems().stream()).toList();
    }

    private static PimView pimViewOf(List<PimEntry> entries) {
        return new PimView() {
            @Override
            public List<PimEntry> findAll() {
                return entries;
            }

            @Override
            public Optional<PimEntry> findByPimId(String pimId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<PimEntry> findByGtin(String gtin) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<PimEntry> findByMpnAndBrand(String mpn, String brand) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<PimEntry> findByGtinOrMpnAndBrand(String gtin, String mpn, String brand) {
                throw new UnsupportedOperationException();
            }
        };
    }

    // ---- signatures: independent of the order and identity of groups and offers ----

    private record GroupSignature(String pimId, List<String> eans, List<String> codes, List<String> offers) {
    }

    // a record compares its double components with Double.compare, so NaN equals NaN
    private record DecisionSignature(GroupSignature own, GroupSignature global, Outcome outcome,
                                     double ownAveragePrice, double globalAveragePrice) {
    }

    private static GroupSignature signatureOf(MatchedInventory group) {
        InventoryKey key = group.getInventoryKey();
        return new GroupSignature(key.getId(), sorted(key.getProductEans()), sorted(key.getProductCodes()),
                group.offersView().stream()
                        .map(offer -> offer.supplier() + "|" + offer.sku() + "|" + offer.ean() + "|" + offer.mfn()
                                + "|" + offer.brand() + "|" + offer.netPrice())
                        .sorted()
                        .toList());
    }

    private static Map<GroupSignature, Long> signaturesOf(Collection<MatchedInventory> groups) {
        return multiset(groups, MatchingDifferentialTest::signatureOf);
    }

    private static Map<DecisionSignature, Long> signaturesOf(List<Decision> decisions) {
        return multiset(decisions, decision -> new DecisionSignature(signatureOf(decision.own()),
                decision.global() == null ? null : signatureOf(decision.global()), decision.outcome(),
                decision.ownAveragePrice(), decision.globalAveragePrice()));
    }

    /** Every own group with the EANs it was linked to; groups with equal signatures keep one entry each. */
    private static Map<GroupSignature, List<List<String>>> linkedEansByGroup(InventoryIndex ownIndex) {
        return ownIndex.all().stream().collect(Collectors.groupingBy(MatchingDifferentialTest::signatureOf,
                Collectors.collectingAndThen(
                        Collectors.mapping(group -> sorted(group.getInventoryKey().getLinkedEans()), Collectors.toList()),
                        lists -> lists.stream().sorted((a, b) -> a.toString().compareTo(b.toString())).toList())));
    }

    private static <T, S> Map<S, Long> multiset(Collection<T> values, Function<T, S> signature) {
        return values.stream().collect(Collectors.groupingBy(signature, Collectors.counting()));
    }

    private static List<String> sorted(Collection<String> values) {
        return values.stream().sorted().toList();
    }

    // ---- rule hits, counted on the reference run with every toggle on ----

    private ReferenceAutoDiscovery countingReference(RuleHits hits) {
        return new ReferenceAutoDiscovery(pimCatalog, taxonomyCache, supplierRegistry, InventoryMatchingProperties.allEnabled()) {
            @Override
            public List<MatchedInventory> processMatchedInventoryCandidates(MatchedInventory candidate) {
                List<InventoryItem> offers = candidate.getInventoryItems();
                List<MatchedInventory> result = super.processMatchedInventoryCandidates(candidate);
                hits.countPriceControl(candidate, offers, result);
                return result;
            }
        };
    }

    private static final class RuleHits {

        private final Map<String, Integer> counts = new LinkedHashMap<>();

        RuleHits() {
            for (String rule : List.of("conflicting offers left out", "offers under a shared code",
                    "offers under an EAN-only pair", "groups split by SingleCodePriceSplit",
                    "groups changed by the mixed-code price control", "groups emptied by the price control",
                    "groups carrying a pimId")) {
                counts.put(rule, 0);
            }
            for (Outcome outcome : Outcome.values()) {
                counts.put(outcome.name(), 0);
            }
        }

        void count(String rule) {
            add(rule, 1);
        }

        void add(String rule, int hits) {
            counts.merge(rule, hits, Integer::sum);
        }

        void countPools(MatchingPoolGenerator.Scenario scenario, List<InventoryItem> globalWitnesses) {
            countPool(scenario.globalOffers(), List.of(), scenario.globalEanOnly());
            countPool(scenario.ownOffers(), globalWitnesses, scenario.globalEanOnly().plus(scenario.storeEanOnly()));
        }

        private void countPool(List<InventoryItem> offers, List<InventoryItem> witnesses, VariantCodes eanOnly) {
            SharedManufacturerCodes sharedCodes = SharedManufacturerCodes.of(offers);
            add("offers under a shared code", sharedCodes.offersUnderSharedCode().size());
            if (!offers.isEmpty()) {
                add("conflicting offers left out", ConflictingOffers.of(offers, witnesses, sharedCodes).size());
            }
            add("offers under an EAN-only pair",
                    (int) offers.stream().filter(offer -> eanOnly.contains(offer.brandKey(), offer.mfn())).count());
        }

        void countPimId(MatchedInventory group) {
            if (group.getInventoryKey().getId() != null) {
                count("groups carrying a pimId");
            }
        }

        void countPriceControl(MatchedInventory candidate, List<InventoryItem> offers, List<MatchedInventory> result) {
            if (!candidate.hasOffersFromMultipleSuppliers(2)) {
                return;
            }
            boolean singleCode = offers.stream().map(InventoryItem::mfn).distinct().count() == 1;
            boolean unchanged = result.size() == 1 && result.getFirst() == candidate;
            if (singleCode && SingleCodePriceSplit.byEan(offers).size() > 1) {
                count("groups split by SingleCodePriceSplit");
            } else if (!singleCode && !unchanged) {
                count("groups changed by the mixed-code price control");
            }
            add("groups emptied by the price control", (int) result.stream().filter(MatchedInventory::isEmpty).count());
        }
    }
}
