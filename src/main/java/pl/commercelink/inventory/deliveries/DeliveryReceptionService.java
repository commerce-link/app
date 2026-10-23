package pl.commercelink.inventory.deliveries;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.commercelink.invoicing.api.BillingParty;
import pl.commercelink.invoicing.api.InvoicingProvider;
import pl.commercelink.invoicing.InvoicingProviderFactory;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.WarehouseConfiguration;
import pl.commercelink.warehouse.api.GoodsInRequest;
import pl.commercelink.documents.Document;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.warehouse.api.Warehouse;

import java.util.List;

@Slf4j
@Service
public class DeliveryReceptionService {

    @Autowired
    private InvoicingProviderFactory invoicingProviderFactory;

    @Autowired
    private StoresRepository storesRepository;
    @Autowired
    private Warehouse warehouse;
    @Autowired
    private DeliveriesRepository deliveriesRepository;
    @Autowired
    private CounterpartyShortcuts counterpartyShortcuts;

    public OperationResult<Document> receive(
            String storeId, String provider, String deliveryId,
            List<Allocation> orderAllocations,
            List<Allocation> warehouseAllocations,
            List<Allocation> remainingAllocations
    ) {
        Store store = storesRepository.findById(storeId);
        WarehouseConfiguration warehouseConfiguration = store.getWarehouseConfiguration();
        boolean documentsGenerationEnabled = warehouseConfiguration != null && warehouseConfiguration.isDocumentsGenerationEnabled();

        GoodsInRequest.Builder builder = GoodsInRequest.builder()
                .deliveryId(deliveryId)
                .orderAllocations(orderAllocations)
                .warehouseAllocations(warehouseAllocations)
                .createdBy(CustomSecurityContext.getLoggedInUserName());

        if (documentsGenerationEnabled) {
            if (!warehouseConfiguration.isComplete()) {
                // the operator sees a translated refusal; support needs the ids the old message carried
                log.warn("Delivery {} not received: warehouse configuration of store {} is incomplete (warehouse {}, cost centre {})",
                        deliveryId, storeId, warehouseConfiguration.getWarehouseId(), warehouseConfiguration.getCostCenterId());
                return OperationResult.failure("deliveries.receive.error.warehouseConfig");
            }

            InvoicingProvider invoicingProvider = invoicingProviderFactory.get(store);
            if (invoicingProvider == null) {
                log.warn("Delivery {} not received: store {} has no invoicing provider configured", deliveryId, storeId);
                return OperationResult.failure("deliveries.receive.error.invoicing");
            }

            BillingParty issuer = invoicingProvider.fetchCostCenterById(warehouseConfiguration.getCostCenterId());
            if (issuer == null || !issuer.hasCompanyDetails()) {
                log.warn("Delivery {} not received: cost centre {} of store {} not found or without company details",
                        deliveryId, warehouseConfiguration.getCostCenterId(), storeId);
                return OperationResult.failure("deliveries.receive.error.costCenter");
            }

            Delivery delivery = deliveriesRepository.findById(storeId, deliveryId);
            String shortcut = delivery != null ? counterpartyShortcuts.forDelivery(store, delivery) : provider;
            BillingParty counterparty = invoicingProvider.fetchBillingPartyByShortcut(shortcut);
            if (counterparty == null || !counterparty.hasCompanyDetails()) {
                log.warn("Delivery {} not received: counterparty with shortcut {} of store {} not found or without company details",
                        deliveryId, shortcut, storeId);
                return OperationResult.failure("deliveries.receive.error.counterparty");
            }

            builder.issuer(issuer)
                    .counterparty(counterparty)
                    .warehouseId(warehouseConfiguration.getWarehouseId());
        }

        OperationResult<Document> result = warehouse.goodsInHandler(storeId).receive(
                builder.build(), documentsGenerationEnabled);
        if (!result.isSuccess()) {
            return result;
        }

        var delivery = deliveriesRepository.findById(storeId, deliveryId);

        if (result.hasPayload()) {
            delivery.addDocument(result.getPayload());
        }

        if (remainingAllocations.stream().noneMatch(Allocation::isInAllocation)) {
            delivery.markAsReceived();
        }

        deliveriesRepository.save(delivery);

        return result;
    }
}
