package pl.commercelink.demo;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.localdev.CatalogSeed;
import pl.commercelink.localdev.CatalogSeedRow;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.rma.RMACenter;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.CategoryDefinitionType;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.stores.AuthorizedCarrier;
import pl.commercelink.stores.BankAccount;
import pl.commercelink.stores.CheckoutConfiguration;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.stores.DemoStoreMetadata;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.PackageTemplate;
import pl.commercelink.stores.Parcel;
import pl.commercelink.stores.RMAConfiguration;
import pl.commercelink.stores.ShippingConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.WarehouseConfiguration;
import pl.commercelink.starter.storage.FileStorage;
import pl.commercelink.warehouse.builtin.WarehouseItem;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class DemoStoreSeeder {

    public static final String CATALOG_ID = "cat-local-01";

    private static final String ACME = "Acme";
    private static final String ACME_B = "AcmeB";
    private static final String PRICELIST_TEMPLATE = "/local-init/s3/stores/uma2dqukxr/pricelists/cat-local-01/seed.csv";
    private static final String CARRIER_ID = "local-carrier-01";
    private static final String CARRIER_NAME = "local";
    private static final String CARRIER_DISPLAY_NAME = "Kurier Lokalny (demo)";
    private static final double WAREHOUSE_MARGIN = 0.85;
    private static final int WAREHOUSE_QTY = 3;

    private final AmazonDynamoDB dynamoDB;
    private final FileStorage fileStorage;
    private final String storesBucket;

    public DemoStoreSeeder(AmazonDynamoDB dynamoDB,
                           FileStorage fileStorage,
                           @Value("${s3.bucket.stores}") String storesBucket) {
        this.dynamoDB = dynamoDB;
        this.fileStorage = fileStorage;
        this.storesBucket = storesBucket;
    }

    public Store seedStore(String storeId, String storeName, DemoStoreMetadata demo) {
        List<CatalogSeedRow> rows = CatalogSeed.load();
        DynamoDBMapper mapper = new DynamoDBMapper(dynamoDB);
        DynamoDBMapperConfig clobber = DynamoDBMapperConfig.builder()
                .withSaveBehavior(DynamoDBMapperConfig.SaveBehavior.CLOBBER)
                .build();

        Store store = Objects.requireNonNullElseGet(mapper.load(Store.class, storeId), Store::new);
        applyStoreConfiguration(store, storeId, storeName, demo);
        mapper.save(store);

        saveCatalog(mapper, clobber, rows, storeId);
        saveProducts(mapper, rows, storeId);
        saveWarehouseItems(mapper, rows, storeId);
        saveRmaCenter(mapper, clobber, storeId);
        savePricelist(storeId);
        return store;
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
                new StoreSupplierConnection(ACME_B, ConnectionMode.GLOBAL)));
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

    private void saveCatalog(DynamoDBMapper mapper, DynamoDBMapperConfig clobber, List<CatalogSeedRow> rows, String storeId) {
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
            categories.add(definition);
        }

        ProductCatalog catalog = new ProductCatalog();
        catalog.setStoreId(storeId);
        catalog.setCatalogId(CATALOG_ID);
        catalog.setName("Local Catalog");
        catalog.setDeletionProtection(false);
        catalog.setCategories(categories);
        mapper.save(catalog, clobber);
    }

    private void saveProducts(DynamoDBMapper mapper, List<CatalogSeedRow> rows, String storeId) {
        List<Product> products = new ArrayList<>();
        for (CatalogSeedRow row : rows) {
            if (!row.inCatalog()) {
                continue;
            }
            Product product = new Product(CatalogSeed.categoryId(row.category(), storeId), row.pimId(), row.ean(),
                    row.mfn(), row.brand(), row.label(), row.name(), row.category(), null);
            product.setProductId("prod-" + row.pimId());
            product.setEnabled(true);
            product.setEstimatedDeliveryDays(row.estimatedDeliveryDays());
            products.add(product);
        }
        mapper.batchSave(products);
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
            item.setItemId("local-wh-" + row.pimId());
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

    private void savePricelist(String storeId) {
        try (InputStream template = DemoStoreSeeder.class.getResourceAsStream(PRICELIST_TEMPLATE)) {
            if (template == null) {
                throw new IllegalStateException("Missing pricelist template resource: " + PRICELIST_TEMPLATE);
            }
            fileStorage.put(storesBucket, storeId + "/pricelists/" + CATALOG_ID + "/seed.csv", template.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write demo pricelist for store " + storeId, e);
        }
    }

    private static List<String> distinctCategories(List<CatalogSeedRow> rows) {
        return rows.stream().map(CatalogSeedRow::category).distinct().toList();
    }

    private static ShippingDetails warehouseAddress() {
        ShippingDetails address = new ShippingDetails();
        address.setId("local-pickup-01");
        address.setName("Demo");
        address.setSurname("Magazynier");
        address.setCompanyName("Demo Store Sp. z o.o.");
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
        account.setAccountHolder("Demo Store Sp. z o.o.");
        account.setSwiftCode("WBKPPLPP");
        account.setCurrency("PLN");
        account.set_default(true);
        return account;
    }
}
