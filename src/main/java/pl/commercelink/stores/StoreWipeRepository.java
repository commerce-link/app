package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import org.springframework.stereotype.Repository;
import pl.commercelink.baskets.Basket;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.notifications.StoreNotificationRecord;
import pl.commercelink.orders.filters.model.OwnedOrderFilters;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.receipts.ReceiptAttempt;
import pl.commercelink.scheduling.DailyScheduleExecutionCount;
import pl.commercelink.shipping.ShipmentTracking;
import pl.commercelink.templates.EmailTemplate;
import pl.commercelink.warehouse.builtin.WarehouseDocument;
import pl.commercelink.warehouse.builtin.WarehouseDocumentItem;
import pl.commercelink.warehouse.builtin.WarehouseDocumentSequence;
import pl.commercelink.warehouse.builtin.WarehouseItem;

import java.util.ArrayList;
import java.util.List;

@Repository
public class StoreWipeRepository {

    private final DynamoDBMapper mapper;

    public StoreWipeRepository(AmazonDynamoDB dynamoDB) {
        this.mapper = new DynamoDBMapper(dynamoDB);
    }

    public List<WarehouseItem> findWarehouseItems(String storeId) {
        WarehouseItem key = new WarehouseItem();
        key.setStoreId(storeId);
        return query(WarehouseItem.class, key);
    }

    public List<WarehouseDocument> findWarehouseDocuments(String storeId) {
        WarehouseDocument key = new WarehouseDocument();
        key.setStoreId(storeId);
        return query(WarehouseDocument.class, key);
    }

    public List<WarehouseDocumentItem> findWarehouseDocumentItems(String documentId) {
        WarehouseDocumentItem key = new WarehouseDocumentItem();
        key.setDocumentId(documentId);
        return query(WarehouseDocumentItem.class, key);
    }

    public List<WarehouseDocumentSequence> findWarehouseDocumentSequences(String storeId) {
        WarehouseDocumentSequence key = new WarehouseDocumentSequence();
        key.setStoreId(storeId);
        return query(WarehouseDocumentSequence.class, key);
    }

    public List<RMA> findRmas(String storeId) {
        RMA key = new RMA();
        key.setStoreId(storeId);
        return query(RMA.class, key);
    }

    public List<Basket> findBaskets(String storeId) {
        Basket key = new Basket();
        key.setStoreId(storeId);
        return query(Basket.class, key);
    }

    public List<Delivery> findDeliveries(String storeId) {
        Delivery key = new Delivery();
        key.setStoreId(storeId);
        return query(Delivery.class, key);
    }

    public List<EmailTemplate> findEmailTemplates(String storeId) {
        EmailTemplate key = new EmailTemplate();
        key.setStoreId(storeId);
        return query(EmailTemplate.class, key);
    }

    public List<ReceiptAttempt> findReceiptAttempts(String storeId) {
        ReceiptAttempt key = new ReceiptAttempt();
        key.setStoreId(storeId);
        return query(ReceiptAttempt.class, key);
    }

    public List<StoreNotificationRecord> findStoreNotifications(String storeId) {
        StoreNotificationRecord key = new StoreNotificationRecord();
        key.setStoreId(storeId);
        return query(StoreNotificationRecord.class, key);
    }

    public List<OwnedOrderFilters> findOrderFilters(String storeId) {
        OwnedOrderFilters key = new OwnedOrderFilters();
        key.setStoreId(storeId);
        return query(OwnedOrderFilters.class, key);
    }

    public List<ShipmentTracking> findShipmentTrackings(String storeId) {
        ShipmentTracking key = new ShipmentTracking();
        key.setStoreId(storeId);
        return query(ShipmentTracking.class, key);
    }

    public List<DailyScheduleExecutionCount> findScheduleExecutionCounts(String storeId) {
        DailyScheduleExecutionCount key = new DailyScheduleExecutionCount();
        key.setStoreId(storeId);
        return query(DailyScheduleExecutionCount.class, key);
    }

    public void deleteAll(List<?> entities) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        mapper.batchDelete(entities);
    }

    private <T> List<T> query(Class<T> clazz, T hashKey) {
        return new ArrayList<>(mapper.query(clazz, new DynamoDBQueryExpression<T>().withHashKeyValues(hashKey)));
    }
}
