package pl.commercelink.inventory.deliveries;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.invoicing.InvoicingProviderFactory;
import pl.commercelink.invoicing.api.Invoice;
import pl.commercelink.invoicing.api.InvoiceDirection;
import pl.commercelink.invoicing.api.InvoicePosition;
import pl.commercelink.invoicing.api.InvoicingProvider;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RMAItemsRepository;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.api.InvoiceSyncRequest;
import pl.commercelink.warehouse.api.Warehouse;
import pl.commercelink.web.dtos.InvoiceSyncPreview;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class InvoiceSyncService {

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private InvoicingProviderFactory invoicingProviderFactory;

    @Autowired
    private DeliveriesRepository deliveriesRepository;

    @Autowired
    private RMAItemsRepository rmaItemsRepository;

    @Autowired
    private Warehouse warehouse;

    @Autowired
    private DeliveryCostSync deliveryCostSync;

    public InvoiceSyncResult sync(String storeId) {
        Store store = storesRepository.findById(storeId);
        InvoicingProvider invoicingProvider = store == null ? null : invoicingProviderFactory.get(store);
        if (invoicingProvider == null) {
            return InvoiceSyncResult.notConfigured();
        }

        int checked = 0;
        int unpaid = 0;
        List<String> paidDeliveries = new ArrayList<>();
        List<String> failedInvoices = new ArrayList<>();
        for (Delivery delivery : deliveriesRepository.findUnpaidDeliveries(storeId)) {
            if (!delivery.isInvoiced() || !delivery.isWaitingForPayment() || !delivery.getPayments().isEmpty()) {
                continue;
            }
            Optional<Document> invoiceDocument = delivery.getDocuments().stream()
                    .filter(doc -> doc.getType() == DocumentType.InvoiceVat)
                    .findFirst();
            if (invoiceDocument.isEmpty()) {
                continue;
            }
            checked++;
            try {
                Invoice invoice = invoicingProvider.fetchInvoiceById(invoiceDocument.get().getId(), InvoiceDirection.Purchase);
                if (invoice.paid()) {
                    delivery.addPayment(Payment.outgoingBankTransfer(invoice.number(), null, delivery.getTotalCostGross()));
                    deliveriesRepository.save(delivery);
                    paidDeliveries.add(delivery.getShortenedDeliveryId());
                } else {
                    unpaid++;
                }
            } catch (RuntimeException e) {
                // one invoice the system cannot answer for must not hide the payments of all the others
                log.error("Purchase invoice {} of delivery {} could not be checked", invoiceDocument.get().getId(), delivery.getDeliveryId(), e);
                failedInvoices.add(invoiceDocument.get().getNumber() != null ? invoiceDocument.get().getNumber() : invoiceDocument.get().getId());
            }
        }
        return new InvoiceSyncResult(true, checked, paidDeliveries, unpaid, failedInvoices);
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
        double allocationsCostDelta = deliveryCostSync.apply(storeId, preview.getDeliveryId(), costsByMfn);
        warehouse.invoiceSyncHandler(storeId).sync(syncRequest);

        if (!costsByMfn.isEmpty()) {
            updateRMAItems(preview.getDeliveryId(), costsByMfn);
        }

        updateDelivery(storeId, preview, preview.getDeliveryId(), positionById, invoice, allocationsCostDelta);
    }

    private void updateDelivery(String storeId, InvoiceSyncPreview preview, String deliveryId, Map<String, InvoicePosition> positionById, Invoice invoice, double allocationsCostDelta) {
        var delivery = deliveriesRepository.findById(storeId, deliveryId);

        if (preview.getPaymentCostPositionId() != null && !preview.getPaymentCostPositionId().isBlank()) {
            InvoicePosition position = positionById.get(preview.getPaymentCostPositionId());
            delivery.updatePaymentCost(position.totalPrice().netValue());
        }

        if (preview.getShippingCostPositionId() != null && !preview.getShippingCostPositionId().isBlank()) {
            InvoicePosition position = positionById.get(preview.getShippingCostPositionId());
            delivery.updateShippingCost(position.totalPrice().netValue());
        }

        if (invoice.paid() && delivery.getPayments().isEmpty()) {
            delivery.addPayment(Payment.outgoingBankTransfer(invoice.number(), null, delivery.getTotalCostGross()));
        } else if (!invoice.paid() && !delivery.getPayments().isEmpty()) {
            delivery.clearPayments();
        }

        if (invoice.paymentToDate() != null) {
            long paymentTerms = ChronoUnit.DAYS.between(delivery.getOrderedAt().toLocalDate(), invoice.paymentToDate());
            delivery.setPaymentTerms((int) paymentTerms);
        }

        String shortcut = preview.getInvoiceShortcut();
        if (StringUtils.isNotBlank(shortcut)) {
            delivery.setCounterpartyShortcut(shortcut);
        }

        delivery.increaseTotalCost(allocationsCostDelta);

        delivery.setSynced(true);
        deliveriesRepository.save(delivery);
    }

    private void updateRMAItems(String deliveryId, Map<String, Double> costsByMfn) {
        for (RMAItem item : rmaItemsRepository.findByDeliveryId(deliveryId)) {
            if (costsByMfn.containsKey(item.getMfn())) {
                double itemDelta = item.updateCost(costsByMfn.get(item.getMfn()));
                if (itemDelta != 0) {
                    rmaItemsRepository.save(item);
                }
            }
        }
    }
}
