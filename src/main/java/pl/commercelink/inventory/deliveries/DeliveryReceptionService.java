package pl.commercelink.inventory.deliveries;

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

    public OperationResult<Document> receive(
            String storeId, String provider, String deliveryId,
            List<Allocation> orderAllocations,
            List<Allocation> warehouseAllocations,
            List<Allocation> remainingAllocations
    ) {
        Store store = storesRepository.findById(storeId);

        WarehouseConfiguration warehouseConfiguration = store.getWarehouseConfiguration();
        if (warehouseConfiguration == null || !warehouseConfiguration.isComplete()) {
            return OperationResult.failure("Warehouse configuration is missing for store: " + storeId);
        }

        InvoicingProvider invoicingProvider = invoicingProviderFactory.get(store);
        BillingParty issuer = invoicingProvider.fetchCostCenterById(warehouseConfiguration.getCostCenterId());
        if (issuer == null || !issuer.hasCompanyDetails()) {
            return OperationResult.failure("Failed to fetch cost center with id: " + warehouseConfiguration.getCostCenterId());
        }

        BillingParty counterparty = invoicingProvider.fetchBillingPartyByShortcut(provider);
        if (counterparty == null || !counterparty.hasCompanyDetails()) {
            return OperationResult.failure("Failed to fetch counterparty with shortcut: " + provider);
        }

        String createdBy = CustomSecurityContext.getLoggedInUserName();

        GoodsInRequest request = GoodsInRequest.builder()
                .issuer(issuer)
                .counterparty(counterparty)
                .warehouseId(warehouseConfiguration.getWarehouseId())
                .deliveryId(deliveryId)
                .orderAllocations(orderAllocations)
                .warehouseAllocations(warehouseAllocations)
                .createdBy(createdBy)
                .build();

        OperationResult<Document> result = warehouse.goodsInHandler(storeId).receive(
                request, warehouseConfiguration.isDocumentsGenerationEnabled());
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
