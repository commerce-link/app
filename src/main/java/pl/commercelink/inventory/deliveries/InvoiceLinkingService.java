package pl.commercelink.inventory.deliveries;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.invoicing.InvoicingProviderFactory;
import pl.commercelink.invoicing.api.Invoice;
import pl.commercelink.invoicing.api.InvoiceDirection;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.Collections;
import java.util.List;

@Component
public class InvoiceLinkingService {

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private InvoicingProviderFactory invoicingProviderFactory;

    @Autowired
    private DeliveriesRepository deliveriesRepository;

    public void linkInvoices(String storeId, String deliveryId) {
        var delivery = deliveriesRepository.findById(storeId, deliveryId);

        for (Invoice inv : fetchConnectedDocuments(delivery)) {
            Document doc = delivery.findDocumentById(inv.id());
            if (doc != null) {
                doc.setNumber(inv.number());
            } else {
                doc = new Document(inv.id(), inv.number(), inv.viewUrl(), DocumentType.InvoiceVat);
                delivery.addDocument(doc);
            }
        }

        deliveriesRepository.save(delivery);
    }

    public void linkInvoiceById(String storeId, String deliveryId, String invoiceId) {
        if (StringUtils.isEmpty(invoiceId)) {
            return;
        }

        Store store = storesRepository.findById(storeId);
        Invoice inv = invoicingProviderFactory.get(store).fetchInvoiceById(invoiceId.trim(), InvoiceDirection.Purchase);
        if (inv == null) {
            return;
        }

        var delivery = deliveriesRepository.findById(storeId, deliveryId);

        Document doc = new Document(inv.id(), inv.number(), inv.viewUrl(), DocumentType.InvoiceVat);
        delivery.addDocument(doc);
        deliveriesRepository.save(delivery);
    }

    public void unlinkInvoice(String storeId, String deliveryId, String invoiceId) {
        var delivery = deliveriesRepository.findById(storeId, deliveryId);
        Document doc = delivery.findDocumentById(invoiceId);
        delivery.removeDocument(doc);
        deliveriesRepository.save(delivery);
    }

    public List<Invoice> fetchConnectedDocuments(Delivery delivery) {
        Store store = storesRepository.findById(delivery.getStoreId());

        if (!store.hasIntegration(IntegrationType.INVOICING_PROVIDER)) {
            return Collections.emptyList();
        }

        return invoicingProviderFactory.get(store)
                .fetchInvoicesByOrderId(delivery.getExternalDeliveryId(), InvoiceDirection.Purchase);
    }

}
