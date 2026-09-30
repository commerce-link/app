package pl.commercelink.web.orders;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.deliveries.DeliveryRedirectResolver;
import pl.commercelink.inventory.deliveries.DropshipAssessment;
import pl.commercelink.inventory.deliveries.DropshipEligibility;
import pl.commercelink.inventory.deliveries.DropshipRejection;
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
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.OrderRealizationStepBack;
import pl.commercelink.orders.PositionGroup;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCarrierOptions;
import pl.commercelink.orders.ShipmentTrackingStatus;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.event.OrderEvent;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.receipts.ReceiptAlerts;
import pl.commercelink.receipts.ReceiptAttemptService;
import pl.commercelink.receipts.ReceiptAttemptState;
import pl.commercelink.receipts.ReceiptLock;
import pl.commercelink.receipts.ReceiptOrderState;
import pl.commercelink.receipts.ReceiptOrderView;
import pl.commercelink.receipts.ReceiptRequestConverter;
import pl.commercelink.shipping.ShippingService;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    private static final Set<String> KNOWN_ACTIONS = Set.of("SHIPMENT_COLLECTED", "SHIPMENT_DELIVERED", "SHIPMENT_TRACKING_FAILED",
            OrderRealizationStepBack.EVENT);

    private final StoresRepository storesRepository;
    private final OrderEventsRepository orderEventsRepository;
    private final DropshipItemLookup dropshipItemLookup;
    private final DeliveryRedirectResolver deliveryRedirectResolver;
    private final DropshipEligibility dropshipEligibility;
    private final SupplierLabels supplierLabels;
    private final ShipmentCarrierOptions shipmentCarrierOptions;
    private final ProductCatalogRepository productCatalogRepository;
    private final TaxonomyCache taxonomyCache;
    private final MessageSource messageSource;
    private final ReceiptAttemptService receiptAttemptService;
    private final ReceiptAlerts receiptAlerts;
    private final ShippingService shippingService;

    /** Fiscal dates are Polish dates, whatever zone the server runs in (as ReceiptEffects dates the document). */
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    @Value("${app.domain}")
    private String appDomain;

    /** Who looks: a super admin (read-only, store-scoped links), an admin (dropship), and the list to return to. */
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
        DropshipAssessment dropship = dropship(order, items);
        // the order's e-receipt attempts are read once here; the documents card and every lock below derive from them
        ReceiptOrderState receipts = receiptAttemptService.orderState(store, order, receiptAlerts, locale);
        ReceiptLock receiptLock = receipts.receiptLock(order);
        return new OrderPageModel(order.getOrderId(), order.getShortenedOrderId(),
                viewer.superAdmin() ? null : OrderBackLink.sanitize(viewer.back()),
                closed, readOnly, viewer.superAdmin(), viewer.admin(), store == null ? null : store.getName(),
                header(order, items, store, viewer, readOnly, links, locale, dropship, receipts, receiptLock),
                items(order, items, store, viewer, readOnly, links, hasDropshipItems, hasWarehouseDocument, dropship,
                        receiptLock, locale),
                shipments(order, store, readOnly),
                documents(order, store, viewer, closed, readOnly,
                        documentsEnabled && hasWarehouseItems && !hasWarehouseDocument, receipts, receiptLock),
                payments(order, readOnly, receiptLock),
                CustomerView.of(order, readOnly, receiptLock, locale),
                settings(order, items, readOnly),
                FinancesView.of(order, items),
                history(order, readOnly),
                OrderStatusOptions.of(order));
    }

    public OrderSettingsView settings(Order order, List<OrderItem> items, boolean readOnly) {
        return OrderSettingsView.of(order, items, readOnly);
    }

    private OrderPageModel.Header header(Order order, List<OrderItem> items, Store store, Viewer viewer, boolean readOnly,
                                         OrderLinks links, Locale locale, DropshipAssessment dropship,
                                         ReceiptOrderState receipts, ReceiptLock receiptLock) {
        boolean receiptLocked = receiptLock.locks();
        boolean canOrderShipment = order.canOrderShipment();
        OrderPageModel.PrimaryAction primary = null;
        // with items at several suppliers the dropship page without ?provider= sends the operator back to choose one,
        // so the button names the first waiting supplier the dropship page accepts (DropshipEligibility, as the
        // delivery link and the deliveries planning); a supplier without dropshipping is ordered through the
        // warehouse route, so it gets no button that would only end on the page's refusal
        OrderItem firstDropship = !readOnly && viewer.admin() && order.getFulfilmentType() == FulfilmentType.DirectToConsumer
                ? items.stream().filter(OrderPageModelFactory::awaitsDropship)
                        .filter(item -> dropship.supports(item.getDeliveryId())).findFirst().orElse(null) : null;
        if (firstDropship != null) {
            primary = new OrderPageModel.PrimaryAction("order.page.action.dropship", links.details() + "/dropship?provider="
                    + URLEncoder.encode(firstDropship.getDeliveryId(), StandardCharsets.UTF_8), "fa-truck");
        } else if (!readOnly && canOrderShipment && order.hasShipmentToBook()
                && shippingService.isAvailable(store)) {
            // the courier page's own rule (OrdersShippingController#initiate): a store without a courier account types
            // the shipping data into the shipment, so the page would only end on its refusal
            primary = new OrderPageModel.PrimaryAction("order.page.action.courier", links.details() + "/shipping", "fa-truck");
        }
        String splitFrom = order.getSplitFromOrderId();
        boolean clientPage = store != null && store.isClientOrderPageEnabled() && !order.hasStatus(OrderStatus.Completed);
        String sourceName = order.getSource() == null ? null : StringUtils.trimToNull(order.getSource().getName());
        // the type only stands in for a missing name: "Allegro (Marketplace)" says nothing the name does not
        String sourceTypeKey = order.getSource() == null || sourceName != null ? null
                : OrderLabels.sourceType(order.getSource().getType());
        // as OrdersController#deleteOrder refuses: an order whose e-receipt is being issued stays
        boolean canDelete = !viewer.superAdmin() && order.hasStatus(OrderStatus.New) && items.isEmpty()
                && !order.isInvoiced() && !receiptLocked;
        return new OrderPageModel.Header(
                OrderLabels.status(order.getStatus()), OrderLabels.tone(order.getStatus()), !readOnly,
                order.hasStatus(OrderStatus.Completed),
                clientName(order),
                sourceName,
                sourceTypeKey,
                OrderFormats.dateTime(order.getOrderedAt()), Money.format(order.getTotalPrice()),
                OrderLabels.fulfilmentTypeShort(order.getFulfilmentType()), OrderLabels.fulfilmentTypeIcon(order.getFulfilmentType()),
                order.getExternalOrderId(),
                RoutedSupplierView.from(order, store),
                splitFrom == null ? null : ConversionUtil.getShortenedId(splitFrom),
                splitFrom == null ? null : (viewer.superAdmin()
                        ? "/dashboard/store/" + order.getStoreId() + "/orders/" + splitFrom : "/dashboard/orders/" + splitFrom),
                // the link is public and changes nothing, so a super admin (support) may copy it too
                clientPage ? order.createClientOrderUrl(appDomain) : null,
                primary, links.card(), links.collection(),
                // a completed order can still be cancelled after a full return, so this follows the viewer, not readOnly;
                // while an e-receipt is being issued OrdersController#cancelOrder refuses it, so it is greyed with that
                // reason (only when the order could otherwise be cancelled: the general reason says more otherwise)
                !viewer.superAdmin() && order.canBeCancelled(items) && !receiptLocked,
                !viewer.superAdmin() && order.canBeCancelled(items) && receiptLocked
                        ? receiptLock.key(CANCEL_LOCKED_RECEIPT) : null,
                cancelUnavailableKey(order, items, canDelete),
                cancelMessage(receipts.hasFiscalisedReceipt(order), messageSource, locale),
                canDelete,
                deleteMessage(order, messageSource, locale));
    }

    private static final String CANCEL_LOCKED_RECEIPT = "order.page.cancel.locked.receipt";

    /**
     * Why "Anuluj zamówienie" is greyed when Order#canBeCancelled says no (the rule stays as on main), built from
     * Order#cancelBlockers so the reason cannot drift from the rule. Before delivery the reason suggests "Usuń
     * zamówienie" only when the menu offers it; otherwise, and after delivery, it names what is still missing.
     */
    static String cancelUnavailableKey(Order order, List<OrderItem> items, boolean canDelete) {
        Set<Order.CancelBlocker> blockers = order.cancelBlockers(items);
        if (blockers.contains(Order.CancelBlocker.NOT_DELIVERED)) {
            return canDelete ? "order.page.cancel.unavailable.delete" : "order.page.cancel.unavailable.open";
        }
        boolean products = blockers.contains(Order.CancelBlocker.PRODUCTS_NOT_RETURNED);
        boolean payments = blockers.contains(Order.CancelBlocker.PAYMENTS_NOT_REFUNDED);
        if (products && payments) {
            return "order.page.cancel.unavailable.itemsAndPayments";
        }
        if (products) {
            return "order.page.cancel.unavailable.items";
        }
        return payments ? "order.page.cancel.unavailable.payments" : null;
    }

    /**
     * The cancel confirmation, in the dialog and on the no-JS page: once the e-receipt is fiscalised (or closed by
     * hand) it says that cancelling the order does not undo the receipt and the refund is settled separately.
     */
    public static String cancelMessage(boolean fiscalisedReceipt, MessageSource messages, Locale locale) {
        String message = messages.getMessage("order.page.cancel.confirm.message", null, locale);
        return fiscalisedReceipt
                ? message + " " + messages.getMessage("order.page.cancel.confirm.receipt", null, locale)
                : message;
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
                                           boolean hasWarehouseDocument, DropshipAssessment dropship,
                                           ReceiptLock receiptLock, Locale locale) {
        SupplierLabelMap labels = labels(store, locale);
        OrderItemRow.Context context = new OrderItemRow.Context(order, readOnly, viewer.superAdmin(), labels,
                item -> deliveryHref(order, item, viewer, links, dropship),
                serial -> viewer.superAdmin() ? null : OrderLinks.itemHistory(serial),
                receiptLock, hasDropshipItems);
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
        String addReason = addItemsLockedKey(order, hasDropshipItems, receiptLock);
        List<OrderPageModel.SerialItemRow> serialItems = items.stream()
                .filter(i -> i.hasOneOfTheStatuses(FulfilmentStatus.Delivered)).filter(OrderItem::isProduct)
                .map(i -> serialItemRow(i, labels)).toList();
        List<OrderPageModel.BulkActionButton> bulk = new ArrayList<>();
        for (BulkAction action : BulkAction.values()) {
            if (!action.inSelectionRow() || action == BulkAction.REMOVE && order.isInvoiced()) {
                continue;
            }
            BulkReason reason = bulkReason(action, canSplitOrder, hasDropshipItems, receiptLock);
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
        return bulkReason(action, canSplitOrder, hasDropshipItems, false);
    }

    /**
     * receiptLocked: an e-receipt is being issued, so removing, splitting off and moving items is locked as once the
     * order is invoiced (OrdersController refuses the same); routing items (allocation, warehouse) changes nothing the
     * receipt carries and stays.
     */
    static BulkReason bulkReason(BulkAction action, boolean canSplitOrder, boolean hasDropshipItems, boolean receiptLocked) {
        return bulkReason(action, canSplitOrder, hasDropshipItems, ReceiptLock.of(receiptLocked));
    }

    /** The same, worded after why the e-receipt locks the order (still issuing, or fiscalised and being attached). */
    static BulkReason bulkReason(BulkAction action, boolean canSplitOrder, boolean hasDropshipItems, ReceiptLock receiptLock) {
        if (receiptLock.locks() && (action == BulkAction.REMOVE || action == BulkAction.SPLIT || action == BulkAction.MOVE)) {
            return switch (receiptLock) {
                case ATTACHING -> BulkReason.RECEIPT_ATTACHING;
                case ATTACH_FAILED -> BulkReason.RECEIPT_ATTACH_FAILED;
                default -> BulkReason.RECEIPT_ISSUING;
            };
        }
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

    /**
     * The delivery of one item as its row in the items table shows it; null when the item has none. items are all of
     * the order's items: whether the item's supplier may dropship depends on the whole order.
     */
    public OrderItemRow.Delivery delivery(Order order, OrderItem item, List<OrderItem> items, Viewer viewer, Locale locale) {
        if (StringUtils.isBlank(item.getDeliveryId())) {
            return null;
        }
        SupplierLabelMap labels = labels(storesRepository.findById(order.getStoreId()), locale);
        return new OrderItemRow.Delivery(OrderItemRow.deliveryLabel(item, labels),
                deliveryHref(order, item, viewer, OrderLinks.of(order, viewer.superAdmin()), dropship(order, items)),
                deliveryRedirectResolver.pointsToDelivery(item));
    }

    /** The store's supplier labels, with its own warehouse read as "Magazyn sklepu" rather than its technical id. */
    private SupplierLabelMap labels(Store store, Locale locale) {
        return supplierLabels.forStore(store).withWarehouse(warehouseLabel(messageSource, locale));
    }

    /** What the store's own warehouse is called where an item's supplier is shown (items, item page, printouts). */
    public static String warehouseLabel(MessageSource messages, Locale locale) {
        return messages.getMessage("order.item.delivery.warehouse", null, locale);
    }

    /** Assessed only for a direct-to-consumer order: a warehouse order never leads to the dropship page anyway. */
    private DropshipAssessment dropship(Order order, List<OrderItem> items) {
        return order.getFulfilmentType() == FulfilmentType.DirectToConsumer
                ? dropshipEligibility.assess(order, items)
                : DropshipAssessment.rejected(DropshipRejection.WAREHOUSE_FULFILMENT);
    }

    private String deliveryHref(Order order, OrderItem item, Viewer viewer, OrderLinks links, DropshipAssessment dropship) {
        String href = deliveryRedirectResolver.resolveFor(order, item, dropship);
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
        List<Shipment> shipments = order.getShipments();
        LocalDateTime now = LocalDateTime.now();
        List<String> carriers = readOnly || store == null ? List.of() : shipmentCarrierOptions.forOrder(order, store);
        String base = "/dashboard/orders/" + order.getOrderId() + "/shipments/";
        List<OrderPageModel.ShipmentRow> rows = new ArrayList<>();
        List<OrderShipmentForm> forms = new ArrayList<>();
        // the courier order can be cancelled only while its labelled parcel is still on the way; the same shipment
        // ShipmentCancelService cancels, found by its courier order whatever its shipped date says
        Shipment courierCancellable = order.canOrderShipment() ? order.courierShipmentToCancel().orElse(null) : null;
        boolean placeholder = order.onlyPlaceholder().isPresent();
        for (int i = 0; i < shipments.size(); i++) {
            Shipment s = shipments.get(i);
            OrderShipmentForm form = OrderShipmentForm.of(order.getOrderId(), i, s, carriers);
            rows.add(new OrderPageModel.ShipmentRow(i + 1, OrderLabels.shipmentType(s.getType()), s.getCarrier(),
                    s.getTrackingNo(), safeWebUrl(s.getTrackingUrl()), s.getCollectionPointCode(),
                    OrderFormats.moment(s.getShippedAt()), OrderFormats.moment(s.getDeliveredAt()),
                    order.hasTrackedShipments() ? OrderLabels.tracking(s.getTrackingSubscriptionStatus()) : null,
                    OrderLabels.tone(s.getTrackingSubscriptionStatus()),
                    // the help sends the reader to "Edit", which a read-only page does not offer
                    !readOnly && s.getTrackingSubscriptionStatus() == ShipmentTrackingStatus.FAILED
                            ? "order.shipment.tracking.failed.help" : null,
                    OrderLabels.cancellation(s, now), OrderLabels.cancellationTone(s, now),
                    form.dialogId(), readOnly ? null : base + i,
                    readOnly || removeLockedKey(order, i) != null ? null
                            : base + i + "/remove?version=" + form.version(),
                    // every parcel of one courier order carries its externalId, and cancelling it cancels them all
                    readOnly ? null : removeReasonKey(order, i, courierCancellable != null
                            && Objects.equals(s.getExternalId(), courierCancellable.getExternalId())),
                    removeShipmentMessageKey(order, i),
                    removeShipmentActionKey(order, i), placeholder));
            if (!readOnly) {
                forms.add(form);
            }
        }
        String emptyKey = readOnly ? "order.shipments.empty.readonly"
                : order.getFulfilmentType() == FulfilmentType.DirectToConsumer ? "order.shipments.empty.dropship"
                : "order.shipments.empty";
        boolean canCancelCourier = !readOnly && courierCancellable != null;
        // a second command while the first may still succeed would fail on the cancelled package (the server refuses it too)
        String cancelCourierLockedKey = canCancelCourier && courierCancellable.isCancellationInProgress(now)
                ? "order.shipments.cancel.locked.pending" : null;
        // the super admin page is store-scoped by its path and has no polling route; it is refreshed by hand
        String pollHref = !readOnly && shipments.stream().anyMatch(s -> s.isCancellationInProgress(now))
                ? "/dashboard/orders/" + order.getOrderId() + "/shipments/cancellation-state" : null;
        return new OrderPageModel.ShipmentsCard(rows, emptyKey, canCancelCourier, cancelCourierLockedKey, pollHref, forms,
                readOnly ? null : OrderShipmentForm.blank(order, carriers));
    }

    private static final String PLACEHOLDER_LOCKED = "order.shipments.remove.error.placeholder";

    /**
     * The short reason next to a greyed "Remove" in the row; the refusal of a forced removal says it in full. A
     * shipment with a courier order points to "Cancel courier order" only when the card offers it for that shipment's
     * courier order (which covers every parcel of it): before the order is ready to ship the button is not there yet,
     * and it only ever cancels the first courier order on the list whose parcel is not delivered.
     */
    private static String removeReasonKey(Order order, int index, boolean courierCancellable) {
        String locked = removeLockedKey(order, index);
        // the placeholder's row reads as "no shipment yet" with "Uzupełnij": no greyed "Remove" to explain
        if (locked == null || locked.equals(PLACEHOLDER_LOCKED)) {
            return null;
        }
        if (locked.equals("order.shipments.remove.error.courier") && !courierCancellable) {
            return order.canOrderShipment() ? "order.shipments.remove.locked.courierNotFirst"
                    : "order.shipments.remove.locked.courierLater";
        }
        return locked.replace(".remove.error.", ".remove.locked.");
    }

    /**
     * Why the shipment at index cannot be removed, or null. The only shipment can go, with the customer's delivery
     * choice (the user's decision of 2026-09-30): the order waits for the next one (OrderLifecycle neither delivers nor
     * completes an order without shipments before Delivered; a Shipping order left with nothing shipped goes back to
     * Realization). A delivered order keeps its shipments, they are the record of the delivery; so does a shipment with
     * a delivery date. One with a courier order is cancelled with "Cancel courier order", which also cancels the paid
     * label at the carrier, never by dropping the record; after a failed or unconfirmed cancellation the operator settles
     * the label in the provider's panel and may drop the record. The only shipment with nothing but the customer's choice
     * of delivery (the one every order is created with) is not removed either: its row reads as "no shipment yet" with
     * "Uzupełnij", and removing it would only lose the choice.
     */
    public static String removeLockedKey(Order order, int index) {
        if (order.getStatus() == OrderStatus.Delivered) {
            return "order.shipments.remove.error.delivered";
        }
        if (order.onlyPlaceholder().isPresent()) {
            return PLACEHOLDER_LOCKED;
        }
        Shipment shipment = order.getShipments().get(index);
        if (shipment.getDeliveredAt() != null) {
            return "order.shipments.remove.error.shipmentDelivered";
        }
        return shipment.getExternalId() != null && !shipment.isCancellationUnresolved()
                ? "order.shipments.remove.error.courier" : null;
    }

    private OrderPageModel.DocumentsCard documents(Order order, Store store, Viewer viewer, boolean closed,
                                                   boolean readOnly, boolean goodsIssue, ReceiptOrderState receipts,
                                                   ReceiptLock receiptLock) {
        List<DocumentType> manual = manualDocumentTypes(order, receipts.blocksManualReceipt());
        DocumentType next = order.getNextDocumentToIssue().orElse(null);
        // the document an e-receipt attempt attached (its id is the attempt's key) is the e-receipt row itself, never
        // listed again as a "Paragon" that could be unpinned; a receipt typed in by hand stays an ordinary row
        List<OrderPageModel.DocumentRow> rows = order.getDocuments().stream()
                .filter(d -> !isAutomaticReceipt(d, receipts))
                .map(d -> documentRow(order, d, viewer, closed)).toList();
        // while an attempt owns the receipt the invoicing system must not issue a second sale document; the
        // controller refuses it too (OrdersController#createInvoice)
        List<DocumentType> issuable = receipts.blocksManualReceipt() ? List.of() : order.getIssuableDocumentTypes();
        boolean canIssueReceipt = !readOnly && receipts.canIssueManually();
        // a POS sale without the customer's e-mail would only get one more blocked attempt: "E-paragon" and "Wystaw
        // ponownie" stay in place, greyed with what to do first (ReceiptAttemptService refuses both the same way)
        String posNeedsEmailKey = ReceiptRequestConverter.blocksPosWithoutCustomerEmail(order, store)
                ? ReceiptAttemptService.POS_NEEDS_EMAIL : null;
        // A consumer receipt is typed in with "Add document", or issued as an e-receipt from "Issue" when the store
        // has a receipt system; the text says so. A closed order will not get another document and a read-only
        // viewer cannot issue one, so neither names it.
        String emptyKey = readOnly || next == null ? "order.documents.empty"
                : issuable.contains(next) ? "order.documents.empty.next"
                : next == DocumentType.Receipt && canIssueReceipt ? "order.documents.empty.next.receipt"
                : "order.documents.empty.next.manual";
        String addLocked = readOnly ? null : addDocumentLockedKey(order, null, receiptLock);
        return new OrderPageModel.DocumentsCard(rows,
                receiptRow(order, receipts, viewer, readOnly, receiptLock, posNeedsEmailKey), emptyKey,
                !readOnly && addLocked == null,
                // greyed with its reason only for the receipt; the other locks leave the button out, as before
                receiptLock.locks() && receiptLock.key(RECEIPT_ADD_LOCKED).equals(addLocked) ? addLocked : null,
                OrderLabels.Option.of(manual, OrderLabels::documentType), next,
                next == null ? null : OrderLabels.documentType(next), OrderLabels.Option.of(issuable, OrderLabels::documentType),
                !readOnly && goodsIssue, canIssueReceipt, canIssueReceipt ? posNeedsEmailKey : null,
                !readOnly && (goodsIssue || !issuable.isEmpty() || canIssueReceipt),
                OrderFormats.isoDate(LocalDate.now()), closeForms(order, receipts, viewer));
    }

    private static final String RECEIPT_ADD_LOCKED = "order.documents.add.locked.receipt";

    private static boolean isAutomaticReceipt(Document document, ReceiptOrderState receipts) {
        return document.getType() == DocumentType.Receipt && receipts.attemptOfDocument(document.getId()).isPresent();
    }

    /**
     * The e-receipt row: the newest attempt (the only one that can still be live, a new attempt is created only once
     * every earlier one is dead) with the earlier ones under it. A super admin sees it without actions; a store user
     * keeps them on a closed order too: resending the buyer's e-mail or closing a hung attempt by hand changes the
     * receipt, not the order, and the old order page offered them on every order.
     */
    private OrderPageModel.ReceiptRow receiptRow(Order order, ReceiptOrderState receipts, Viewer viewer, boolean readOnly,
                                                 ReceiptLock receiptLock, String posNeedsEmailKey) {
        List<ReceiptOrderView.Row> rows = receipts.view().rows();
        if (rows.isEmpty()) {
            return null;
        }
        ReceiptOrderView.Row newest = rows.get(0);
        boolean act = !viewer.superAdmin();
        Document document = order.getDocuments().stream()
                .filter(d -> d.getType() == DocumentType.Receipt && newest.key().equals(d.getId()))
                .findFirst().orElse(null);
        String dateKey = null;
        String date = null;
        if (newest.fiscalisedAt() != null) {
            dateKey = "receipts.row.fiscalised";
            date = OrderFormats.date(LocalDate.ofInstant(newest.fiscalisedAt(), WARSAW));
        } else if (newest.state() == ReceiptAttemptState.CLOSED_MANUALLY && document != null && document.getIssuedAt() != null) {
            dateKey = "receipts.row.closed";
            date = OrderFormats.date(document.getIssuedAt());
        }
        String emailKey = newest.emailSentAt() != null ? "receipts.email.sent"
                : newest.emailSkippedAt() != null ? "receipts.email.skipped" : null;
        ReceiptCloseForm close = new ReceiptCloseForm(order.getOrderId(), newest.key(), newest.attemptNo());
        boolean canReissue = !readOnly && receipts.view().canReissue();
        List<OrderPageModel.ReceiptEarlierRow> earlier = rows.subList(1, rows.size()).stream()
                .map(r -> new OrderPageModel.ReceiptEarlierRow(r.attemptNo(), r.statusKey(), r.statusTone(), r.outcome()))
                .toList();
        return new OrderPageModel.ReceiptRow(newest.key(), newest.attemptNo(), newest.receiptNumber(),
                safeWebUrl(newest.documentUrl()), newest.statusKey(), newest.statusTone(), dateKey, date, emailKey,
                newest.problem(), newest.problemTone(), act && newest.canCheck(), act && newest.canResendEmail(),
                act && newest.canClose(),
                // a new attempt changes the order's documents, which a read-only page never does
                canReissue, canReissue ? posNeedsEmailKey : null, ReceiptAttemptService.reissueConfirmMessageKey(order),
                close.dialogId(), act ? close.pageHref() : null, earlier,
                newest.settled(), newest.settled() ? newest.outcome() : null,
                newest.settled() ? settledKey(order, newest.outcome()) : null,
                // the pill says fiscalised while the order still waits for the document the locks name: say so here,
                // unless the row has a problem (e.g. attaching keeps failing), which says what to do instead
                receiptLock == ReceiptLock.ATTACHING && newest.problem() == null ? "receipts.row.attaching" : null);
    }

    /** A cancelled order needs no sale document at all; any other settled order already has its receipt or invoice. */
    private static String settledKey(Order order, String outcome) {
        if (order.getStatus() == OrderStatus.Cancelled) {
            return outcome != null ? "receipts.row.settled.cancelled" : "receipts.row.settled.cancelled.noOutcome";
        }
        return outcome != null ? "receipts.row.settled.outcome" : "receipts.row.settled";
    }

    /** One "Zamknij ręcznie" dialog per attempt that offers it; none for a super admin. */
    private static List<ReceiptCloseForm> closeForms(Order order, ReceiptOrderState receipts, Viewer viewer) {
        if (viewer.superAdmin()) {
            return List.of();
        }
        return receipts.view().rows().stream().filter(ReceiptOrderView.Row::canClose)
                .map(r -> new ReceiptCloseForm(order.getOrderId(), r.key(), r.attemptNo())).toList();
    }

    /** The documents an operator may add by hand: B2B invoices, or a receipt / personal invoice for a consumer. */
    public static List<DocumentType> manualDocumentTypes(Order order) {
        return order.isB2B()
                ? List.of(DocumentType.InvoiceVat, DocumentType.InvoiceAdvance, DocumentType.InvoiceFinal)
                : List.of(DocumentType.Receipt, DocumentType.InvoicePersonal);
    }

    /**
     * The same without Receipt while an e-receipt attempt owns the order's receipt (issuing, fiscalised or closed by
     * hand): a typed one would be a second receipt for the same sale (OrdersController#addReceipt refuses it).
     */
    public static List<DocumentType> manualDocumentTypes(Order order, boolean blocksManualReceipt) {
        List<DocumentType> types = manualDocumentTypes(order);
        return blocksManualReceipt ? types.stream().filter(t -> t != DocumentType.Receipt).toList() : types;
    }

    /** Why a document cannot be added by hand (null when it can): the card hides "Add document" for the same reasons. */
    public static String addDocumentLockedKey(Order order, DocumentType posted) {
        return addDocumentLockedKey(order, posted, false);
    }

    /**
     * receiptLocked: an e-receipt is being issued (ReceiptOrderState#locksOrder); like an issued invoice it leaves no
     * room for another closing document typed in by hand, of any type.
     */
    public static String addDocumentLockedKey(Order order, DocumentType posted, boolean receiptLocked) {
        return addDocumentLockedKey(order, posted, ReceiptLock.of(receiptLocked));
    }

    /** The same, worded after why the e-receipt locks the order (still issuing, or fiscalised and being attached). */
    public static String addDocumentLockedKey(Order order, DocumentType posted, ReceiptLock receiptLock) {
        if (order.isClosed()) {
            return "order.documents.add.locked.closed";
        }
        List<DocumentType> manual = manualDocumentTypes(order);
        DocumentType next = order.getNextDocumentToIssue().orElse(null);
        if (next == null || !manual.contains(next) || (posted != null && !manual.contains(posted))) {
            return "order.documents.add.locked";
        }
        return receiptLock.locks() ? receiptLock.key(RECEIPT_ADD_LOCKED) : null;
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
        return new OrderPageModel.DocumentRow(OrderLabels.documentPrefix(document.getType()), document.getNumber(), href,
                document.isExternal(), OrderFormats.date(document.getIssuedAt()), removable, removeHref);
    }

    private OrderPageModel.PaymentsCard payments(Order order, boolean readOnly, ReceiptLock receiptLock) {
        // every payment is listed, complete or not: an incomplete one is still money recorded against the order
        List<Payment> payments = order.getPayments() == null ? List.of() : order.getPayments();
        String methodLocked = OrderPaymentForm.methodLockedKey(order, receiptLock);
        String base = "/dashboard/orders/" + order.getOrderId() + "/payments/";
        List<OrderPageModel.PaymentRow> rows = new ArrayList<>();
        List<OrderPaymentForm> forms = new ArrayList<>();
        for (int i = 0; i < payments.size(); i++) {
            Payment p = payments.get(i);
            OrderPaymentForm form = OrderPaymentForm.of(order.getOrderId(), i, p, methodLocked);
            boolean refund = OrderPaymentForm.isRefund(p);
            // the row shows what "Wpłacono" counts: a refund saved positive by older code shows as the payment it is
            // counted as, not with a minus the totals do not have
            double shown = p.getAppliedAmount();
            String locked = removePaymentLockedKey(order, i);
            rows.add(new OrderPageModel.PaymentRow(i + 1, Money.format(shown), refund, p.isUnsettled(),
                    OrderLabels.paymentSource(p.getSource()), p.getName(), p.getReferenceNo(),
                    p.getBankTransactionNo(), OrderFormats.date(p.getBankTransactionDate()),
                    p.getFee() > 0 ? Money.format(p.getFee()) : null,
                    form.dialogId(), readOnly ? null : base + i,
                    readOnly || locked != null ? null : base + i + "/remove?version=" + form.version(),
                    removePaymentMessageKey(order),
                    readOnly || locked == null ? null : locked.replace(".remove.error.", ".remove.locked.")));
            if (!readOnly) {
                forms.add(form);
            }
        }
        double unpaid = order.getUnpaidAmount();
        boolean overpaid = unpaid < -0.005;
        return new OrderPageModel.PaymentsCard(rows, Money.format(order.getPaidAmount()), Money.format(Math.max(0, unpaid)),
                unpaid > 0.005, overpaid, overpaid ? Money.format(-unpaid) : null,
                Math.max(0, unpaid),
                order.getPendingPayment(), OrderLabels.Option.of(PaymentSource.values(), OrderLabels::paymentSource),
                forms);
    }

    /**
     * Why the payment at index cannot be removed, or null. The pending payment (amount 0) is not money but the method
     * the customer chose: "Dodaj wpłatę" fills it, so removing it would only lose the method.
     */
    public static String removePaymentLockedKey(Order order, int index) {
        return order.getPayments().get(index).isUnsettled() ? "order.payments.remove.error.pending" : null;
    }

    /**
     * The confirmation's text, which says what the removal does. Removing the last shipment not yet delivered while the
     * others are delivered moves the order to Delivered in the same save (OrderLifecycle), with what follows from it: the
     * goods issue note, the e-receipt for the customer, the notice to the marketplace. Removing the only shipment
     * removes how the customer asked to receive the order (type, pickup point) with it. Removing the last shipment that
     * went out of a Shipping order moves it back to Realization (OrderRealizationStepBack), without an e-mail to the
     * customer.
     */
    public static String removeShipmentMessageKey(Order order, int index) {
        Shipment shipment = order.getShipments().get(index);
        // removable only because its cancellation failed or is unknown: the label may still be live at the carrier.
        // This warning wins over the delivery text; the button still says the removal delivers the order.
        if (shipment.getExternalId() != null && shipment.isCancellationUnresolved()) {
            return "order.shipments.remove.confirm.message.cancellationUnresolved";
        }
        if (removalDelivers(order, index)) {
            return "order.shipments.remove.confirm.delivers";
        }
        String key = order.getShipments().size() == 1 ? "order.shipments.remove.confirm.message.last"
                : "order.shipments.remove.confirm.message";
        if (removalReturnsToRealization(order, index)) {
            return key + ".realization";
        }
        return key;
    }

    /** Whether removing the shipment at index leaves a Shipping order with nothing gone out (no shipment at all included). */
    public static boolean removalReturnsToRealization(Order order, int index) {
        if (order.getStatus() != OrderStatus.Shipping) {
            return false;
        }
        List<Shipment> rest = new ArrayList<>(order.getShipments());
        rest.remove(index);
        return rest.stream().noneMatch(Shipment::hasGoneOut);
    }

    /** The confirmation's button: it names the delivery when the removal delivers the order. */
    public static String removeShipmentActionKey(Order order, int index) {
        return removalDelivers(order, index) ? "order.shipments.remove.confirm.action.delivers"
                : "order.shipments.remove.confirm.action";
    }

    /**
     * Whether OrderLifecycle moves the order to Delivered once the shipment at index is gone: every other shipment is
     * delivered, and the order is Shipping or, from Assembled or Realization, every other shipment has gone out.
     */
    static boolean removalDelivers(Order order, int index) {
        List<Shipment> rest = new ArrayList<>(order.getShipments());
        rest.remove(index);
        if (rest.isEmpty() || !rest.stream().allMatch(s -> s.getDeliveredAt() != null)) {
            return false;
        }
        return order.getStatus() == OrderStatus.Shipping
                || order.getStatus().isOneOf(OrderStatus.Assembled, OrderStatus.Realization)
                && rest.stream().allMatch(s -> s.hasShippingData() || s.hasCollectionData());
    }

    /**
     * The confirmation's text. Removing the only payment leaves a pending one with the same method (the order never
     * loses how the customer pays), which the operator is told before confirming.
     */
    public static String removePaymentMessageKey(Order order) {
        return order.getPayments().size() == 1 ? "order.payments.remove.confirm.message.last"
                : "order.payments.remove.confirm.message";
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
        return addItemsLockedKey(order, hasDropshipItems, false);
    }

    /** receiptLocked: an e-receipt is being issued; its frozen request lists the items, so none is added. */
    public static String addItemsLockedKey(Order order, boolean hasDropshipItems, boolean receiptLocked) {
        return addItemsLockedKey(order, hasDropshipItems, ReceiptLock.of(receiptLocked));
    }

    /** The same, worded after why the e-receipt locks the order (still issuing, or fiscalised and being attached). */
    public static String addItemsLockedKey(Order order, boolean hasDropshipItems, ReceiptLock receiptLock) {
        if (order.isClosed()) {
            return "order.items.add.locked.closed";
        }
        if (order.getDocumentByType(DocumentType.GoodsIssue).isPresent()) {
            return "order.items.add.locked.goods.issue";
        }
        if (order.isInvoiced()) {
            return "order.items.add.locked.invoiced";
        }
        if (receiptLock.locks()) {
            return receiptLock.key("order.items.add.locked.receipt");
        }
        return hasDropshipItems ? "order.items.action.dropship.locked" : null;
    }
}
