package pl.commercelink.warehouse.builtin;

import org.springframework.stereotype.Service;
import pl.commercelink.printing.PrintProviderRegistry;
import pl.commercelink.printing.api.PrintJob;
import pl.commercelink.printing.api.PrintProvider;
import pl.commercelink.printing.api.PrintingException;
import pl.commercelink.printing.api.WarehouseLabel;
import pl.commercelink.stores.Printer;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.WarehouseConfiguration;

import java.util.List;

@Service
public class WarehouseLabelPrintService {

    private final WarehouseDocumentRepository documentRepository;
    private final WarehouseDocumentItemRepository itemRepository;
    private final StoresRepository storesRepository;
    private final PrintProviderRegistry printProviderRegistry;

    public WarehouseLabelPrintService(WarehouseDocumentRepository documentRepository,
                                      WarehouseDocumentItemRepository itemRepository,
                                      StoresRepository storesRepository,
                                      PrintProviderRegistry printProviderRegistry) {
        this.documentRepository = documentRepository;
        this.itemRepository = itemRepository;
        this.storesRepository = storesRepository;
        this.printProviderRegistry = printProviderRegistry;
    }

    public PrintJob renderDocumentLabels(String storeId, String documentId, String printerName) {
        Store store = storesRepository.findById(storeId);
        Printer printer = resolvePrinter(store, printerName);
        PrintProvider provider = printProviderRegistry.create(printer);

        WarehouseDocument document = documentRepository.findByDocumentId(storeId, documentId);
        if (document == null) {
            throw new PrintingException("Document not found: " + documentId);
        }

        List<WarehouseDocumentItem> items = itemRepository.findByDocumentId(documentId);
        WarehouseLabel.Distributor distributor = resolveDistributor(document);
        String deliveryId = document.getDeliveryId() != null ? document.getShortenedDeliveryId() : null;

        StringBuilder content = new StringBuilder();
        String contentType = "application/zpl";
        for (WarehouseDocumentItem item : items) {
            WarehouseLabel label = new WarehouseLabel(item.getName(), item.getEan(), item.getMfn(), deliveryId, distributor);
            PrintJob job = provider.renderWarehouseLabel(label);
            contentType = job.contentType();
            int copies = Math.max(1, item.getQty());
            for (int i = 0; i < copies; i++) {
                content.append(job.content());
            }
        }
        return new PrintJob(content.toString(), contentType);
    }

    private Printer resolvePrinter(Store store, String printerName) {
        WarehouseConfiguration configuration = store.getWarehouseConfiguration();
        if (configuration != null) {
            for (Printer printer : configuration.getPrinters()) {
                if (printerName.equals(printer.getName())) {
                    return printer;
                }
            }
        }
        throw new PrintingException("Printer not found: " + printerName);
    }

    private WarehouseLabel.Distributor resolveDistributor(WarehouseDocument document) {
        if (!document.isForeignDelivery() || document.getIssuer() == null) {
            return null;
        }
        IssuerDetails issuer = document.getIssuer();
        return new WarehouseLabel.Distributor(
                issuer.getCompanyName(),
                issuer.getStreetAndNumber(),
                issuer.getPostalCode(),
                issuer.getCity(),
                issuer.getCountry());
    }
}
