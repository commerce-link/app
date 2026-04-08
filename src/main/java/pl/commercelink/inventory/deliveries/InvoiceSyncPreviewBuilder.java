package pl.commercelink.inventory.deliveries;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.commercelink.invoicing.InvoicePositionMatcher;
import pl.commercelink.invoicing.api.InvoicingProvider;
import pl.commercelink.invoicing.InvoicingProviderFactory;
import pl.commercelink.invoicing.api.Invoice;
import pl.commercelink.invoicing.api.InvoicePosition;
import pl.commercelink.invoicing.api.InvoiceDirection;
import pl.commercelink.orders.PaymentStatus;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.InvoiceSyncPreview;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class InvoiceSyncPreviewBuilder {

    @Autowired
    private StoresRepository storesRepository;
    @Autowired
    private DeliveriesQueryService deliveriesQueryService;
    @Autowired
    private InvoicingProviderFactory invoicingProviderFactory;

    public InvoiceSyncPreview build(String storeId, String deliveryId, String invoiceId) {
        Store store = storesRepository.findById(storeId);
        var delivery = deliveriesQueryService.fetchDeliveryWithAllocations(storeId, deliveryId);

        InvoicingProvider invoicingProvider = invoicingProviderFactory.get(store);
        if (invoicingProvider == null) {
            return null;
        }

        Invoice invoice = invoicingProvider.fetchInvoiceById(invoiceId, InvoiceDirection.Purchase);
        if (invoice == null || invoice.positions() == null) {
            return null;
        }

        // fetch shortcut if missing
        String shortcut = invoice.seller().hasShortcut() ?
                invoice.seller().shortcut() :
                invoicingProvider.fetchBillingPartyById(invoice.seller().id()).shortcut();

        InvoicePositionMatcher matcher = new InvoicePositionMatcher(invoice.positions());

        List<InvoiceSyncPreview.Option> options = createOptions(invoice.positions());
        List<InvoiceSyncPreview.Mapping> mappings = createMappings(delivery.getItems(), matcher);

        InvoiceSyncPreview preview = new InvoiceSyncPreview();
        preview.setDeliveryId(delivery.getDeliveryId());
        preview.setExternalDeliveryId(delivery.getExternalDeliveryId());
        preview.setInvoiceId(invoice.id());
        preview.setInvoiceNumber(invoice.number());
        preview.setInvoicePriceNet(invoice.amount().netValue());
        preview.setInvoicePriceGross(invoice.amount().grossValue());
        preview.setCurrency(invoice.currency());
        preview.setExchangeRate(invoice.exchangeRate());
        preview.setViewUrl(invoice.viewUrl());
        preview.setOptions(options);
        preview.setMappings(mappings);
        preview.setShippingCost(delivery.getShippingCost());
        preview.setPaymentCost(delivery.getPaymentCost());
        preview.setShippingCostPositionId(matcher.findIdByCost(delivery.getShippingCost()));
        preview.setPaymentCostPositionId(matcher.findIdByCost(delivery.getPaymentCost()));
        preview.setInvoiceShortcut(shortcut);
        preview.setDeliveryProvider(delivery.getProvider());
        preview.setInvoicePaid(invoice.paid());
        preview.setInvoicePaymentToDate(invoice.paymentToDate() != null ? invoice.paymentToDate().toString() : null);
        preview.setDeliveryPaymentStatus(delivery.getPaymentStatus() != null ? delivery.getPaymentStatus().getLocalizedName() : null);
        preview.setDeliveryPaid(delivery.getPaymentStatus() == PaymentStatus.Paid);
        preview.setDeliveryPaymentDueDate(delivery.getPaymentDueDate() != null ? delivery.getPaymentDueDate().toString() : null);

        return preview;
    }

    private List<InvoiceSyncPreview.Option> createOptions(List<InvoicePosition> positions) {
        List<InvoiceSyncPreview.Option> options = new ArrayList<>();

        for (InvoicePosition pos : positions) {
            InvoiceSyncPreview.Option option = new InvoiceSyncPreview.Option();
            option.setId(pos.id());

            String truncatedName = pos.name().length() > 30 ? pos.name().substring(0, 30) + "..." : pos.name();
            option.setLabel(String.format("%d x %s (%.2f %s)", pos.qty(), truncatedName, pos.price().netValue(), pos.price().currency()));

            options.add(option);
        }

        return options;
    }

    private List<InvoiceSyncPreview.Mapping> createMappings(List<DeliveryItem> deliveryItems, InvoicePositionMatcher matcher) {
        return deliveryItems.stream()
                .map(item -> {
                    InvoiceSyncPreview.Mapping mapping = new InvoiceSyncPreview.Mapping();
                    mapping.setName(item.getName());
                    mapping.setMfn(item.getMfn());
                    mapping.setQty(item.getOrderedQty());
                    mapping.setUnitCost(item.getUnitCost());
                    mapping.setSelectedPositionId(matcher.findIdByCostAndQty(item.getUnitCost(), item.getOrderedQty()));

                    return mapping;
                })
                .collect(Collectors.toList());
    }
}
