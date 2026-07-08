package pl.commercelink.demo;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import org.springframework.stereotype.Repository;
import pl.commercelink.baskets.Basket;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.stores.Store;
import pl.commercelink.templates.EmailTemplate;
import pl.commercelink.warehouse.builtin.WarehouseDocument;
import pl.commercelink.warehouse.builtin.WarehouseDocumentItem;
import pl.commercelink.warehouse.builtin.WarehouseDocumentSequence;
import pl.commercelink.warehouse.builtin.WarehouseItem;

import java.util.ArrayList;
import java.util.List;

@Repository
public class DemoStoreWipeRepository {

    private final DynamoDBMapper mapper;

    public DemoStoreWipeRepository(AmazonDynamoDB dynamoDB) {
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

    public void deleteAll(List<?> entities) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        mapper.batchDelete(entities);
    }

    public void deleteStore(Store store) {
        mapper.delete(store);
    }

    private <T> List<T> query(Class<T> clazz, T hashKey) {
        return new ArrayList<>(mapper.query(clazz, new DynamoDBQueryExpression<T>().withHashKeyValues(hashKey)));
    }
}
