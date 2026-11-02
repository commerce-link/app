package pl.commercelink.marketplace.links;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.marketplace.api.MarketplaceListedOffer;
import pl.commercelink.marketplace.api.MarketplaceOffers;
import pl.commercelink.marketplace.api.MarketplaceOffersPage;
import pl.commercelink.marketplace.api.MarketplaceRateLimitedException;
import pl.commercelink.marketplace.links.OfferMatchResult.Row;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimView;
import pl.commercelink.products.Product;
import pl.commercelink.products.information.StorePimAliases;
import pl.commercelink.stores.Store;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static pl.commercelink.taxonomy.UnifiedProductIdentifiers.unifyEan;

/**
 * Links a store's offers on a marketplace, published there by another system, to PIM entries by EAN. Each link is
 * saved as soon as its offer is matched, so a run cut short keeps what it linked and the next run skips those offers.
 * Existing links (manual or from an earlier run) are never replaced.
 * <p>
 * A run goes page by page from where its {@link OfferMatchProgress} stopped, and pauses when the marketplace limits
 * requests (Empik answers about ten pages a minute) or the message's time budget is used, so the caller can carry on in
 * another queue message instead of waiting in this one.
 */
@Component
public class MarketplaceOfferMatching {

    static final String CREATED_BY = "offer-matching";
    /** Well under the queue's 900 s visibility timeout, so a message is done before SQS hands it out again. */
    static final Duration TIME_BUDGET = Duration.ofMinutes(10);

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

    /**
     * Reads pages from the progress's cursor until the listing ends, the marketplace asks to wait, or the time budget is
     * used. Any other failure propagates; the links saved before it stay.
     */
    public Step resume(Store store, String marketplace, MarketplaceOffers offers, OfferMatchProgress progress) {
        Instant deadline = clock.instant().plus(TIME_BUDGET);
        Run run = new Run(store.getStoreId(), marketplace, progress);
        String cursor = progress.nextCursor();
        while (true) {
            MarketplaceOffersPage page;
            try {
                page = offers.page(cursor);
            } catch (MarketplaceRateLimitedException e) {
                return new Paused(run.rateLimited(cursor), e.retryAfter() == null ? Duration.ZERO : e.retryAfter());
            }
            run.pageRead();
            if (page.offers() != null) {
                page.offers().forEach(run::match);
            }
            run.total(page.total());
            if (page.nextCursor() == null) {
                return new Finished(run.result());
            }
            if (page.nextCursor().equals(cursor)) {
                // a cursor that does not move would page forever, message after message
                throw new IllegalStateException("The marketplace listing returned the same cursor again: " + cursor);
            }
            cursor = page.nextCursor();
            if (!clock.instant().isBefore(deadline)) {
                return new Paused(run.progress(cursor), Duration.ZERO);
            }
        }
    }

    /** What one queue message got done: the whole run, or a part of it with the rest to come in another message. */
    public sealed interface Step permits Finished, Paused {
    }

    public record Finished(OfferMatchResult result) implements Step {
    }

    /** @param delay how long the marketplace wants the next page to wait (zero: the message used its time budget) */
    public record Paused(OfferMatchProgress progress, Duration delay) implements Step {
    }

    private class Run {

        private final String storeId;
        private final String marketplace;
        private final OfferMatchProgress started;
        private final PimView pim;
        private final PimView globalPim;
        private final MarketplaceOfferResolver.StoreOffers storeOffers;
        private final Map<String, String> linkedPimIds = new HashMap<>();
        private final Set<String> seenKeys;
        private Map<String, Set<String>> catalogEntriesByEan;

        private int total;
        private Integer listed;
        private int idleRateLimitPauses;
        private int ownOffers;
        private int alreadyLinked;
        private final List<Row> linked;
        private final List<Row> notInCatalog;
        private final List<Row> noPimEntry;
        private final List<Row> toReview;

        Run(String storeId, String marketplace, OfferMatchProgress progress) {
            this.storeId = storeId;
            this.marketplace = marketplace;
            this.started = progress;
            this.seenKeys = new LinkedHashSet<>(progress.seenKeys());
            this.total = progress.processed();
            this.listed = progress.total();
            this.idleRateLimitPauses = progress.idleRateLimitPauses();
            this.ownOffers = progress.ownOffers();
            this.alreadyLinked = progress.alreadyLinked();
            this.linked = new ArrayList<>(progress.linked());
            this.notInCatalog = new ArrayList<>(progress.notInCatalog());
            this.noPimEntry = new ArrayList<>(progress.noPimEntry());
            this.toReview = new ArrayList<>(progress.toReview());
            this.pim = pimCatalog.withStoreData(storeId);
            this.globalPim = pimCatalog.withGlobalData();
            // one scope per message: the store's catalogs are read once, not once per offer or page
            this.storeOffers = resolver.forStore(storeId);
            linkRepository.findAll(storeId, marketplace)
                    .forEach(link -> linkedPimIds.put(link.getSellerOfferKey(), link.getPimId()));
        }

        void match(MarketplaceListedOffer offer) {
            String key = offer.sellerOfferKey();
            // the listing can shift between pages while it is read; the offer's first appearance counts
            if (StringUtils.isNotBlank(key) && !seenKeys.add(key)) {
                return;
            }
            total++;
            List<String> eans = eansOf(offer);
            if (StringUtils.isBlank(key)) {
                toReview.add(row(offer, eans, null, OfferMatchResult.NO_KEY));
                return;
            }
            if (linkedPimIds.containsKey(key)) {
                alreadyLinked(offer, eans, linkedPimIds.get(key));
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
            if (!saved) {
                // linked by an operator after this run read the store's links
                linkRepository.find(storeId, marketplace, key).ifPresentOrElse(
                        existing -> alreadyLinked(offer, eans, existing.getPimId()),
                        () -> alreadyLinked++);
                return;
            }
            // linked either way: a product missing from the catalog has no price yet, but the link waits for it
            if (storeOffers.isInStoreCatalog(pimId)) {
                linked.add(row(offer, eans, pimId, null));
            } else {
                notInCatalog.add(row(offer, eans, pimId, null, primaryGtin(pimId)));
            }
        }

        /**
         * Counted, and listed while the link cannot work yet, so a rerun keeps the shop's "add to catalog" list. Nothing
         * is written: an existing link, even a stale one, is the operator's to change.
         */
        private void alreadyLinked(MarketplaceListedOffer offer, List<String> eans, String pimId) {
            alreadyLinked++;
            if (pimAliases.effectiveEntry(storeId, pimId).isEmpty()) {
                toReview.add(row(offer, eans, pimId, OfferMatchResult.STALE_LINK));
            } else if (!storeOffers.isInStoreCatalog(pimId)) {
                notInCatalog.add(row(offer, eans, pimId, OfferMatchResult.ALREADY_LINKED, primaryGtin(pimId)));
            }
        }

        /** The entries the EANs hit, with the store's clone and its source counted as one entry (the source id). */
        private Set<String> matchedEntries(List<String> eans) {
            Set<String> pimIds = new LinkedHashSet<>();
            for (String ean : eans) {
                pimIds.addAll(entriesOf(ean));
            }
            return pimIds;
        }

        /**
         * A product of the store's catalogs decides what its EAN means: the store view's lookup prefers the store's own
         * card, which may share the EAN with the entry the catalog uses, and a link to that card would leave the product
         * exported next to the foreign offer. Outside the catalog both views are asked, so a store card and a global card
         * with one EAN make the offer ambiguous rather than picking one of them.
         */
        private Set<String> entriesOf(String ean) {
            Set<String> inCatalog = catalogEntriesByEan().getOrDefault(unifyEan(ean), Set.of());
            if (!inCatalog.isEmpty()) {
                return inCatalog;
            }
            Set<String> pimIds = new LinkedHashSet<>();
            pim.findByGtin(ean).map(this::canonicalId).ifPresent(pimIds::add);
            globalPim.findByGtin(ean).map(this::canonicalId).ifPresent(pimIds::add);
            return pimIds;
        }

        // built on the first EAN lookup from the scope's single catalog read; keyed by the product's EAN and its entry's
        private Map<String, Set<String>> catalogEntriesByEan() {
            if (catalogEntriesByEan == null) {
                Map<String, Set<String>> index = new HashMap<>();
                for (Product product : storeOffers.catalogProducts()) {
                    String pimId = pimAliases.offerIdOf(storeId, product.getPimId());
                    Set<String> gtins = new HashSet<>();
                    if (StringUtils.isNotBlank(product.getEan())) {
                        gtins.add(product.getEan());
                    }
                    pimAliases.effectiveEntry(storeId, product.getPimId()).map(PimEntry::gtins).orElse(List.of()).stream()
                            .filter(StringUtils::isNotBlank)
                            .map(gtin -> unifyEan(gtin))
                            .forEach(gtins::add);
                    gtins.forEach(gtin -> index.computeIfAbsent(gtin, key -> new LinkedHashSet<>()).add(pimId));
                }
                catalogEntriesByEan = index;
            }
            return catalogEntriesByEan;
        }

        private String primaryGtin(String pimId) {
            return pimAliases.effectiveEntry(storeId, pimId).map(PimEntry::gtins)
                    .flatMap(gtins -> gtins.stream().filter(StringUtils::isNotBlank).findFirst()).orElse(null);
        }

        private String canonicalId(PimEntry entry) {
            if (storeId.equals(entry.ownerStoreId()) && StringUtils.isNotBlank(entry.clonedFromPimId())) {
                return entry.clonedFromPimId();
            }
            return entry.pimId();
        }

        void total(Integer total) {
            if (total != null) {
                listed = total;
            }
        }

        void pageRead() {
            idleRateLimitPauses = 0;
        }

        /** A pause on the request limit; one before any page of this message read counts as a pause without progress. */
        OfferMatchProgress rateLimited(String nextCursor) {
            if (Objects.equals(nextCursor, started.nextCursor())) {
                idleRateLimitPauses++;
            }
            return progress(nextCursor);
        }

        OfferMatchProgress progress(String nextCursor) {
            return new OfferMatchProgress(started.runId(), started.startedAt(), clock.instant(),
                    OfferMatchProgress.Status.RUNNING, nextCursor, total, listed, ownOffers, alreadyLinked, linked,
                    notInCatalog, noPimEntry, toReview, seenKeys, idleRateLimitPauses, null, null);
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
        return row(offer, eans, pimId, reason, null);
    }

    private static Row row(MarketplaceListedOffer offer, List<String> eans, String pimId, String reason,
                           String catalogEan) {
        return new Row(offer.sellerOfferKey(), offer.title(), eans, pimId, reason, offer.active(), catalogEan);
    }
}
