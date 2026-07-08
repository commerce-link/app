package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
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
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.PackageTemplate;
import pl.commercelink.stores.Parcel;
import pl.commercelink.stores.RMAConfiguration;
import pl.commercelink.stores.ShippingConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.WarehouseConfiguration;
import pl.commercelink.warehouse.builtin.WarehouseItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Profile("localdev")
@ChangeUnit(id = "V006-local-development-bootstrap-seed", order = "006", author = "commercelink", runAlways = true)
@RequiredArgsConstructor
public class V006_LocalDevelopmentBootstrapSeed {

    private static final String STORE_ID = "uma2dqukxr";
    private static final String CATALOG_ID = "cat-local-01";
    private static final String ACME = "Acme";
    private static final String ACME_B = "AcmeB";
    private static final String CARRIER_ID = "local-carrier-01";
    private static final String CARRIER_NAME = "local";
    private static final String CARRIER_DISPLAY_NAME = "Kurier Lokalny (demo)";
    private static final double WAREHOUSE_MARGIN = 0.85;
    private static final int WAREHOUSE_QTY = 3;

    private final AmazonDynamoDB dynamoDB;

    @Execution
    public void seed() {
        List<CatalogSeedRow> rows = CatalogSeed.load();
        DynamoDBMapper mapper = new DynamoDBMapper(dynamoDB);
        DynamoDBMapperConfig clobber = DynamoDBMapperConfig.builder()
                .withSaveBehavior(DynamoDBMapperConfig.SaveBehavior.CLOBBER)
                .build();

        patchStore(mapper);
        saveCatalog(mapper, clobber, rows);
        saveProducts(mapper, rows);
        saveWarehouseItems(mapper, rows);
        saveRmaCenter(mapper, clobber);
    }

    @RollbackExecution
    public void rollback() {
    }

    private void patchStore(DynamoDBMapper mapper) {
        Store store = mapper.load(Store.class, STORE_ID);
        if (store == null) {
            store = new Store();
            store.setStoreId(STORE_ID);
            store.setName("Demo Store");
        }

        FulfilmentConfiguration fulfilment = Objects.requireNonNullElseGet(store.getFulfilmentConfiguration(), FulfilmentConfiguration::new);
        fulfilment.setCanUseGlobalSuppliers(true);
        fulfilment.setSupplierConnections(List.of(
                new StoreSupplierConnection(ACME, ConnectionMode.GLOBAL),
                new StoreSupplierConnection(ACME_B, ConnectionMode.GLOBAL)));
        store.setFulfilmentConfiguration(fulfilment);

        WarehouseConfiguration warehouse = Objects.requireNonNullElseGet(store.getWarehouseConfiguration(), WarehouseConfiguration::new);
        warehouse.setWarehouseId("MAG-LOCAL-1");
        warehouse.setCostCenterId("KC-LOCAL-1");
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

        mapper.save(store);
    }

    private void saveCatalog(DynamoDBMapper mapper, DynamoDBMapperConfig clobber, List<CatalogSeedRow> rows) {
        List<CategoryDefinition> categories = new ArrayList<>();
        int sequence = 0;
        for (String category : distinctCategories(rows)) {
            CategoryDefinition definition = new CategoryDefinition();
            definition.setCategoryId(CatalogSeed.categoryId(category));
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
        catalog.setStoreId(STORE_ID);
        catalog.setCatalogId(CATALOG_ID);
        catalog.setName("Local Catalog");
        catalog.setDeletionProtection(false);
        catalog.setCategories(categories);
        mapper.save(catalog, clobber);
    }

    private void saveProducts(DynamoDBMapper mapper, List<CatalogSeedRow> rows) {
        List<Product> products = new ArrayList<>();
        for (CatalogSeedRow row : rows) {
            if (!row.inCatalog()) {
                continue;
            }
            Product product = new Product(CatalogSeed.categoryId(row.category()), row.pimId(), row.ean(),
                    row.mfn(), row.brand(), row.label(), row.name(), row.category(), null);
            product.setProductId("prod-" + row.pimId());
            product.setEnabled(true);
            product.setEstimatedDeliveryDays(row.estimatedDeliveryDays());
            products.add(product);
        }
        mapper.batchSave(products);
    }

    private void saveWarehouseItems(DynamoDBMapper mapper, List<CatalogSeedRow> rows) {
        List<WarehouseItem> items = new ArrayList<>();
        for (CatalogSeedRow row : rows) {
            if (!row.inWarehouse()) {
                continue;
            }
            double unitCost = Math.round(row.priceGross() / Price.DEFAULT_VAT_RATE * WAREHOUSE_MARGIN);
            WarehouseItem item = new WarehouseItem(STORE_ID, "Unknown", row.category(), row.name(),
                    row.ean(), row.mfn(), unitCost, WAREHOUSE_QTY);
            item.setItemId("local-wh-" + row.pimId());
            item.setStatus(FulfilmentStatus.Delivered);
            item.setComment("seed-local");
            items.add(item);
        }
        mapper.batchSave(items);
    }

    private void saveRmaCenter(DynamoDBMapper mapper, DynamoDBMapperConfig clobber) {
        RMACenter center = new RMACenter();
        center.setStoreId(STORE_ID);
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
