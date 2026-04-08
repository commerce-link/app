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
            boolean itemsRequireRepair
    ) {
        Store store = storesRepository.findById(storeId);
        WarehouseConfiguration config = store.getWarehouseConfiguration();

        if (config == null || !config.isComplete()) {
            return OperationResult.failure("Warehouse configuration is missing for store: " + storeId);
        }

        RmaGoodsInRequest request = buildFullRequest(
                store, config, rma, rmaItems, customerBillingDetails, itemsRequireRepair
        );

        return warehouse.rmaGoodsInHandler(storeId)
                .receive(request, config.isDocumentsGenerationEnabled());
    }

    private RmaGoodsInRequest buildFullRequest(
            Store store,
            WarehouseConfiguration config,
            RMA rma,
            List<RMAItem> rmaItems,
            BillingDetails customerBillingDetails,
            boolean itemsRequireRepair
    ) {
        List<GoodsReceiptItem> items = rmaItems
                .stream()
                .map(GoodsReceiptItem::from)
                .collect(Collectors.toList());

        RmaGoodsInRequest.Builder builder = RmaGoodsInRequest.builder()
                .storeId(store.getStoreId())
                .warehouseId(config.getWarehouseId())
                .rmaId(rma.getRmaId())
                .orderId(rma.getOrderId())
                .items(items)
                .itemsRequireRepair(itemsRequireRepair)
                .createdBy(CustomSecurityContext.getLoggedInUserName());

        builder.issuer(fetchIssuer(store, config));
        builder.counterparty(customerBillingDetails.toBillingParty());

        return builder.build();
    }

    private BillingParty fetchIssuer(Store store, WarehouseConfiguration config) {
        InvoicingProvider invoicingProvider = invoicingProviderFactory.get(store);
        return invoicingProvider.fetchCostCenterById(config.getCostCenterId());
    }
}
