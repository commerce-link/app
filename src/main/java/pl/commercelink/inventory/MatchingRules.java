package pl.commercelink.inventory;

import java.util.List;

/**
 * Lists, in order, every rule of offer matching. To add a rule, implement one of four roles and list it here;
 * the order of a list is the order of execution.
 * <ul>
 * <li>{@link OfferFilter}: runs first in {@code InventoryAutoDiscovery.group}, on the whole pool, one filter after
 *     another on what the previous left. It may only reject offers, which then belong to no group.</li>
 * <li>{@link CodeJoinGuard}: asked per offer while the pool is indexed and joined; it may only say that an offer joins
 *     by EAN alone (its code neither pulls nor is pulled) and name brand and code pairs that refuse an own-to-global
 *     link. Several guards are united. It cannot reject or split offers.</li>
 * <li>{@link GroupRefiner}: runs on each finished group that has offers of at least two suppliers, in list order;
 *     the first that returns a result replaces the group with the offer sets it returns (it may drop offers).
 *     A group replaced this way is not refined again.</li>
 * <li>{@link LinkVeto}: asked in list order for each own-to-global link candidate that has a single target, in
 *     {@code OwnToGlobalLinks.evaluate}; the first that refuses names the outcome and ends the check.
 *     {@code VARIANT_CODE} and {@code SEVERAL_TARGETS} are decided before any veto, {@code SEVERAL_OWN_GROUPS} after
 *     all of them. A veto can only refuse, never change a group.</li>
 * </ul>
 * {@link #of} reads three switches of {@link InventoryMatchingProperties}: {@code excludeConflictingOffers} (lists
 * {@code ConflictingOfferFilter}), {@code splitSingleCodeByPrice} (lists {@code SingleCodePriceSplit}) and
 * {@code linkOwnToGlobalByCode} (whether own groups are linked to global ones at all).
 * <p>
 * Two things sit outside this chain. The per-supplier placeholder-EAN rule runs in the feed import
 * ({@code inventory/supplier/PlaceholderEans}), before any offer reaches matching. The switches
 * {@code pimEanOnlyFlag} and {@code pimStoreEanOnlyFlag} are read where the flagged pairs are computed and watched
 * ({@code Inventory}, {@code EanOnlyCodes}, {@code StoreEanOnlyCodes}, {@code EanOnlyCodesWatch},
 * {@code StoreEanOnlyCodesWatch}), and the pairs reach the chain as the {@code eanOnly} argument of {@code group}.
 */
final class MatchingRules {

    private final List<OfferFilter> offerFilters;
    private final List<GroupRefiner> groupRefiners;
    private final boolean linksOwnToGlobal;

    private MatchingRules(List<OfferFilter> offerFilters, List<GroupRefiner> groupRefiners, boolean linksOwnToGlobal) {
        this.offerFilters = offerFilters;
        this.groupRefiners = groupRefiners;
        this.linksOwnToGlobal = linksOwnToGlobal;
    }

    static MatchingRules of(InventoryMatchingProperties properties) {
        return new MatchingRules(
                properties.excludeConflictingOffers()
                        ? List.of(new ConflictingOfferFilter())
                        : List.of(),
                properties.splitSingleCodeByPrice()
                        ? List.of(new SingleCodePriceSplit(), new MixedCodePriceControl())
                        : List.of(new MixedCodePriceControl()),
                properties.linkOwnToGlobalByCode());
    }

    static MatchingRules standard() {
        return of(InventoryMatchingProperties.allEnabled());
    }

    List<OfferFilter> offerFilters() {
        return offerFilters;
    }

    /** Tried in order on each finished group of at least two suppliers; the first that changes the group wins. */
    List<GroupRefiner> groupRefiners() {
        return groupRefiners;
    }

    /** Whether own groups of a store are linked to global groups of the same brand and code at all. */
    boolean linksOwnToGlobal() {
        return linksOwnToGlobal;
    }

    /** Asked in order for each link candidate with a single target; the first that refuses names the outcome. */
    List<LinkVeto> linkVetos() {
        return List.of(new ConflictingPimIdsVeto(), new TargetSharesEanVeto(), new TargetHasOwnGroupVeto(), new PriceTooFarVeto());
    }

    CodeJoinGuard codeJoinGuard(SharedManufacturerCodes sharedCodes, VariantCodes inherited, VariantCodes eanOnly) {
        return CodeJoinGuard.anyOf(List.of(
                sharedCodes, CodeJoinGuard.linkOnly(inherited), new EanOnlyPairsGuard(eanOnly)));
    }
}
