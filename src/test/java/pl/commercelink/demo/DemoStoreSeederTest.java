package pl.commercelink.demo;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.localdev.CatalogSeed;
import pl.commercelink.localdev.CatalogSeedRow;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.documents.DocumentReason;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.event.OrderEvent;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMACenter;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RMAItemStatus;
import pl.commercelink.orders.rma.RMAStatus;
import pl.commercelink.warehouse.builtin.WarehouseDocument;
import pl.commercelink.warehouse.builtin.WarehouseItem;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.stores.DemoStoreMetadata;
import pl.commercelink.stores.InvoicingConfiguration;
import pl.commercelink.stores.Store;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DemoStoreSeederTest {

    @Test
    void appliesCompleteDemoConfigurationToNewStore() {
        // given
        Store store = new Store();
        DemoStoreMetadata metadata = new DemoStoreMetadata("user@example.com", "2026-07-08T10:00:00Z", "2026-07-22T10:00:00Z");

        // when
        DemoStoreSeeder.applyStoreConfiguration(store, "abc123def4", "Sklep demo", metadata);

        // then
        assertEquals("abc123def4", store.getStoreId());
        assertEquals("Sklep demo", store.getName());
        assertSame(metadata, store.getDemo());
        assertTrue(store.canUseGlobalSuppliers());
        assertEquals(List.of("Acme"), store.getGlobalSupplierNames());
        assertEquals(List.of("AcmeB"), store.getOwnSupplierNames());
        assertEquals("MAG-abc123def4", store.getWarehouseConfiguration().getWarehouseId());
        assertEquals("KC-abc123def4", store.getWarehouseConfiguration().getCostCenterId());
        assertEquals(2, store.getCheckoutConfiguration().getDeliveryOptions().size());
        assertEquals(1, store.getBankAccounts().size());
        assertEquals(2, store.getShippingConfiguration().getPackageTemplates().size());
        assertNotNull(store.getRmaConfiguration().getCarrier());
    }

    @Test
    void registeredDemoStoreGetsConstantWarehouseId() {
        // given
        Store store = new Store();
        DemoStoreSeeder.applyStoreConfiguration(store, "abc123def4", "Sklep demo", null);

        // when
        DemoStoreSeeder.applyDemoWarehouseId(store);

        // then
        assertEquals("MAG-01", store.getWarehouseConfiguration().getWarehouseId());
        assertEquals("KC-abc123def4", store.getWarehouseConfiguration().getCostCenterId());
    }

    @Test
    void fillsBillingAndWarehouseShippingDetailsForRegisteredDemoStore() {
        // given
        Store store = new Store();
        BillingDetails ownerBilling = new BillingDetails();
        ownerBilling.setEmail("owner@example.com");
        store.setBillingDetails(ownerBilling);

        // when
        DemoStoreSeeder.applyDemoCompanyDetails(store);

        // then
        BillingDetails billing = store.getBillingDetails();
        assertTrue(billing.isProperlyFilled());
        assertEquals("owner@example.com", billing.getEmail());
        assertEquals("Demo Store sp. z o.o.", billing.getCompanyName());
        assertEquals("1234567890", billing.getTaxId());
        ShippingDetails shipping = store.getDefaultShippingDetails();
        assertNotNull(shipping);
        assertEquals("demo-warehouse-ship-01", shipping.getId());
        assertEquals("Demo Store sp. z o.o.", shipping.getCompanyName());
        assertEquals("Warszawa", shipping.getCity());
    }

    @Test
    void configuresInvoicingDefaultsForRegisteredDemoStore() {
        // given
        Store store = new Store();

        // when
        DemoStoreSeeder.applyDemoInvoicingConfiguration(store);

        // then
        InvoicingConfiguration invoicing = store.getInvoicingConfiguration();
        assertEquals(7, invoicing.getPaymentTerms());
        assertTrue(invoicing.isSendInvoicesAsAttachment());
        assertTrue(invoicing.isSplitPaymentsEnabled());
    }

    @Test
    void configuresFulfilmentDayDefaultsForRegisteredDemoStore() {
        // given
        Store store = new Store();

        // when
        DemoStoreSeeder.applyDemoFulfilmentDefaults(store);

        // then
        assertEquals(1, store.getFulfilmentConfiguration().getOrderAssemblyDays());
        assertEquals(0, store.getFulfilmentConfiguration().getOrderRealizationDays());
    }

    @Test
    void buildsRmaCentersForBothDemoSuppliers() {
        // when
        List<RMACenter> centers = DemoStoreSeeder.buildSupplierRmaCenters("store-1");

        // then
        assertEquals(2, centers.size());
        assertEquals(List.of("Acme", "AcmeB"), centers.stream().map(RMACenter::getProvider).toList());
        centers.forEach(center -> {
            assertEquals("store-1", center.getStoreId());
            assertNotNull(center.getRmaCenterId());
            assertTrue(center.getShippingDetails().isProperlyFilled(),
                    center.getProvider() + " RMA center address should be properly filled");
        });
    }

    @Test
    void fallsBackToDemoEmailWhenStoreHasNoBillingDetails() {
        // given
        Store store = new Store();

        // when
        DemoStoreSeeder.applyDemoCompanyDetails(store);

        // then
        assertEquals("demo@commercelink.local", store.getBillingDetails().getEmail());
    }

    @Test
    void localBootstrapConfigurationLeavesBillingAndStoreShippingDetailsUntouched() {
        // given
        Store store = new Store();

        // when
        DemoStoreSeeder.applyStoreConfiguration(store, "uma2dqukxr", "Demo Store", null);

        // then
        assertNull(store.getBillingDetails());
        assertTrue(store.getShippingDetails().isEmpty());
    }

    @Test
    void keepsExistingStoreNameAndSkipsDemoMarkerWhenNull() {
        // given
        Store store = new Store();
        store.setName("Demo Store");

        // when
        DemoStoreSeeder.applyStoreConfiguration(store, "uma2dqukxr", "ignored", null);

        // then
        assertEquals("Demo Store", store.getName());
        assertNull(store.getDemo());
    }

    @Test
    void buildsFirstOrderWithNewItemsWithoutAssignedSupplier() {
        // given
        List<CatalogSeedRow> rows = CatalogSeed.load();

        // when
        DemoOrders demoOrders = DemoStoreSeeder.buildDemoOrders("store-1", rows);

        // then
        Order first = orderById(demoOrders, DemoStoreSeeder.demoId("store-1", DemoStoreSeeder.POS_ORDER_KEY));
        assertEquals(OrderStatus.New, first.getStatus());
        List<OrderItem> items = demoOrders.itemsByOrderId().get(first.getOrderId());
        assertEquals(2, items.size());
        items.forEach(i -> {
            assertEquals(FulfilmentStatus.New, i.getStatus());
            assertNull(i.getDeliveryId());
            assertFalse(i.hasAllocationDetails());
            assertNotNull(i.getEan());
            assertNotNull(i.getManufacturerCode());
        });
    }

    @Test
    void seededCategoryDefinitionsCarryPimCategoryIdsNextToLegacyNames() {
        // given
        List<CatalogSeedRow> rows = CatalogSeed.load();

        // when
        List<CategoryDefinition> definitions = DemoStoreSeeder.buildCategoryDefinitions(rows, "store-1");

        // then
        assertFalse(definitions.isEmpty());
        definitions.forEach(definition -> {
            assertNotNull(definition.getCategory());
            assertTrue(definition.hasCategoryMapping(), definition.getName() + " should be mapped");
        });
        CategoryDefinition cpu = definitions.stream()
                .filter(d -> "CPU".equals(d.getName())).findFirst().orElseThrow();
        assertEquals(List.of("989"), cpu.getPimCategoryIds());
        assertEquals("CPU", cpu.getCategory());
    }

    @Test
    void seededStoreEnablesComputerPeripheralsCategoryGroup() {
        // given
        Store store = new Store();

        // when
        DemoStoreSeeder.applyStoreConfiguration(store, "store-1", "Demo Store", null);

        // then
        assertEquals(List.of("Komputery i urządzenia peryferyjne"),
                store.getFulfilmentConfiguration().getEnabledCategories());
    }

    @Test
    void seededCategoryDefinitionsAreCompleteForUiEdits() {
        // given
        List<CatalogSeedRow> rows = CatalogSeed.load();

        // when
        List<CategoryDefinition> definitions = DemoStoreSeeder.buildCategoryDefinitions(rows, "store-1");

        // then
        assertFalse(definitions.isEmpty());
        definitions.forEach(definition ->
                assertTrue(definition.isComplete(), definition.getName() + " should be complete"));
    }

    @Test
    void buildsSecondOrderWithItemsInAllocation() {
        // given
        List<CatalogSeedRow> rows = CatalogSeed.load();

        // when
        DemoOrders demoOrders = DemoStoreSeeder.buildDemoOrders("store-1", rows);

        // then
        Order second = orderById(demoOrders, DemoStoreSeeder.demoId("store-1", DemoStoreSeeder.MARKETPLACE_ORDER_KEY));
        assertEquals(OrderStatus.New, second.getStatus());
        demoOrders.itemsByOrderId().get(second.getOrderId()).forEach(i -> {
            assertTrue(i.isInAllocation());
            assertNotNull(i.getEan());
            assertNotNull(i.getManufacturerCode());
            assertNotNull(i.getDeliveryId());
            assertTrue(i.getCost() > 0);
        });
    }

    @Test
    void orderWithAllItemsOrderedIsInAssemblyStatusLikeTheLifecycleWouldSet() {
        // given
        List<CatalogSeedRow> rows = CatalogSeed.load();

        // when
        DemoOrders demoOrders = DemoStoreSeeder.buildDemoOrders("store-1", rows);

        // then
        Order third = orderById(demoOrders, DemoStoreSeeder.demoId("store-1", DemoStoreSeeder.MARKETPLACE_ORDER_2_KEY));
        assertTrue(demoOrders.itemsByOrderId().get(third.getOrderId()).stream().allMatch(OrderItem::isOrdered));
        assertEquals(OrderStatus.Assembly, third.getStatus());
        assertEquals(demoOrders.delivery().getEstimatedDeliveryAt(), third.getEstimatedAssemblyAt());
    }

    @Test
    void buildsFourthOrderAllocatedToAcmeBWithAProductOnlyAcmeBSells() {
        // given
        List<CatalogSeedRow> rows = CatalogSeed.load();

        // when
        DemoOrders demoOrders = DemoStoreSeeder.buildDemoOrders("store-1", rows);

        // then
        Order fourth = orderById(demoOrders, DemoStoreSeeder.demoId("store-1", DemoStoreSeeder.WEBSTORE_ORDER_KEY));
        List<OrderItem> items = demoOrders.itemsByOrderId().get(fourth.getOrderId());
        assertEquals(1, items.size());
        OrderItem item = items.getFirst();
        assertTrue(item.isInAllocation());
        assertEquals("AcmeB", item.getDeliveryId());
        CatalogSeedRow row = rows.stream()
                .filter(r -> r.mfn().equals(item.getManufacturerCode()))
                .findFirst().orElseThrow();
        assertTrue(row.soldBy("AcmeB"));
        assertFalse(row.soldBy("Acme"));
    }

    @Test
    void buildsOrdersWithCommonDemoDetails() {
        // given
        List<CatalogSeedRow> rows = CatalogSeed.load();

        // when
        DemoOrders demoOrders = DemoStoreSeeder.buildDemoOrders("store-1", rows);

        // then
        demoOrders.orders().forEach(o -> {
            assertTrue(o.getBillingDetails().getEmail().matches("[a-z]+\\.[a-z]+\\d{2}@test\\.com"),
                    o.getBillingDetails().getEmail() + " should be a random-looking customer email at test.com");
            assertEquals(o.getBillingDetails().getEmail(), o.getShippingDetails().getEmail());
            assertNotNull(o.getShippingDetails());
            assertEquals(o.getBillingDetails().getName(), o.getShippingDetails().getName());
            assertEquals(o.getBillingDetails().getSurname(), o.getShippingDetails().getSurname());
            assertEquals(o.getBillingDetails().getStreetAndNumber(), o.getShippingDetails().getStreetAndNumber());
            assertEquals(o.getBillingDetails().getCity(), o.getShippingDetails().getCity());
            assertTrue(o.getShippingDetails().isProperlyFilled());
            assertTrue(o.getTotalPrice() > 0);
            assertEquals(LocalDate.now().plusDays(3), o.getEstimatedShippingAt());
        });
        List<String> dropshipOrderIds = List.of(
                DemoStoreSeeder.demoId("store-1", DemoStoreSeeder.DROPSHIP_ACME_ORDER_KEY),
                DemoStoreSeeder.demoId("store-1", DemoStoreSeeder.DROPSHIP_ACME_B_ORDER_KEY));
        demoOrders.orders().forEach(o -> assertEquals(
                dropshipOrderIds.contains(o.getOrderId()) ? FulfilmentType.DirectToConsumer : FulfilmentType.WarehouseFulfilment,
                o.getFulfilmentType(), o.getOrderId()));
    }

    @Test
    void buildsDropshipOrdersForGlobalAcmeAndOwnAcmeB() {
        // given
        List<CatalogSeedRow> rows = CatalogSeed.load();

        // when
        DemoOrders demoOrders = DemoStoreSeeder.buildDemoOrders("store-1", rows);

        // then
        Order acmeOrder = orderById(demoOrders, DemoStoreSeeder.demoId("store-1", DemoStoreSeeder.DROPSHIP_ACME_ORDER_KEY));
        Order acmeBOrder = orderById(demoOrders, DemoStoreSeeder.demoId("store-1", DemoStoreSeeder.DROPSHIP_ACME_B_ORDER_KEY));
        for (Order order : List.of(acmeOrder, acmeBOrder)) {
            assertEquals(FulfilmentType.DirectToConsumer, order.getFulfilmentType());
            assertEquals(OrderStatus.New, order.getStatus());
            assertTrue(order.hasShippingDetails(), "dropship needs a consignee address");
            assertNull(order.getExternalOrderId());
            List<OrderItem> items = demoOrders.itemsByOrderId().get(order.getOrderId());
            assertEquals(1, items.size());
            assertTrue(items.getFirst().isInAllocation());
        }
        assertEquals("Acme", demoOrders.itemsByOrderId().get(acmeOrder.getOrderId()).getFirst().getDeliveryId());
        assertEquals("AcmeB", demoOrders.itemsByOrderId().get(acmeBOrder.getOrderId()).getFirst().getDeliveryId());
        assertEquals("Lis", acmeOrder.getBillingDetails().getSurname());
        assertEquals("Krol", acmeBOrder.getBillingDetails().getSurname());
    }

    @Test
    void buildsOrdersWithVariedSourcesIncludingTwoMarketplaceOrders() {
        // given
        List<CatalogSeedRow> rows = CatalogSeed.load();

        // when
        DemoOrders demoOrders = DemoStoreSeeder.buildDemoOrders("store-1", rows);

        // then
        Order first = orderById(demoOrders, DemoStoreSeeder.demoId("store-1", DemoStoreSeeder.POS_ORDER_KEY));
        assertEquals("Demo", first.getSource().getName());
        assertEquals(OrderSourceType.PointOfSale, first.getSource().getType());
        assertNull(first.getExternalOrderId());

        Order second = orderById(demoOrders, DemoStoreSeeder.demoId("store-1", DemoStoreSeeder.MARKETPLACE_ORDER_KEY));
        assertEquals("Allegro", second.getSource().getName());
        assertEquals(OrderSourceType.Marketplace, second.getSource().getType());
        assertEquals(DemoStoreSeeder.demoExternalOrderNo("store-1", DemoStoreSeeder.MARKETPLACE_EXTERNAL_KEY), second.getExternalOrderId());
        assertTrue(second.getExternalOrderId().matches("\\d{10}"),
                second.getExternalOrderId() + " should be a digits-only external order number");

        Order third = orderById(demoOrders, DemoStoreSeeder.demoId("store-1", DemoStoreSeeder.MARKETPLACE_ORDER_2_KEY));
        assertEquals("Allegro", third.getSource().getName());
        assertEquals(OrderSourceType.Marketplace, third.getSource().getType());
        assertEquals(DemoStoreSeeder.demoExternalOrderNo("store-1", DemoStoreSeeder.MARKETPLACE_EXTERNAL_2_KEY), third.getExternalOrderId());
        assertTrue(third.getExternalOrderId().matches("\\d{10}"),
                third.getExternalOrderId() + " should be a digits-only external order number");

        Order fourth = orderById(demoOrders, DemoStoreSeeder.demoId("store-1", DemoStoreSeeder.WEBSTORE_ORDER_KEY));
        assertEquals("Sklep internetowy", fourth.getSource().getName());
        assertEquals(OrderSourceType.WebStore, fourth.getSource().getType());
        assertNull(fourth.getExternalOrderId());
    }

    @Test
    void differentStoresGetDifferentOrderIdsSoOrderItemsPartitionsNeverCollide() {
        // given
        List<CatalogSeedRow> rows = CatalogSeed.load();

        // when
        DemoOrders storeA = DemoStoreSeeder.buildDemoOrders("store-a", rows);
        DemoOrders storeB = DemoStoreSeeder.buildDemoOrders("store-b", rows);

        // then
        List<String> storeAOrderIds = storeA.orders().stream().map(Order::getOrderId).toList();
        storeB.orders().forEach(o -> assertFalse(storeAOrderIds.contains(o.getOrderId())));
    }

    @Test
    void buildsTwoCompletedOrdersWithDeliveredItemsAndFullPayments() {
        // given
        List<CatalogSeedRow> rows = CatalogSeed.load();

        // when
        CompletedDemoOrders completed = DemoStoreSeeder.buildCompletedDemoOrders("store-1", "a@b.pl", rows);

        // then
        assertEquals(2, completed.orders().size());
        completed.orders().forEach(order -> {
            assertEquals(OrderStatus.Completed, order.getStatus());
            assertTrue(order.isFullyPaid(), order.getOrderId() + " should be fully paid");
            assertTrue(order.getDocumentByType(DocumentType.GoodsIssue).isPresent(),
                    order.getOrderId() + " should carry a WZ document reference");
            assertTrue(order.getDocumentByType(DocumentType.Receipt).isPresent(),
                    order.getOrderId() + " should carry a receipt document");
            order.getDocumentByType(DocumentType.Receipt)
                    .ifPresent(receipt -> assertNotNull(receipt.getNumber()));
            assertEquals(1, order.getShipments().size());
            Shipment shipment = order.getShipments().getFirst();
            assertTrue(shipment.hasShippingData(),
                    order.getOrderId() + " shipment should carry carrier, tracking number and shipping date");
            assertEquals("DHL", shipment.getCarrier());
            assertNotNull(shipment.getDeliveredAt());
            assertTrue(shipment.getTrackingNo().matches("\\d{10}"));
            List<OrderItem> items = completed.itemsByOrderId().get(order.getOrderId());
            assertFalse(items.isEmpty());
            items.forEach(item -> {
                assertEquals(FulfilmentStatus.Delivered, item.getStatus());
                assertNotNull(item.getDeliveryId());
                assertTrue(item.getCost() > 0);
            });
        });
    }

    @Test
    void completedOrdersDeliveriesAreReceivedAndFullyPaid() {
        // given
        List<CatalogSeedRow> rows = CatalogSeed.load();

        // when
        CompletedDemoOrders completed = DemoStoreSeeder.buildCompletedDemoOrders("store-1", "a@b.pl", rows);

        // then
        assertEquals(2, completed.deliveries().size());
        completed.deliveries().forEach(delivery -> {
            assertTrue(delivery.hasBeenReceived(), delivery.getDeliveryId() + " should be received");
            assertTrue(delivery.hasEvent("DELIVERY_RECEIVED"));
            assertTrue(delivery.isFullyPaid(), delivery.getDeliveryId() + " should be fully paid");
            assertTrue(delivery.isPaid());
            assertTrue(delivery.hasDocumentOfType(DocumentType.GoodsReceipt),
                    delivery.getDeliveryId() + " should carry a PZ document reference");
        });
    }

    @Test
    void completedOrdersCarryPzAndWzWarehouseDocumentsWithSequences() {
        // given
        List<CatalogSeedRow> rows = CatalogSeed.load();

        // when
        CompletedDemoOrders completed = DemoStoreSeeder.buildCompletedDemoOrders("store-1", "a@b.pl", rows);

        // then
        List<WarehouseDocument> receipts = completed.documents().stream()
                .filter(d -> d.getType() == DocumentType.GoodsReceipt).toList();
        List<WarehouseDocument> issues = completed.documents().stream()
                .filter(d -> d.getType() == DocumentType.GoodsIssue).toList();
        assertEquals(2, receipts.size());
        assertEquals(2, issues.size());

        receipts.forEach(document -> {
            assertEquals(DocumentReason.SupplierDelivery, document.getReason());
            assertNotNull(document.getDeliveryId());
            assertNull(document.getOrderId());
            assertNotNull(document.getCounterparty().getTaxId());
        });
        issues.forEach(document -> {
            assertEquals(DocumentReason.CustomerOrder, document.getReason());
            assertNotNull(document.getOrderId());
            assertNotNull(document.getDeliveryAddress());
        });
        completed.documents().forEach(document -> {
            assertEquals("MAG-01", document.getWarehouseId());
            assertEquals("Demo Store sp. z o.o.", document.getIssuer().getCompanyName());
            assertTrue(document.getDocumentNo().matches("(PZ|WZ)/MAG-01/\\d{4}/00000[12]"),
                    document.getDocumentNo() + " should follow the sequence format");
        });

        completed.sequences().forEach(sequence -> assertEquals(2, sequence.getCurrentValue()));
        List<String> documentIds = completed.documents().stream().map(WarehouseDocument::getDocumentId).toList();
        completed.documentItems().forEach(item -> {
            assertTrue(documentIds.contains(item.getDocumentId()));
            assertTrue(item.getUnitPrice() > 0);
        });
    }

    @Test
    void orderedProductsAreNotSeededAsWarehouseStockSoFulfilmentBuysFromSupplier() {
        // given
        List<CatalogSeedRow> rows = CatalogSeed.load();

        // when
        DemoOrders open = DemoStoreSeeder.buildDemoOrders("store-1", rows);
        CompletedDemoOrders completed = DemoStoreSeeder.buildCompletedDemoOrders("store-1", "a@b.pl", rows);

        // then
        List<OrderItem> orderedItems = new ArrayList<>();
        open.itemsByOrderId().values().forEach(orderedItems::addAll);
        completed.itemsByOrderId().values().forEach(orderedItems::addAll);
        assertFalse(orderedItems.isEmpty());
        for (OrderItem item : orderedItems) {
            CatalogSeedRow row = rows.stream()
                    .filter(r -> r.mfn().equals(item.getManufacturerCode()))
                    .findFirst().orElseThrow();
            assertFalse(row.inWarehouse(),
                    row.mfn() + " is ordered so it must not sit in the seeded warehouse stock");
        }
    }

    @Test
    void everyWarehouseItemIsLinkedToASeededDelivery() {
        // given
        List<CatalogSeedRow> rows = CatalogSeed.load();

        // when
        WarehouseStock stock = DemoStoreSeeder.buildWarehouseStock("store-1", "a@b.pl", rows);

        // then
        assertEquals(rows.stream().filter(CatalogSeedRow::inWarehouse).count(), stock.items().size());
        List<String> deliveryIds = stock.deliveries().stream().map(Delivery::getDeliveryId).toList();
        assertTrue(deliveryIds.size() >= 4);
        stock.items().forEach(item -> {
            assertDoesNotThrow(() -> UUID.fromString(item.getItemId()),
                    item.getItemId() + " should be a UUID");
            assertTrue(deliveryIds.contains(item.getDeliveryId()),
                    item.getItemId() + " should be linked to a seeded delivery");
        });
        stock.deliveries().forEach(delivery -> assertTrue(
                stock.items().stream().anyMatch(item -> delivery.getDeliveryId().equals(item.getDeliveryId())),
                delivery.getExternalDeliveryId() + " should carry at least one warehouse item"));
    }

    @Test
    void receivedWarehouseDeliveriesArePaidAndCarryPzDocumentWithLinkedInvoice() {
        // given
        List<CatalogSeedRow> rows = CatalogSeed.load();

        // when
        WarehouseStock stock = DemoStoreSeeder.buildWarehouseStock("store-1", "a@b.pl", rows);

        // then
        List<Delivery> received = stock.deliveries().stream().filter(Delivery::hasBeenReceived).toList();
        assertEquals(3, received.size());
        received.forEach(delivery -> {
            assertTrue(delivery.isFullyPaid(), delivery.getExternalDeliveryId() + " should be fully paid");
            assertTrue(delivery.isPaid());
            assertTrue(delivery.hasDocumentOfType(DocumentType.GoodsReceipt));
            assertTrue(delivery.hasDocumentOfType(DocumentType.InvoiceVat),
                    delivery.getExternalDeliveryId() + " should have a linked invoice");
            assertTrue(delivery.isInvoiced());
            assertTrue(stock.documents().stream()
                            .anyMatch(document -> delivery.getDeliveryId().equals(document.getDeliveryId())),
                    delivery.getExternalDeliveryId() + " should have a PZ warehouse document");
            stock.items().stream()
                    .filter(item -> delivery.getDeliveryId().equals(item.getDeliveryId()))
                    .forEach(item -> assertEquals(FulfilmentStatus.Delivered, item.getStatus()));
        });
        stock.sequences().forEach(sequence -> assertEquals(5, sequence.getCurrentValue()));
    }

    @Test
    void pendingWarehouseDeliveryHasOrderedItemsAndNoWarehouseDocument() {
        // given
        List<CatalogSeedRow> rows = CatalogSeed.load();

        // when
        WarehouseStock stock = DemoStoreSeeder.buildWarehouseStock("store-1", "a@b.pl", rows);

        // then
        List<Delivery> pending = stock.deliveries().stream().filter(d -> !d.hasBeenReceived()).toList();
        assertEquals(1, pending.size());
        Delivery delivery = pending.getFirst();
        assertFalse(delivery.isPaid());
        assertFalse(delivery.hasDocumentOfType(DocumentType.GoodsReceipt));
        assertTrue(stock.documents().stream()
                .noneMatch(document -> delivery.getDeliveryId().equals(document.getDeliveryId())));
        List<WarehouseItem> orderedItems = stock.items().stream()
                .filter(item -> delivery.getDeliveryId().equals(item.getDeliveryId()))
                .toList();
        assertFalse(orderedItems.isEmpty());
        orderedItems.forEach(item -> assertEquals(FulfilmentStatus.Ordered, item.getStatus()));
    }

    @Test
    void everyOpenOrderHasConfirmationEventAndAssemblyOrderHasAssemblyEvent() {
        // given
        List<CatalogSeedRow> rows = CatalogSeed.load();

        // when
        DemoOrders demoOrders = DemoStoreSeeder.buildDemoOrders("store-1", rows);

        // then
        demoOrders.orders().forEach(order -> assertTrue(demoOrders.events().stream()
                        .anyMatch(event -> event.getOrderId().equals(order.getOrderId())
                                && event.getType() == EventType.email
                                && "ORDER_CONFIRMATION".equals(event.getName())),
                order.getOrderId() + " should have an ORDER_CONFIRMATION event"));
        Order third = orderById(demoOrders, DemoStoreSeeder.demoId("store-1", DemoStoreSeeder.MARKETPLACE_ORDER_2_KEY));
        assertTrue(demoOrders.events().stream()
                .anyMatch(event -> event.getOrderId().equals(third.getOrderId())
                        && "ORDER_ASSEMBLY".equals(event.getName())));
        demoOrders.events().forEach(event -> assertNotNull(event.getCreatedAt()));
    }

    @Test
    void completedOrdersCarryFullLifecycleEventTimeline() {
        // given
        List<CatalogSeedRow> rows = CatalogSeed.load();

        // when
        CompletedDemoOrders completed = DemoStoreSeeder.buildCompletedDemoOrders("store-1", "a@b.pl", rows);

        // then
        completed.orders().forEach(order -> {
            List<OrderEvent> orderEvents = completed.events().stream()
                    .filter(event -> event.getOrderId().equals(order.getOrderId()))
                    .sorted(Comparator.comparing(OrderEvent::getCreatedAt))
                    .toList();
            assertEquals(List.of("ORDER_CONFIRMATION", "ORDER_ASSEMBLY", "ORDER_ASSEMBLED",
                            "ORDER_SHIPPING", "SHIPMENT_COLLECTED", "SHIPMENT_DELIVERED"),
                    orderEvents.stream().map(OrderEvent::getName).toList());
            OrderEvent delivered = orderEvents.getLast();
            assertEquals(EventType.action, delivered.getType());
            assertEquals(order.getShipments().getFirst().getDeliveredAt(), delivered.getCreatedAt());
            orderEvents.forEach(event -> assertDoesNotThrow(() -> UUID.fromString(event.getEventId())));
        });
    }

    @Test
    void completedOrdersAreDatedInThePreviousMonthSoTheyShowUpInReports() {
        // given
        List<CatalogSeedRow> rows = CatalogSeed.load();

        // when
        CompletedDemoOrders completed = DemoStoreSeeder.buildCompletedDemoOrders("store-1", "a@b.pl", rows);

        // then
        YearMonth previousMonth = YearMonth.now().minusMonths(1);
        completed.orders().forEach(order -> {
            assertEquals(previousMonth, YearMonth.from(order.getOrderedAt()));
            assertEquals(previousMonth, YearMonth.from(order.getShipments().getFirst().getDeliveredAt()));
        });
        completed.deliveries().forEach(delivery ->
                assertEquals(previousMonth, YearMonth.from(delivery.getReceivedAt())));
        completed.documents().forEach(document ->
                assertEquals(previousMonth, YearMonth.from(document.getCreatedAt())));
    }

    @Test
    void seedsApprovedRmaForACompletedOrderStillWaitingForClientItems() {
        // given
        List<CatalogSeedRow> rows = CatalogSeed.load();

        // when
        CompletedDemoOrders completed = DemoStoreSeeder.buildCompletedDemoOrders("store-1", "a@b.pl", rows);

        // then
        RMA rma = completed.rma();
        assertEquals(RMAStatus.Approved, rma.getStatus());
        Order rmaOrder = completed.orders().stream()
                .filter(order -> order.getOrderId().equals(rma.getOrderId()))
                .findFirst().orElseThrow();
        assertEquals(OrderStatus.Completed, rmaOrder.getStatus());
        assertEquals(rmaOrder.getBillingDetails().getEmail(), rma.getEmail());
        assertTrue(rma.getShipments().isEmpty(), "items should still be with the client - no return shipment yet");
        assertNotNull(rma.getShippingDetails());
        assertDoesNotThrow(() -> UUID.fromString(rma.getRmaId()));

        assertEquals(1, completed.rmaItems().size());
        RMAItem rmaItem = completed.rmaItems().getFirst();
        assertEquals(rma.getRmaId(), rmaItem.getRmaId());
        assertEquals(RMAItemStatus.New, rmaItem.getStatus());
        List<OrderItem> orderItems = completed.itemsByOrderId().get(rmaOrder.getOrderId());
        assertTrue(orderItems.stream().anyMatch(item -> item.getItemId().equals(rmaItem.getItemId())));
        assertTrue(rmaItem.isComplete());
    }

    @Test
    void demoOrderIdsLookLikeStoreGeneratedUuids() {
        // given
        List<CatalogSeedRow> rows = CatalogSeed.load();

        // when
        DemoOrders demoOrders = DemoStoreSeeder.buildDemoOrders("store-1", rows);

        // then
        demoOrders.orders().forEach(o -> assertDoesNotThrow(() -> UUID.fromString(o.getOrderId()),
                o.getOrderId() + " should be a UUID"));
    }

    @Test
    void everySeededDeliveryTotalCostMatchesItsItemsPlusShippingAndPaymentCosts() {
        // given
        List<CatalogSeedRow> rows = CatalogSeed.load();

        // when
        DemoOrders open = DemoStoreSeeder.buildDemoOrders("store-1", rows);
        CompletedDemoOrders completed = DemoStoreSeeder.buildCompletedDemoOrders("store-1", "a@b.pl", rows);
        WarehouseStock stock = DemoStoreSeeder.buildWarehouseStock("store-1", "a@b.pl", rows);

        // then
        Map<String, Double> itemsCostByDeliveryId = new HashMap<>();
        List<OrderItem> orderItems = new ArrayList<>();
        open.itemsByOrderId().values().forEach(orderItems::addAll);
        completed.itemsByOrderId().values().forEach(orderItems::addAll);
        orderItems.stream()
                .filter(item -> item.getDeliveryId() != null)
                .forEach(item -> itemsCostByDeliveryId.merge(
                        item.getDeliveryId(), item.getCost() * item.getQty(), Double::sum));
        stock.items().forEach(item -> itemsCostByDeliveryId.merge(
                item.getDeliveryId(), item.getCost() * item.getQty(), Double::sum));

        List<Delivery> deliveries = new ArrayList<>();
        deliveries.add(open.delivery());
        deliveries.addAll(completed.deliveries());
        deliveries.addAll(stock.deliveries());
        deliveries.forEach(delivery -> assertEquals(
                itemsCostByDeliveryId.getOrDefault(delivery.getDeliveryId(), 0.0)
                        + delivery.getShippingCost() + delivery.getPaymentCost(),
                delivery.getTotalCost(), 0.01,
                delivery.getExternalDeliveryId() + " total cost should equal its items plus shipping and payment costs"));
    }

    @Test
    void allSeededDeliveriesHaveUuidIdsAndRealisticSupplierRefs() {
        // given
        List<CatalogSeedRow> rows = CatalogSeed.load();

        // when
        DemoOrders open = DemoStoreSeeder.buildDemoOrders("store-1", rows);
        CompletedDemoOrders completed = DemoStoreSeeder.buildCompletedDemoOrders("store-1", "a@b.pl", rows);
        WarehouseStock stock = DemoStoreSeeder.buildWarehouseStock("store-1", "a@b.pl", rows);

        // then
        List<Delivery> deliveries = new ArrayList<>();
        deliveries.add(open.delivery());
        deliveries.addAll(completed.deliveries());
        deliveries.addAll(stock.deliveries());
        deliveries.forEach(delivery -> {
            assertDoesNotThrow(() -> UUID.fromString(delivery.getDeliveryId()),
                    delivery.getDeliveryId() + " should be a UUID");
            assertFalse(delivery.getExternalDeliveryId().toLowerCase().contains("demo"),
                    delivery.getExternalDeliveryId() + " should look like a real supplier order ref");
        });
    }

    @Test
    void seedsPartialFullAndMissingPaymentsAcrossOrders() {
        // given
        List<CatalogSeedRow> rows = CatalogSeed.load();

        // when
        DemoOrders demoOrders = DemoStoreSeeder.buildDemoOrders("store-1", rows);

        // then
        Order first = orderById(demoOrders, DemoStoreSeeder.demoId("store-1", DemoStoreSeeder.POS_ORDER_KEY));
        assertEquals("DEMO-PAY-001", first.getLatestPayment().getReferenceNo());
        assertTrue(first.getUnpaidAmount() > 0);
        assertFalse(first.isFullyPaid());

        Order second = orderById(demoOrders, DemoStoreSeeder.demoId("store-1", DemoStoreSeeder.MARKETPLACE_ORDER_KEY));
        assertEquals("DEMO-PAY-002", second.getLatestPayment().getReferenceNo());
        assertTrue(second.isFullyPaid());

        Order third = orderById(demoOrders, DemoStoreSeeder.demoId("store-1", DemoStoreSeeder.MARKETPLACE_ORDER_2_KEY));
        assertTrue(third.getPayments().isEmpty());
        assertNull(third.getLatestPayment());
    }

    private static Order orderById(DemoOrders demoOrders, String orderId) {
        return demoOrders.orders().stream()
                .filter(o -> orderId.equals(o.getOrderId()))
                .findFirst().orElseThrow();
    }

    @Test
    void buildsPersistedDeliveryWithOrderedItems() {
        // given
        List<CatalogSeedRow> rows = CatalogSeed.load();

        // when
        DemoOrders demoOrders = DemoStoreSeeder.buildDemoOrders("store-1", rows);

        // then
        Delivery delivery = demoOrders.delivery();
        assertNotNull(delivery.getEstimatedDeliveryAt());
        assertEquals("store-1", delivery.getStoreId());
        List<OrderItem> orderedItems = demoOrders.itemsByOrderId().values().stream()
                .flatMap(List::stream)
                .filter(i -> i.getStatus() == FulfilmentStatus.Ordered)
                .toList();
        assertFalse(orderedItems.isEmpty());
        orderedItems.forEach(i -> assertEquals(delivery.getDeliveryId(), i.getDeliveryId()));
        double orderedItemsCost = orderedItems.stream().mapToDouble(i -> i.getCost() * i.getQty()).sum();
        assertEquals(orderedItemsCost + delivery.getShippingCost() + delivery.getPaymentCost(),
                delivery.getTotalCost(), 0.01);
    }

    @Test
    void buildsDeterministicIdsForIdempotentReseeding() {
        // given
        List<CatalogSeedRow> rows = CatalogSeed.load();

        // when
        DemoOrders firstRun = DemoStoreSeeder.buildDemoOrders("store-1", rows);
        DemoOrders secondRun = DemoStoreSeeder.buildDemoOrders("store-1", rows);

        // then
        List<String> orderIds = firstRun.orders().stream().map(Order::getOrderId).toList();
        assertEquals(List.of(DemoStoreSeeder.demoId("store-1", DemoStoreSeeder.POS_ORDER_KEY), DemoStoreSeeder.demoId("store-1", DemoStoreSeeder.MARKETPLACE_ORDER_KEY), DemoStoreSeeder.demoId("store-1", DemoStoreSeeder.MARKETPLACE_ORDER_2_KEY), DemoStoreSeeder.demoId("store-1", DemoStoreSeeder.WEBSTORE_ORDER_KEY), DemoStoreSeeder.demoId("store-1", DemoStoreSeeder.DROPSHIP_ACME_ORDER_KEY), DemoStoreSeeder.demoId("store-1", DemoStoreSeeder.DROPSHIP_ACME_B_ORDER_KEY)), orderIds);
        assertEquals(orderIds, secondRun.orders().stream().map(Order::getOrderId).toList());
        assertEquals(DemoStoreSeeder.demoId("store-1", "demo-delivery-open"), firstRun.delivery().getDeliveryId());
        assertEquals(firstRun.delivery().getDeliveryId(), secondRun.delivery().getDeliveryId());
        firstRun.itemsByOrderId().forEach((orderId, items) ->
                assertEquals(items.stream().map(OrderItem::getItemId).toList(),
                        secondRun.itemsByOrderId().get(orderId).stream().map(OrderItem::getItemId).toList()));
    }

    @Test
    void seedsOneOrderPerSimProductPerSupplierWhenAcmeIsRegistered() {
        // given
        List<CatalogSeedRow> allRows = CatalogSeed.load();
        List<CatalogSeedRow> rows = DemoStoreSeeder.filterSimulationRows(allRows, true);

        // when
        SimOrders simOrders = DemoStoreSeeder.buildSimOrders("store-1", rows);

        // then
        assertEquals(10, simOrders.orders().size());
        simOrders.orders().forEach(order -> {
            List<OrderItem> items = simOrders.itemsByOrderId().get(order.getOrderId());
            assertEquals(1, items.size());
            OrderItem item = items.getFirst();
            assertTrue(item.getManufacturerCode().startsWith("SIM-"));
            assertEquals(FulfilmentStatus.Allocation, item.getStatus());
            assertTrue(item.getDeliveryId().equals("Acme") || item.getDeliveryId().equals("AcmeB"));
            assertEquals("Symulacja", order.getBillingDetails().getName());
            CatalogSeedRow row = rows.stream()
                    .filter(r -> r.mfn().equals(item.getManufacturerCode()))
                    .findFirst().orElseThrow();
            assertEquals(row.name().replaceFirst("^Symulacja: ", ""), order.getBillingDetails().getSurname());
        });

        List<Product> products = DemoStoreSeeder.buildProducts(rows, "store-1");
        assertTrue(products.stream().anyMatch(p -> p.getManufacturerCode().startsWith("SIM-")));

        String pricelist = readClasspathResource("/local-init/s3/stores/uma2dqukxr/pricelists/cat-local-01/seed.csv");
        assertTrue(pricelist.contains("SIM-"));
    }

    @Test
    void filtersOutSimRowsAndProductsWhenAcmeIsNotRegistered() {
        // given
        List<CatalogSeedRow> allRows = CatalogSeed.load();
        List<CatalogSeedRow> rows = DemoStoreSeeder.filterSimulationRows(allRows, false);

        // when
        SimOrders simOrders = DemoStoreSeeder.buildSimOrders("store-1", rows);
        List<Product> products = DemoStoreSeeder.buildProducts(rows, "store-1");
        DemoOrders demoOrders = DemoStoreSeeder.buildDemoOrders("store-1", rows);

        // then
        assertTrue(rows.stream().noneMatch(row -> row.mfn().startsWith("SIM-")));
        assertTrue(simOrders.orders().isEmpty());
        assertTrue(simOrders.itemsByOrderId().isEmpty());
        assertTrue(simOrders.events().isEmpty());
        assertTrue(products.stream().noneMatch(p -> p.getManufacturerCode().startsWith("SIM-")));
        assertEquals(6, demoOrders.orders().size());

        String pricelist = DemoStoreSeeder.filterSimulationPricelistRows(
                readClasspathResource("/local-init/s3/stores/uma2dqukxr/pricelists/cat-local-01/seed.csv"));
        assertFalse(pricelist.contains("SIM-"));
        assertTrue(pricelist.startsWith("PimId;EAN;Mfn"));
    }

    @Test
    void completedOrdersAndWarehouseStockNeverSeeSimRowsWhenAcmeIsNotRegistered() {
        // given
        List<CatalogSeedRow> rows = DemoStoreSeeder.filterSimulationRows(CatalogSeed.load(), false);

        // when
        CompletedDemoOrders completed = DemoStoreSeeder.buildCompletedDemoOrders("store-1", "a@b.pl", rows);
        WarehouseStock stock = DemoStoreSeeder.buildWarehouseStock("store-1", "a@b.pl", rows);

        // then
        completed.itemsByOrderId().values().stream()
                .flatMap(List::stream)
                .forEach(item -> assertFalse(item.getManufacturerCode().startsWith("SIM-")));
        stock.items().forEach(item -> assertFalse(item.getManufacturerCode().startsWith("SIM-")));
    }

    @Test
    void slugifiesSimulationCustomerEmailButKeepsExistingEmailsByteIdentical() {
        // given
        String storeId = "store-1";

        // when
        String existingEmail = DemoStoreSeeder.customerEmail(storeId, "Jan", "Kowalski");
        String simEmail = DemoStoreSeeder.customerEmail(storeId, "Symulacja", "timeout, zamówienie u dostawcy istnieje");

        // then
        assertEquals("jan.kowalski14@test.com", existingEmail);
        assertEquals("symulacja.timeout.zamowienie.u.dostawcy.istnieje31@test.com", simEmail);
    }

    private static String readClasspathResource(String resource) {
        try (java.io.InputStream stream = DemoStoreSeeder.class.getResourceAsStream(resource)) {
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
