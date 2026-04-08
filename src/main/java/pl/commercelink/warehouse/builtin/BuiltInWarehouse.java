package pl.commercelink.warehouse.builtin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.taxonomy.TaxonomyResolver;
import pl.commercelink.warehouse.api.*;

@Component
class BuiltInWarehouse implements Warehouse {

    @Autowired
    private OrdersRepository ordersRepository;
    @Autowired
    private OrderItemsRepository orderItemsRepository;
    @Autowired
    private OrderLifecycle orderLifecycle;

    @Autowired
    private WarehouseRepository warehouseRepository;
    @Autowired
    private WarehouseDocumentRepository warehouseDocumentRepository;
    @Autowired
    private WarehouseDocumentItemRepository warehouseDocumentItemRepository;

    @Autowired
    private DeliveriesRepository deliveriesRepository;
    @Autowired
    private WarehouseItemFactory warehouseItemFactory;
    @Autowired
    private TaxonomyResolver taxonomyResolver;

    @Override
    public GoodsInHandler goodsInHandler(String storeId) {
        return new BuiltInGoodsInHandler(
                storeId,
                ordersRepository,
                orderItemsRepository,
                orderLifecycle,
                warehouseRepository,
                getDocumentCreationService(storeId)
        );
    }

    @Override
    public GoodsOutHandler goodsOutHandler(String storeId) {
        return new BuiltInGoodsOutHandler(getDocumentCreationService(storeId));
    }

    @Override
    public RmaGoodsInHandler rmaGoodsInHandler(String storeId) {
        return new BuiltInRmaGoodsInHandler(
                storeId,
                warehouseRepository,
                getDocumentCreationService(storeId),
                warehouseItemFactory
        );
    }

    @Override
    public DocumentQueryService documentQueryService(String storeId) {
        return new BuiltInDocumentQueryService();
    }

    @Override
    public StockQueryService stockQueryService(String storeId) {
        return new BuiltInStockQueryService(warehouseRepository);
    }

    @Override
    public ReservationService reservationService(String storeId) {
        return new BuiltInReservationService(deliveriesRepository, warehouseRepository, warehouseItemFactory);
    }

    @Override
    public InvoiceSyncHandler invoiceSyncHandler(String storeId) {
        return new BuiltInInvoiceSyncHandler(
                storeId,
                warehouseRepository,
                warehouseDocumentRepository,
                warehouseDocumentItemRepository
        );
    }

    private BuiltInDocumentCreationService getDocumentCreationService(String storeId) {
        return new BuiltInDocumentCreationService(warehouseDocumentRepository, warehouseDocumentItemRepository, taxonomyResolver);
    }
}
