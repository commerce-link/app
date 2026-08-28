package pl.commercelink.demo;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentReason;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveryType;
import pl.commercelink.inventory.supplier.SupplierProviderFactory;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.localdev.CatalogSeed;
import pl.commercelink.localdev.CatalogSeedRow;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.event.OrderEvent;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMACenter;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RMAItemStatus;
import pl.commercelink.orders.rma.RMAResolutionType;
import pl.commercelink.orders.rma.RMAStatus;
import pl.commercelink.products.AvailabilityDefinition;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.CategoryDefinitionType;
import pl.commercelink.products.PriceDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.StockDefinition;
import pl.commercelink.stores.AuthorizedCarrier;
import pl.commercelink.stores.BankAccount;
import pl.commercelink.stores.CheckoutConfiguration;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.stores.DemoStoreMetadata;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.InvoicingConfiguration;
import pl.commercelink.stores.PackageTemplate;
import pl.commercelink.stores.Parcel;
import pl.commercelink.stores.RMAConfiguration;
import pl.commercelink.stores.ShippingConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSeeder;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.WarehouseConfiguration;
import pl.commercelink.starter.storage.FileStorage;
import pl.commercelink.warehouse.builtin.CounterpartyDetails;
import pl.commercelink.warehouse.builtin.DeliveryAddress;
import pl.commercelink.warehouse.builtin.IssuerDetails;
import pl.commercelink.warehouse.builtin.WarehouseDocument;
import pl.commercelink.warehouse.builtin.WarehouseDocumentItem;
import pl.commercelink.warehouse.builtin.WarehouseDocumentSequence;
import pl.commercelink.warehouse.builtin.WarehouseItem;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DemoStoreSeeder implements StoreSeeder {

    public static final String CATALOG_ID = "cat-local-01";

    static final String POS_ORDER_KEY = "demo-order-pos";
    static final String MARKETPLACE_ORDER_KEY = "demo-order-marketplace-1";
    static final String MARKETPLACE_ORDER_2_KEY = "demo-order-marketplace-2";
    static final String WEBSTORE_ORDER_KEY = "demo-order-webstore";
    static final String DROPSHIP_ACME_ORDER_KEY = "demo-order-dropship-acme";
    static final String DROPSHIP_ACME_B_ORDER_KEY = "demo-order-dropship-acmeb";
    static final String DROPSHIP_PICKUP_ACME_ORDER_KEY = "demo-order-dropship-pickup-acme";
    static final String DROPSHIP_PICKUP_ACME_B_ORDER_KEY = "demo-order-dropship-pickup-acmeb";
    static final String DROPSHIP_PICKUP_NO_CODE_ORDER_KEY = "demo-order-dropship-pickup-nocode";
    static final String DROPSHIP_ACME_MULTI_ORDER_KEY = "demo-order-dropship-acme-multi";
    static final String DROPSHIP_ACME_SPARE_ORDER_KEY = "demo-order-dropship-acme-spare";
    static final String DROPSHIP_ACME_B_SPARE_ORDER_KEY = "demo-order-dropship-acmeb-spare";
    static final String WAREHOUSE_ACME_TWO_ITEMS_ORDER_KEY = "demo-order-warehouse-acme-two-items";
    static final List<String> DROPSHIP_ORDER_KEYS = List.of(DROPSHIP_ACME_ORDER_KEY, DROPSHIP_ACME_B_ORDER_KEY,
            DROPSHIP_PICKUP_ACME_ORDER_KEY, DROPSHIP_PICKUP_ACME_B_ORDER_KEY, DROPSHIP_PICKUP_NO_CODE_ORDER_KEY,
            DROPSHIP_ACME_MULTI_ORDER_KEY, DROPSHIP_ACME_SPARE_ORDER_KEY, DROPSHIP_ACME_B_SPARE_ORDER_KEY);
    static final String MARKETPLACE_EXTERNAL_KEY = "demo-external-allegro-1";
    static final String MARKETPLACE_EXTERNAL_2_KEY = "demo-external-allegro-2";
    static final String DEMO_WAREHOUSE_ID = "MAG-01";
    static final String COMPLETED_ORDER_KEY = "demo-order-completed-1";
    static final String COMPLETED_ORDER_2_KEY = "demo-order-completed-2";
    static final String COMPLETED_EXTERNAL_KEY = "demo-external-allegro-3";

    static String demoId(String storeId, String key) {
        return UUID.nameUUIDFromBytes((storeId + "/" + key).getBytes(StandardCharsets.UTF_8)).toString();
    }

    static String demoExternalOrderNo(String storeId, String key) {
        long hash = UUID.nameUUIDFromBytes((storeId + "/" + key).getBytes(StandardCharsets.UTF_8)).getMostSignificantBits();
        return String.valueOf(1_000_000_000L + Math.floorMod(hash, 9_000_000_000L));
    }

    static String customerEmail(String storeId, String name, String surname) {
        String localPart = (name + "." + surname).toLowerCase(Locale.ROOT);
        long digits = Math.floorMod(
                UUID.nameUUIDFromBytes((storeId + "/" + localPart).getBytes(StandardCharsets.UTF_8)).getMostSignificantBits(), 90) + 10;
        return slugify(localPart) + digits + "@test.com";
    }

    private static String slugify(String value) {
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("[^\\x00-\\x7F]", "");
        return ascii.replaceAll("[^a-z0-9]+", ".").replaceAll("^\\.+|\\.+$", "");
    }

    private static final String ACME = "Acme";
    private static final String ACME_B = "AcmeB";
    private static final List<String> SIM_SUPPLIERS = List.of(ACME, ACME_B);
    private static final String SIM_MFN_PREFIX = "SIM-";
    /** AcmeB simulates dropshipping only when asked to; the demo store asks, so the OWN path is visible. */
    static final String ACME_B_DROPSHIP_KNOB = "orderingDropshipEnabled";
    /** AcmeB is the demo supplier WITHOUT pickup-point deliveries (Acme has them), so both paths can be exercised. */
    static final String ACME_B_PICKUP_POINTS_KNOB = "orderingPickupPointsEnabled";
    private static final String SIM_LABEL_PREFIX = "Symulacja: ";
    private static final String ENABLED_CATEGORY_GROUP = "Komputery i urządzenia peryferyjne";
    private static final String PRICELIST_TEMPLATE = "/local-init/s3/stores/uma2dqukxr/pricelists/cat-local-01/seed.csv";
    private static final String CARRIER_ID = "local-carrier-01";
    private static final String CARRIER_NAME = "local";
    private static final String CARRIER_DISPLAY_NAME = "Kurier Lokalny (demo)";
    private static final double WAREHOUSE_MARGIN = 0.85;
    private static final int WAREHOUSE_QTY = 3;

    private final AmazonDynamoDB dynamoDB;
    private final FileStorage fileStorage;
    private final SupplierRegistry supplierRegistry;
    private final SupplierProviderFactory supplierProviderFactory;

    @Value("${s3.bucket.stores}")
    String storesBucket;

    @Override
    public void seed(Store store) {
        applyStoreConfiguration(store, store.getStoreId(), store.getName(), store.getDemo());
        applyDemoWarehouseId(store);
        applyDemoCompanyDetails(store);
        applyDemoInvoicingConfiguration(store);
        applyDemoFulfilmentDefaults(store);
        List<CatalogSeedRow> rows = loadFilteredRows();
        seedStoreData(store.getStoreId(), rows);
        enableAcmeBDropship(store);
        saveSupplierRmaCenters(store.getStoreId());
        saveCompletedOrders(store, rows);
        saveWarehouseStock(store, rows);
    }

    public Store seedStore(String storeId, String storeName, DemoStoreMetadata demo) {
        DynamoDBMapper mapper = new DynamoDBMapper(dynamoDB);
        Store store = Objects.requireNonNullElseGet(mapper.load(Store.class, storeId), Store::new);
        applyStoreConfiguration(store, storeId, storeName, demo);
        mapper.save(store);
        seedStoreData(storeId, loadFilteredRows());
        enableAcmeBDropship(store);
        return store;
    }

    /**
     * Turns on AcmeB's dropship simulation in the store's OWN configuration so the seeded AcmeB
     * dropship order can be fulfilled. Only the missing knob is added: a value already chosen in
     * the fulfilment settings stays untouched, so re-seeding never flips it back.
     */
    private void enableAcmeBDropship(Store store) {
        if (!supplierRegistry.exists(ACME_B)) {
            return;
        }
        Map<String, String> current = supplierProviderFactory.loadConfiguration(store, ACME_B);
        Map<String, String> merged = new HashMap<>(current);
        merged.putIfAbsent(ACME_B_DROPSHIP_KNOB, "1");
        merged.putIfAbsent(ACME_B_PICKUP_POINTS_KNOB, "0");
        if (!merged.equals(current)) {
            supplierProviderFactory.saveConfiguration(store, ACME_B, merged);
        }
    }

    private List<CatalogSeedRow> loadFilteredRows() {
        return filterSimulationRows(CatalogSeed.load(), simulationSuppliersAvailable());
    }

    private void seedStoreData(String storeId, List<CatalogSeedRow> rows) {
        DynamoDBMapper mapper = new DynamoDBMapper(dynamoDB);

        savePricelist(storeId);

        if (isAlreadySeeded(mapper, storeId)) {
            return;
        }

        DynamoDBMapperConfig clobber = DynamoDBMapperConfig.builder()
                .withSaveBehavior(DynamoDBMapperConfig.SaveBehavior.CLOBBER)
                .build();

        saveCatalog(mapper, clobber, rows, storeId);
        saveProducts(mapper, rows, storeId);
        saveWarehouseItems(mapper, rows, storeId);
        saveRmaCenter(mapper, clobber, storeId);
        saveOrders(mapper, clobber, storeId, rows);
    }

    private boolean isAlreadySeeded(DynamoDBMapper mapper, String storeId) {
        return mapper.load(ProductCatalog.class, storeId, CATALOG_ID) != null;
    }

    static void applyStoreConfiguration(Store store, String storeId, String storeName, DemoStoreMetadata demo) {
        store.setStoreId(storeId);
        if (store.getName() == null) {
            store.setName(storeName);
        }
        if (demo != null) {
            store.setDemo(demo);
        }

        FulfilmentConfiguration fulfilment = Objects.requireNonNullElseGet(store.getFulfilmentConfiguration(), FulfilmentConfiguration::new);
        fulfilment.setCanUseGlobalSuppliers(true);
        fulfilment.setSupplierConnections(List.of(
                new StoreSupplierConnection(ACME, ConnectionMode.GLOBAL),
                new StoreSupplierConnection(ACME_B, ConnectionMode.OWN)));
        fulfilment.setEnabledCategories(List.of(ENABLED_CATEGORY_GROUP));
        store.setFulfilmentConfiguration(fulfilment);

        WarehouseConfiguration warehouse = Objects.requireNonNullElseGet(store.getWarehouseConfiguration(), WarehouseConfiguration::new);
        warehouse.setWarehouseId("MAG-" + storeId);
        warehouse.setCostCenterId("KC-" + storeId);
        warehouse.setDocumentsGenerationEnabled(true);
        store.setWarehouseConfiguration(warehouse);

        ShippingConfiguration shipping = Objects.requireNonNullElseGet(store.getShippingConfiguration(), ShippingConfiguration::new);
        shipping.setPickUpAddresses(List.of(warehouseAddress()));
        shipping.setSenderAddresses(List.of(warehouseAddress()));
        shipping.setPackageTemplates(List.of(defaultPackage(), rmaPackage()));
        shipping.setAuthorizedCarriers(List.of(carrier()));
        store.setShippingConfiguration(shipping);

        CheckoutConfiguration checkout = Objects.requireNonNullElseGet(store.getCheckoutConfiguration(), CheckoutConfiguration::new);
        checkout.setDeliveryOptions(List.of(courierDelivery(), pickupDelivery()));
        store.setCheckoutConfiguration(checkout);

        store.setBankAccounts(List.of(bankAccount()));

        RMAConfiguration rma = Objects.requireNonNullElseGet(store.getRmaConfiguration(), RMAConfiguration::new);
        rma.setCarrier(carrier());
        store.setRmaConfiguration(rma);
    }

    static void applyDemoWarehouseId(Store store) {
        store.getWarehouseConfiguration().setWarehouseId(DEMO_WAREHOUSE_ID);
    }

    static void applyDemoCompanyDetails(Store store) {
        BillingDetails billing = Objects.requireNonNullElseGet(store.getBillingDetails(), BillingDetails::new);
        billing.setCompanyName("Demo Store sp. z o.o.");
        billing.setTaxId("1234567890");
        billing.setStreetAndNumber("ul. Testowa 1");
        billing.setPostalCode("00-001");
        billing.setCity("Warszawa");
        billing.setCountry("PL");
        billing.setPhone("+48123123123");
        if (billing.getEmail() == null) {
            billing.setEmail(ownerEmailOrFallback(store.getDemo()));
        }
        store.setBillingDetails(billing);

        ShippingDetails warehouseShipping = warehouseAddress();
        warehouseShipping.setId("demo-warehouse-ship-01");
        store.setShippingDetails(new LinkedList<>(List.of(warehouseShipping)));
    }

    static void applyDemoInvoicingConfiguration(Store store) {
        InvoicingConfiguration invoicing = Objects.requireNonNullElseGet(store.getInvoicingConfiguration(), InvoicingConfiguration::new);
        invoicing.setPaymentTerms(7);
        invoicing.setSendInvoicesAsAttachment(true);
        invoicing.setSplitPaymentsEnabled(true);
        store.setInvoicingConfiguration(invoicing);
    }

    static void applyDemoFulfilmentDefaults(Store store) {
        FulfilmentConfiguration fulfilment = Objects.requireNonNullElseGet(store.getFulfilmentConfiguration(), FulfilmentConfiguration::new);
        fulfilment.setOrderAssemblyDays(1);
        fulfilment.setOrderRealizationDays(0);
        store.setFulfilmentConfiguration(fulfilment);
    }

    private void saveCatalog(DynamoDBMapper mapper, DynamoDBMapperConfig clobber, List<CatalogSeedRow> rows, String storeId) {
        List<CategoryDefinition> categories = buildCategoryDefinitions(rows, storeId);

        ProductCatalog catalog = new ProductCatalog();
        catalog.setStoreId(storeId);
        catalog.setCatalogId(CATALOG_ID);
        catalog.setName("Local Catalog");
        catalog.setDeletionProtection(false);
        catalog.setCategories(categories);
        mapper.save(catalog, clobber);
    }

    private void saveProducts(DynamoDBMapper mapper, List<CatalogSeedRow> rows, String storeId) {
        mapper.batchSave(buildProducts(rows, storeId));
    }

    static List<Product> buildProducts(List<CatalogSeedRow> rows, String storeId) {
        List<Product> products = new ArrayList<>();
        for (CatalogSeedRow row : rows) {
            if (!row.inCatalog()) {
                continue;
            }
            Product product = new Product(CatalogSeed.categoryId(row.category(), storeId), row.pimId(), row.ean(),
                    row.mfn(), row.brand(), row.label(), row.name(), null);
            product.setProductId("prod-" + row.pimId());
            product.setEnabled(true);
            product.setEstimatedDeliveryDays(row.estimatedDeliveryDays());
            products.add(product);
        }
        return products;
    }

    private void saveWarehouseItems(DynamoDBMapper mapper, List<CatalogSeedRow> rows, String storeId) {
        List<WarehouseItem> items = new ArrayList<>();
        for (CatalogSeedRow row : rows) {
            if (!row.inWarehouse()) {
                continue;
            }
            double unitCost = Math.round(row.priceGross() / Price.DEFAULT_VAT_RATE * WAREHOUSE_MARGIN);
            WarehouseItem item = new WarehouseItem(storeId, "Unknown", row.category(), row.name(),
                    row.ean(), row.mfn(), unitCost, WAREHOUSE_QTY);
            item.setItemId(demoId(storeId, "local-wh-" + row.pimId()));
            item.setStatus(FulfilmentStatus.Delivered);
            item.setComment("seed-local");
            items.add(item);
        }
        mapper.batchSave(items);
    }

    private void saveRmaCenter(DynamoDBMapper mapper, DynamoDBMapperConfig clobber, String storeId) {
        RMACenter center = new RMACenter();
        center.setStoreId(storeId);
        center.setRmaCenterId("local-rma-center-01");
        center.setProvider("Warehouse");
        ShippingDetails address = warehouseAddress();
        address.setId("local-rma-addr-01");
        address.setName("Centrum");
        address.setSurname("Zwrotow");
        address.setEmail("rma@commercelink.local");
        center.setShippingDetails(address);
        mapper.save(center, clobber);
    }

    private void saveSupplierRmaCenters(String storeId) {
        DynamoDBMapper mapper = new DynamoDBMapper(dynamoDB);
        DynamoDBMapperConfig clobber = DynamoDBMapperConfig.builder()
                .withSaveBehavior(DynamoDBMapperConfig.SaveBehavior.CLOBBER)
                .build();
        buildSupplierRmaCenters(storeId).forEach(center -> mapper.save(center, clobber));
    }

    static List<RMACenter> buildSupplierRmaCenters(String storeId) {
        return List.of(
                supplierRmaCenter(storeId, ACME, "demo-rma-center-acme", "demo-rma-addr-acme",
                        "Acme sp. z o.o.", "ul. Dystrybucyjna 10", "02-100", "Warszawa", "rma@acme.local"),
                supplierRmaCenter(storeId, ACME_B, "demo-rma-center-acmeb", "demo-rma-addr-acmeb",
                        "AcmeB sp. z o.o.", "ul. Hurtowa 7", "26-600", "Radom", "rma@acmeb.local"));
    }

    private static RMACenter supplierRmaCenter(String storeId, String provider, String rmaCenterId, String addressId,
                                               String companyName, String street, String postalCode, String city, String email) {
        RMACenter center = new RMACenter();
        center.setStoreId(storeId);
        center.setRmaCenterId(rmaCenterId);
        center.setProvider(provider);
        ShippingDetails address = new ShippingDetails();
        address.setId(addressId);
        address.setName("Centrum");
        address.setSurname("Zwrotow");
        address.setCompanyName(companyName);
        address.setStreetAndNumber(street);
        address.setPostalCode(postalCode);
        address.setCity(city);
        address.setCountry("PL");
        address.setEmail(email);
        address.setPhone("+48123123123");
        center.setShippingDetails(address);
        return center;
    }

    private void saveCompletedOrders(Store store, List<CatalogSeedRow> rows) {
        DynamoDBMapper mapper = new DynamoDBMapper(dynamoDB);
        DynamoDBMapperConfig clobber = DynamoDBMapperConfig.builder()
                .withSaveBehavior(DynamoDBMapperConfig.SaveBehavior.CLOBBER)
                .build();
        CompletedDemoOrders completed = buildCompletedDemoOrders(
                store.getStoreId(), ownerEmailOrFallback(store.getDemo()), rows);
        completed.orders().forEach(order -> mapper.save(order, clobber));
        completed.itemsByOrderId().values().forEach(mapper::batchSave);
        completed.deliveries().forEach(delivery -> mapper.save(delivery, clobber));
        completed.documents().forEach(document -> mapper.save(document, clobber));
        mapper.batchSave(completed.documentItems());
        completed.sequences().forEach(sequence -> mapper.save(sequence, clobber));
        completed.events().forEach(event -> mapper.save(event, clobber));
        mapper.save(completed.rma(), clobber);
        completed.rmaItems().forEach(rmaItem -> mapper.save(rmaItem, clobber));
    }

    static CompletedDemoOrders buildCompletedDemoOrders(String storeId, String ownerEmail, List<CatalogSeedRow> rows) {
        List<CatalogSeedRow> catalogRows = rows.stream().filter(CatalogSeedRow::inCatalog).toList();
        List<CatalogSeedRow> acmeRows = catalogRows.stream().filter(row -> row.soldBy(ACME)).toList();
        List<CatalogSeedRow> acmeBRows = catalogRows.stream().filter(row -> row.soldBy(ACME_B)).toList();
        String warehouseId = DEMO_WAREHOUSE_ID;
        String pzSequenceKey = DocumentType.GoodsReceipt.getSequenceKey(warehouseId);
        String wzSequenceKey = DocumentType.GoodsIssue.getSequenceKey(warehouseId);

        CompletedOrderBundle first = completedOrderBundle(storeId, ownerEmail, "Tomasz", "Lis",
                COMPLETED_ORDER_KEY, new OrderSource("Sklep internetowy", OrderSourceType.WebStore), null,
                List.of(acmeRows.get(3), acmeRows.get(4)), ACME, ConnectionMode.GLOBAL, acmeOrderRef(104496),
                acmeCounterparty(), 8, warehouseId, pzSequenceKey + "/000001", wzSequenceKey + "/000001", "1");
        CompletedOrderBundle second = completedOrderBundle(storeId, ownerEmail, "Ewa", "Mazur",
                COMPLETED_ORDER_2_KEY, new OrderSource("Allegro", OrderSourceType.Marketplace),
                demoExternalOrderNo(storeId, COMPLETED_EXTERNAL_KEY),
                List.of(acmeBRows.get(3)), ACME_B, ConnectionMode.OWN, acmeBOrderRef(88203),
                acmeBCounterparty(), 15, warehouseId, pzSequenceKey + "/000002", wzSequenceKey + "/000002", "2");

        Map<String, List<OrderItem>> itemsByOrderId = new HashMap<>();
        itemsByOrderId.put(first.order().getOrderId(), first.items());
        itemsByOrderId.put(second.order().getOrderId(), second.items());

        List<WarehouseDocument> documents = new ArrayList<>(first.documents());
        documents.addAll(second.documents());
        List<WarehouseDocumentItem> documentItems = new ArrayList<>(first.documentItems());
        documentItems.addAll(second.documentItems());
        List<OrderEvent> events = new ArrayList<>(first.events());
        events.addAll(second.events());

        RMA rma = demoRma(storeId, first.order());
        List<RMAItem> rmaItems = List.of(demoRmaItem(storeId, rma.getRmaId(), first.items().getFirst()));

        return new CompletedDemoOrders(
                List.of(first.order(), second.order()),
                itemsByOrderId,
                List.of(first.delivery(), second.delivery()),
                documents,
                documentItems,
                List.of(new WarehouseDocumentSequence(storeId, pzSequenceKey, 2),
                        new WarehouseDocumentSequence(storeId, wzSequenceKey, 2)),
                events,
                rma,
                rmaItems);
    }

    private static RMA demoRma(String storeId, Order order) {
        RMA rma = new RMA(storeId);
        rma.setRmaId(demoId(storeId, "demo-rma-1"));
        rma.setOrderId(order.getOrderId());
        rma.setEmail(order.getBillingDetails().getEmail());
        rma.setStatus(RMAStatus.Approved);
        rma.setCreatedAt(LocalDateTime.now().minusDays(2));
        rma.setEmailNotificationsEnabled(false);
        rma.setShippingDetails(customerShippingDetails(
                order.getBillingDetails().getName(), order.getBillingDetails().getSurname(),
                order.getBillingDetails().getEmail()));
        rma.addEvent(new Event(EventType.email,
                EmailNotificationType.RMA_CARRIER_ARRANGEMENT.name(), rma.getCreatedAt().plusMinutes(10)));
        return rma;
    }

    private static RMAItem demoRmaItem(String storeId, String rmaId, OrderItem orderItem) {
        RMAItem item = new RMAItem();
        item.setRmaId(rmaId);
        item.setRmaItemId(demoId(storeId, "demo-rma-1-item-1"));
        item.setItemId(orderItem.getItemId());
        item.setDesiredResolution(RMAResolutionType.Repair);
        item.setReason("Produkt nie uruchamia się po podłączeniu zasilania");
        item.setQty(1);
        item.setStatus(RMAItemStatus.New);
        item.setName(orderItem.getName());
        item.setDeliveryId(orderItem.getDeliveryId());
        item.setEan(orderItem.getEan());
        item.setMfn(orderItem.getManufacturerCode());
        item.setPrice(orderItem.getPrice());
        item.setCost(orderItem.getCost());
        item.setTax(orderItem.getTax());
        return item;
    }

    private record CompletedOrderBundle(Order order, List<OrderItem> items, Delivery delivery,
                                        List<WarehouseDocument> documents, List<WarehouseDocumentItem> documentItems,
                                        List<OrderEvent> events) {
    }

    private static CompletedOrderBundle completedOrderBundle(String storeId, String ownerEmail, String name, String surname,
                                                             String orderKey, OrderSource source, String externalOrderId,
                                                             List<CatalogSeedRow> rows, String supplier, ConnectionMode connectionMode,
                                                             String externalDeliveryId, CounterpartyDetails supplierCounterparty,
                                                             int orderedDayOfPreviousMonth, String warehouseId, String pzNumber, String wzNumber,
                                                             String paymentSuffix) {
        LocalDateTime orderedAt = LocalDate.now().minusMonths(1).withDayOfMonth(orderedDayOfPreviousMonth).atTime(10, 30);
        LocalDateTime receivedAt = orderedAt.plusDays(2);
        LocalDateTime shippedAt = orderedAt.plusDays(3);

        Order order = demoOrder(storeId,name, surname, demoId(storeId, orderKey), source);
        order.setExternalOrderId(externalOrderId);
        order.setStatus(OrderStatus.Completed);
        order.setOrderedAt(orderedAt);
        order.setEstimatedAssemblyAt(receivedAt.toLocalDate());
        order.setEstimatedShippingAt(shippedAt.toLocalDate());

        Delivery delivery = new Delivery(storeId, externalDeliveryId, supplier,
                receivedAt.toLocalDate(), 15.0, 0.0, 14, Price.DEFAULT_VAT_RATE);
        delivery.setDeliveryId(demoId(storeId, orderKey + "-delivery"));
        delivery.setConnectionMode(connectionMode);
        delivery.setOrderedAt(orderedAt);
        delivery.setReceivedAt(receivedAt);
        delivery.addEvent(new Event(EventType.action, "DELIVERY_RECEIVED", receivedAt));

        List<OrderItem> items = new ArrayList<>();
        int position = 0;
        for (CatalogSeedRow row : rows) {
            OrderItem item = allocationItem(order.getOrderId(), row, delivery.getDeliveryId(), 1, ++position);
            item.setStatus(FulfilmentStatus.Delivered);
            items.add(item);
        }
        order.setTotalPrice(items.stream().mapToDouble(OrderItem::getTotalPrice).sum());
        order.setPayments(new ArrayList<>(List.of(Payment.bankTransfer(
                "DEMO-PAY-C" + paymentSuffix, name + " " + surname, order.getTotalPrice()))));

        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setCarrier("DHL");
        shipment.setTrackingNo(demoExternalOrderNo(storeId, orderKey + "-tracking"));
        shipment.setShippedAt(shippedAt);
        shipment.setDeliveredAt(shippedAt.plusDays(1));
        order.setShipments(new ArrayList<>(List.of(shipment)));
        order.addDocument(new Document(demoId(storeId, orderKey + "-receipt"),
                "PAR/" + LocalDate.now().getYear() + "/DEMO/00" + paymentSuffix, null, DocumentType.Receipt));

        delivery.increaseTotalCost(items.stream().mapToDouble(item -> item.getCost() * item.getQty()).sum());
        delivery.addPayment(Payment.outgoingBankTransfer(
                "DEMO-PAY-DELIV-" + paymentSuffix, "Demo Store sp. z o.o.", delivery.getTotalCostGross()));

        WarehouseDocument goodsReceipt = warehouseDocument(storeId, orderKey + "-pz", pzNumber, DocumentType.GoodsReceipt,
                warehouseId, receivedAt, ownerEmail, DocumentReason.SupplierDelivery, supplierCounterparty);
        goodsReceipt.setDeliveryId(delivery.getDeliveryId());
        delivery.addDocument(new Document(goodsReceipt.getDocumentId(), goodsReceipt.getDocumentNo(), null, DocumentType.GoodsReceipt));

        WarehouseDocument goodsIssue = warehouseDocument(storeId, orderKey + "-wz", wzNumber, DocumentType.GoodsIssue,
                warehouseId, shippedAt, ownerEmail, DocumentReason.CustomerOrder, customerCounterparty(order));
        goodsIssue.setOrderId(order.getOrderId());
        goodsIssue.setDeliveryAddress(deliveryAddress(order.getShippingDetails()));
        order.addDocument(new Document(goodsIssue.getDocumentId(), goodsIssue.getDocumentNo(), null, DocumentType.GoodsIssue));

        List<WarehouseDocumentItem> documentItems = new ArrayList<>();
        documentItems.addAll(documentItems(storeId, goodsReceipt, delivery.getDeliveryId(), items));
        documentItems.addAll(documentItems(storeId, goodsIssue, delivery.getDeliveryId(), items));

        List<OrderEvent> events = List.of(
                orderEvent(storeId, order, EventType.email, EmailNotificationType.ORDER_CONFIRMATION.name(), orderedAt),
                orderEvent(storeId, order, EventType.email, EmailNotificationType.ORDER_ASSEMBLY.name(), orderedAt.plusHours(2)),
                orderEvent(storeId, order, EventType.email, EmailNotificationType.ORDER_ASSEMBLED.name(), receivedAt),
                orderEvent(storeId, order, EventType.email, EmailNotificationType.ORDER_SHIPPING.name(), shippedAt),
                orderEvent(storeId, order, EventType.action, "SHIPMENT_COLLECTED", shippedAt.plusHours(4)),
                orderEvent(storeId, order, EventType.action, "SHIPMENT_DELIVERED", shipment.getDeliveredAt()));

        return new CompletedOrderBundle(order, items, delivery, List.of(goodsReceipt, goodsIssue), documentItems, events);
    }

    private void saveWarehouseStock(Store store, List<CatalogSeedRow> rows) {
        DynamoDBMapper mapper = new DynamoDBMapper(dynamoDB);
        DynamoDBMapperConfig clobber = DynamoDBMapperConfig.builder()
                .withSaveBehavior(DynamoDBMapperConfig.SaveBehavior.CLOBBER)
                .build();
        WarehouseStock stock = buildWarehouseStock(
                store.getStoreId(), ownerEmailOrFallback(store.getDemo()), rows);
        mapper.batchSave(stock.items());
        stock.deliveries().forEach(delivery -> mapper.save(delivery, clobber));
        stock.documents().forEach(document -> mapper.save(document, clobber));
        mapper.batchSave(stock.documentItems());
        stock.sequences().forEach(sequence -> mapper.save(sequence, clobber));
    }

    static WarehouseStock buildWarehouseStock(String storeId, String ownerEmail, List<CatalogSeedRow> rows) {
        List<CatalogSeedRow> warehouseRows = rows.stream().filter(CatalogSeedRow::inWarehouse).toList();
        String warehouseId = DEMO_WAREHOUSE_ID;
        String pzSequenceKey = DocumentType.GoodsReceipt.getSequenceKey(warehouseId);

        Delivery first = receivedWarehouseDelivery(storeId, "demo-delivery-wh-1", acmeOrderRef(104432), ACME, ConnectionMode.GLOBAL, 21);
        Delivery second = receivedWarehouseDelivery(storeId, "demo-delivery-wh-2", acmeBOrderRef(88144), ACME_B, ConnectionMode.OWN, 14);
        Delivery third = receivedWarehouseDelivery(storeId, "demo-delivery-wh-3", acmeOrderRef(104501), ACME, ConnectionMode.GLOBAL, 5);
        Delivery pending = pendingWarehouseDelivery(storeId, "demo-delivery-wh-4", acmeBOrderRef(88229), ACME_B);
        List<Delivery> deliveries = List.of(first, second, third, pending);

        Map<String, List<WarehouseItem>> itemsByDeliveryId = new HashMap<>();
        List<WarehouseItem> items = new ArrayList<>();
        for (int i = 0; i < warehouseRows.size(); i++) {
            Delivery delivery = deliveries.get(i % deliveries.size());
            WarehouseItem item = warehouseItem(storeId, warehouseRows.get(i), delivery);
            items.add(item);
            itemsByDeliveryId.computeIfAbsent(delivery.getDeliveryId(), key -> new ArrayList<>()).add(item);
        }

        List<WarehouseDocument> documents = new ArrayList<>();
        List<WarehouseDocumentItem> documentItems = new ArrayList<>();
        List<Delivery> received = List.of(first, second, third);
        for (int i = 0; i < received.size(); i++) {
            Delivery delivery = received.get(i);
            int number = i + 1;
            List<WarehouseItem> deliveryItems = itemsByDeliveryId.get(delivery.getDeliveryId());
            delivery.increaseTotalCost(deliveryItems.stream().mapToDouble(item -> item.getCost() * item.getQty()).sum());
            delivery.addPayment(Payment.outgoingBankTransfer(
                    "DEMO-PAY-WH-" + number, "Demo Store sp. z o.o.", delivery.getTotalCostGross()));

            CounterpartyDetails counterparty = ACME.equals(delivery.getProvider()) ? acmeCounterparty() : acmeBCounterparty();
            WarehouseDocument goodsReceipt = warehouseDocument(storeId, "demo-doc-pz-wh-" + number,
                    pzSequenceKey + "/" + String.format("%06d", number + 2), DocumentType.GoodsReceipt,
                    warehouseId, delivery.getReceivedAt(), ownerEmail, DocumentReason.SupplierDelivery, counterparty);
            goodsReceipt.setDeliveryId(delivery.getDeliveryId());
            delivery.addDocument(new Document(goodsReceipt.getDocumentId(), goodsReceipt.getDocumentNo(), null, DocumentType.GoodsReceipt));
            delivery.addDocument(new Document(demoId(storeId, "demo-invoice-wh-" + number),
                    "FV/" + LocalDate.now().getYear() + "/DEMO/" + String.format("%03d", number), null, DocumentType.InvoiceVat));
            documents.add(goodsReceipt);
            documentItems.addAll(warehouseDocumentItems(storeId, goodsReceipt, delivery.getDeliveryId(), deliveryItems));
        }

        pending.increaseTotalCost(itemsByDeliveryId.get(pending.getDeliveryId()).stream()
                .mapToDouble(item -> item.getCost() * item.getQty()).sum());

        return new WarehouseStock(items, deliveries, documents, documentItems,
                List.of(new WarehouseDocumentSequence(storeId, pzSequenceKey, 5)));
    }

    private static WarehouseItem warehouseItem(String storeId, CatalogSeedRow row, Delivery delivery) {
        double unitCost = Math.round(row.priceGross() / Price.DEFAULT_VAT_RATE * WAREHOUSE_MARGIN);
        WarehouseItem item = new WarehouseItem(storeId, delivery.getDeliveryId(), row.category(), row.name(),
                row.ean(), row.mfn(), unitCost, WAREHOUSE_QTY);
        item.setItemId(demoId(storeId, "local-wh-" + row.pimId()));
        item.setStatus(delivery.hasBeenReceived() ? FulfilmentStatus.Delivered : FulfilmentStatus.Ordered);
        return item;
    }

    private static Delivery receivedWarehouseDelivery(String storeId, String key, String externalDeliveryId,
                                                      String supplier, ConnectionMode connectionMode, int receivedDaysAgo) {
        LocalDateTime receivedAt = LocalDateTime.now().minusDays(receivedDaysAgo);
        Delivery delivery = warehouseDelivery(storeId, key, externalDeliveryId, supplier, connectionMode,
                receivedAt.minusDays(2), receivedAt.toLocalDate());
        delivery.setReceivedAt(receivedAt);
        delivery.addEvent(new Event(EventType.action, "DELIVERY_RECEIVED", receivedAt));
        return delivery;
    }

    private static Delivery pendingWarehouseDelivery(String storeId, String key, String externalDeliveryId, String supplier) {
        return warehouseDelivery(storeId, key, externalDeliveryId, supplier, ConnectionMode.OWN,
                LocalDateTime.now().minusDays(1), LocalDate.now().plusDays(3));
    }

    private static String acmeOrderRef(int number) {
        return "ZS/" + number + "/" + LocalDate.now().getYear();
    }

    private static String acmeBOrderRef(int number) {
        return "ZK/" + number + "/" + LocalDate.now().getYear();
    }

    private static Delivery warehouseDelivery(String storeId, String key, String externalDeliveryId, String supplier,
                                              ConnectionMode connectionMode, LocalDateTime orderedAt, LocalDate estimatedDeliveryAt) {
        Delivery delivery = new Delivery(storeId, externalDeliveryId, supplier, estimatedDeliveryAt,
                15.0, 0.0, 14, Price.DEFAULT_VAT_RATE);
        delivery.setDeliveryId(demoId(storeId, key));
        delivery.setConnectionMode(connectionMode);
        delivery.setOrderedAt(orderedAt);
        return delivery;
    }

    private static List<WarehouseDocumentItem> warehouseDocumentItems(String storeId, WarehouseDocument document,
                                                                      String deliveryId, List<WarehouseItem> items) {
        List<WarehouseDocumentItem> documentItems = new ArrayList<>();
        for (WarehouseItem item : items) {
            WarehouseDocumentItem documentItem = new WarehouseDocumentItem(document.getDocumentId(), document.getType(),
                    document.getCreatedAt(), deliveryId, item.getEan(), item.getManufacturerCode(), item.getName(),
                    item.getQty(), item.getCost());
            documentItem.setItemId(demoId(storeId, document.getDocumentId() + "-item-" + item.getItemId()));
            documentItems.add(documentItem);
        }
        return documentItems;
    }

    private static WarehouseDocument warehouseDocument(String storeId, String key, String documentNo, DocumentType type,
                                                       String warehouseId, LocalDateTime createdAt, String createdBy,
                                                       DocumentReason reason, CounterpartyDetails counterparty) {
        WarehouseDocument document = new WarehouseDocument(storeId, documentNo, type);
        document.setDocumentId(demoId(storeId, key));
        document.setWarehouseId(warehouseId);
        document.setCreatedAt(createdAt);
        document.setCreatedBy(createdBy);
        document.setReason(reason);
        document.setIssuer(demoIssuer());
        document.setCounterparty(counterparty);
        return document;
    }

    private static List<WarehouseDocumentItem> documentItems(String storeId, WarehouseDocument document,
                                                             String deliveryId, List<OrderItem> items) {
        List<WarehouseDocumentItem> documentItems = new ArrayList<>();
        for (OrderItem item : items) {
            WarehouseDocumentItem documentItem = new WarehouseDocumentItem(document.getDocumentId(), document.getType(),
                    document.getCreatedAt(), deliveryId, item.getEan(), item.getManufacturerCode(), item.getName(),
                    item.getQty(), item.getCost());
            documentItem.setItemId(demoId(storeId, document.getDocumentId() + "-item-" + item.getPosition()));
            documentItems.add(documentItem);
        }
        return documentItems;
    }

    private static IssuerDetails demoIssuer() {
        IssuerDetails issuer = new IssuerDetails();
        issuer.setCompanyName("Demo Store sp. z o.o.");
        issuer.setStreetAndNumber("ul. Testowa 1");
        issuer.setPostalCode("00-001");
        issuer.setCity("Warszawa");
        issuer.setCountry("PL");
        issuer.setTaxId("1234567890");
        return issuer;
    }

    private static CounterpartyDetails acmeCounterparty() {
        return supplierCounterparty("Acme sp. z o.o.", "ul. Dystrybucyjna 10", "02-100", "Warszawa", "5213000001");
    }

    private static CounterpartyDetails acmeBCounterparty() {
        return supplierCounterparty("AcmeB sp. z o.o.", "ul. Hurtowa 7", "26-600", "Radom", "9482000002");
    }

    private static CounterpartyDetails supplierCounterparty(String companyName, String street, String postalCode,
                                                            String city, String taxId) {
        CounterpartyDetails details = new CounterpartyDetails();
        details.setCompanyName(companyName);
        details.setStreetAndNumber(street);
        details.setPostalCode(postalCode);
        details.setCity(city);
        details.setCountry("PL");
        details.setTaxId(taxId);
        return details;
    }

    private static CounterpartyDetails customerCounterparty(Order order) {
        CounterpartyDetails details = new CounterpartyDetails();
        details.setName(order.getBillingDetails().getName());
        details.setSurname(order.getBillingDetails().getSurname());
        details.setStreetAndNumber(order.getBillingDetails().getStreetAndNumber());
        details.setPostalCode(order.getBillingDetails().getPostalCode());
        details.setCity(order.getBillingDetails().getCity());
        details.setCountry(order.getBillingDetails().getCountry());
        return details;
    }

    private static DeliveryAddress deliveryAddress(ShippingDetails shipping) {
        DeliveryAddress address = new DeliveryAddress();
        address.setName(shipping.getName());
        address.setSurname(shipping.getSurname());
        address.setCompanyName(shipping.getCompanyName());
        address.setStreetAndNumber(shipping.getStreetAndNumber());
        address.setPostalCode(shipping.getPostalCode());
        address.setCity(shipping.getCity());
        address.setCountry(shipping.getCountry());
        return address;
    }

    private void savePricelist(String storeId) {
        try (InputStream template = DemoStoreSeeder.class.getResourceAsStream(PRICELIST_TEMPLATE)) {
            if (template == null) {
                throw new IllegalStateException("Missing pricelist template resource: " + PRICELIST_TEMPLATE);
            }
            String csv = new String(template.readAllBytes(), StandardCharsets.UTF_8);
            String content = simulationSuppliersAvailable() ? csv : filterSimulationPricelistRows(csv);
            fileStorage.put(storesBucket, storeId + "/pricelists/" + CATALOG_ID + "/seed.csv",
                    content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write demo pricelist for store " + storeId, e);
        }
    }

    static String filterSimulationPricelistRows(String csv) {
        return Arrays.stream(csv.split("\n"))
                .filter(line -> !isSimulationPricelistRow(line))
                .collect(Collectors.joining("\n", "", "\n"));
    }

    private static boolean isSimulationPricelistRow(String line) {
        String[] columns = line.split(";", -1);
        return columns.length > 2 && columns[2].startsWith(SIM_MFN_PREFIX);
    }

    private void saveOrders(DynamoDBMapper mapper, DynamoDBMapperConfig clobber, String storeId, List<CatalogSeedRow> rows) {
        DemoOrders demoOrders = buildDemoOrders(storeId, rows);
        demoOrders.orders().forEach(order -> mapper.save(order, clobber));
        demoOrders.itemsByOrderId().values().forEach(mapper::batchSave);
        mapper.save(demoOrders.delivery(), clobber);
        demoOrders.events().forEach(event -> mapper.save(event, clobber));

        SimOrders simOrders = buildSimOrders(storeId, rows);
        simOrders.orders().forEach(order -> mapper.save(order, clobber));
        simOrders.itemsByOrderId().values().forEach(mapper::batchSave);
        simOrders.events().forEach(event -> mapper.save(event, clobber));
    }

    private boolean simulationSuppliersAvailable() {
        return supplierRegistry.exists(ACME);
    }

    static List<CatalogSeedRow> filterSimulationRows(List<CatalogSeedRow> rows, boolean simulationSuppliersAvailable) {
        if (simulationSuppliersAvailable) {
            return rows;
        }
        return rows.stream().filter(row -> !row.mfn().startsWith(SIM_MFN_PREFIX)).toList();
    }

    private static String ownerEmailOrFallback(DemoStoreMetadata demo) {
        return demo != null ? demo.getOwnerEmail() : "demo@commercelink.local";
    }

    static DemoOrders buildDemoOrders(String storeId, List<CatalogSeedRow> rows) {
        List<CatalogSeedRow> catalogRows = rows.stream().filter(CatalogSeedRow::inCatalog).toList();
        List<Order> orders = new ArrayList<>();
        Map<String, List<OrderItem>> itemsByOrderId = new HashMap<>();

        Order first = demoOrder(storeId,"Jan", "Kowalski", demoId(storeId, POS_ORDER_KEY),
                new OrderSource("Demo", OrderSourceType.PointOfSale));
        itemsByOrderId.put(first.getOrderId(), List.of(
                unassignedItem(first.getOrderId(), catalogRows.get(0), 1, 1),
                unassignedItem(first.getOrderId(), catalogRows.get(1), 2, 2)));
        Order second = demoOrder(storeId,"Anna", "Nowak", demoId(storeId, MARKETPLACE_ORDER_KEY),
                new OrderSource("Allegro", OrderSourceType.Marketplace));
        second.setExternalOrderId(demoExternalOrderNo(storeId, MARKETPLACE_EXTERNAL_KEY));
        itemsByOrderId.put(second.getOrderId(), List.of(
                allocationItem(second.getOrderId(), catalogRows.get(2), ACME, 1, 1)));

        Delivery delivery = new Delivery(storeId, acmeOrderRef(104518), ACME,
                LocalDate.now().plusDays(2), 15.0, 0.0, 14, Price.DEFAULT_VAT_RATE);
        delivery.setDeliveryId(demoId(storeId, "demo-delivery-open"));
        delivery.setType(DeliveryType.WAREHOUSE);
        Order third = demoOrder(storeId,"Piotr", "Wisniewski", demoId(storeId, MARKETPLACE_ORDER_2_KEY),
                new OrderSource("Allegro", OrderSourceType.Marketplace));
        third.setExternalOrderId(demoExternalOrderNo(storeId, MARKETPLACE_EXTERNAL_2_KEY));
        OrderItem orderedItem = allocationItem(third.getOrderId(), catalogRows.get(0), delivery.getDeliveryId(), 1, 1);
        orderedItem.setStatus(FulfilmentStatus.Ordered);
        itemsByOrderId.put(third.getOrderId(), List.of(orderedItem));
        third.setStatus(OrderStatus.Assembly);
        third.setEstimatedAssemblyAt(delivery.getEstimatedDeliveryAt());
        delivery.increaseTotalCost(orderedItem.getCost() * orderedItem.getQty());

        Order fourth = demoOrder(storeId,"Maria", "Zielinska", demoId(storeId, WEBSTORE_ORDER_KEY),
                new OrderSource("Sklep internetowy", OrderSourceType.WebStore));
        itemsByOrderId.put(fourth.getOrderId(), List.of(
                allocationItem(fourth.getOrderId(), acmeBExclusiveRow(catalogRows), ACME_B, 1, 1)));

        Order fifth = demoOrder(storeId, "Tomasz", "Lis", demoId(storeId, DROPSHIP_ACME_ORDER_KEY),
                new OrderSource("Sklep internetowy", OrderSourceType.WebStore));
        fifth.setFulfilmentType(FulfilmentType.DirectToConsumer);
        itemsByOrderId.put(fifth.getOrderId(), List.of(
                allocationItem(fifth.getOrderId(), acmeRow(catalogRows), ACME, 1, 1)));

        Order sixth = demoOrder(storeId, "Zofia", "Krol", demoId(storeId, DROPSHIP_ACME_B_ORDER_KEY),
                new OrderSource("Sklep internetowy", OrderSourceType.WebStore));
        sixth.setFulfilmentType(FulfilmentType.DirectToConsumer);
        itemsByOrderId.put(sixth.getOrderId(), List.of(
                allocationItem(sixth.getOrderId(), acmeBExclusiveRow(catalogRows), ACME_B, 1, 1)));

        List<CatalogSeedRow> acmeRows = acmeRows(catalogRows, 3);
        Order pickupAtAcme = dropshipOrder(storeId, "Krzysztof", "Dudek", demoId(storeId, DROPSHIP_PICKUP_ACME_ORDER_KEY),
                pickupShipment("InPost", "WAW04A"));
        itemsByOrderId.put(pickupAtAcme.getOrderId(), List.of(
                allocationItem(pickupAtAcme.getOrderId(), acmeRows.get(0), ACME, 1, 1),
                allocationItem(pickupAtAcme.getOrderId(), acmeRows.get(1), ACME, 1, 2)));
        Order pickupAtAcmeB = dropshipOrder(storeId, "Barbara", "Zajac", demoId(storeId, DROPSHIP_PICKUP_ACME_B_ORDER_KEY),
                pickupShipment("DPD", "PL12345"));
        itemsByOrderId.put(pickupAtAcmeB.getOrderId(), List.of(
                allocationItem(pickupAtAcmeB.getOrderId(), acmeBExclusiveRow(catalogRows), ACME_B, 1, 1)));
        Order pickupWithoutCode = dropshipOrder(storeId, "Pawel", "Sadowski", demoId(storeId, DROPSHIP_PICKUP_NO_CODE_ORDER_KEY),
                pickupShipment("InPost", null));
        itemsByOrderId.put(pickupWithoutCode.getOrderId(), List.of(
                allocationItem(pickupWithoutCode.getOrderId(), acmeRows.get(0), ACME, 1, 1)));
        Order courierMulti = dropshipOrder(storeId, "Natalia", "Borkowska", demoId(storeId, DROPSHIP_ACME_MULTI_ORDER_KEY), null);
        itemsByOrderId.put(courierMulti.getOrderId(), List.of(
                allocationItem(courierMulti.getOrderId(), acmeRows.get(0), ACME, 1, 1),
                allocationItem(courierMulti.getOrderId(), acmeRows.get(1), ACME, 1, 2),
                allocationItem(courierMulti.getOrderId(), acmeRows.get(2), ACME, 1, 3)));
        Order courierAcmeSpare = dropshipOrder(storeId, "Joanna", "Michalska", demoId(storeId, DROPSHIP_ACME_SPARE_ORDER_KEY), null);
        itemsByOrderId.put(courierAcmeSpare.getOrderId(), List.of(
                allocationItem(courierAcmeSpare.getOrderId(), acmeRows.get(1), ACME, 1, 1)));
        Order courierAcmeBSpare = dropshipOrder(storeId, "Lukasz", "Czarnecki", demoId(storeId, DROPSHIP_ACME_B_SPARE_ORDER_KEY), null);
        itemsByOrderId.put(courierAcmeBSpare.getOrderId(), List.of(
                allocationItem(courierAcmeBSpare.getOrderId(), acmeBExclusiveRow(catalogRows), ACME_B, 1, 1)));
        Order warehouseTwoItems = demoOrder(storeId, "Marek", "Pawlak", demoId(storeId, WAREHOUSE_ACME_TWO_ITEMS_ORDER_KEY),
                new OrderSource("Sklep internetowy", OrderSourceType.WebStore));
        itemsByOrderId.put(warehouseTwoItems.getOrderId(), List.of(
                allocationItem(warehouseTwoItems.getOrderId(), acmeRows.get(0), ACME, 1, 1),
                allocationItem(warehouseTwoItems.getOrderId(), acmeRows.get(1), ACME, 1, 2)));

        orders.add(first);
        orders.add(second);
        orders.add(third);
        orders.add(fourth);
        orders.add(fifth);
        orders.add(sixth);
        orders.addAll(List.of(pickupAtAcme, pickupAtAcmeB, pickupWithoutCode, courierMulti, courierAcmeSpare,
                courierAcmeBSpare, warehouseTwoItems));
        orders.forEach(order -> order.setTotalPrice(itemsByOrderId.get(order.getOrderId()).stream()
                .mapToDouble(OrderItem::getTotalPrice).sum()));
        first.setPayments(new ArrayList<>(List.of(
                Payment.bankTransfer("DEMO-PAY-001", "Jan Kowalski", Math.round(first.getTotalPrice() / 2.0)))));
        second.setPayments(new ArrayList<>(List.of(
                Payment.bankTransfer("DEMO-PAY-002", "Anna Nowak", second.getTotalPrice()))));

        List<OrderEvent> events = new ArrayList<>();
        orders.forEach(order -> events.add(orderEvent(storeId, order,
                EventType.email, EmailNotificationType.ORDER_CONFIRMATION.name(), order.getOrderedAt())));
        events.add(orderEvent(storeId, third,
                EventType.email, EmailNotificationType.ORDER_ASSEMBLY.name(), third.getOrderedAt().plusHours(1)));
        return new DemoOrders(orders, itemsByOrderId, delivery, events);
    }

    private static OrderEvent orderEvent(String storeId, Order order, EventType type, String name, LocalDateTime createdAt) {
        OrderEvent event = new OrderEvent(order.getOrderId(), type, name, createdAt);
        event.setEventId(demoId(storeId, order.getOrderId() + "-event-" + name));
        return event;
    }

    static SimOrders buildSimOrders(String storeId, List<CatalogSeedRow> rows) {
        List<Order> orders = new ArrayList<>();
        Map<String, List<OrderItem>> itemsByOrderId = new HashMap<>();
        List<OrderEvent> events = new ArrayList<>();

        rows.stream()
                .filter(row -> row.mfn().startsWith(SIM_MFN_PREFIX))
                .forEach(row -> SIM_SUPPLIERS.stream()
                        .filter(row::soldBy)
                        .forEach(supplier -> {
                            Order order = demoOrder(storeId, "Symulacja", simulationScenarioLabel(row),
                                    demoId(storeId, "sim-" + row.mfn().toLowerCase(Locale.ROOT) + "-" + supplier.toLowerCase(Locale.ROOT)),
                                    new OrderSource("Sklep internetowy", OrderSourceType.WebStore));
                            OrderItem item = allocationItem(order.getOrderId(), row, supplier, 1, 1);
                            order.setTotalPrice(item.getTotalPrice());
                            orders.add(order);
                            itemsByOrderId.put(order.getOrderId(), List.of(item));
                            events.add(orderEvent(storeId, order,
                                    EventType.email, EmailNotificationType.ORDER_CONFIRMATION.name(), order.getOrderedAt()));
                        }));

        return new SimOrders(orders, itemsByOrderId, events);
    }

    private static String simulationScenarioLabel(CatalogSeedRow row) {
        return row.name().startsWith(SIM_LABEL_PREFIX) ? row.name().substring(SIM_LABEL_PREFIX.length()) : row.name();
    }

    private static CatalogSeedRow acmeRow(List<CatalogSeedRow> catalogRows) {
        return catalogRows.stream()
                .filter(row -> row.soldBy(ACME))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No catalog row sold by " + ACME));
    }

    private static List<CatalogSeedRow> acmeRows(List<CatalogSeedRow> catalogRows, int count) {
        List<CatalogSeedRow> rows = catalogRows.stream().filter(row -> row.soldBy(ACME)).limit(count).toList();
        if (rows.size() < count) {
            throw new IllegalStateException("Need " + count + " catalog rows sold by " + ACME + ", found " + rows.size());
        }
        return rows;
    }

    private static Order dropshipOrder(String storeId, String name, String surname, String orderId, Shipment pickup) {
        Order order = demoOrder(storeId, name, surname, orderId, new OrderSource("Sklep internetowy", OrderSourceType.WebStore));
        order.setFulfilmentType(FulfilmentType.DirectToConsumer);
        if (pickup != null) {
            order.setShipments(new ArrayList<>(List.of(pickup)));
        }
        return order;
    }

    private static Shipment pickupShipment(String carrier, String collectionPointCode) {
        Shipment shipment = new Shipment(ShipmentType.PickupPoint);
        shipment.setCarrier(carrier);
        shipment.setCollectionPointCode(collectionPointCode);
        return shipment;
    }

    private static CatalogSeedRow acmeBExclusiveRow(List<CatalogSeedRow> catalogRows) {
        return catalogRows.stream()
                .filter(row -> row.soldBy(ACME_B) && !row.soldBy(ACME))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No catalog row sold only by " + ACME_B));
    }

    private static Order demoOrder(String storeId, String name, String surname, String orderId,
                                   OrderSource source) {
        Order order = new Order(storeId);
        order.setOrderId(orderId);
        BillingDetails billing = new BillingDetails();
        billing.setName(name);
        billing.setSurname(surname);
        billing.setEmail(customerEmail(storeId, name, surname));
        billing.setStreetAndNumber("ul. Przykladowa 5");
        billing.setPostalCode("00-002");
        billing.setCity("Warszawa");
        billing.setCountry("PL");
        order.setBillingDetails(billing);
        order.setShippingDetails(customerShippingDetails(name, surname, billing.getEmail()));
        order.setSource(source);
        order.setFulfilmentType(FulfilmentType.WarehouseFulfilment);
        order.setEstimatedShippingAt(LocalDate.now().plusDays(3));
        return order;
    }

    private static OrderItem allocationItem(String orderId, CatalogSeedRow row, String deliveryId, int qty, int position) {
        OrderItem item = unassignedItem(orderId, row, qty, position);
        item.setCost(Math.round(row.priceGross() / Price.DEFAULT_VAT_RATE * WAREHOUSE_MARGIN));
        item.setDeliveryId(deliveryId);
        item.setStatus(FulfilmentStatus.Allocation);
        return item;
    }

    private static OrderItem unassignedItem(String orderId, CatalogSeedRow row, int qty, int position) {
        OrderItem item = new OrderItem(orderId, row.category(), row.name(), qty, row.priceGross(), row.mfn(), false, position);
        item.setItemId("demo-item-" + position);
        item.setEan(row.ean());
        item.setManufacturerCode(row.mfn());
        return item;
    }

    static List<CategoryDefinition> buildCategoryDefinitions(List<CatalogSeedRow> rows, String storeId) {
        Map<String, String> pimCategoryIdByCategory = rows.stream()
                .filter(row -> !row.pimCategoryId().isBlank())
                .collect(Collectors.toMap(CatalogSeedRow::category, CatalogSeedRow::pimCategoryId, (first, second) -> first));

        List<CategoryDefinition> categories = new ArrayList<>();
        int sequence = 0;
        for (String category : distinctCategories(rows)) {
            CategoryDefinition definition = new CategoryDefinition();
            definition.setCategoryId(CatalogSeed.categoryId(category, storeId));
            definition.setName(category);
            definition.setCategory(category);
            definition.setType(CategoryDefinitionType.Managed);
            definition.setRequiredDuringOrder(false);
            definition.setSequenceNumber(++sequence);
            definition.setMaxQty(10);
            definition.setDeletionProtection(false);
            definition.setStockDefinition(new StockDefinition(2, 5, 20));
            definition.setAvailabilityDefinition(new AvailabilityDefinition(1, 1));
            definition.setPriceDefinitions(new LinkedList<>(List.of(
                    new PriceDefinition(1.15, 10, 0, 0, 0, PriceDefinition.DEFAULT_PRICING_GROUP))));
            String pimCategoryId = pimCategoryIdByCategory.get(category);
            if (pimCategoryId != null) {
                definition.setPimCategoryIds(new LinkedList<>(List.of(pimCategoryId)));
            }
            categories.add(definition);
        }
        return categories;
    }

    private static List<String> distinctCategories(List<CatalogSeedRow> rows) {
        return rows.stream().map(CatalogSeedRow::category).distinct().toList();
    }

    private static ShippingDetails customerShippingDetails(String name, String surname, String email) {
        ShippingDetails address = new ShippingDetails();
        address.setName(name);
        address.setSurname(surname);
        address.setStreetAndNumber("ul. Przykladowa 5");
        address.setPostalCode("00-002");
        address.setCity("Warszawa");
        address.setCountry("PL");
        address.setEmail(email);
        address.setPhone("+48601234567");
        return address;
    }

    private static ShippingDetails warehouseAddress() {
        ShippingDetails address = new ShippingDetails();
        address.setId("local-pickup-01");
        address.setName("Demo");
        address.setSurname("Magazynier");
        address.setCompanyName("Demo Store sp. z o.o.");
        address.setStreetAndNumber("ul. Testowa 1");
        address.setPostalCode("00-001");
        address.setCity("Warszawa");
        address.setCountry("PL");
        address.setEmail("magazyn@commercelink.local");
        address.setPhone("+48123123123");
        address.set_default(true);
        return address;
    }

    private static PackageTemplate defaultPackage() {
        PackageTemplate template = new PackageTemplate();
        template.setId("local-pkg-m");
        template.setName("Karton M");
        template.setDefault(true);
        template.setParcels(List.of(new Parcel(30, 20, 15, 2, 100, "Karton M")));
        return template;
    }

    private static PackageTemplate rmaPackage() {
        PackageTemplate template = new PackageTemplate();
        template.setId("local-pkg-rma");
        template.setName("RMA - Karton S");
        template.setDefault(false);
        template.setParcels(List.of(new Parcel(25, 15, 10, 1, 50, "Karton S (RMA)")));
        return template;
    }

    private static AuthorizedCarrier carrier() {
        return new AuthorizedCarrier(CARRIER_ID, CARRIER_NAME, CARRIER_DISPLAY_NAME);
    }

    private static DeliveryOption courierDelivery() {
        DeliveryOption option = new DeliveryOption();
        option.setId("local-delivery-courier");
        option.setName("Kurier (demo)");
        option.setDescription("Dostawa kurierem 1-2 dni");
        option.setPrice(19.99);
        option.setType(ShipmentType.Courier);
        return option;
    }

    private static DeliveryOption pickupDelivery() {
        DeliveryOption option = new DeliveryOption();
        option.setId("local-delivery-pickup");
        option.setName("Odbior osobisty");
        option.setDescription("Odbior w sklepie");
        option.setPrice(0);
        option.setType(ShipmentType.PersonalCollection);
        return option;
    }

    private static BankAccount bankAccount() {
        BankAccount account = new BankAccount();
        account.setId("local-bank-01");
        account.setBankName("Demo Bank");
        account.setIban("PL61109010140000071219812874");
        account.setAccountHolder("Demo Store sp. z o.o.");
        account.setSwiftCode("WBKPPLPP");
        account.setCurrency("PLN");
        account.set_default(true);
        return account;
    }
}
