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
import pl.commercelink.orders.PaymentDirection;
import pl.commercelink.orders.PaymentSource;
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
import pl.commercelink.receipts.ReceiptOrderState;
import pl.commercelink.receipts.ReceiptOrderView;
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

    private static final Set<String> KNOWN_ACTIONS = Set.of("SHIPMENT_COLLECTED", "SHIPMENT_DELIVERED", "SHIPMENT_TRACKING_FAILED");

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
        boolean receiptLocked = receipts.locksOrder(order);
        return new OrderPageModel(order.getOrderId(), order.getShortenedOrderId(),
                viewer.superAdmin() ? null : OrderBackLink.sanitize(viewer.back()),
                closed, readOnly, viewer.superAdmin(), viewer.admin(), store == null ? null : store.getName(),
                header(order, items, store, viewer, readOnly, links, locale, dropship, receipts, receiptLocked),
                items(order, items, store, viewer, readOnly, links, hasDropshipItems, hasWarehouseDocument, dropship,
                        receiptLocked),
                shipments(order, store, readOnly),
                documents(order, viewer, closed, readOnly, documentsEnabled && hasWarehouseItems && !hasWarehouseDocument,
                        receipts, receiptLocked),
                payments(order, readOnly),
                CustomerView.of(order, readOnly, receiptLocked, locale),
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
                                         ReceiptOrderState receipts, boolean receiptLocked) {
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
                OrderLabels.fulfilmentTypeShort(order.getFulfilmentType()), OrderLabels.fulfilmentTypeIcon(order.getFulfilmentType()),
                order.getExternalOrderId(),
                RoutedSupplierView.from(order, store),
                splitFrom == null ? null : ConversionUtil.getShortenedId(splitFrom),
                splitFrom == null ? null : (viewer.superAdmin()
                        ? "/dashboard/store/" + order.getStoreId() + "/orders/" + splitFrom : "/dashboard/orders/" + splitFrom),
                // the link is public and changes nothing, so a super admin (support) may copy it too
                clientPage ? order.createClientOrderUrl(appDomain) : null,
                primary, links.card(), links.collection(), itemHistory,
                // a completed order can still be cancelled after a full return, so this follows the viewer, not readOnly;
                // while an e-receipt is being issued OrdersController#cancelOrder refuses it, so it is greyed with that
                // reason (only when the order could otherwise be cancelled: the general reason says more otherwise)
                !viewer.superAdmin() && order.canBeCancelled(items) && !receiptLocked,
                !viewer.superAdmin() && order.canBeCancelled(items) && receiptLocked ? CANCEL_LOCKED_RECEIPT : null,
                cancelMessage(receipts.hasFiscalisedReceipt(order), messageSource, locale),
                // as OrdersController#deleteOrder refuses: an order whose e-receipt is being issued stays
                !viewer.superAdmin() && order.hasStatus(OrderStatus.New) && items.isEmpty() && !order.isInvoiced()
                        && !receiptLocked,
                deleteMessage(order, messageSource, locale));
    }

    private static final String CANCEL_LOCKED_RECEIPT = "order.page.cancel.locked.receipt";

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
                                           boolean receiptLocked) {
        SupplierLabelMap labels = supplierLabels.forStore(store);
        OrderItemRow.Context context = new OrderItemRow.Context(order, readOnly, viewer.superAdmin(), labels,
                item -> deliveryHref(order, item, viewer, links, dropship),
                serial -> viewer.superAdmin() ? null
                        : "/dashboard/item/history?serialNo=" + URLEncoder.encode(serial, StandardCharsets.UTF_8),
                receiptLocked);
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
        String addReason = addItemsLockedKey(order, hasDropshipItems, receiptLocked);
        List<OrderPageModel.SerialItemRow> serialItems = items.stream()
                .filter(i -> i.hasOneOfTheStatuses(FulfilmentStatus.Delivered)).filter(OrderItem::isProduct)
                .map(i -> serialItemRow(i, labels)).toList();
        List<OrderPageModel.BulkActionButton> bulk = new ArrayList<>();
        for (BulkAction action : BulkAction.values()) {
            if (action == BulkAction.REMOVE && order.isInvoiced()) {
                continue;
            }
            BulkReason reason = bulkReason(action, canSplitOrder, hasDropshipItems, receiptLocked);
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
        if (receiptLocked && (action == BulkAction.REMOVE || action == BulkAction.SPLIT || action == BulkAction.MOVE)) {
            return BulkReason.RECEIPT_ISSUING;
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
    public OrderItemRow.Delivery delivery(Order order, OrderItem item, List<OrderItem> items, Viewer viewer) {
        if (StringUtils.isBlank(item.getDeliveryId())) {
            return null;
        }
        SupplierLabelMap labels = supplierLabels.forStore(storesRepository.findById(order.getStoreId()));
        return new OrderItemRow.Delivery(OrderItemRow.deliveryLabel(item, labels),
                deliveryHref(order, item, viewer, OrderLinks.of(order, viewer.superAdmin()), dropship(order, items)),
                deliveryRedirectResolver.pointsToDelivery(item));
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
        List<String> carriers = readOnly || store == null ? List.of() : shipmentCarrierOptions.forOrder(order, store);
        String base = "/dashboard/orders/" + order.getOrderId() + "/shipments/";
        List<OrderPageModel.ShipmentRow> rows = new ArrayList<>();
        List<OrderShipmentForm> forms = new ArrayList<>();
        // the courier order can be cancelled only while its labelled parcel is still on the way
        Shipment courierCancellable = order.canOrderShipment() ? order.firstShipmentWithShippingData()
                .filter(s -> s.getExternalId() != null && s.getDeliveredAt() == null).orElse(null) : null;
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
                    form.dialogId(), readOnly ? null : base + i,
                    readOnly || removeLockedKey(order, i) != null || isBarePlaceholder(order, i) ? null
                            : base + i + "/remove?version=" + form.version(),
                    // every parcel of one courier order carries its externalId, and cancelling it cancels them all
                    readOnly ? null : removeReasonKey(order, i, courierCancellable != null
                            && Objects.equals(s.getExternalId(), courierCancellable.getExternalId())),
                    removeShipmentMessageKey(order, i),
                    removeShipmentActionKey(order, i)));
            if (!readOnly) {
                forms.add(form);
            }
        }
        String emptyKey = readOnly ? "order.shipments.empty.readonly"
                : order.getFulfilmentType() == FulfilmentType.DirectToConsumer ? "order.shipments.empty.dropship"
                : "order.shipments.empty";
        return new OrderPageModel.ShipmentsCard(rows, emptyKey, !readOnly && courierCancellable != null, forms,
                readOnly ? null : OrderShipmentForm.blank(order, carriers));
    }

    /**
     * The short reason next to a greyed "Remove" in the row; the refusal of a forced removal says it in full. A
     * shipment with a courier order points to "Cancel courier order" only when the card offers it for that shipment's
     * courier order (which covers every parcel of it): before the order is ready to ship the button is not there yet,
     * and it only ever cancels the courier order of the first shipment that went out.
     */
    private static String removeReasonKey(Order order, int index, boolean courierCancellable) {
        String locked = removeLockedKey(order, index);
        if (locked == null) {
            return null;
        }
        if (locked.equals("order.shipments.remove.error.courier") && !courierCancellable) {
            return order.canOrderShipment() ? "order.shipments.remove.locked.courierNotFirst"
                    : "order.shipments.remove.locked.courierLater";
        }
        return locked.replace(".remove.error.", ".remove.locked.");
    }

    /**
     * Why the shipment at index cannot be removed, or null. The only shipment can go: OrdersController keeps a
     * placeholder with the customer's delivery choice in its place, and the order waits for it to be sent (OrderLifecycle
     * neither delivers nor completes an order before its shipments are delivered). A delivered order keeps its
     * shipments, they are the record of the delivery; so does a shipment with a delivery date. One with a courier order
     * is cancelled with "Cancel courier order", which also cancels the paid label at the carrier, never by dropping
     * the record.
     */
    public static String removeLockedKey(Order order, int index) {
        if (order.getStatus() == OrderStatus.Delivered) {
            return "order.shipments.remove.error.delivered";
        }
        Shipment shipment = order.getShipments().get(index);
        if (shipment.getDeliveredAt() != null) {
            return "order.shipments.remove.error.shipmentDelivered";
        }
        return shipment.getExternalId() != null ? "order.shipments.remove.error.courier" : null;
    }

    private OrderPageModel.DocumentsCard documents(Order order, Viewer viewer, boolean closed, boolean readOnly,
                                                   boolean goodsIssue, ReceiptOrderState receipts, boolean receiptLocked) {
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
        // A consumer receipt is typed in with "Add document", or issued as an e-receipt from "Issue" when the store
        // has a receipt system; the text says so. A closed order will not get another document and a read-only
        // viewer cannot issue one, so neither names it.
        String emptyKey = readOnly || next == null ? "order.documents.empty"
                : issuable.contains(next) ? "order.documents.empty.next"
                : next == DocumentType.Receipt && canIssueReceipt ? "order.documents.empty.next.receipt"
                : "order.documents.empty.next.manual";
        String addLocked = readOnly ? null : addDocumentLockedKey(order, null, receiptLocked);
        return new OrderPageModel.DocumentsCard(rows, receiptRow(order, receipts, viewer, readOnly), emptyKey,
                !readOnly && addLocked == null,
                // greyed with its reason only for the receipt; the other locks leave the button out, as before
                RECEIPT_ADD_LOCKED.equals(addLocked) ? addLocked : null,
                OrderLabels.Option.of(manual, OrderLabels::documentType), next,
                next == null ? null : OrderLabels.documentType(next), OrderLabels.Option.of(issuable, OrderLabels::documentType),
                !readOnly && goodsIssue, canIssueReceipt, !readOnly && (goodsIssue || !issuable.isEmpty() || canIssueReceipt),
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
    private OrderPageModel.ReceiptRow receiptRow(Order order, ReceiptOrderState receipts, Viewer viewer, boolean readOnly) {
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
        List<OrderPageModel.ReceiptEarlierRow> earlier = rows.subList(1, rows.size()).stream()
                .map(r -> new OrderPageModel.ReceiptEarlierRow(r.attemptNo(), r.statusKey(), r.statusTone(), r.outcome()))
                .toList();
        return new OrderPageModel.ReceiptRow(newest.key(), newest.attemptNo(), newest.receiptNumber(),
                safeWebUrl(newest.documentUrl()), newest.statusKey(), newest.statusTone(), dateKey, date, emailKey,
                newest.problem(), act && newest.canCheck(), act && newest.canResendEmail(), act && newest.canClose(),
                // a new attempt changes the order's documents, which a read-only page never does
                !readOnly && receipts.view().canReissue(), close.dialogId(), act ? close.pageHref() : null, earlier,
                newest.settled(), newest.settled() ? newest.outcome() : null);
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
        if (order.isClosed()) {
            return "order.documents.add.locked.closed";
        }
        List<DocumentType> manual = manualDocumentTypes(order);
        DocumentType next = order.getNextDocumentToIssue().orElse(null);
        if (next == null || !manual.contains(next) || (posted != null && !manual.contains(posted))) {
            return "order.documents.add.locked";
        }
        return receiptLocked ? RECEIPT_ADD_LOCKED : null;
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
        String base = "/dashboard/orders/" + order.getOrderId() + "/payments/";
        List<OrderPageModel.PaymentRow> rows = new ArrayList<>();
        List<OrderPaymentForm> forms = new ArrayList<>();
        for (int i = 0; i < payments.size(); i++) {
            Payment p = payments.get(i);
            OrderPaymentForm form = OrderPaymentForm.of(order.getOrderId(), i, p);
            boolean refund = p.getDirection() == PaymentDirection.Outgoing;
            // a refund is typed with either sign (the dialog asks for a minus, supplier payouts are stored
            // positive); the page shows one minus whichever way it was saved
            double shown = refund ? -Math.abs(p.getAmount()) : p.getAmount();
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
     * The only shipment with nothing but the customer's choice of delivery: removing it would leave the same placeholder
     * (OrdersController keeps one in place of the only shipment), so the row offers no "Remove" and needs no reason.
     */
    private static boolean isBarePlaceholder(Order order, int index) {
        return order.onlyPlaceholder().isPresent();
    }

    /**
     * The confirmation's text, which says what the removal does. Removing the last shipment not yet delivered while the
     * others are delivered moves the order to Delivered in the same save (OrderLifecycle), with what follows from it: the
     * goods issue note, the e-receipt for the customer, the notice to the marketplace. Removing the only shipment keeps
     * it as a placeholder waiting to go out, with how the customer asked to receive the order (type, pickup point).
     */
    public static String removeShipmentMessageKey(Order order, int index) {
        if (removalDelivers(order, index)) {
            return "order.shipments.remove.confirm.delivers";
        }
        return order.getShipments().size() == 1 ? "order.shipments.remove.confirm.message.last"
                : "order.shipments.remove.confirm.message";
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
        if (order.isClosed()) {
            return "order.items.add.locked.closed";
        }
        if (order.getDocumentByType(DocumentType.GoodsIssue).isPresent()) {
            return "order.items.add.locked.goods.issue";
        }
        if (order.isInvoiced()) {
            return "order.items.add.locked.invoiced";
        }
        if (receiptLocked) {
            return "order.items.add.locked.receipt";
        }
        return hasDropshipItems ? "order.items.action.dropship.locked" : null;
    }
}
