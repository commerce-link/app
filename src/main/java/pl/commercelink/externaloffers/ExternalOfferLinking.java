package pl.commercelink.externaloffers;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.ArchiveIndexRequest;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.products.Product;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The operator's "Link to product" on an order item whose marketplace offer was not recognised, and "Change link" on
 * one whose link is wrong: saves a MANUAL offer link to an EAN, either a catalog product's or one typed in (the goods
 * need not be in a catalog, fulfilment finds them by EAN), so later imports resolve the offer. The chosen item gets
 * exactly what the next import would give it (the EAN, the category the taxonomy gives it, no manufacturer code). The
 * store's other New items with the same offer key in open orders of the last 90 days get the same, but only those
 * still unrecognised or carrying the codes of the link being replaced: a code assigned by hand is the operator's and
 * stays. Items already in fulfilment are left to the operator, as with "Assign SKU"; the link is saved regardless.
 * Neither action starts automatic fulfilment, which only the import does.
 */
@Slf4j
@Component
public class ExternalOfferLinking {

    public static final String NO_OFFER_KEY = "order.item.link.offer.no.key";
    public static final String NOT_A_MARKETPLACE_ORDER = "order.item.link.offer.not.marketplace";
    public static final String PRODUCT_NOT_IN_CATALOG = "order.item.link.offer.product.unknown";
    public static final String INVALID_EAN = "order.item.link.offer.ean.invalid";
    public static final String OWN_OFFER = "order.item.link.offer.own";

    // EAN-8, UPC-A, EAN-13 and GTIN-14 with a valid check digit; the code is not looked up anywhere
    private static final Pattern EAN = Pattern.compile("[0-9]{8,14}");

    // any space, including the non-breaking ones a spreadsheet pastes
    private static final Pattern SPACES = Pattern.compile("[\\s\\p{Z}]+");

    // reads the other orders' items a few at a time instead of one query after another
    private static final int ITEM_READ_THREADS = 4;

    // bounds the sweep over other orders (plan decision); an older open order keeps its item for a manual fix
    static final int SWEEP_DAYS = 90;

    private static final Set<OrderStatus> OPEN = Arrays.stream(OrderStatus.values())
            .filter(status -> status != OrderStatus.Completed && status != OrderStatus.Cancelled)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(OrderStatus.class)));

    // DynamoDB page of the date index; the window is read to its end either way
    private static final int INDEX_PAGE = 500;

    private final ExternalOfferLinkRepository linkRepository;
    private final ExternalOfferResolver resolver;
    private final OrdersRepository ordersRepository;
    private final OrderItemsRepository orderItemsRepository;
    private final Clock clock;
    private final ExecutorService itemReads = Executors.newFixedThreadPool(ITEM_READ_THREADS, runnable -> {
        Thread thread = new Thread(runnable, "offer-link-item-read");
        thread.setDaemon(true);
        return thread;
    });

    @Autowired
    public ExternalOfferLinking(ExternalOfferLinkRepository linkRepository, ExternalOfferResolver resolver,
                                   OrdersRepository ordersRepository, OrderItemsRepository orderItemsRepository) {
        this(linkRepository, resolver, ordersRepository, orderItemsRepository, Clock.systemDefaultZone());
    }

    ExternalOfferLinking(ExternalOfferLinkRepository linkRepository, ExternalOfferResolver resolver,
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
        OrderItem chosen = linkableItem(storeId, order, itemId);
        Product product = resolver.forStore(storeId).catalogProduct(categoryId, productId)
                // same rule as the dialog's list: a link holds EANs, and the import skips a value without the format
                // of one, so a product without a valid EAN cannot be linked
                .filter(candidate -> OfferEans.unifyValid(candidate.getEan()) != null)
                .orElseThrow(() -> new IllegalArgumentException(PRODUCT_NOT_IN_CATALOG));
        return save(storeId, order, chosen, OfferEans.unifyValid(product.getEan()), userId);
    }

    /**
     * Links the item's offer key to an EAN the operator typed, for goods that are in no catalog.
     *
     * @throws IllegalArgumentException with a message key when the item cannot be linked or the EAN is malformed
     */
    public LinkResult linkEan(String storeId, Order order, String itemId, String ean, String userId) {
        OrderItem chosen = linkableItem(storeId, order, itemId);
        String typed = SPACES.matcher(StringUtils.defaultString(ean)).replaceAll("");
        if (!EAN.matcher(typed).matches() || !hasValidCheckDigit(typed)) {
            throw new IllegalArgumentException(INVALID_EAN);
        }
        return save(storeId, order, chosen, OfferEans.unify(typed), userId);
    }

    /** The GTIN check digit: a typo in one digit, or most swaps of two neighbours, gives another last digit. */
    static boolean hasValidCheckDigit(String digits) {
        int sum = 0;
        for (int i = digits.length() - 2, weight = 3; i >= 0; i--, weight = 4 - weight) {
            sum += (digits.charAt(i) - '0') * weight;
        }
        return (10 - sum % 10) % 10 == digits.charAt(digits.length() - 1) - '0';
    }

    private OrderItem linkableItem(String storeId, Order order, String itemId) {
        OrderItem chosen = orderItemsRepository.findById(order.getOrderId(), itemId);
        if (chosen == null || StringUtils.isBlank(chosen.getExternalOfferKey())) {
            throw new IllegalArgumentException(NO_OFFER_KEY);
        }
        // an offer CommerceLink published is recognised by its pimId and never linked
        if (resolver.isOwnOffer(storeId, chosen.getExternalOfferKey())) {
            throw new IllegalArgumentException(OWN_OFFER);
        }
        if (marketplaceOf(order) == null) {
            throw new IllegalArgumentException(NOT_A_MARKETPLACE_ORDER);
        }
        return chosen;
    }

    private LinkResult save(String storeId, Order order, OrderItem chosen, String ean, String userId) {
        String orderSource = marketplaceOf(order);
        String offerKey = chosen.getExternalOfferKey();
        // what the replaced link gave the lines, so items it filled are corrected and nothing else is touched
        String replacedSku = linkRepository.find(storeId, orderSource, offerKey)
                .map(previous -> OfferEans.validUnified(previous.getEans()))
                .filter(eans -> !eans.isEmpty())
                .map(eans -> ResolvedOffer.linked(eans, null).sku())
                .orElse(null);
        // an existing link for the key is replaced: the operator's choice wins over an earlier one
        linkRepository.save(ExternalOfferLink.of(storeId, orderSource, offerKey, List.of(ean),
                ExternalOfferLinkSource.MANUAL, userId, clock.instant()));

        // what the next import gives a line of this offer, so filled items look like newly imported ones
        ResolvedOffer offer = resolver.forStore(storeId).linked(List.of(ean));
        int updated = 0;
        for (OrderItem item : itemsWithTheKey(storeId, order, orderSource, offerKey)) {
            if (!item.isNew() || item.isGroup()) {
                continue;
            }
            boolean isChosen = item.getItemId().equals(chosen.getItemId());
            // a link outranks a manufacturer code in the resolver, but only a line nobody categorised or re-skued since
            // is re-filled, as before the source was stored
            boolean recognisedByManufacturerCode = item.getExternalOfferSource() == ResolvedOffer.Source.MFN
                    && item.looksUnlinkedByCode();
            if (!isChosen && !item.hasUnlinkedExternalOffer() && !recognisedByManufacturerCode
                    && (replacedSku == null || !replacedSku.equals(item.getSku()))) {
                continue;
            }
            List<Object> before = valuesOf(item);
            offer.replace(item);
            // a repeated link changes nothing, and the count says what was filled
            if (before.equals(valuesOf(item))) {
                continue;
            }
            if (isChosen) {
                orderItemsRepository.save(item);
                updated++;
            } else if (saveOther(item)) {
                updated++;
            }
        }
        return new LinkResult(updated);
    }

    // the source is compared too: an item stored before the source existed may already carry the link's codes
    private static List<Object> valuesOf(OrderItem item) {
        return Arrays.asList(item.getSku(), item.getEan(), item.getManufacturerCode(), item.getCategory(),
                item.getExternalOfferSource());
    }

    private static String marketplaceOf(Order order) {
        if (order.getSource() == null || order.getSource().getType() != OrderSourceType.Marketplace) {
            return null;
        }
        return StringUtils.trimToNull(order.getSource().getName());
    }

    /**
     * Open orders of the sweep window read through StoreIdOrderedAtIndex, so a store's long history of open orders is
     * not loaded for a link; narrowed to the marketplace here. The order on screen is always included, however old.
     * Their items are read a few orders at a time, inside the operator's request.
     */
    private List<OrderItem> itemsWithTheKey(String storeId, Order order, String orderSource, String offerKey) {
        LocalDateTime since = LocalDateTime.now(clock).minusDays(SWEEP_DAYS);
        LocalDate today = LocalDate.now(clock);
        List<String> windowIds = new ArrayList<>();
        ordersRepository.archiveIndex(storeId, new ArchiveIndexRequest(OPEN, since.toLocalDate(), today, null, false,
                INDEX_PAGE)).forEachRemaining(entry -> windowIds.add(entry.orderId()));
        Set<String> orderIds = new LinkedHashSet<>();
        orderIds.add(order.getOrderId());
        ordersRepository.findByIds(storeId, windowIds).stream()
                .filter(candidate -> !candidate.isClosed())
                .filter(candidate -> orderSource.equals(marketplaceOf(candidate)))
                .filter(candidate -> candidate.getOrderedAt() != null && !candidate.getOrderedAt().isBefore(since))
                .map(Order::getOrderId)
                .forEach(orderIds::add);
        return itemsOf(new ArrayList<>(orderIds)).stream()
                .filter(item -> offerKey.equals(item.getExternalOfferKey()))
                .toList();
    }

    private List<OrderItem> itemsOf(List<String> orderIds) {
        int chunk = Math.max(1, (orderIds.size() + ITEM_READ_THREADS - 1) / ITEM_READ_THREADS);
        List<CompletableFuture<Map<String, List<OrderItem>>>> reads = new ArrayList<>();
        for (int from = 0; from < orderIds.size(); from += chunk) {
            List<String> part = orderIds.subList(from, Math.min(orderIds.size(), from + chunk));
            reads.add(CompletableFuture.supplyAsync(() -> orderItemsRepository.findByOrderIds(part), itemReads));
        }
        return reads.stream()
                .map(CompletableFuture::join)
                .flatMap(found -> found.values().stream())
                .flatMap(List::stream)
                .toList();
    }

    @PreDestroy
    void shutdown() {
        itemReads.shutdown();
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
