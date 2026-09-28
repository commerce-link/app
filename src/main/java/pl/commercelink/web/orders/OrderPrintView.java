package pl.commercelink.web.orders;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.documents.Document;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.Shipment;
import pl.commercelink.stores.Store;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** What the two order printouts show (order card, collection protocol), prepared so the templates only print text. */
public final class OrderPrintView {

    private OrderPrintView() {
    }

    public record Card(String orderId, String shortId, String detailsHref, String email, String orderedAt,
                       String comment, List<ItemRow> items, List<DocumentRow> documents, List<ShipmentRow> shipments) {
    }

    public record Collection(String orderId, String shortId, String detailsHref, String store, String date,
                             String location, List<ItemRow> items) {
    }

    /** delivery is the supplier's name where the delivery id is a supplier connection, as on the details page. */
    public record ItemRow(String category, String name, int qty, String mfn, String delivery, String comment,
                          String serialNo) {
    }

    public record DocumentRow(String number, String typeKey) {
    }

    public record ShipmentRow(String typeKey, String trackingNo, String carrier, String shippedAt) {
    }

    public static Card card(Order order, List<OrderItem> items, OrderLinks links, SupplierLabelMap labels) {
        return new Card(order.getOrderId(), order.getShortenedOrderId(), links.details(), order.getEmail(),
                OrderFormats.dateTime(order.getOrderedAt()), StringUtils.trimToNull(order.getComment()),
                items.stream().map(item -> row(item, labels)).toList(),
                orEmpty(order.getDocuments()).map(OrderPrintView::document).toList(),
                orEmpty(order.getShipments()).map(OrderPrintView::shipment).toList());
    }

    /** Services are not handed over at the counter, so the protocol lists products only (unchanged from the old page). */
    public static Collection collection(Order order, List<OrderItem> items, Store store, LocalDate date,
                                        String location, OrderLinks links, SupplierLabelMap labels) {
        return new Collection(order.getOrderId(), order.getShortenedOrderId(), links.details(),
                store.getStoreId() + " (" + store.getName() + ")", OrderFormats.date(date), location,
                items.stream().filter(item -> !item.isService()).map(item -> row(item, labels)).toList());
    }

    private static ItemRow row(OrderItem item, SupplierLabelMap labels) {
        String deliveryId = StringUtils.trimToNull(item.getDeliveryId());
        String delivery = deliveryId == null ? null
                : labels.has(deliveryId) ? labels.of(deliveryId) : item.getShortenedDeliveryId();
        return new ItemRow(StringUtils.trimToNull(item.getCategory()), item.getName(), item.getQty(),
                StringUtils.trimToNull(item.getManufacturerCode()), delivery, StringUtils.trimToNull(item.getComment()),
                StringUtils.trimToNull(item.getSerialNo()));
    }

    private static DocumentRow document(Document document) {
        return new DocumentRow(StringUtils.trimToNull(document.getNumber()), OrderLabels.documentType(document.getType()));
    }

    private static ShipmentRow shipment(Shipment shipment) {
        return new ShipmentRow(OrderLabels.shipmentType(shipment.getType()), StringUtils.trimToNull(shipment.getTrackingNo()),
                StringUtils.trimToNull(shipment.getCarrier()), OrderFormats.dateTime(shipment.getShippedAt()));
    }

    private static <T> Stream<T> orEmpty(List<T> list) {
        return list == null ? Stream.empty() : list.stream().filter(Objects::nonNull);
    }
}
