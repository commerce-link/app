package pl.commercelink.web;

import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RMAResolutionType;
import pl.commercelink.orders.rma.RMAReturnForm;
import pl.commercelink.orders.rma.RMAStatus;
import pl.commercelink.stores.AuthorizedCarrier;
import pl.commercelink.stores.ClientNotificationsConfiguration;
import pl.commercelink.stores.PackageTemplate;
import pl.commercelink.stores.Parcel;
import pl.commercelink.stores.RMAConfiguration;
import pl.commercelink.stores.ShippingConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.web.clientreturn.ClientReturnNotice;
import pl.commercelink.web.clientreturn.ClientReturnView;
import pl.commercelink.web.clientreturn.ReturnPackageOption;
import pl.commercelink.web.clientreturn.ReturnPackageOptions;
import pl.commercelink.web.publicpage.PublicPageFactory;
import pl.commercelink.web.publicpage.PublicTemplateRenderer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders client-return.html on the public page frame with the model RMAClientController builds: a real ClientReturnView
 * from an RMA, its items and the store, the package options and the flashed form, errors and notice.
 */
final class ClientReturnRenderer {

    static final String RMA_ID = "3f9c2a71-5b2e-4c1d-8a9f-0e1d2c3b4a59";
    static final String ORDER_ID = "7f3a91c2-4b1e-4d6a-9c2e-15b8e0a4d913";
    static final String STORE_ID = "s1";
    static final String RETURN_PATH = "/store/" + STORE_ID + "/client/rma/" + RMA_ID;
    static final Locale PL = Locale.forLanguageTag("pl");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 23, 12, 0);

    private final Store store;
    private final RMA rma;
    private final List<RMAItem> items;
    private ClientReturnNotice notice;
    private RMAReturnForm form;
    private Map<String, String> errors = Map.of();
    private Locale locale = PL;

    private ClientReturnRenderer(Store store, RMA rma, List<RMAItem> items) {
        this.store = store;
        this.rma = rma;
        this.items = items;
    }

    /** Store "Nova Tech" with a return carrier and three return boxes (Karton S is the default); an Approved RMA. */
    static ClientReturnRenderer approved() {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setName("Nova Tech");
        ClientNotificationsConfiguration notifications = new ClientNotificationsConfiguration();
        notifications.setSenderName("Nova Tech");
        notifications.setReplyToEmail("biuro@novatech.pl");
        store.setClientNotificationsConfiguration(notifications);
        RMAConfiguration rmaConfiguration = new RMAConfiguration();
        rmaConfiguration.setCarrier(new AuthorizedCarrier("c1", "DPD", "DPD"));
        store.setRmaConfiguration(rmaConfiguration);
        store.setShippingConfiguration(new ShippingConfiguration());

        RMA rma = new RMA(STORE_ID);
        rma.setRmaId(RMA_ID);
        rma.setOrderId(ORDER_ID);
        rma.setStatus(RMAStatus.Approved);
        rma.setCreatedAt(LocalDateTime.of(2026, 10, 21, 10, 0));
        rma.setShipments(new ArrayList<>());

        ClientReturnRenderer renderer = new ClientReturnRenderer(store, rma, List.of(
                item("Karta graficzna MSI GeForce RTX 4070 SUPER", 1, RMAResolutionType.Repair),
                item("Pamięć Kingston Fury Beast DDR5 32 GB", 2, RMAResolutionType.Return)));
        return renderer.templates(
                template("t-s", "RMA - Karton S", true, new Parcel(25, 15, 10, 1, 0, null)),
                template("t-m", "RMA - Karton M", false, new Parcel(40, 30, 20, 5, 0, null)),
                template("t-l", "RMA - karton L na kartę graficzną", false,
                        new Parcel(80, 40, 60, 12, 0, null), new Parcel(60, 40, 40, 5, 0, null)));
    }

    static PackageTemplate template(String id, String name, boolean isDefault, Parcel... parcels) {
        PackageTemplate template = new PackageTemplate(name, List.of(parcels));
        template.setId(id);
        template.setDefault(isDefault);
        return template;
    }

    static ShippingDetails address() {
        ShippingDetails details = ShippingDetails._default();
        details.setName("Jan");
        details.setSurname("Kowalski");
        details.setStreetAndNumber("Marszałkowska 140/12");
        details.setPostalCode("00950");
        details.setCity("Warszawa");
        details.setEmail("jan.kowalski@poczta");
        details.setPhone("601234567");
        return details;
    }

    ClientReturnRenderer templates(PackageTemplate... templates) {
        List<PackageTemplate> list = new ArrayList<>(List.of(templates));
        list.add(template("t-out", "Karton wysyłkowy", false, new Parcel(30, 30, 30, 3, 0, null)));
        store.getShippingConfiguration().setPackageTemplates(list);
        return this;
    }

    ClientReturnRenderer noCarrier() {
        store.setRmaConfiguration(null);
        return this;
    }

    ClientReturnRenderer contactEmail(String email) {
        store.getClientNotificationsConfiguration().setReplyToEmail(email);
        return this;
    }

    ClientReturnRenderer status(RMAStatus status) {
        rma.setStatus(status);
        return this;
    }

    ClientReturnRenderer savedAddress(ShippingDetails details) {
        rma.setShippingDetails(details);
        return this;
    }

    ClientReturnRenderer shipments(Shipment... shipments) {
        rma.setShipments(new ArrayList<>(List.of(shipments)));
        return this;
    }

    ClientReturnRenderer rejectionReason(String reason) {
        rma.setRejectionReason(reason);
        return this;
    }

    ClientReturnRenderer createdAt(LocalDateTime createdAt) {
        rma.setCreatedAt(createdAt);
        return this;
    }

    ClientReturnRenderer orderId(String orderId) {
        rma.setOrderId(orderId);
        return this;
    }

    ClientReturnRenderer notice(ClientReturnNotice notice) {
        this.notice = notice;
        return this;
    }

    /** A refused submission: the posted values come back flashed, with the errors of their fields. */
    ClientReturnRenderer refused(ShippingDetails details, String packageId, Map<String, String> errors) {
        RMAReturnForm posted = new RMAReturnForm();
        posted.setShippingDetails(details);
        posted.setSelectedPackageTemplateId(packageId);
        this.form = posted;
        this.errors = errors;
        return this;
    }

    Store store() {
        return store;
    }

    ClientReturnRenderer locale(Locale locale) {
        this.locale = locale;
        return this;
    }

    String render() {
        RMAReturnForm shown = form;
        if (shown == null) {
            shown = new RMAReturnForm();
            shown.setShippingDetails(ShippingDetails._default());
        }
        List<ReturnPackageOption> options = ReturnPackageOptions.of(store);
        Map<String, Object> model = new HashMap<>();
        model.put("publicPage", PublicPageFactory.of(store, RETURN_PATH, locale));
        model.put("view", ClientReturnView.of(rma, items, store, NOW));
        model.put("packageOptions", options);
        if (ReturnPackageOptions.selected(options, shown.getSelectedPackageTemplateId()) != null) {
            model.put("selectedPackage", ReturnPackageOptions.selected(options, shown.getSelectedPackageTemplateId()));
        }
        model.put("form", shown);
        model.put("errors", errors);
        model.put("returnPath", RETURN_PATH);
        if (notice != null) {
            model.put("clientReturnNotice", notice);
        }
        return PublicTemplateRenderer.render("client-return", locale, model);
    }

    private static RMAItem item(String name, int qty, RMAResolutionType resolution) {
        RMAItem item = new RMAItem();
        item.setName(name);
        item.setQty(qty);
        item.setDesiredResolution(resolution);
        item.setReason("Nie działa.");
        return item;
    }
}
