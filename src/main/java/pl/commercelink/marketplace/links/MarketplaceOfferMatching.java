package pl.commercelink.marketplace.links;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.marketplace.api.MarketplaceListedOffer;
import pl.commercelink.marketplace.api.MarketplaceOffers;
import pl.commercelink.marketplace.links.OfferMatchResult.Row;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimView;
import pl.commercelink.products.information.StorePimAliases;
import pl.commercelink.stores.Store;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Links a store's offers on a marketplace, published there by another system, to PIM entries by EAN. Each link is
 * saved as soon as its offer is matched, so a run cut short keeps what it linked and the next run skips those offers.
 * Existing links (manual or from an earlier run) are never replaced.
 */
@Component
public class MarketplaceOfferMatching {

    static final String CREATED_BY = "offer-matching";

    private final MarketplaceOfferLinkRepository linkRepository;
    private final MarketplaceOfferResolver resolver;
    private final PimCatalog pimCatalog;
    private final StorePimAliases pimAliases;
    private final Clock clock;

    @Autowired
    public MarketplaceOfferMatching(MarketplaceOfferLinkRepository linkRepository, MarketplaceOfferResolver resolver,
                                    PimCatalog pimCatalog, StorePimAliases pimAliases) {
        this(linkRepository, resolver, pimCatalog, pimAliases, Clock.systemUTC());
    }

    MarketplaceOfferMatching(MarketplaceOfferLinkRepository linkRepository, MarketplaceOfferResolver resolver,
                             PimCatalog pimCatalog, StorePimAliases pimAliases, Clock clock) {
        this.linkRepository = linkRepository;
        this.resolver = resolver;
        this.pimCatalog = pimCatalog;
        this.pimAliases = pimAliases;
        this.clock = clock;
    }

    public OfferMatchResult match(Store store, String marketplace, MarketplaceOffers offers) {
        Run run = new Run(store.getStoreId(), marketplace);
        offers.forEachPage(page -> page.forEach(run::match));
        return run.result();
    }

    private class Run {

        private final String storeId;
        private final String marketplace;
        private final PimView pim;
        private final MarketplaceOfferResolver.StoreOffers storeOffers;
        private final Set<String> linkedKeys = new HashSet<>();

        private int total;
        private int ownOffers;
        private int alreadyLinked;
        private final List<Row> linked = new ArrayList<>();
        private final List<Row> notInCatalog = new ArrayList<>();
        private final List<Row> noPimEntry = new ArrayList<>();
        private final List<Row> toReview = new ArrayList<>();

        Run(String storeId, String marketplace) {
            this.storeId = storeId;
            this.marketplace = marketplace;
            this.pim = pimCatalog.withStoreData(storeId);
            // one scope for the whole run: the store's catalogs are read once, not once per offer
            this.storeOffers = resolver.forStore(storeId);
            linkRepository.findAll(storeId, marketplace)
                    .forEach(link -> linkedKeys.add(link.getSellerOfferKey()));
        }

        void match(MarketplaceListedOffer offer) {
            total++;
            String key = offer.sellerOfferKey();
            List<String> eans = eansOf(offer);
            if (StringUtils.isBlank(key)) {
                toReview.add(row(offer, eans, null, OfferMatchResult.NO_KEY));
                return;
            }
            if (linkedKeys.contains(key)) {
                alreadyLinked++;
                return;
            }
            // the export publishes our offers under the pimId, and a product on a clone under its source's id
            if (pimAliases.effectiveEntry(storeId, key).isPresent()) {
                ownOffers++;
                return;
            }
            if (eans.isEmpty()) {
                toReview.add(row(offer, eans, null, OfferMatchResult.NO_EAN));
                return;
            }

            Set<String> pimIds = matchedEntries(eans);
            if (pimIds.isEmpty()) {
                noPimEntry.add(row(offer, eans, null, null));
            } else if (pimIds.size() > 1) {
                toReview.add(row(offer, eans, null, OfferMatchResult.AMBIGUOUS));
            } else {
                link(offer, key, eans, pimIds.iterator().next());
            }
        }

        private void link(MarketplaceListedOffer offer, String key, List<String> eans, String pimId) {
            boolean saved = linkRepository.saveIfAbsent(MarketplaceOfferLink.of(storeId, marketplace, key, pimId,
                    MarketplaceOfferLinkSource.EAN, CREATED_BY, clock.instant()));
            linkedKeys.add(key);
            if (!saved) {
                // linked by an operator after this run read the store's links
                alreadyLinked++;
                return;
            }
            // linked either way: a product missing from the catalog has no price yet, but the link waits for it
            Row row = row(offer, eans, pimId, null);
            if (storeOffers.isInStoreCatalog(pimId)) {
                linked.add(row);
            } else {
                notInCatalog.add(row);
            }
        }

        /** The entries the EANs hit, with the store's clone and its source counted as one entry (the source id). */
        private Set<String> matchedEntries(List<String> eans) {
            Set<String> pimIds = new LinkedHashSet<>();
            for (String ean : eans) {
                pim.findByGtin(ean).map(this::canonicalId).ifPresent(pimIds::add);
            }
            return pimIds;
        }

        private String canonicalId(PimEntry entry) {
            if (storeId.equals(entry.ownerStoreId()) && StringUtils.isNotBlank(entry.clonedFromPimId())) {
                return entry.clonedFromPimId();
            }
            return entry.pimId();
        }

        OfferMatchResult result() {
            return new OfferMatchResult(clock.instant(), total, List.copyOf(linked), List.copyOf(notInCatalog),
                    List.copyOf(noPimEntry), List.copyOf(toReview), ownOffers, alreadyLinked);
        }
    }

    private static List<String> eansOf(MarketplaceListedOffer offer) {
        if (offer.eans() == null) {
            return List.of();
        }
        return offer.eans().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(StringUtils::isNotEmpty)
                .distinct()
                .toList();
    }

    private static Row row(MarketplaceListedOffer offer, List<String> eans, String pimId, String reason) {
        return new Row(offer.sellerOfferKey(), offer.title(), eans, pimId, reason);
    }
}
