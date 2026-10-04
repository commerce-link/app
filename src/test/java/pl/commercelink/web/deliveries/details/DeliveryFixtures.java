package pl.commercelink.web.deliveries.details;

import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.deliveries.Allocation;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveryItem;
import pl.commercelink.inventory.deliveries.DeliveryOrderStatus;
import pl.commercelink.inventory.deliveries.DeliveryTrackingState;
import pl.commercelink.inventory.deliveries.DeliveryType;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.warehouse.builtin.WarehouseItem;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Deliveries in the states the details page distinguishes, built from the real entities. */
final class DeliveryFixtures {

    static final String STORE_ID = "store-1";
    static final String DELIVERY_ID = "2f9eb794-74ee-4122-aff2-cc614b6d417d";
    static final String ORDER_ID = "a9f693b8-1111-2222-3333-444455556666";
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 12, 0);
    static final DeliveryViewer ADMIN = new DeliveryViewer(false, true, null);
    static final DeliveryViewer USER = new DeliveryViewer(false, false, null);
    static final DeliveryViewer SUPER_ADMIN = new DeliveryViewer(true, false, null);

    private DeliveryFixtures() {
    }

    /** Manual-Hurt, in transit: one order line (1 pc) and one warehouse top-up (2 pcs), both waiting. */
    static Delivery warehouse() {
        Delivery delivery = new Delivery(STORE_ID, "MH-2026/0917", "Manual-Hurt", LocalDate.of(2026, 10, 8), 0, 0, 14, 1.23);
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setOrderedAt(LocalDateTime.of(2026, 10, 1, 15, 12));
        delivery.setType(DeliveryType.WAREHOUSE);
        delivery.setConnectionMode(ConnectionMode.MANUAL);
        delivery.addEvent(new Event(EventType.action, "DELIVERY_CREATED", LocalDateTime.of(2026, 10, 1, 15, 12)));
        return withAllocations(delivery,
                orderAllocation("NVIDIA ValueKing RTX Ultra", "5900000000002", "MFN-VALUE-01", 3814.0, 1, false),
                warehouseAllocation("Samsung MirageDrive 2TB NVMe", "5900000000006", "MFN-MIRAGE-01", 635.0, 2));
    }

    /** AcmeB dropship outside the system to a pickup point, waiting for the shipment confirmation. */
    static Delivery dropship() {
        Delivery delivery = new Delivery(STORE_ID, "ACB-DS-5530", "AcmeB", LocalDate.of(2026, 10, 3), 0, 0, 7, 1.23);
        delivery.setDeliveryId("ed2fca8a-073d-47cd-8bd3-2f1cedfdbeb2");
        delivery.setOrderedAt(LocalDateTime.of(2026, 10, 1, 15, 2));
        delivery.setType(DeliveryType.DROPSHIP);
        delivery.setConnectionMode(ConnectionMode.OWN);
        return withAllocations(delivery,
                orderAllocation("G.Skill TwinMatch 32GB DDR5 Kit", "5900000000003", "MFN-TWIN-01", 448.0, 1, true));
    }

    static Delivery withStatus(Delivery delivery, DeliveryOrderStatus status) {
        delivery.setOrderStatus(status);
        return delivery;
    }

    static Delivery outcomeUnknown(Delivery delivery) {
        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_DISPATCHED);
        delivery.setOrderErrorMessage("HTTP 502 Bad Gateway");
        return delivery;
    }

    static Delivery global(Delivery delivery) {
        delivery.setConnectionMode(ConnectionMode.GLOBAL);
        return delivery;
    }

    static Delivery own(Delivery delivery) {
        delivery.setConnectionMode(ConnectionMode.OWN);
        delivery.setPurchaseRef("ref-1");
        return delivery;
    }

    static Delivery received(Delivery delivery) {
        delivery.setReceivedAt(LocalDateTime.of(2026, 10, 2, 9, 30));
        delivery.getAllocations().forEach(allocation -> allocation.setInAllocation(false));
        return delivery;
    }

    static Delivery partlyReceived(Delivery delivery) {
        delivery.getAllocations().get(0).setInAllocation(false);
        return delivery;
    }

    static Delivery tracking(Delivery delivery, DeliveryTrackingState state) {
        delivery.tracking().finish(state);
        return delivery;
    }

    static Delivery withGoodsReceipt(Delivery delivery) {
        delivery.addDocument(new Document("pz-1", "PZ/MAG/2026/000001", null, DocumentType.GoodsReceipt, LocalDate.of(2026, 10, 1)));
        return delivery;
    }

    static Delivery withAllocations(Delivery delivery, Allocation... allocations) {
        List<Allocation> list = new ArrayList<>(List.of(allocations));
        delivery.setAllocations(list);
        delivery.setItems(DeliveryItem.groupAndUnify(list));
        delivery.recomputeTotalCost(list);
        return delivery;
    }

    static Allocation orderAllocation(String name, String ean, String mfn, double unitCost, int qty, boolean directToConsumer) {
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        order.setFulfilmentType(directToConsumer ? FulfilmentType.DirectToConsumer : FulfilmentType.WarehouseFulfilment);
        BillingDetails billing = new BillingDetails();
        billing.setEmail("marek.pawlak@example.pl");
        order.setBillingDetails(billing);
        OrderItem item = new OrderItem(ORDER_ID, "GPU", name, qty, unitCost * 1.3, mfn, false, 0);
        item.setItemId("item-" + mfn);
        item.setManufacturerCode(mfn);
        item.setEan(ean);
        item.setCost(unitCost);
        item.setDeliveryId(DELIVERY_ID);
        Allocation allocation = Allocation.fromOrderItem(order, item);
        allocation.setInAllocation(true);
        return allocation;
    }

    static Allocation warehouseAllocation(String name, String ean, String mfn, double unitCost, int qty) {
        WarehouseItem item = new WarehouseItem(STORE_ID, DELIVERY_ID, "SSD", name, ean, mfn, unitCost, qty);
        item.setItemId("wh-" + mfn);
        Allocation allocation = Allocation.fromWarehouseItem(item);
        allocation.setInAllocation(true);
        return allocation;
    }

    /** The order a dropship delivery serves, with the customer and a pickup-point shipment. */
    static Order dropshipOrder() {
        Order order = new Order(STORE_ID);
        order.setOrderId("1de57483-1111-2222-3333-444455556666");
        ShippingDetails shipping = new ShippingDetails();
        shipping.setName("Barbara");
        shipping.setSurname("Zając");
        shipping.setStreetAndNumber("ul. Kwiatowa 12/4");
        shipping.setPostalCode("30-001");
        shipping.setCity("Kraków");
        shipping.setCountry("PL");
        shipping.setPhone("+48 512 345 678");
        shipping.setEmail("barbara.zajac@example.com");
        order.setShippingDetails(shipping);
        Shipment shipment = new Shipment(ShipmentType.PickupPoint);
        shipment.setCarrier("DPD");
        shipment.setCollectionPointCode("PL12345");
        order.addShipment(shipment);
        return order;
    }

    static DeliveryPageData data(Delivery delivery) {
        return new DeliveryPageData(delivery, delivery.getProvider(), null, List.of(), null, List.of(), null, Set.of(),
                null, null, NOW);
    }

    static DeliveryPageData data(Delivery delivery, String openDialog, Set<Integer> preselected) {
        return new DeliveryPageData(delivery, delivery.getProvider(), null, List.of(), null, List.of(), null, preselected,
                openDialog, null, NOW);
    }
}
