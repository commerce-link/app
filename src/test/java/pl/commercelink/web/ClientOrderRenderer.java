package pl.commercelink.web;

import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.stores.BankAccount;
import pl.commercelink.stores.ClientNotificationsConfiguration;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.taxonomy.CategoryLocalizer;
import pl.commercelink.web.clientorder.ClientOrderNotice;
import pl.commercelink.web.dtos.ClientOrderView;
import pl.commercelink.web.publicpage.PublicPageFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Renders clientOrder.html on the public page frame with the model ClientOrderController builds: a real ClientOrderView
 * from an order, its items and the store. Category keys are shown as they are (the localizer echoes them).
 */
final class ClientOrderRenderer {

    static final String ORDER_ID = "7f3a91c2-4b1e-4d6a-9c2e-15b8e0a4d913";
    static final String STORE_ID = "s1";
    static final String ORDER_PATH = "/store/" + STORE_ID + "/client/order/" + ORDER_ID;

    private final Store store;
    private final Order order;
    private final List<OrderItem> items;
    private String addressStep;
    private ClientOrderNotice notice;
    private Locale locale = Locale.ENGLISH;
    private LocalDate today = LocalDate.of(2026, 10, 22);

    private ClientOrderRenderer(Store store, Order order, List<OrderItem> items) {
        this.store = store;
        this.order = order;
        this.items = items;
    }

    /** Store "Nova Tech" with the page, the address change and the preferred date on; an order in assembly sent by courier. */
    static ClientOrderRenderer newOrder() {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setName("Nova Tech");
        FulfilmentConfiguration fulfilment = new FulfilmentConfiguration();
        fulfilment.setClientOrderPageEnabled(true);
        fulfilment.setClientShippingAddressChangeEnabled(true);
        fulfilment.setClientPreferredShippingDateEnabled(true);
        store.setFulfilmentConfiguration(fulfilment);
        ClientNotificationsConfiguration notifications = new ClientNotificationsConfiguration();
        notifications.setSenderName("Nova Tech");
        notifications.setReplyToEmail("biuro@novatech.pl");
        store.setClientNotificationsConfiguration(notifications);
        BankAccount account = new BankAccount();
        account.setIban("PL61109010140000071219812874");
        account.setAccountHolder("Nova Tech sp. z o.o.");
        account.setCurrency("PLN");
        store.addBankAccount(account, true);

        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        order.setStatus(OrderStatus.Assembly);
        order.setOrderedAt(LocalDateTime.of(2026, 10, 14, 9, 30));
        order.setEstimatedAssemblyAt(LocalDate.of(2026, 10, 21));
        order.setEstimatedShippingAt(LocalDate.of(2026, 10, 23));
        order.setTotalPrice(6245.99);
        ShippingDetails shipping = ShippingDetails._default();
        shipping.setName("Jan");
        shipping.setSurname("Kowalski");
        shipping.setStreetAndNumber("Miodowa 14");
        shipping.setPostalCode("00-251");
        shipping.setCity("Warszawa");
        shipping.setEmail("jan.kowalski@gmail.com");
        shipping.setPhone("600100789");
        order.setShippingDetails(shipping);

        List<OrderItem> items = new ArrayList<>(List.of(
                product("AMD Ryzen 7 7800X3D", "Processor", 1, FulfilmentStatus.Reserved, 1),
                product("MSI GeForce RTX 4070 SUPER", "Graphics card", 1, FulfilmentStatus.Ordered, 2),
                product("Kingston Fury Beast 32 GB", "Memory", 2, FulfilmentStatus.Reserved, 3),
                product("ASUS TUF GAMING B650-PLUS", null, 1, FulfilmentStatus.New, 4)));
        OrderItem assembly = new OrderItem(ORDER_ID, "Services", "PC assembly service", 1, 199.0, null, false, 5);
        assembly.setService(true);
        items.add(assembly);
        DeliveryOption courier = new DeliveryOption();
        courier.setName("DPD courier");
        items.add(OrderItem.fromDeliveryOption(ORDER_ID, courier));
        return new ClientOrderRenderer(store, order, items);
    }

    static Shipment shipment(String carrier, String trackingNo, String trackingUrl, LocalDateTime shippedAt, LocalDateTime deliveredAt) {
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setCarrier(carrier);
        shipment.setTrackingNo(trackingNo);
        shipment.setTrackingUrl(trackingUrl);
        shipment.setShippedAt(shippedAt);
        shipment.setDeliveredAt(deliveredAt);
        return shipment;
    }

    ClientOrderRenderer status(OrderStatus status) {
        order.setStatus(status);
        return this;
    }

    ClientOrderRenderer shipments(Shipment... shipments) {
        order.setShipments(new LinkedList<>(List.of(shipments)));
        return this;
    }

    ClientOrderRenderer payments(Payment... payments) {
        for (Payment payment : payments) {
            order.addPayment(payment);
        }
        return this;
    }

    ClientOrderRenderer personalCollection() {
        order.setShipments(new LinkedList<>(List.of(new Shipment(ShipmentType.PersonalCollection))));
        return this;
    }

    ClientOrderRenderer pickupPoint(String code) {
        Shipment point = new Shipment(ShipmentType.PickupPoint);
        point.setCarrier("inpost");
        point.setCollectionPointCode(code);
        order.setShipments(new LinkedList<>(List.of(point)));
        return this;
    }

    ClientOrderRenderer preferredShippingAt(LocalDate day) {
        order.setPreferredShippingAt(day);
        return this;
    }

    ClientOrderRenderer addressStep(String step) {
        this.addressStep = step;
        return this;
    }

    ClientOrderRenderer notice(ClientOrderNotice notice) {
        this.notice = notice;
        return this;
    }

    /** The store's contact e-mail: the reply-to address the page and the frame both read. */
    ClientOrderRenderer contactEmail(String email) {
        store.getClientNotificationsConfiguration().setReplyToEmail(email);
        return this;
    }

    ClientOrderRenderer locale(Locale locale) {
        this.locale = locale;
        return this;
    }

    ClientOrderRenderer today(LocalDate today) {
        this.today = today;
        return this;
    }

    Store store() {
        return store;
    }

    Order order() {
        return order;
    }

    String render() {
        boolean beforeDispatch = order.getStatus() != OrderStatus.Shipping && order.getStatus() != OrderStatus.Delivered
                && order.getStatus() != OrderStatus.Cancelled;
        ClientOrderView view = ClientOrderView.from(order, items, store, echoingLocalizer(),
                beforeDispatch && store.isClientShippingAddressChangeEnabled() && !order.isPersonalCollection(),
                store.isClientPreferredShippingDateEnabled() && order.canClientSetPreferredShippingAt(),
                today);
        Context context = new Context(locale);
        Map<String, String> kept = new LinkedHashMap<>();
        context.setVariable("publicPage", PublicPageFactory.of(store, ORDER_PATH, kept, locale));
        context.setVariable("view", view);
        if ("code".equals(addressStep)) {
            context.setVariable("addressStep", "code");
            context.setVariable("verificationId", "ver-1");
            context.setVariable("maskedEmail", "j***@gmail.com");
            context.setVariable("codeValidityMinutes", 15);
        } else if ("edit".equals(addressStep)) {
            context.setVariable("addressStep", "edit");
            context.setVariable("editToken", "tok-1");
            context.setVariable("form", order.getShippingDetails().copy());
            context.setVariable("editTokenValidityMinutes", 30);
        }
        if (notice != null) {
            context.setVariable("clientOrderNotice", notice);
        }
        return engine(locale).process("clientOrder", context);
    }

    private static CategoryLocalizer echoingLocalizer() {
        CategoryLocalizer localizer = mock(CategoryLocalizer.class);
        lenient().when(localizer.localize(any(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        return localizer;
    }

    private static TemplateEngine engine(Locale locale) {
        TemplateEngine engine = EnglishFragmentTemplateEngine.create(locale);
        engine.addDialect(new LayoutDialect());
        return engine;
    }

    private static OrderItem product(String name, String category, int qty, FulfilmentStatus status, int position) {
        OrderItem item = new OrderItem(ORDER_ID, category, name, qty, 100.0, null, false, position);
        item.setStatus(status);
        return item;
    }
}
