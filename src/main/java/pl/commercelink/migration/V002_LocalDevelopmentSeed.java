package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.context.annotation.Profile;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentDirection;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.stores.Branding;
import pl.commercelink.stores.CheckoutConfiguration;
import pl.commercelink.stores.ClientNotificationsConfiguration;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.InvoicingConfiguration;
import pl.commercelink.stores.RMAConfiguration;
import pl.commercelink.stores.ReportingConfiguration;
import pl.commercelink.stores.ShippingConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.WarehouseConfiguration;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedList;
import java.util.List;

@Profile("localdev")
@ChangeUnit(id = "V002-local-development-seed", order = "002", author = "commercelink")
public class V002_LocalDevelopmentSeed {

    private static final String STORE_ID = "uma2dqukxr";
    private static final String ORDER_ID = "00000000-1000-0000-0000-000000000001";
    private static final String ORDER_ITEM_ID_1 = "00000000-1000-0000-0000-000000000101";
    private static final String ORDER_ITEM_ID_2 = "00000000-1000-0000-0000-000000000102";
    private static final String SHIPPING_DETAILS_ID = "00000000-1000-0000-0000-000000000201";

    private final AmazonDynamoDB dynamoDB;

    public V002_LocalDevelopmentSeed(AmazonDynamoDB dynamoDB) {
        this.dynamoDB = dynamoDB;
    }

    @Execution
    public void seed() {
        DynamoDBMapper mapper = new DynamoDBMapper(dynamoDB);
        mapper.save(buildStore());
        mapper.save(buildOrder());
        mapper.save(buildOrderItem1());
        mapper.save(buildOrderItem2());
    }

    @RollbackExecution
    public void rollback() {
    }

    private Store buildStore() {
        CheckoutConfiguration checkout = new CheckoutConfiguration();
        checkout.setCurrency("pln");
        checkout.setSuccessUrl("http://localhost:8080/success/");
        checkout.setCancelUrl("http://localhost:8080/cancel");
        checkout.setDeliveryOptions(new LinkedList<>());

        FulfilmentConfiguration fulfilment = new FulfilmentConfiguration();
        fulfilment.setOrderAssemblyDays(2);
        fulfilment.setOrderRealizationDays(5);
        fulfilment.setAutomatedFulfilment(false);
        fulfilment.setDefaultFulfilmentType(FulfilmentType.WarehouseFulfilment);

        BillingDetails billing = new BillingDetails();
        billing.setCompanyName("Demo Store Sp. z o.o.");
        billing.setTaxId("1234567890");
        billing.setStreetAndNumber("ul. Testowa 1");
        billing.setPostalCode("00-001");
        billing.setCity("Warszawa");
        billing.setCountry("PL");

        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setName("Demo Store");
        store.setCheckoutConfiguration(checkout);
        store.setFulfilmentConfiguration(fulfilment);
        store.setBillingDetails(billing);
        store.setInvoicingConfiguration(new InvoicingConfiguration());
        store.setRmaConfiguration(new RMAConfiguration());
        store.setWarehouseConfiguration(new WarehouseConfiguration());
        store.setReportingConfiguration(new ReportingConfiguration());
        store.setShippingConfiguration(new ShippingConfiguration());
        store.setBranding(new Branding());
        store.setClientNotificationsConfiguration(new ClientNotificationsConfiguration());
        return store;
    }

    private Order buildOrder() {
        BillingDetails billing = new BillingDetails();
        billing.setName("Jan");
        billing.setSurname("Kowalski");
        billing.setStreetAndNumber("ul. Klienta 5/2");
        billing.setPostalCode("00-002");
        billing.setCity("Warszawa");
        billing.setCountry("PL");
        billing.setEmail("test.customer@example.com");
        billing.setPhone("+48123456789");

        ShippingDetails shipping = new ShippingDetails();
        shipping.setId(SHIPPING_DETAILS_ID);
        shipping.setName("Jan");
        shipping.setSurname("Kowalski");
        shipping.setStreetAndNumber("ul. Klienta 5/2");
        shipping.setPostalCode("00-002");
        shipping.setCity("Warszawa");
        shipping.setCountry("PL");
        shipping.setEmail("test.customer@example.com");
        shipping.setPhone("+48123456789");
        shipping.set_default(true);

        Payment payment = new Payment("", "", PaymentSource.BankTransfer, PaymentDirection.Incoming, 0, 0, null, null);
        List<Payment> payments = new LinkedList<>();
        payments.add(payment);

        Order order = new Order();
        order.setStoreId(STORE_ID);
        order.setOrderId(ORDER_ID);
        order.setTotalPrice(1758.00);
        order.setOrderedAt(LocalDateTime.now(ZoneOffset.UTC));
        order.setOrderRealizationDays(5);
        order.setEmailNotificationsEnabled(false);
        order.setStatus(OrderStatus.New);
        order.setFulfilmentType(FulfilmentType.WarehouseFulfilment);
        order.setSource(new OrderSource("Test", OrderSourceType.WebStore));
        order.setBillingDetails(billing);
        order.setShippingDetails(shipping);
        order.setPayments(payments);
        order.setShipments(new LinkedList<>());
        order.setDocuments(new LinkedList<>());
        return order;
    }

    private OrderItem buildOrderItem1() {
        OrderItem item = new OrderItem();
        item.setOrderId(ORDER_ID);
        item.setItemId(ORDER_ITEM_ID_1);
        item.setSku("MFN-CLEAR-01");
        item.setPrice(1299.00);
        item.setConsolidated(false);
        item.setCategoryKey("CPU");
        item.setName("AMD ClearEdge Pro X3D");
        item.setQty(1);
        item.setEan("5900000000001");
        item.setManufacturerCode("MFN-CLEAR-01");
        item.setCost(1000.00);
        item.setTax(1.23);
        item.setStatus(FulfilmentStatus.New);
        item.setPosition(0);
        return item;
    }

    private OrderItem buildOrderItem2() {
        OrderItem item = new OrderItem();
        item.setOrderId(ORDER_ID);
        item.setItemId(ORDER_ITEM_ID_2);
        item.setSku("MFN-TWIN-01");
        item.setPrice(459.00);
        item.setConsolidated(false);
        item.setCategoryKey("Memory");
        item.setName("G.Skill TwinMatch 32GB DDR5 Kit");
        item.setQty(1);
        item.setEan("5900000000003");
        item.setManufacturerCode("MFN-TWIN-01");
        item.setCost(330.00);
        item.setTax(1.23);
        item.setStatus(FulfilmentStatus.New);
        item.setPosition(1);
        return item;
    }
}
