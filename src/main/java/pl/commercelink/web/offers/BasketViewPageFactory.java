package pl.commercelink.web.offers;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.CategoryLocalizer;
import pl.commercelink.web.OfferController;
import pl.commercelink.web.offers.BasketViewPage.*;
import pl.commercelink.web.orders.AddressBlock;
import pl.commercelink.web.orders.Money;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    private final CategoryLocalizer categoryLocalizer;
    private final MessageSource messages;
    private final int cleanupDays;

    BasketViewPageFactory(StoresRepository storesRepository, CategoryLocalizer categoryLocalizer, MessageSource messages,
                          @Value("${basket.cleanup.days:14}") int cleanupDays) {
        this.storesRepository = storesRepository;
        this.categoryLocalizer = categoryLocalizer;
        this.messages = messages;
        this.cleanupDays = cleanupDays;
    }

    public BasketViewPage build(Basket basket, String returnTo, Locale locale, LocalDateTime now) {
        String id = basket.getBasketId();
        Store store = storesRepository.findById(basket.getStoreId());
        List<BasketItem> items = basket.getEffectiveBasketItems();

        List<Row> products = new ArrayList<>();
        List<Row> services = new ArrayList<>();
        for (BasketItem item : items) {
            (item.isService() ? services : products).add(row(item));
        }

        Summary summary = summary(basket, store);
        String back = backHref(returnTo);
        LocalDate deletion = basket.getCreatedAt() == null ? null : basket.getCreatedAt().toLocalDate().plusDays(cleanupDays);
        long daysLeft = deletion == null ? Long.MAX_VALUE : ChronoUnit.DAYS.between(now.toLocalDate(), deletion);

        return new BasketViewPage(id, basket.getShortenedBasketId(), back,
                "/dashboard/basket/view/" + id + "/order" + returnToQuery(returnTo),
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

    /** The one place a user-supplied return address is put back into a query string; empty for the default list and for anything unsafe. */
    static String returnToQuery(String returnTo) {
        String back = backHref(returnTo);
        return back.equals(BASKETS_PATH) ? "" : "?returnTo=" + URLEncoder.encode(back, StandardCharsets.UTF_8);
    }

    private Row row(BasketItem item) {
        String category = StringUtils.isBlank(item.getCategory()) ? null
                : StringUtils.trimToNull(categoryLocalizer.localize(item.getCategory(), null));
        return new Row(item.getName(), category, StringUtils.trimToNull(item.getMfn()), item.getQty(),
                Money.format(item.getUnitPrice()), item.isService());
    }

    private Summary summary(Basket basket, Store store) {
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
                log.warn("Delivery option of basket {} (store {}, delivery option {}) unresolved on the basket view: {}",
                        basket.getBasketId(), basket.getStoreId(), basket.getDeliveryOptionId(), e.toString());
                state = DeliveryState.UNKNOWN;
            }
        }
        double total = itemsTotal + delivery;
        double discount = basket.hasDiscountCode() ? basket.getDiscountAmount() : 0;
        return new Summary(Money.format(itemsTotal + discount), basket.getDiscountCode(),
                basket.hasDiscountCode() ? Money.format(discount) : null, state, deliveryName, Money.format(delivery),
                Money.format(total), Money.format(total / DEFAULT_VAT_RATE), Money.format(total - total / DEFAULT_VAT_RATE));
    }

    static Customer customer(Basket basket, Locale locale) {
        AddressBlock billing = AddressBlock.of(basket.getBillingDetails(), locale);
        AddressBlock shipping = AddressBlock.of(basket.getShippingDetails(), locale);
        boolean same = hasAny(billing) && hasAny(shipping)
                && Objects.equals(billing.name(), shipping.name()) && Objects.equals(billing.company(), shipping.company())
                && Objects.equals(billing.street(), shipping.street()) && Objects.equals(billing.cityLine(), shipping.cityLine())
                && Objects.equals(billing.country(), shipping.country())
                && Objects.equals(billing.email(), shipping.email()) && Objects.equals(billing.phone(), shipping.phone());
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
