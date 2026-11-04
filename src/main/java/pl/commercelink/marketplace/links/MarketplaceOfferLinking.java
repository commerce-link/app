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
import pl.commercelink.products.Product;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The operator's "Link to product" on an order item whose marketplace offer was not recognised: saves a MANUAL offer
 * link to the chosen catalog product's EAN, so later imports resolve the offer, and fills this item and the store's
 * other New items carrying the same offer key. Items already in fulfilment are left to the operator, as with
 * "Assign SKU"; the link is saved regardless.
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

    /**
     * Links the item's offer key to the EAN of one product of the store's catalogs, named by its category and product
     * id. Other products with the same EAN are the same goods to fulfilment, so the link needs nothing else.
     *
     * @throws IllegalArgumentException with a message key when the item or the product cannot be linked
     */
    public LinkResult link(String storeId, Order order, String itemId, String categoryId, String productId, String userId) {
        OrderItem chosen = orderItemsRepository.findById(order.getOrderId(), itemId);
        if (chosen == null || StringUtils.isBlank(chosen.getMarketplaceOfferKey())) {
            throw new IllegalArgumentException(NO_OFFER_KEY);
        }
        String marketplace = marketplaceOf(order);
        if (marketplace == null) {
            throw new IllegalArgumentException(NOT_A_MARKETPLACE_ORDER);
        }
        MarketplaceOfferResolver.StoreOffers storeOffers = resolver.forStore(storeId);
        Product product = storeOffers.catalogProducts().stream()
                .filter(candidate -> Objects.equals(candidate.getCategoryId(), categoryId)
                        && Objects.equals(candidate.getProductId(), productId))
                // same rule as the dialog's list: a link holds EANs, so a product without one cannot be linked
                .filter(candidate -> OfferEans.unify(candidate.getEan()) != null)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(PRODUCT_NOT_IN_CATALOG));
        String offerKey = chosen.getMarketplaceOfferKey();

        // an existing link for the key is replaced: the operator's choice wins over an earlier one
        linkRepository.save(MarketplaceOfferLink.of(storeId, marketplace, offerKey, List.of(product.getEan()),
                MarketplaceOfferLinkSource.MANUAL, userId, clock.instant()));

        // resolved through the saved link, so the item gets exactly what the next import would give it
        ResolvedOffer offer = storeOffers.resolve(marketplace, offerKey, null)
                .filter(resolved -> resolved.source() == ResolvedOffer.Source.LINK)
                .orElse(null);
        if (offer == null) {
            // only when the catalog or the link changed since the product was read, e.g. another operator linked meanwhile
            log.warn("Offer {} of store {} linked to EAN {} of product {}/{}, but the link no longer leads to one"
                    + " catalog product; no item filled", offerKey, storeId, product.getEan(), categoryId, productId);
            return new LinkResult(0);
        }
        if (StringUtils.isBlank(offer.category())) {
            log.warn("Offer {} of store {} linked to product {}/{}, whose category has no name; the items keep their"
                    + " category", offerKey, storeId, categoryId, productId);
        }

        int updated = 0;
        for (OrderItem item : itemsWithTheKey(storeId, order, marketplace, offerKey)) {
            if (!item.isNew() || item.isGroup()) {
                continue;
            }
            offer.fill(item);
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
