package pl.commercelink.web.offers;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.baskets.BasketType;
import pl.commercelink.baskets.ContactDetails;
import pl.commercelink.baskets.OfferItem;
import pl.commercelink.baskets.OfferItemReloader;
import pl.commercelink.baskets.OfferValidity;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.StoreCategories;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.OfferController;
import pl.commercelink.web.offers.OfferDetailsPage.*;
import pl.commercelink.web.orders.ItemMargin;
import pl.commercelink.web.orders.Money;
import pl.commercelink.web.orders.OrderLabels;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static pl.commercelink.invoicing.api.Price.DEFAULT_VAT_RATE;

@Slf4j
@Component
public class OfferDetailsPageFactory {

    static final String LIST_PATH = "/dashboard/offers";
    static final String TEMPLATES_PATH = "/dashboard/offers?segment=templates";

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm");
    private static final DateTimeFormatter INPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final StoresRepository storesRepository;
    private final OfferItemReloader offerItemReloader;
    private final ProductCatalogRepository productCatalogRepository;
    private final StoreCategories storeCategories;
    private final MessageSource messages;
    private final String appDomain;

    OfferDetailsPageFactory(StoresRepository storesRepository, OfferItemReloader offerItemReloader,
                            ProductCatalogRepository productCatalogRepository, StoreCategories storeCategories,
                            MessageSource messages, @Value("${app.domain}") String appDomain) {
        this.storesRepository = storesRepository;
        this.offerItemReloader = offerItemReloader;
        this.productCatalogRepository = productCatalogRepository;
        this.storeCategories = storeCategories;
        this.messages = messages;
        this.appDomain = appDomain;
    }

    /** Never saves: a template shows prices recalculated on open and an offer the reloaded ones, as the page always did. */
    public OfferDetailsPage build(Basket basket, String returnTo, Locale locale, LocalDateTime now) {
        boolean template = basket.hasType(BasketType.OfferTemplate);
        return build(basket, template ? offerItemReloader.recalculate(basket) : offerItemReloader.reload(basket),
                returnTo, locale, now);
    }

    /**
     * Never saves: the items are the ones the operator posted (a draft of the table, or a save refused over a changed
     * version), so their prices are kept as posted, a template's too; only the market figures are read.
     */
    public OfferDetailsPage buildDraft(Basket basket, String returnTo, Locale locale, LocalDateTime now) {
        return build(basket, offerItemReloader.reload(basket), returnTo, locale, now);
    }

    private OfferDetailsPage build(Basket basket, List<OfferItem> offerItems, String returnTo, Locale locale,
                                   LocalDateTime now) {
        boolean template = basket.hasType(BasketType.OfferTemplate);
        Store store = storesRepository.findById(basket.getStoreId());
        List<ProductCatalog> catalogs = productCatalogRepository.findAll(basket.getStoreId());

        List<Line> lines = lines(basket, offerItems);
        Summary summary = summary(basket, store, lines);
        ContactDetails contact = basket.getContactDetails();
        boolean contactFilled = contact != null && contact.isProperlyFilled();
        BasketViewPage.Customer addresses = BasketViewPageFactory.customer(basket, locale);
        OfferValidity validity = OfferValidity.of(basket.getExpiresAt(), now);

        return new OfferDetailsPage(basket.getBasketId(), basket.getStoreId(), basket.getShortenedBasketId(), template,
                title(basket, template, locale), backHref(returnTo, template),
                template ? "offers.details.back.templates" : "offers.details.back.offers",
                template ? new Pill("offers.details.pill.template", null, "is-neutral")
                        : new Pill("offers.list.pill." + validity.param(), null, OfferRowMapper.tone(validity)),
                meta(basket, template, validity, summary, contact, locale, now),
                lines, storeCategories.groups(catalogs), catalogs, summary,
                terms(basket, store, template, validity, locale, now),
                new Customer(contact == null ? null : contact.getName(), contact == null ? null : contact.getSurname(),
                        contact == null ? null : contact.getCompanyName(), contact == null ? null : contact.getEmail(),
                        contact == null ? null : contact.getPhone(), contactFilled,
                        addresses.hasBilling() ? addresses.billing() : null,
                        addresses.hasShipping() ? addresses.shipping() : null, addresses.sameAddress()),
                basket.getComment(), template ? null : basket.createOfferUrl(appDomain),
                basket.getBillingDetails() != null, contactFilled, basket.getName(),
                basket.getFulfilmentType() == null ? null : basket.getFulfilmentType().name(), basket.getVersion());
    }

    /** The list's address when it is one (OfferController.safeReturnTo keeps it), else the segment the record belongs to. */
    static String backHref(String returnTo, boolean template) {
        String fallback = template ? TEMPLATES_PATH : LIST_PATH;
        if (returnTo == null) {
            return fallback;
        }
        String safe = OfferController.safeReturnTo(returnTo);
        return safe.equals(returnTo) ? returnTo : fallback;
    }

    private String title(Basket basket, boolean template, Locale locale) {
        String name = StringUtils.trimToNull(basket.getName());
        return name != null ? name : messages.getMessage(template ? "offers.list.untitledTemplate" : "offers.list.untitled",
                new Object[]{basket.getShortenedBasketId()}, locale);
    }

    private Meta meta(Basket basket, boolean template, OfferValidity validity, Summary summary, ContactDetails contact,
                      Locale locale, LocalDateTime now) {
        String author = basket.getSource() == null ? null : StringUtils.trimToNull(basket.getSource().getName());
        return new Meta(template ? null : client(contact),
                template ? null : OfferRowMapper.validityNote(validity, basket.getExpiresAt(), now, messages, locale),
                basket.getCreatedAt() == null ? null : DATE_TIME.format(basket.getCreatedAt()), author, summary.total(),
                basket.getEffectiveBasketItems().size());
    }

    private static String client(ContactDetails contact) {
        if (contact == null) {
            return null;
        }
        String person = StringUtils.trimToNull(StringUtils.trimToEmpty(contact.getName()) + " " + StringUtils.trimToEmpty(contact.getSurname()));
        String company = StringUtils.trimToNull(contact.getCompanyName());
        return person != null && company != null ? person + " · " + company : person != null ? person : company;
    }

    /** All record items, not only the chosen variants: the form edits and saves every one of them. */
    private List<Line> lines(Basket basket, List<OfferItem> offerItems) {
        Map<BasketItem, OfferItem> market = new IdentityHashMap<>();
        for (OfferItem offer : offerItems) {
            if (offer.getBasketItem() != null) {
                market.put(offer.getBasketItem(), offer);
            }
        }
        List<BasketItem> items = basket.getBasketItems();
        List<Line> lines = new ArrayList<>();
        Set<String> headed = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            BasketItem item = items.get(i);
            if (item.isInVariantGroup() && headed.add(item.getVariantGroupId())) {
                lines.add(new GroupHead(item.getVariantGroupId(), basket.getVariantGroup(item.getVariantGroupId()).size()));
            }
            lines.add(item(i, item, market.get(item)));
        }
        return lines;
    }

    private Item item(int index, BasketItem item, OfferItem offer) {
        boolean service = item.isService();
        boolean known = !service && offer != null && offer.isExistInInventory();
        Market market = service ? null
                : known ? new Market(false, Money.format(offer.getLowestCost()), Money.format(offer.getMedianCost()),
                offer.getSupplierCount(), offer.getTotalQty())
                : Market.none();
        double estimate = known ? estimatedCost(offer) : 0;
        boolean estimated = !service && item.getUnitCost() <= 0 && estimate > 0;
        double costForMargin = estimated ? estimate : item.getUnitCost();
        return new Item(index, item.getId(), item.getCatalogId(), item.getName(), item.getCategory(), item.getMfn(),
                item.getQty(), Money.inputRounded(item.getUnitPrice()), Money.inputRounded(item.getUnitCost()),
                item.getUnitPrice(), item.getUnitCost(), costForMargin, estimated, estimate, item.isConsolidated(), item.getVariantGroupId(),
                item.isVariantSelected(), service, item.getEstimatedDeliveryDays(), droppedOnSave(item),
                service ? null : ItemMargin.of(item.getUnitPrice(), costForMargin, DEFAULT_VAT_RATE), market,
                known ? offer.getMedianCostProviders() : null, known ? offer.getRemainingProviders() : null,
                known ? offer.getLowestCostQty() : 0, known ? offer.getMedianCostQty() : 0);
    }

    /**
     * The cost the page assumes for an item without its own: halfway between the suppliers' lowest and median gross cost
     * (client 2026-10-27: buying at the median is rare, at the lowest not always possible), rounded to grosze. 0 when
     * either is unknown. Display only: "Przelicz z cennika" and items added from the inventory store their own cost as
     * before (the median and the lowest price).
     */
    static double estimatedCost(OfferItem offer) {
        if (offer.getLowestCost() <= 0 || offer.getMedianCost() <= 0) {
            return 0;
        }
        return BigDecimal.valueOf(offer.getLowestCost()).add(BigDecimal.valueOf(offer.getMedianCost()))
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * BasketItem.isComplete on what the row posts: a product without a category posts it as "" and is kept, so only
     * the name, the code, the quantity and the price can make the save drop it.
     */
    private static boolean droppedOnSave(BasketItem item) {
        return StringUtils.isBlank(item.getName()) || StringUtils.isBlank(item.getMfn()) || item.getQty() <= 0
                || item.getUnitPrice() < 0;
    }

    /**
     * The formulas of OfferController.showEditOfferForm: chosen variants plus delivery, and the profit from it; the cost
     * of an item without one is the estimate (estimatedCost), which Basket.getTotalCost does not know.
     */
    private Summary summary(Basket basket, Store store, List<Line> lines) {
        Optional<DeliveryOption> option = deliveryOption(basket, store);
        boolean removed = basket.getDeliveryOptionId() != null && option.isEmpty();
        if (removed) {
            log.warn("Delivery option {} of offer {} (store {}) is no longer configured; counted as 0",
                    basket.getDeliveryOptionId(), basket.getBasketId(), basket.getStoreId());
        }
        double delivery = option.map(DeliveryOption::getPrice).orElse(0.0);
        double total = basket.getTotalPrice() + delivery;
        Map<BasketItem, Item> rows = new IdentityHashMap<>();
        for (Line line : lines) {
            if (line instanceof Item row) {
                rows.put(basket.getBasketItems().get(row.index()), row);
            }
        }
        // the chosen items are the ones Basket counts in its totals, so the figures stay in step with getTotalPrice
        List<BasketItem> chosen = basket.getEffectiveBasketItems();
        double cost = chosen.stream().mapToDouble(i -> i.getQty() * rows.get(i).costForMargin()).sum();
        boolean estimated = chosen.stream().anyMatch(i -> rows.get(i).costEstimated());
        double profitGross = total - cost;
        double profitNet = profitGross / DEFAULT_VAT_RATE;
        return new Summary(Money.format(basket.getTotalPrice()), option.map(DeliveryOption::getName).orElse(null),
                removed, Money.format(delivery), delivery, Money.format(total), Money.format(total / DEFAULT_VAT_RATE),
                Money.format(cost), Money.format(Math.round(profitGross * 100.0) / 100.0),
                Money.format(Math.round(profitNet * 100.0) / 100.0), basket.hasVariantGroups(), estimated);
    }

    /** Looked up in the configured options instead of Basket.getDeliveryPrice, which throws for a removed one. */
    private Optional<DeliveryOption> deliveryOption(Basket basket, Store store) {
        if (basket.getDeliveryOptionId() == null || store == null || store.getCheckoutConfiguration() == null) {
            return Optional.empty();
        }
        return store.getCheckoutConfiguration().deliveryOptionsFor(basket.getDeliveryOptionId()).stream()
                .filter(o -> basket.getDeliveryOptionId().equals(o.getId()))
                .findFirst();
    }

    private Terms terms(Basket basket, Store store, boolean template, OfferValidity validity, Locale locale,
                        LocalDateTime now) {
        List<DeliveryChoice> choices = store == null || store.getCheckoutConfiguration() == null ? List.of()
                : store.getCheckoutConfiguration().deliveryOptionsFor(basket.getDeliveryOptionId()).stream()
                .map(o -> new DeliveryChoice(o.getId(), o.getName() + " (" + messages.getMessage("general.currency.amount",
                        new Object[]{Money.format(o.getPrice())}, locale) + ")"))
                .collect(Collectors.toList());
        LocalDateTime expiresAt = template ? null : basket.getExpiresAt();
        return new Terms(expiresAt == null ? null : OfferRowMapper.expiresAtWithDays(validity,
                        DATE_TIME.format(expiresAt), expiresAt, now, messages, locale),
                expiresAt == null ? null : INPUT.format(expiresAt), basket.getDeliveryOptionId(), choices,
                basket.isShowPrices(), OrderLabels.fulfilmentType(basket.getFulfilmentType()), basket.getGclid());
    }
}
