package pl.commercelink.inventory.deliveries;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.invoicing.api.InvoicingProvider;
import pl.commercelink.invoicing.InvoicingProviderFactory;
import pl.commercelink.invoicing.api.Invoice;
import pl.commercelink.invoicing.api.InvoicePosition;
import pl.commercelink.invoicing.api.InvoiceDirection;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.PaymentStatus;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RMAItemsRepository;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.api.InvoiceSyncRequest;
import pl.commercelink.warehouse.api.Warehouse;
import pl.commercelink.web.dtos.InvoiceSyncPreview;

import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class InvoiceSyncService {

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private InvoicingProviderFactory invoicingProviderFactory;

    @Autowired
    private DeliveriesRepository deliveriesRepository;

    @Autowired
    private OrderItemsRepository orderItemsRepository;

    @Autowired
    private RMAItemsRepository rmaItemsRepository;

    @Autowired
    private Warehouse warehouse;

    public void sync(String storeId) {
        Store store = storesRepository.findById(storeId);
        InvoicingProvider invoicingProvider = invoicingProviderFactory.get(store);

        for (Delivery delivery : deliveriesRepository.findAllActiveDeliveries(storeId)) {
            if (!delivery.isInvoiced() || !delivery.isWaitingForPayment()) {
                continue;
            }

            Optional<Document> invoiceDocument = delivery.getDocuments().stream()
                    .filter(doc -> doc.getType() == DocumentType.InvoiceVat)
                    .findFirst();

            if (invoiceDocument.isEmpty()) {
                continue;
            }

            Invoice invoice = invoicingProvider.fetchInvoiceById(invoiceDocument.get().getId(), InvoiceDirection.Purchase);
            if (invoice.paid()) {
                delivery.setPaymentStatus(PaymentStatus.Paid);
                deliveriesRepository.save(delivery);
            }
        }
    }

    public void apply(String storeId, InvoiceSyncPreview preview) {
        Store store = storesRepository.findById(storeId);
        InvoicingProvider invoicingProvider = invoicingProviderFactory.get(store);
        Invoice invoice = invoicingProvider.fetchInvoiceById(preview.getInvoiceId(), InvoiceDirection.Purchase);

        Map<String, InvoicePosition> positionById = new HashMap<>();
        for (InvoicePosition pos : invoice.positions()) {
            positionById.put(pos.id(), pos);
        }

        Map<String, Double> costsByMfn = new HashMap<>();
        for (InvoiceSyncPreview.Mapping mapping : preview.getMappings()) {
            var positionId = mapping.getSelectedPositionId();
            if (positionId != null && !positionId.isBlank() && mapping.getMfn() != null && positionById.containsKey(positionId)) {
                costsByMfn.put(mapping.getMfn(), positionById.get(positionId).price().netValue());
            }
        }

        InvoiceSyncRequest syncRequest = new InvoiceSyncRequest(
                preview.getDeliveryId(),
                costsByMfn,
                invoice.seller()
        );
        warehouse.invoiceSyncHandler(storeId).sync(syncRequest);

        if (!costsByMfn.isEmpty()) {
            updateOrderItems(preview.getDeliveryId(), costsByMfn);
            updateRMAItems(preview.getDeliveryId(), costsByMfn);
        }

        updateDelivery(storeId, preview, preview.getDeliveryId(), positionById, invoice);
    }

    private void updateDelivery(String storeId, InvoiceSyncPreview preview, String deliveryId, Map<String, InvoicePosition> positionById, Invoice invoice) {
        var delivery = deliveriesRepository.findById(storeId, deliveryId);

        if (preview.getPaymentCostPositionId() != null && !preview.getPaymentCostPositionId().isBlank()) {
            InvoicePosition position = positionById.get(preview.getPaymentCostPositionId());
            delivery.setPaymentCost(position.totalPrice().netValue());
        }

        if (preview.getShippingCostPositionId() != null && !preview.getShippingCostPositionId().isBlank()) {
            InvoicePosition position = positionById.get(preview.getShippingCostPositionId());
            delivery.setShippingCost(position.totalPrice().netValue());
        }

        if (invoice.paid()) {
            delivery.setPaymentStatus(PaymentStatus.Paid);
        } else {
            delivery.setPaymentStatus(PaymentStatus.Unpaid);
        }

        if (invoice.paymentToDate() != null) {
            long paymentTerms = ChronoUnit.DAYS.between(delivery.getOrderedAt().toLocalDate(), invoice.paymentToDate());
            delivery.setPaymentTerms((int) paymentTerms);
        }

        String shortcut = preview.getInvoiceShortcut();
        if (StringUtils.isNotBlank(shortcut)) {
            delivery.setProvider(shortcut);
        }

        delivery.setSynced(true);
        deliveriesRepository.save(delivery);
    }

    private void updateOrderItems(String deliveryId, Map<String, Double> costsByMfn) {
        for (OrderItem item : orderItemsRepository.findByDeliveryId(deliveryId)) {
            if (!item.isReturned() && costsByMfn.containsKey(item.getManufacturerCode())) {
                item.setCost(costsByMfn.get(item.getManufacturerCode()));
                orderItemsRepository.save(item);
            }
        }
    }

    private void updateRMAItems(String deliveryId, Map<String, Double> costsByMfn) {
        for (RMAItem item : rmaItemsRepository.findByDeliveryId(deliveryId)) {
            if (costsByMfn.containsKey(item.getManufacturerCode())) {
                item.setCost(costsByMfn.get(item.getManufacturerCode()));
                rmaItemsRepository.save(item);
            }
        }
    }
}
