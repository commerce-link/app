package pl.commercelink.web.offers;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.baskets.OfferItem;
import pl.commercelink.baskets.OfferItemReloader;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.CategoryLocalizer;
import pl.commercelink.web.OfferController;
import pl.commercelink.web.offers.BasketViewPage.*;
import pl.commercelink.web.orders.AddressBlock;
import pl.commercelink.web.orders.ItemMargin;
import pl.commercelink.web.orders.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static pl.commercelink.invoicing.api.Price.DEFAULT_VAT_RATE;

@Component
public class BasketViewPageFactory {

    public static final String BASKETS_PATH = "/dashboard/offers?segment=baskets";

    private static final Logger log = LoggerFactory.getLogger(BasketViewPageFactory.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm");
    /** The header shows when the basket goes only in its last days; earlier the date in the Basket card is enough. */
    private static final int PILL_DAYS = 2;

    private final StoresRepository storesRepository;
    private final OfferItemReloader offerItemReloader;
    private final CategoryLocalizer categoryLocalizer;
    private final MessageSource messages;
    private final int cleanupDays;

    BasketViewPageFactory(StoresRepository storesRepository, OfferItemReloader offerItemReloader,
                          CategoryLocalizer categoryLocalizer, MessageSource messages,
                          @Value("${basket.cleanup.days:14}") int cleanupDays) {
        this.storesRepository = storesRepository;
        this.offerItemReloader = offerItemReloader;
        this.categoryLocalizer = categoryLocalizer;
        this.messages = messages;
        this.cleanupDays = cleanupDays;
    }

    public BasketViewPage build(Basket basket, String returnTo, Locale locale, LocalDateTime now) {
        String id = basket.getBasketId();
        Store store = storesRepository.findById(basket.getStoreId());
        List<BasketItem> items = basket.getEffectiveBasketItems();
        Map<BasketItem, OfferItem> market = market(basket);

        List<Row> products = new ArrayList<>();
        List<Row> services = new ArrayList<>();
        for (BasketItem item : items) {
            Row row = row(item, market == null ? null : market.get(item));
            (item.isService() ? services : products).add(row);
        }

        Summary summary = summary(basket, store, items, market);
        String back = backHref(returnTo);
        String query = returnTo == null || back.equals(BASKETS_PATH) ? "" : "?returnTo=" + URLEncoder.encode(back, StandardCharsets.UTF_8);
        LocalDate deletion = basket.getCreatedAt() == null ? null : basket.getCreatedAt().toLocalDate().plusDays(cleanupDays);
        long daysLeft = deletion == null ? Long.MAX_VALUE : ChronoUnit.DAYS.between(now.toLocalDate(), deletion);

        return new BasketViewPage(id, basket.getShortenedBasketId(), back,
                "/dashboard/basket/view/" + id + "/order" + query,
                "/dashboard/offer/new?intent=copy&sourceId=" + id,
                BasketClient.fullName(basket),
                basket.getCreatedAt() == null ? null : DATE_TIME.format(basket.getCreatedAt()),
                summary.total(), items.size(), pill(daysLeft), products, services, summary,
                customer(basket, locale),
                new Facts(basket.getCreatedAt() == null ? null : DATE_TIME.format(basket.getCreatedAt()),
                        basket.getSource() == null || basket.getSource().getType() == null ? null
                                : "order.source.type." + basket.getSource().getType().name(),
                        StringUtils.trimToNull(basket.getAffiliateId()),
                        basket.getFulfilmentType() == null ? null : "store.fulfilment.type." + basket.getFulfilmentType().name(),
                        deletionText(deletion, daysLeft, locale), cleanupDays));
    }

    /** The list's address when it is one (OfferController.safeReturnTo keeps it), else the baskets segment. */
    static String backHref(String returnTo) {
        if (returnTo == null) {
            return BASKETS_PATH;
        }
        String safe = OfferController.safeReturnTo(returnTo);
        return safe.equals(returnTo) ? returnTo : BASKETS_PATH;
    }

    /** Live supplier offers per item, never saved; null when the index cannot answer (the page still renders). */
    private Map<BasketItem, OfferItem> market(Basket basket) {
        try {
            Map<BasketItem, OfferItem> byItem = new IdentityHashMap<>();
            for (OfferItem offer : offerItemReloader.reload(basket)) {
                if (offer.getBasketItem() != null) {
                    byItem.put(offer.getBasketItem(), offer);
                }
            }
            return byItem;
        } catch (RuntimeException e) {
            log.warn("Supplier offers for basket {} (store {}) unavailable: {}", basket.getBasketId(), basket.getStoreId(), e.toString());
            return null;
        }
    }

    private Row row(BasketItem item, OfferItem offer) {
        String category = StringUtils.isBlank(item.getCategory()) ? null
                : StringUtils.trimToNull(categoryLocalizer.localize(item.getCategory(), null));
        if (item.isService()) {
            return new Row(item.getName(), category, StringUtils.trimToNull(item.getMfn()), item.getQty(),
                    Money.format(item.getUnitPrice()), true, null, null);
        }
        boolean known = offer != null && offer.isExistInInventory();
        Market market = known
                ? new Market(false, Money.format(offer.getLowestCost()), Money.format(offer.getMedianCost()),
                offer.getSupplierCount(), offer.getTotalQty())
                : Market.none();
        ItemMargin margin = ItemMargin.of(item.getUnitPrice(), known ? offer.getMedianCost() : 0, DEFAULT_VAT_RATE);
        return new Row(item.getName(), category, StringUtils.trimToNull(item.getMfn()), item.getQty(),
                Money.format(item.getUnitPrice()), false, margin, market);
    }

    private Summary summary(Basket basket, Store store, List<BasketItem> items, Map<BasketItem, OfferItem> market) {
        double itemsTotal = basket.getTotalPrice();
        DeliveryState state = DeliveryState.NONE;
        String deliveryName = null;
        double delivery = 0;
        if (basket.getDeliveryOptionId() != null) {
            try {
                Optional<DeliveryOption> option = store == null || store.getCheckoutConfiguration() == null
                        ? Optional.empty() : basket.resolveDeliveryOption(store);
                if (option.isPresent()) {
                    state = DeliveryState.CHOSEN;
                    deliveryName = option.get().getName();
                    delivery = option.get().getPrice();
                } else {
                    state = DeliveryState.UNKNOWN;
                }
            } catch (RuntimeException e) {
                // a removed delivery option must not take the page down; the list counts it as 0 too
                state = DeliveryState.UNKNOWN;
            }
        }
        double total = itemsTotal + delivery;
        return new Summary(Money.format(itemsTotal), state, deliveryName, Money.format(delivery), Money.format(total),
                Money.format(total / DEFAULT_VAT_RATE), Money.format(total - total / DEFAULT_VAT_RATE),
                estimate(items, market));
    }

    private Estimate estimate(List<BasketItem> items, Map<BasketItem, OfferItem> market) {
        List<BasketItem> products = items.stream().filter(i -> !i.isService()).toList();
        if (products.isEmpty()) {
            return new Estimate(EstimateState.NO_PRODUCTS, null, null, null, null, 0);
        }
        if (market == null) {
            return new Estimate(EstimateState.UNAVAILABLE, null, null, null, null, 0);
        }
        double revenue = 0;
        double cost = 0;
        int missing = 0;
        for (BasketItem item : products) {
            OfferItem offer = market.get(item);
            if (offer == null || !offer.isExistInInventory()) {
                missing++;
                continue;
            }
            revenue += item.getUnitPrice() * item.getQty();
            cost += offer.getMedianCost() * item.getQty();
        }
        if (missing == products.size()) {
            return new Estimate(EstimateState.NO_OFFERS, null, null, null, null, missing);
        }
        double profit = revenue - cost;
        String margin = revenue > 0 ? percent(profit / revenue * 100) : "—";
        return new Estimate(EstimateState.OK, Money.format(cost), Money.format(profit),
                Money.format(profit / DEFAULT_VAT_RATE), margin, missing);
    }

    private static String percent(double value) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pl-PL"));
        symbols.setDecimalSeparator(',');
        symbols.setMinusSign('−');
        return new DecimalFormat("0.0", symbols).format(BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP));
    }

    private Customer customer(Basket basket, Locale locale) {
        AddressBlock billing = AddressBlock.of(basket.getBillingDetails(), locale);
        AddressBlock shipping = AddressBlock.of(basket.getShippingDetails(), locale);
        boolean same = hasAny(billing) && hasAny(shipping)
                && Objects.equals(billing.name(), shipping.name()) && Objects.equals(billing.company(), shipping.company())
                && Objects.equals(billing.street(), shipping.street()) && Objects.equals(billing.cityLine(), shipping.cityLine())
                && Objects.equals(billing.country(), shipping.country());
        return new Customer(billing, shipping, same);
    }

    static boolean hasAny(AddressBlock b) {
        return b != null && (b.name() != null || b.company() != null || b.taxId() != null || b.street() != null
                || b.cityLine() != null || b.country() != null || b.email() != null || b.phone() != null);
    }

    private Pill pill(long daysLeft) {
        if (daysLeft > PILL_DAYS) {
            return null;
        }
        if (daysLeft <= 0) {
            return new Pill("offers.basket.pill.deletion.today", null);
        }
        return daysLeft == 1 ? new Pill("offers.basket.pill.deletion.tomorrow", null)
                : new Pill("offers.basket.pill.deletion.days", daysLeft);
    }

    private String deletionText(LocalDate deletion, long daysLeft, Locale locale) {
        if (deletion == null) {
            return null;
        }
        String date = DATE.format(deletion);
        if (daysLeft <= 0) {
            return messages.getMessage("offers.basket.facts.deletion.today", new Object[]{date}, locale);
        }
        if (daysLeft == 1) {
            return messages.getMessage("offers.basket.facts.deletion.tomorrow", new Object[]{date}, locale);
        }
        return messages.getMessage("offers.basket.facts.deletion.days", new Object[]{date, daysLeft}, locale);
    }
}
