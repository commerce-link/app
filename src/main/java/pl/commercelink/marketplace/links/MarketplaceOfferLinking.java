package pl.commercelink.marketplace.links;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.taxonomy.UnifiedProductIdentifiers;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The operator's "Link to product" on an order item whose marketplace offer was not recognised: saves a MANUAL offer
 * link, so later imports resolve the offer, and fills this item and the store's other New items carrying the same offer
 * key. Items already in fulfilment are left to the operator, as with "Assign SKU"; the link is saved regardless.
 */
@Slf4j
@Component
public class MarketplaceOfferLinking {

    public static final String NO_OFFER_KEY = "order.item.link.offer.no.key";
    public static final String NOT_A_MARKETPLACE_ORDER = "order.item.link.offer.not.marketplace";
    public static final String PRODUCT_NOT_IN_CATALOG = "order.item.link.offer.product.unknown";

    // bounds the sweep over other orders (plan decision); an older open order keeps its item for a manual fix
    static final int SWEEP_DAYS = 90;

    private static final List<OrderStatus> OPEN = Arrays.stream(OrderStatus.values())
            .filter(status -> status != OrderStatus.Completed && status != OrderStatus.Cancelled)
            .toList();

    private final MarketplaceOfferLinkRepository linkRepository;
    private final MarketplaceOfferResolver resolver;
    private final OrdersRepository ordersRepository;
    private final OrderItemsRepository orderItemsRepository;
    private final Clock clock;

    @Autowired
    public MarketplaceOfferLinking(MarketplaceOfferLinkRepository linkRepository, MarketplaceOfferResolver resolver,
                                   OrdersRepository ordersRepository, OrderItemsRepository orderItemsRepository) {
        this(linkRepository, resolver, ordersRepository, orderItemsRepository, Clock.systemDefaultZone());
    }

    MarketplaceOfferLinking(MarketplaceOfferLinkRepository linkRepository, MarketplaceOfferResolver resolver,
                            OrdersRepository ordersRepository, OrderItemsRepository orderItemsRepository, Clock clock) {
        this.linkRepository = linkRepository;
        this.resolver = resolver;
        this.ordersRepository = ordersRepository;
        this.orderItemsRepository = orderItemsRepository;
        this.clock = clock;
    }

    /** @throws IllegalArgumentException with a message key when the item or the product cannot be linked */
    public LinkResult link(String storeId, Order order, String itemId, String pimId, String userId) {
        OrderItem chosen = orderItemsRepository.findById(order.getOrderId(), itemId);
        if (chosen == null || StringUtils.isBlank(chosen.getMarketplaceOfferKey())) {
            throw new IllegalArgumentException(NO_OFFER_KEY);
        }
        String marketplace = marketplaceOf(order);
        if (marketplace == null) {
            throw new IllegalArgumentException(NOT_A_MARKETPLACE_ORDER);
        }
        if (StringUtils.isBlank(pimId) || !resolver.isInStoreCatalog(storeId, pimId)) {
            throw new IllegalArgumentException(PRODUCT_NOT_IN_CATALOG);
        }
        String offerKey = chosen.getMarketplaceOfferKey();

        // an existing link for the key is replaced: the operator's choice wins over an earlier one
        linkRepository.save(MarketplaceOfferLink.of(storeId, marketplace, offerKey, pimId,
                MarketplaceOfferLinkSource.MANUAL, userId, clock.instant()));

        // resolved through the saved link, so the item gets exactly what the next import would give it
        ResolvedOffer offer = resolver.resolve(storeId, marketplace, offerKey, null).orElse(null);
        if (offer == null) {
            log.warn("Offer {} of store {} linked to {}, but the product is not in the PIM view; no item filled",
                    offerKey, storeId, pimId);
            return new LinkResult(0);
        }

        int updated = 0;
        for (OrderItem item : itemsWithTheKey(storeId, order, marketplace, offerKey)) {
            if (!item.isNew() || item.isGroup()) {
                continue;
            }
            fill(item, offer);
            if (item.getItemId().equals(chosen.getItemId())) {
                orderItemsRepository.save(item);
                updated++;
            } else if (saveOther(item)) {
                updated++;
            }
        }
        return new LinkResult(updated);
    }

    private static String marketplaceOf(Order order) {
        if (order.getSource() == null || order.getSource().getType() != OrderSourceType.Marketplace) {
            return null;
        }
        return StringUtils.trimToNull(order.getSource().getName());
    }

    /**
     * Open orders read through StoreIdStatusIndex (the orders list's read), narrowed to the marketplace and the sweep
     * window here; the order on screen is always included, however old.
     */
    private List<OrderItem> itemsWithTheKey(String storeId, Order order, String marketplace, String offerKey) {
        LocalDateTime since = LocalDateTime.now(clock).minusDays(SWEEP_DAYS);
        Set<String> orderIds = new LinkedHashSet<>();
        orderIds.add(order.getOrderId());
        ordersRepository.findByStoreAndStatuses(storeId, OPEN).stream()
                .filter(candidate -> !candidate.isClosed())
                .filter(candidate -> marketplace.equals(marketplaceOf(candidate)))
                .filter(candidate -> candidate.getOrderedAt() != null && !candidate.getOrderedAt().isBefore(since))
                .map(Order::getOrderId)
                .forEach(orderIds::add);
        List<OrderItem> items = new ArrayList<>();
        orderItemsRepository.findByOrderIds(new ArrayList<>(orderIds)).values().stream()
                .flatMap(List::stream)
                .filter(item -> offerKey.equals(item.getMarketplaceOfferKey()))
                .forEach(items::add);
        return items;
    }

    /** As the importer fills a resolved line; externalItemId stays the marketplace's key (returns match on it). */
    private static void fill(OrderItem item, ResolvedOffer offer) {
        if (StringUtils.isNotBlank(offer.mfn())) {
            item.setSku(UnifiedProductIdentifiers.unifyMfn(offer.mfn()));
            item.setManufacturerCode(offer.mfn());
        }
        if (StringUtils.isNotBlank(offer.ean())) {
            item.setEan(offer.ean());
        }
        if (StringUtils.isNotBlank(offer.category())) {
            item.setCategory(offer.category());
        }
    }

    // another order's item changed meanwhile: it is skipped rather than failing the operator's link
    private boolean saveOther(OrderItem item) {
        try {
            orderItemsRepository.save(item);
            return true;
        } catch (ConditionalCheckFailedException e) {
            log.warn("Order item {} of order {} changed while linking its offer; left as it is", item.getItemId(),
                    item.getOrderId());
            return false;
        }
    }

    public record LinkResult(int updatedItems) {
    }
}
