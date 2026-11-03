package pl.commercelink.marketplace.links;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.marketplace.api.MarketplaceListedOffer;
import pl.commercelink.marketplace.api.MarketplaceOffers;
import pl.commercelink.marketplace.api.MarketplaceOffersPage;
import pl.commercelink.marketplace.api.MarketplaceRateLimitedException;
import pl.commercelink.marketplace.links.OfferMatchResult.Row;
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

/**
 * Links a store's offers on a marketplace, published there by another system, to their EANs, and reports what the
 * store's catalogs make of them. Every offer with an EAN gets its link, also when no catalog product has the EAN yet: an
 * order line finds the product through the link as soon as one is added. A link from an earlier run is replaced when
 * the offer's EANs changed; an operator's (MANUAL) link is never touched. Each link is saved as soon as its offer is
 * read, so a run cut short keeps what it linked.
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
    private final StorePimAliases pimAliases;
    private final Clock clock;

    @Autowired
    public MarketplaceOfferMatching(MarketplaceOfferLinkRepository linkRepository, MarketplaceOfferResolver resolver,
                                    StorePimAliases pimAliases) {
        this(linkRepository, resolver, pimAliases, Clock.systemUTC());
    }

    MarketplaceOfferMatching(MarketplaceOfferLinkRepository linkRepository, MarketplaceOfferResolver resolver,
                             StorePimAliases pimAliases, Clock clock) {
        this.linkRepository = linkRepository;
        this.resolver = resolver;
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
        private final MarketplaceOfferResolver.StoreOffers storeOffers;
        private final Map<String, MarketplaceOfferLink> existingLinks = new HashMap<>();
        private final Set<String> seenKeys;

        private int total;
        private Integer listed;
        private int idleRateLimitPauses;
        private int ownOffers;
        private int alreadyLinked;
        private final List<Row> linked;
        private final List<Row> notInCatalog;
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
            this.toReview = new ArrayList<>(progress.toReview());
            // one scope per message: the store's catalogs are read once, not once per offer or page
            this.storeOffers = resolver.forStore(storeId);
            linkRepository.findAll(storeId, marketplace)
                    .forEach(link -> existingLinks.put(link.getSellerOfferKey(), link));
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
                toReview.add(row(offer, eans, null, OfferMatchResult.NO_KEY, null));
                return;
            }
            // the export publishes our offers under the pimId, and a product on a clone under its source's id
            if (pimAliases.effectiveEntry(storeId, key).isPresent()) {
                ownOffers++;
                return;
            }
            MarketplaceOfferLink existing = existingLinks.get(key);
            if (existing != null && existing.getSource() == MarketplaceOfferLinkSource.MANUAL) {
                linkedByHand(offer, eans, existing);
                return;
            }
            MarketplaceOfferLink link = MarketplaceOfferLink.of(storeId, marketplace, key, eans,
                    MarketplaceOfferLinkSource.EAN, CREATED_BY, clock.instant());
            if (link.getEans().isEmpty()) {
                toReview.add(row(offer, eans, null, OfferMatchResult.NO_EAN, null));
                return;
            }
            // an unchanged link is not written again, so a rerun does not rewrite thousands of items
            boolean unchanged = existing != null && Set.copyOf(link.getEans()).equals(eanSet(existing));
            if (!unchanged && !linkRepository.saveUnlessManual(link)) {
                // linked by an operator after this run read the store's links
                linkRepository.find(storeId, marketplace, key).ifPresentOrElse(
                        stored -> linkedByHand(offer, eans, stored),
                        () -> alreadyLinked++);
                return;
            }
            classify(offer, eans, link.getEans());
        }

        private void classify(MarketplaceListedOffer offer, List<String> eans, List<String> linkEans) {
            switch (storeOffers.matchCatalog(linkEans)) {
                case CatalogMatch.Found found -> linked.add(row(offer, eans, found.product().getName(), null, null));
                case CatalogMatch.NotInCatalog none -> notInCatalog.add(row(offer, eans, null, null, linkEans.get(0)));
                case CatalogMatch.Ambiguous ambiguous -> toReview.add(row(offer, eans, null, OfferMatchResult.AMBIGUOUS, null));
                case CatalogMatch.NoManufacturerCode noCode -> toReview.add(row(offer, eans, null, OfferMatchResult.NO_MFN, null));
            }
        }

        /**
         * Counted, and listed while the link cannot work, so a rerun keeps the shop's "add to catalog" list. Nothing is
         * written: an operator's link is the operator's to change.
         */
        private void linkedByHand(MarketplaceListedOffer offer, List<String> eans, MarketplaceOfferLink link) {
            alreadyLinked++;
            List<String> linkEans = link.getEans() == null ? List.of() : link.getEans();
            switch (storeOffers.matchCatalog(linkEans)) {
                case CatalogMatch.Found found -> {
                    // a working link is only counted
                }
                case CatalogMatch.NotInCatalog none -> notInCatalog.add(row(offer, eans, null,
                        OfferMatchResult.ALREADY_LINKED, linkEans.isEmpty() ? null : linkEans.get(0)));
                case CatalogMatch.Ambiguous ambiguous -> toReview.add(row(offer, eans, null, OfferMatchResult.AMBIGUOUS, null));
                case CatalogMatch.NoManufacturerCode noCode -> toReview.add(row(offer, eans, null, OfferMatchResult.NO_MFN, null));
            }
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
                    notInCatalog, toReview, seenKeys, idleRateLimitPauses, null, null);
        }

        OfferMatchResult result() {
            return new OfferMatchResult(clock.instant(), total, List.copyOf(linked), List.copyOf(notInCatalog),
                    List.copyOf(toReview), ownOffers, alreadyLinked);
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

    private static Set<String> eanSet(MarketplaceOfferLink link) {
        return link.getEans() == null ? Set.of() : new HashSet<>(link.getEans());
    }

    private static Row row(MarketplaceListedOffer offer, List<String> eans, String product, String reason,
                           String catalogEan) {
        return new Row(offer.sellerOfferKey(), offer.title(), eans, product, reason, offer.active(), catalogEan);
    }
}
