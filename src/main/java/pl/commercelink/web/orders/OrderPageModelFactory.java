package pl.commercelink.web.orders;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.deliveries.DeliveryRedirectResolver;
import pl.commercelink.inventory.deliveries.DropshipItemLookup;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderReviewStatus;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentDirection;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.PositionGroup;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCarrierOptions;
import pl.commercelink.orders.ShipmentTrackingStatus;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.event.OrderEvent;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.starter.util.ConversionUtil;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.web.dtos.RoutedSupplierView;
import pl.commercelink.web.dtos.SplitGroupPreviewDto;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class OrderPageModelFactory {

    private static final Set<String> KNOWN_ACTIONS = Set.of("SHIPMENT_COLLECTED", "SHIPMENT_DELIVERED", "SHIPMENT_TRACKING_FAILED");

    private final StoresRepository storesRepository;
    private final OrderEventsRepository orderEventsRepository;
    private final DropshipItemLookup dropshipItemLookup;
    private final DeliveryRedirectResolver deliveryRedirectResolver;
    private final SupplierLabels supplierLabels;
    private final ShipmentCarrierOptions shipmentCarrierOptions;
    private final ProductCatalogRepository productCatalogRepository;
    private final TaxonomyCache taxonomyCache;
    private final MessageSource messageSource;

    @Value("${app.domain}")
    private String appDomain;

    /** Who looks: a super admin (read-only, store-scoped links), an admin (costs, dropship), and the list to return to. */
    public record Viewer(boolean superAdmin, boolean admin, String back) {
    }

    public OrderPageModel build(Order order, List<OrderItem> items, Viewer viewer, Locale locale) {
        Store store = storesRepository.findById(order.getStoreId());
        boolean closed = order.isClosed();
        boolean readOnly = closed || viewer.superAdmin();
        OrderLinks links = OrderLinks.of(order, viewer.superAdmin());
        Set<String> dropshipItemIds = dropshipItemLookup.itemIdsInDropshipDeliveries(order.getStoreId(), items);
        boolean hasDropshipItems = !dropshipItemIds.isEmpty();
        boolean hasWarehouseDocument = order.getDocumentByType(DocumentType.GoodsIssue).isPresent();
        boolean hasWarehouseItems = items.stream().filter(OrderItem::isProduct).anyMatch(i -> !dropshipItemIds.contains(i.getItemId()));
        boolean documentsEnabled = store != null && store.hasDocumentsGenerationEnabled();
        OrderClosingChecklist checklist = closed ? null
                : OrderClosingChecklist.of(order, documentsEnabled && hasWarehouseItems, messageSource, locale);
        return new OrderPageModel(order.getOrderId(), order.getShortenedOrderId(),
                viewer.superAdmin() ? null : OrderBackLink.sanitize(viewer.back()),
                closed, readOnly, viewer.superAdmin(), viewer.admin(), store == null ? null : store.getName(),
                header(order, items, store, viewer, readOnly, links, locale),
                checklist, checklist == null ? null : checklist.title(messageSource, locale),
                items(order, items, store, viewer, readOnly, links, hasDropshipItems, hasWarehouseDocument),
                shipments(order, store, readOnly),
                documents(order, viewer, closed, readOnly, documentsEnabled && hasWarehouseItems && !hasWarehouseDocument),
                payments(order, readOnly),
                CustomerView.of(order, readOnly, locale),
                settings(order, items, readOnly),
                FinancesView.of(order, items, viewer.admin()),
                history(order, readOnly),
                OrderStatusOptions.of(order));
    }

    public OrderSettingsView settings(Order order, List<OrderItem> items, boolean readOnly) {
        return OrderSettingsView.of(order, items, readOnly);
    }

    private OrderPageModel.Header header(Order order, List<OrderItem> items, Store store, Viewer viewer, boolean readOnly,
                                         OrderLinks links, Locale locale) {
        boolean canOrderShipment = order.canOrderShipment();
        OrderPageModel.PrimaryAction primary = null;
        // with items at several suppliers the dropship page without ?provider= sends the operator back to choose one,
        // so the button names the first waiting supplier, as DeliveryRedirectResolver does for the delivery link
        OrderItem firstDropship = !readOnly && viewer.admin() && order.getFulfilmentType() == FulfilmentType.DirectToConsumer
                ? items.stream().filter(OrderPageModelFactory::awaitsDropship).findFirst().orElse(null) : null;
        if (firstDropship != null) {
            primary = new OrderPageModel.PrimaryAction("order.page.action.dropship", links.details() + "/dropship?provider="
                    + URLEncoder.encode(firstDropship.getDeliveryId(), StandardCharsets.UTF_8), "fa-truck");
        } else if (!readOnly && canOrderShipment && order.hasShipmentWithoutShippingData()) {
            primary = new OrderPageModel.PrimaryAction("order.page.action.courier", links.details() + "/shipping", "fa-truck");
        }
        // the header link names one physical item; with several serial numbers the rows link each of theirs
        List<String> serials = items.stream().map(OrderItem::getSerialNo).filter(Objects::nonNull)
                .flatMap(sn -> Arrays.stream(sn.split(","))).map(String::trim).filter(sn -> !sn.isEmpty())
                .distinct().toList();
        String itemHistory = viewer.superAdmin() || serials.size() != 1 ? null
                : "/dashboard/item/history?serialNo=" + URLEncoder.encode(serials.get(0), StandardCharsets.UTF_8);
        String splitFrom = order.getSplitFromOrderId();
        boolean clientPage = store != null && store.isClientOrderPageEnabled() && !order.hasStatus(OrderStatus.Completed);
        String sourceName = order.getSource() == null ? null : StringUtils.trimToNull(order.getSource().getName());
        // the type only stands in for a missing name: "Allegro (Marketplace)" says nothing the name does not
        String sourceTypeKey = order.getSource() == null || sourceName != null ? null
                : OrderLabels.sourceType(order.getSource().getType());
        return new OrderPageModel.Header(
                OrderLabels.status(order.getStatus()), OrderLabels.tone(order.getStatus()), !readOnly,
                order.hasStatus(OrderStatus.Completed),
                clientName(order),
                sourceName,
                sourceTypeKey,
                OrderFormats.dateTime(order.getOrderedAt()), Money.format(order.getTotalPrice()),
                OrderLabels.fulfilmentType(order.getFulfilmentType()), order.getExternalOrderId(),
                RoutedSupplierView.from(order, store),
                splitFrom == null ? null : ConversionUtil.getShortenedId(splitFrom),
                splitFrom == null ? null : (viewer.superAdmin()
                        ? "/dashboard/store/" + order.getStoreId() + "/orders/" + splitFrom : "/dashboard/orders/" + splitFrom),
                clientPage && !viewer.superAdmin() ? order.createClientOrderUrl(appDomain) : null,
                primary, links.card(), links.collection(), itemHistory,
                // a completed order can still be cancelled after a full return, so this follows the viewer, not readOnly
                !viewer.superAdmin() && order.canBeCancelled(items),
                !viewer.superAdmin() && order.hasStatus(OrderStatus.New) && items.isEmpty() && !order.isInvoiced(),
                deleteMessage(order, messageSource, locale));
    }

    /** The client as the orders list names it (shipping first, company before person), then the billing e-mail. */
    public static String clientName(Order order) {
        AddressBlock shipping = AddressBlock.of(order.getShippingDetails(), Locale.ROOT);
        AddressBlock billing = AddressBlock.of(order.getBillingDetails(), Locale.ROOT);
        return Stream.of(shipping.companyOrPerson(), billing.companyOrPerson(), billing.email())
                .filter(Objects::nonNull).findFirst().orElse(null);
    }

    /** The delete confirmation warns that a marketplace order is cancelled there too. */
    public static String deleteMessage(Order order, MessageSource messages, Locale locale) {
        return order.isMarketplaceOrder()
                ? messages.getMessage("order.page.delete.confirm.marketplace", new Object[]{order.getSource().getName()}, locale)
                : messages.getMessage("order.page.delete.confirm.message", null, locale);
    }

    // What DropshipEligibility accepts: an item in Allocation with its supplier, not yet claimed by a delivery. A New
    // item is not enough — the dropship page would send the operator back ("assign a supplier first"), so the row
    // menu's "Przypisz dostawcę" is the next step there, not this button.
    private static boolean awaitsDropship(OrderItem item) {
        return item.isProduct() && item.hasOneOfTheStatuses(FulfilmentStatus.Allocation)
                && !item.isClaimed() && item.getDeliveryId() != null
                && !SupplierRegistry.WAREHOUSE.equalsIgnoreCase(item.getDeliveryId());
    }

    private OrderPageModel.ItemsCard items(Order order, List<OrderItem> items, Store store, Viewer viewer,
                                           boolean readOnly, OrderLinks links, boolean hasDropshipItems,
                                           boolean hasWarehouseDocument) {
        SupplierLabelMap labels = supplierLabels.forStore(store);
        OrderItemRow.Context context = new OrderItemRow.Context(order, viewer.admin(), readOnly, viewer.superAdmin(), labels,
                item -> deliveryHref(order, item, viewer, links),
                serial -> viewer.superAdmin() ? null
                        : "/dashboard/item/history?serialNo=" + URLEncoder.encode(serial, StandardCharsets.UTF_8));
        List<OrderItem> sorted = items.stream().sorted(Comparator.comparingInt(OrderItem::getPosition)).toList();
        List<OrderItemRow> products = new ArrayList<>();
        List<OrderItemRow> services = new ArrayList<>();
        int index = 0;
        for (OrderItem item : sorted) {
            if (item.getPosition() < PositionGroup.SERVICE_GROUP_START) {
                products.add(OrderItemRow.of(item, index++, context));
            }
        }
        for (OrderItem item : sorted) {
            if (item.getPosition() >= PositionGroup.SERVICE_GROUP_START) {
                services.add(OrderItemRow.of(item, index++, context));
            }
        }
        boolean canSplitOrder = order.canBeSplit() && !items.isEmpty();
        String addReason = addItemsLockedKey(order, hasDropshipItems);
        List<OrderPageModel.SerialItemRow> serialItems = items.stream()
                .filter(i -> i.hasOneOfTheStatuses(FulfilmentStatus.Delivered)).filter(OrderItem::isProduct)
                .map(i -> serialItemRow(i, labels)).toList();
        List<OrderPageModel.BulkActionButton> bulk = new ArrayList<>();
        for (BulkAction action : BulkAction.values()) {
            if (action == BulkAction.REMOVE && order.isInvoiced()) {
                continue;
            }
            BulkReason reason = bulkReason(action, canSplitOrder, hasDropshipItems);
            bulk.add(OrderPageModel.BulkActionButton.of(action, reason,
                    "/dashboard/orders/" + order.getOrderId() + "/" + action.path()));
        }
        boolean selectable = !readOnly && !hasWarehouseDocument;
        Map<String, SplitGroupPreviewDto> previews = readOnly ? Map.of() : items.stream()
                .filter(OrderItem::isNew).filter(OrderItem::isGroup)
                .collect(Collectors.toMap(OrderItem::getItemId, i -> SplitGroupPreviewDto.from(i, this::taxonomyName)));
        return new OrderPageModel.ItemsCard(products, services, items.size(), selectable,
                !readOnly && addReason == null, readOnly ? null : addReason,
                !readOnly && !order.hasStatus(OrderStatus.New) && !serialItems.isEmpty(), serialItems,
                bulk, selectable && (canSplitOrder || !hasDropshipItems),
                readOnly ? List.of() : productCatalogRepository.findAll(order.getStoreId()),
                readOnly ? List.of() : labels.options(), previews);
    }

    /** Why a bulk action is unavailable for the whole order, or null; package-visible so a test can walk every case. */
    static BulkReason bulkReason(BulkAction action, boolean canSplitOrder, boolean hasDropshipItems) {
        return switch (action) {
            case SPLIT, MOVE -> canSplitOrder ? null : BulkReason.SPLIT_UNAVAILABLE;
            default -> hasDropshipItems ? BulkReason.DROPSHIP_LOCKED : null;
        };
    }

    // the serial-number dialog only ever assigns serials, so it gets a slim row with no cost, not the raw item.
    private static OrderPageModel.SerialItemRow serialItemRow(OrderItem item, SupplierLabelMap labels) {
        String deliveryId = StringUtils.trimToNull(item.getDeliveryId());
        String deliveryLabel = deliveryId == null ? null
                : labels.has(deliveryId) ? labels.of(deliveryId) : item.getShortenedDeliveryId();
        return new OrderPageModel.SerialItemRow(item.getItemId(), item.getName(), item.getManufacturerCode(),
                item.getQty(), deliveryLabel, item.getSerialNo());
    }

    private String deliveryHref(Order order, OrderItem item, Viewer viewer, OrderLinks links) {
        String href = deliveryRedirectResolver.resolveFor(order, item);
        // the dropship screens are the admin's; a user or a super admin only sees the supplier's name
        if (href.contains("/dropship") && (!viewer.admin() || viewer.superAdmin())) {
            return null;
        }
        return links.forViewer(href);
    }

    private String taxonomyName(String mfn) {
        Taxonomy taxonomy = taxonomyCache.findByMfn(mfn);
        return taxonomy != null && taxonomy.name() != null ? taxonomy.name() : "";
    }

    private OrderPageModel.ShipmentsCard shipments(Order order, Store store, boolean readOnly) {
        List<OrderPageModel.ShipmentRow> rows = order.getShipments().stream()
                .map(s -> new OrderPageModel.ShipmentRow(OrderLabels.shipmentType(s.getType()), s.getCarrier(),
                        s.getTrackingNo(), safeWebUrl(s.getTrackingUrl()), s.getCollectionPointCode(),
                        OrderFormats.dateTime(s.getShippedAt()), OrderFormats.dateTime(s.getDeliveredAt()),
                        order.hasTrackedShipments() ? OrderLabels.tracking(s.getTrackingSubscriptionStatus()) : null,
                        OrderLabels.tone(s.getTrackingSubscriptionStatus()),
                        // the help sends the reader to "Edit shipments", which a read-only page does not offer
                        !readOnly && s.getTrackingSubscriptionStatus() == ShipmentTrackingStatus.FAILED
                                ? "order.shipment.tracking.failed.help" : null))
                .toList();
        String emptyKey = readOnly ? "order.shipments.empty.readonly"
                : order.getFulfilmentType() == FulfilmentType.DirectToConsumer ? "order.shipments.empty.dropship"
                : "order.shipments.empty";
        // the courier order can be cancelled only while its labelled parcel is still on the way
        boolean cancellable = order.firstShipmentWithShippingData()
                .map(s -> s.getExternalId() != null && s.getDeliveredAt() == null).orElse(false);
        // an order can genuinely have zero shipments (not yet allocated); the dialog needs one blank row to edit,
        // not an empty table that posts nothing on Save (an extra blank row next to existing ones would post
        // an empty shipment)
        List<Shipment> editable = order.getShipments().isEmpty() ? List.of(new Shipment()) : order.getShipments();
        return new OrderPageModel.ShipmentsCard(rows, emptyKey,
                !readOnly && order.canOrderShipment() && cancellable, OrderLabels.Option.of(ShipmentType.values(), OrderLabels::shipmentType),
                store == null ? List.of() : shipmentCarrierOptions.forOrder(order, store), editable);
    }

    private OrderPageModel.DocumentsCard documents(Order order, Viewer viewer, boolean closed, boolean readOnly,
                                                   boolean goodsIssue) {
        List<DocumentType> manual = manualDocumentTypes(order);
        DocumentType next = order.getNextDocumentToIssue().orElse(null);
        List<OrderPageModel.DocumentRow> rows = order.getDocuments().stream().map(d -> documentRow(order, d, viewer, closed)).toList();
        List<DocumentType> issuable = order.getIssuableDocumentTypes();
        // a consumer receipt is never issued from here, it is typed in with "Add document"; the text says so.
        // A closed order will not get another document and a read-only viewer cannot issue one, so neither names it.
        String emptyKey = readOnly || next == null ? "order.documents.empty"
                : issuable.contains(next) ? "order.documents.empty.next" : "order.documents.empty.next.manual";
        return new OrderPageModel.DocumentsCard(rows, emptyKey, !readOnly && addDocumentLockedKey(order, null) == null,
                OrderLabels.Option.of(manual, OrderLabels::documentType), next,
                next == null ? null : OrderLabels.documentType(next), OrderLabels.Option.of(issuable, OrderLabels::documentType),
                !readOnly && goodsIssue, !readOnly && (goodsIssue || !issuable.isEmpty()), OrderFormats.isoDate(LocalDate.now()));
    }

    /** The documents an operator may add by hand: B2B invoices, or a receipt / personal invoice for a consumer. */
    public static List<DocumentType> manualDocumentTypes(Order order) {
        return order.isB2B()
                ? List.of(DocumentType.InvoiceVat, DocumentType.InvoiceAdvance, DocumentType.InvoiceFinal)
                : List.of(DocumentType.Receipt, DocumentType.InvoicePersonal);
    }

    /** Why a document cannot be added by hand (null when it can): the card hides "Add document" for the same reasons. */
    public static String addDocumentLockedKey(Order order, DocumentType posted) {
        if (order.isClosed()) {
            return "order.documents.add.locked.closed";
        }
        List<DocumentType> manual = manualDocumentTypes(order);
        DocumentType next = order.getNextDocumentToIssue().orElse(null);
        if (next == null || !manual.contains(next) || (posted != null && !manual.contains(posted))) {
            return "order.documents.add.locked";
        }
        return null;
    }

    /**
     * A tracking or document link is typed by any store user and shown to every other: only a web address becomes a
     * link, anything else (javascript:, data:, ...) is dropped.
     */
    public static String safeWebUrl(String url) {
        String trimmed = StringUtils.trimToNull(url);
        if (trimmed == null) {
            return null;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://") ? trimmed : null;
    }

    private OrderPageModel.DocumentRow documentRow(Order order, Document document, Viewer viewer, boolean closed) {
        boolean removable = viewer.admin() && !viewer.superAdmin() && !closed && document.getType() != null
                && document.getType().isInvoiceOrReceipt();
        // an external link is typed in by hand, so only a web address is rendered; the internal warehouse path is ours
        String href = document.isExternal() ? safeWebUrl(document.getLink()) : document.getViewUrl();
        if (viewer.superAdmin() && href != null && !document.isExternal()) {
            href = null;
        }
        String removeHref = !removable ? null
                : OrderLinks.removeDocumentPath(order.getOrderId(), document.getType(), document.getNumber());
        return new OrderPageModel.DocumentRow(OrderLabels.documentType(document.getType()), document.getNumber(), href,
                document.isExternal(), OrderFormats.date(document.getIssuedAt()), removable, removeHref);
    }

    private OrderPageModel.PaymentsCard payments(Order order, boolean readOnly) {
        // every payment is listed, complete or not: an incomplete one is still money recorded against the order
        List<Payment> payments = order.getPayments() == null ? List.of() : order.getPayments();
        List<OrderPageModel.PaymentRow> rows = payments.stream()
                .map(p -> {
                    boolean refund = p.getDirection() == PaymentDirection.Outgoing;
                    // a refund is typed with either sign (the dialog asks for a minus, supplier payouts are stored
                    // positive); the page shows one minus whichever way it was saved
                    double shown = refund ? -Math.abs(p.getAmount()) : p.getAmount();
                    return new OrderPageModel.PaymentRow(Money.format(shown), refund, p.isUnsettled(),
                            OrderLabels.paymentSource(p.getSource()), p.getName(), p.getReferenceNo(),
                            p.getBankTransactionNo(), OrderFormats.date(p.getBankTransactionDate()),
                            p.getFee() > 0 ? Money.format(p.getFee()) : null);
                })
                .toList();
        double unpaid = order.getUnpaidAmount();
        boolean overpaid = unpaid < -0.005;
        return new OrderPageModel.PaymentsCard(rows, Money.format(order.getPaidAmount()), Money.format(Math.max(0, unpaid)),
                unpaid > 0.005, overpaid, overpaid ? Money.format(-unpaid) : null, !readOnly && !payments.isEmpty(),
                Math.max(0, unpaid),
                order.getPendingPayment(), OrderLabels.Option.of(PaymentSource.values(), OrderLabels::paymentSource),
                payments);
    }

    private OrderPageModel.HistoryCard history(Order order, boolean readOnly) {
        List<OrderPageModel.EventRow> events = orderEventsRepository.findByOrderId(order.getOrderId()).stream()
                .sorted(Comparator.comparing(OrderEvent::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(OrderPageModelFactory::eventRow)
                .toList();
        return new OrderPageModel.HistoryCard(events, order.getReview(),
                order.getReview() == null ? null : OrderLabels.reviewStatus(order.getReview().getStatus()),
                order.getReview() == null ? null : OrderFormats.date(order.getReview().getRequestedAt()),
                !readOnly, OrderLabels.Option.of(OrderReviewStatus.values(), OrderLabels::reviewStatus));
    }

    private static OrderPageModel.EventRow eventRow(OrderEvent event) {
        String at = OrderFormats.dateTime(event.getCreatedAt());
        String name = event.getName();
        if (event.getType() == EventType.email && name != null
                && Arrays.stream(EmailNotificationType.values()).anyMatch(type -> type.name().equals(name))) {
            return new OrderPageModel.EventRow(at, "order.event.type.email", "email.notification.type." + name, null);
        }
        if (event.getType() == EventType.action && KNOWN_ACTIONS.contains(name)) {
            return new OrderPageModel.EventRow(at, "order.event.type.action." + name, null, null);
        }
        return new OrderPageModel.EventRow(at, "order.event.type.other", null, name);
    }

    /** Why items cannot be added (null when they can): the page greys the button with it, the controller refuses with it. */
    public static String addItemsLockedKey(Order order, boolean hasDropshipItems) {
        if (order.isClosed()) {
            return "order.items.add.locked.closed";
        }
        if (order.getDocumentByType(DocumentType.GoodsIssue).isPresent()) {
            return "order.items.add.locked.goods.issue";
        }
        if (order.isInvoiced()) {
            return "order.items.add.locked.invoiced";
        }
        return hasDropshipItems ? "order.items.action.dropship.locked" : null;
    }
}
