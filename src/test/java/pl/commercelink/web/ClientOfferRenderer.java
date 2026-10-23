package pl.commercelink.web;

import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.baskets.BasketType;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.payments.PaymentProviderFactory;
import pl.commercelink.stores.BankAccount;
import pl.commercelink.stores.CheckoutConfiguration;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.stores.PaymentIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.web.clientoffer.ClientOfferDataForm;
import pl.commercelink.web.clientoffer.ClientOfferNotice;
import pl.commercelink.web.clientoffer.ClientOfferPage;
import pl.commercelink.web.clientoffer.ClientOfferPageFactory;
import pl.commercelink.web.dtos.CountryOptions;
import pl.commercelink.web.publicpage.PublicPageFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.mockito.Mockito.mock;

/**
 * Renders clientOffer.html on the public page frame with the model ClientOfferController builds, from a basket and a
 * store run through the real ClientOfferPageFactory. The payment provider factory knows no descriptors, so a payment
 * method is shown under its integration name.
 */
final class ClientOfferRenderer {

    static final String OFFER_ID = "6f0c1a2b-41d3-4f6e-9d0a-1c2b3d4e5f60";

    private final Store store;
    private final Basket basket;
    private ClientOfferDataForm form;
    private Map<String, String> errors = Map.of();
    private boolean editing;
    private boolean canEdit;
    private ClientOfferNotice notice;
    private Locale locale = Locale.ENGLISH;

    private ClientOfferRenderer(Store store, Basket basket) {
        this.store = store;
        this.basket = basket;
    }

    /** A store with a courier delivery, two payment methods and a bank account; an offer with a variant group. */
    static ClientOfferRenderer newOffer() {
        Store store = new Store();
        store.setStoreId("s1");
        store.setName("Nova Tech");
        CheckoutConfiguration checkout = new CheckoutConfiguration();
        checkout.addDeliveryOption(delivery("d1", "DPD courier", 29.99, ShipmentType.Courier));
        checkout.addDeliveryOption(delivery("d2", "Pickup in store", 0.0, ShipmentType.PersonalCollection));
        store.setCheckoutConfiguration(checkout);
        store.setPayments(new ArrayList<>(List.of(new PaymentIntegration("Paynow", true),
                new PaymentIntegration("Santander", false))));
        BankAccount account = new BankAccount();
        account.setIban("PL61109010140000071219812874");
        account.setAccountHolder("Nova Tech sp. z o.o.");
        account.setCurrency("PLN");
        store.addBankAccount(account, true);

        Basket basket = new Basket();
        basket.setStoreId("s1");
        basket.setBasketId(OFFER_ID);
        basket.setType(BasketType.Offer);
        basket.setShowPrices(true);
        basket.setExpiresAt(LocalDateTime.now().plusDays(3));
        basket.setDeliveryOptionId("d1");
        basket.setBasketItems(new ArrayList<>(List.of(
                item(1, "AMD Ryzen 7 7800X3D", "Processors", 1699.0, 1, null),
                item(2, "MSI GeForce RTX 4070 SUPER", "Graphics", 2899.0, 1, "Graphics card"),
                item(3, "ASUS TUF RTX 4070 Ti SUPER", "Graphics", 3899.0, 1, "Graphics card"),
                item(4, "Gigabyte RX 7800 XT", "Graphics", 2899.0, 1, "Graphics card"),
                item(5, "Kingston Fury 32 GB", "Memory", 449.0, 2, null))));
        ClientOfferRenderer renderer = new ClientOfferRenderer(store, basket);
        renderer.form = ClientOfferDataForm.empty(false);
        return renderer;
    }

    static BillingDetails billing() {
        BillingDetails billing = new BillingDetails();
        billing.setName("Jan");
        billing.setSurname("Kowalski");
        billing.setStreetAndNumber("Lipowa 14");
        billing.setPostalCode("00-950");
        billing.setCity("Warszawa");
        billing.setCountry("PL");
        billing.setEmail("jan.kowalski@example.com");
        billing.setPhone("600100200");
        return billing;
    }

    ClientOfferRenderer submitted(BillingDetails billing) {
        basket.setBillingDetails(billing);
        basket.setShippingDetails(ClientOfferDataForm.shippingCopyOf(billing));
        return this;
    }

    ClientOfferRenderer form(ClientOfferDataForm form, Map<String, String> errors) {
        this.form = form;
        this.errors = errors;
        return this;
    }

    /** Personal collection: the controller then builds a form that asks for no delivery address. */
    ClientOfferRenderer personalCollection() {
        basket.setDeliveryOptionId("d2");
        this.form = ClientOfferDataForm.empty(true);
        return this;
    }

    ClientOfferRenderer editing() {
        this.editing = true;
        return this;
    }

    ClientOfferRenderer canEdit() {
        this.canEdit = true;
        return this;
    }

    ClientOfferRenderer notice(ClientOfferNotice notice) {
        this.notice = notice;
        return this;
    }

    ClientOfferRenderer locale(Locale locale) {
        this.locale = locale;
        return this;
    }

    Store store() {
        return store;
    }

    Basket basket() {
        return basket;
    }

    String render() {
        ClientOfferPage page = new ClientOfferPageFactory(mock(PaymentProviderFactory.class))
                .build(basket, store, canEdit, locale);
        Context context = new Context(locale);
        context.setVariable("publicPage", PublicPageFactory.of(store, basket.createOfferUrl(""), locale));
        context.setVariable("page", page);
        context.setVariable("form", form);
        context.setVariable("errors", errors);
        context.setVariable("editing", editing);
        context.setVariable("countries",
                CountryOptions.forPicker(form.dto().getBillingDetails().getCountry(), locale));
        if (notice != null) {
            context.setVariable("clientOfferNotice", notice);
        }
        return engine(locale).process("clientOffer", context);
    }

    /** The answer to a variant chosen in the background: the controller puts only the page model. */
    String renderVariantUpdate() {
        Context context = new Context(locale);
        context.setVariable("page", new ClientOfferPageFactory(mock(PaymentProviderFactory.class))
                .build(basket, store, canEdit, locale));
        return engine(locale).process("client-offer/variant-update", context);
    }

    private static TemplateEngine engine(Locale locale) {
        TemplateEngine engine = EnglishFragmentTemplateEngine.create(locale);
        engine.addDialect(new LayoutDialect());
        return engine;
    }

    private static DeliveryOption delivery(String id, String name, double price, ShipmentType type) {
        DeliveryOption option = new DeliveryOption();
        option.setId(id);
        option.setName(name);
        option.setDescription(name + " description");
        option.setPrice(price);
        option.setType(type);
        return option;
    }

    private static BasketItem item(int position, String name, String category, double price, long qty, String group) {
        BasketItem item = new BasketItem("pim-" + position, name, "MFN-" + position, category, price, 0, qty, null, 3,
                false);
        item.setPosition(position);
        item.setVariantGroupId(group);
        return item;
    }
}
