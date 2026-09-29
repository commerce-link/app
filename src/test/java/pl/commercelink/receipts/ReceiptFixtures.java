package pl.commercelink.receipts;

import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.Shipment;

import java.time.LocalDateTime;
import java.util.List;

public final class ReceiptFixtures {

    public static final String STORE_ID = "store-1";
    public static final String ORDER_ID = "0f3c2a8e-1b2c-4d5e-8f90-123456789abc";
    public static final LocalDateTime DELIVERED_AT = LocalDateTime.of(2026, 9, 23, 12, 0);

    private ReceiptFixtures() {
    }

    /** A delivered consumer order fully paid through an online gateway. */
    public static Order b2cOrder(double totalPrice) {
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        order.setStatus(OrderStatus.Delivered);
        order.setTotalPrice(totalPrice);
        order.setSource(new OrderSource("sklep", OrderSourceType.WebStore));
        BillingDetails billing = new BillingDetails();
        billing.setName("Jan");
        billing.setSurname("Kowalski");
        billing.setEmail("jan@example.com");
        order.setBillingDetails(billing);
        Shipment shipment = new Shipment();
        shipment.setDeliveredAt(DELIVERED_AT);
        order.getShipments().add(shipment);
        order.getPayments().add(new Payment("ref", "Przelewy24", PaymentSource.OnlinePayment, totalPrice, 1.2));
        return order;
    }

    /** A {@link #b2cOrder} with the given payments instead of the default full online-gateway one. */
    public static Order order(double totalPrice, Payment... payments) {
        Order order = b2cOrder(totalPrice);
        order.getPayments().clear();
        order.getPayments().addAll(List.of(payments));
        return order;
    }

    /** A delivered consumer order with no payment yet, as it exists before the gateway settles it. */
    public static Order deliveredOrder(double totalPrice) {
        return order(totalPrice);
    }

    /** An incoming payment of the given form and amount, unnamed (0 marks it unsettled). */
    public static Payment payment(PaymentSource source, double amount) {
        return new Payment(null, null, source, amount, 0);
    }

    public static OrderItem item(String name, int qty, double price, double tax) {
        OrderItem item = new OrderItem(ORDER_ID, "Laptopy", name, qty, price, "SKU-" + name, false);
        item.setTax(tax);
        return item;
    }

    public static OrderItem delivery(String name, double price, double tax) {
        OrderItem item = new OrderItem(ORDER_ID, OrderItem.DELIVERY_CATEGORY, name, 1, price, null, false);
        item.setService(true);
        item.setTax(tax);
        return item;
    }

    public static List<OrderItem> items(OrderItem... items) {
        return List.of(items);
    }
}
