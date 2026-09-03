package pl.commercelink.orders.rma;

import org.springframework.stereotype.Service;
import pl.commercelink.invoicing.InvoicingProviderFactory;
import pl.commercelink.invoicing.api.BillingParty;
import pl.commercelink.invoicing.api.InvoicingProvider;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.WarehouseConfiguration;
import pl.commercelink.warehouse.api.GoodsReceiptItem;
import pl.commercelink.warehouse.api.ItemCondition;
import pl.commercelink.documents.Document;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.warehouse.api.RmaGoodsInRequest;
import pl.commercelink.warehouse.api.Warehouse;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RmaGoodsInService {

    private final StoresRepository storesRepository;
    private final InvoicingProviderFactory invoicingProviderFactory;
    private final Warehouse warehouse;

    public RmaGoodsInService(
            StoresRepository storesRepository,
            InvoicingProviderFactory invoicingProviderFactory,
            Warehouse warehouse
    ) {
        this.storesRepository = storesRepository;
        this.invoicingProviderFactory = invoicingProviderFactory;
        this.warehouse = warehouse;
    }

    public OperationResult<Document> receive(
            String storeId,
            RMA rma,
            List<RMAItem> rmaItems,
            BillingDetails customerBillingDetails,
            boolean itemsRequireRepair,
            ItemCondition condition
    ) {
        Store store = storesRepository.findById(storeId);
        WarehouseConfiguration config = store.getWarehouseConfiguration();
        boolean documentsGenerationEnabled = config != null && config.isDocumentsGenerationEnabled();

        RmaGoodsInRequest.Builder builder = RmaGoodsInRequest.builder()
                .storeId(store.getStoreId())
                .rmaId(rma.getRmaId())
                .orderId(rma.getOrderId())
                .items(toGoodsReceiptItems(rmaItems, condition))
                .itemsRequireRepair(itemsRequireRepair)
                .createdBy(CustomSecurityContext.getLoggedInUserName());

        if (documentsGenerationEnabled) {
            if (!config.isComplete()) {
                return OperationResult.failure("Warehouse configuration is missing for store: " + storeId);
            }
            BillingParty issuer = fetchIssuer(store, config);
            if (issuer == null) {
                return OperationResult.failure("Failed to fetch cost center with id: " + config.getCostCenterId());
            }
            builder.warehouseId(config.getWarehouseId())
                    .issuer(issuer)
                    .counterparty(customerBillingDetails.toBillingParty());
        }

        return warehouse.rmaGoodsInHandler(storeId)
                .receive(builder.build(), documentsGenerationEnabled);
    }

    private List<GoodsReceiptItem> toGoodsReceiptItems(List<RMAItem> rmaItems, ItemCondition condition) {
        return rmaItems
                .stream()
                .map(rmaItem -> GoodsReceiptItem.from(rmaItem, condition))
                .collect(Collectors.toList());
    }

    private BillingParty fetchIssuer(Store store, WarehouseConfiguration config) {
        InvoicingProvider invoicingProvider = invoicingProviderFactory.get(store);
        if (invoicingProvider == null) {
            return null;
        }
        return invoicingProvider.fetchCostCenterById(config.getCostCenterId());
    }
}
