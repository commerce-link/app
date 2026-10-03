package pl.commercelink.web.offers;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketType;
import pl.commercelink.baskets.ContactDetails;
import pl.commercelink.baskets.OfferValidity;
import pl.commercelink.stores.Store;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import static pl.commercelink.invoicing.api.Price.DEFAULT_VAT_RATE;

/** One basket as a row of the offers list (spec §3.3), texts resolved; same number and date formats as the deliveries list. */
@Slf4j
public class OfferRowMapper {

    private static final DateTimeFormatter SAME_YEAR = DateTimeFormatter.ofPattern("dd.MM");
    private static final DateTimeFormatter OTHER_YEAR = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final MessageSource messages;
    private final Locale locale;
    private final Store store;
    private final String appDomain;
    private final LocalDateTime now;
    private final String returnTo;
    private final DecimalFormat amount;

    public OfferRowMapper(MessageSource messages, Locale locale, Store store, String appDomain, LocalDateTime now, String returnTo) {
        this.messages = messages;
        this.locale = locale;
        this.store = store;
        this.appDomain = appDomain;
        this.now = now;
        this.returnTo = returnTo;
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(locale);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        this.amount = new DecimalFormat("#,##0.00", symbols);
    }

    public OfferListPage.OfferRow offer(Basket basket) {
        String id = basket.getBasketId();
        String name = name(basket);
        ContactDetails contact = basket.getContactDetails();
        OfferValidity validity = OfferValidity.of(basket.getExpiresAt(), now);
        double gross = value(basket);
        return new OfferListPage.OfferRow("/dashboard/offer/" + id, name, basket.getShortenedBasketId(), id,
                text("offers.list.items", String.valueOf(basket.getEffectiveBasketItems().size())),
                client(contact), contact == null ? null : StringUtils.trimToNull(contact.getEmail()),
                date(basket.getCreatedAt()), author(basket),
                text("offers.list.pill." + validity.param()), tone(validity), validityNote(validity, basket.getExpiresAt()),
                validity == OfferValidity.EXPIRING,
                money(gross), text("offers.list.net", money(gross / DEFAULT_VAT_RATE)),
                basket.createOfferUrl(appDomain),
                "/dashboard/offer/" + id + "/copy", "/dashboard/offer/" + id + "/copy?withContact=true",
                deleteHref(id), text("offers.delete.confirm.title", name), text("offers.delete.confirm.message"),
                text("offers.list.menu.aria", name), text("offers.list.copyLink.aria", basket.getShortenedBasketId()));
    }

    public OfferListPage.TemplateRow template(Basket basket) {
        String id = basket.getBasketId();
        String name = name(basket);
        return new OfferListPage.TemplateRow("/dashboard/offer/" + id, name, basket.getShortenedBasketId(), id,
                text("offers.list.items", String.valueOf(basket.getEffectiveBasketItems().size())), date(basket.getCreatedAt()), author(basket),
                money(value(basket)), "/dashboard/offer/new?intent=template&sourceId=" + id,
                deleteHref(id), text("offers.delete.confirm.titleTemplate", name), text("offers.delete.confirm.messageTemplate"),
                text("offers.list.menu.aria", name));
    }

    public OfferListPage.BasketRow basket(Basket basket) {
        String id = basket.getBasketId();
        String client = client(basket.getContactDetails());
        LocalDateTime created = basket.getCreatedAt();
        return new OfferListPage.BasketRow("/dashboard/basket/view/" + id, basket.getShortenedBasketId(), id,
                client != null ? client : text("offers.list.client.guest"),
                created == null ? null : date(created) + ", " + TIME.format(created),
                text("offers.list.items", String.valueOf(basket.getEffectiveBasketItems().size())), money(value(basket)));
    }

    private String name(Basket basket) {
        String name = StringUtils.trimToNull(basket.getName());
        if (name != null) {
            return name;
        }
        return text(basket.hasType(BasketType.OfferTemplate) ? "offers.list.untitledTemplate" : "offers.list.untitled", basket.getShortenedBasketId());
    }

    private static String client(ContactDetails contact) {
        if (contact == null) {
            return null;
        }
        String company = StringUtils.trimToNull(contact.getCompanyName());
        if (company != null) {
            return company;
        }
        return StringUtils.trimToNull(StringUtils.trimToEmpty(contact.getName()) + " " + StringUtils.trimToEmpty(contact.getSurname()));
    }

    private static String author(Basket basket) {
        return basket.getSource() == null ? null : StringUtils.trimToNull(basket.getSource().getName());
    }

    /** Items (chosen variants only) plus delivery, as on the offer page; a delivery the store cannot resolve counts as 0. */
    private double value(Basket basket) {
        double delivery;
        try {
            delivery = store == null || store.getCheckoutConfiguration() == null ? 0.0 : basket.getDeliveryPrice(store);
        } catch (RuntimeException e) {
            // a removed or unknown delivery option must not take the whole list down
            log.warn("Delivery price of basket {} (store {}, delivery option {}) skipped in the offers list: {}",
                    basket.getBasketId(), basket.getStoreId(), basket.getDeliveryOptionId(), e.toString());
            delivery = 0.0;
        }
        return basket.getTotalPrice() + delivery;
    }

    private String validityNote(OfferValidity validity, LocalDateTime expiresAt) {
        return switch (validity) {
            case NO_EXPIRY -> null;
            case EXPIRED -> text("offers.list.note.expired", date(expiresAt));
            case ACTIVE, EXPIRING -> {
                long days = ChronoUnit.DAYS.between(now.toLocalDate(), expiresAt.toLocalDate());
                if (days == 0) yield text("offers.list.note.today", TIME.format(expiresAt));
                if (days == 1) yield text("offers.list.note.tomorrow", date(expiresAt));
                yield text("offers.list.note.until", date(expiresAt), String.valueOf(days));
            }
        };
    }

    private static String tone(OfferValidity validity) {
        return switch (validity) {
            case ACTIVE -> "is-ok";
            case EXPIRING -> "is-warn";
            case EXPIRED, NO_EXPIRY -> "is-neutral";
        };
    }

    private String deleteHref(String id) {
        return "/dashboard/offer/" + id + "/delete?returnTo=" + URLEncoder.encode(returnTo, StandardCharsets.UTF_8);
    }

    private String date(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        LocalDate day = value.toLocalDate();
        return (day.getYear() == now.getYear() ? SAME_YEAR : OTHER_YEAR).format(day);
    }

    private String money(double value) {
        return text("general.currency.amount", amount.format(value));
    }

    private String text(String key, Object... args) {
        return messages.getMessage(key, args, locale);
    }
}
