package pl.commercelink.inventory.deliveries;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.commercelink.invoicing.InvoicingProviderFactory;
import pl.commercelink.invoicing.api.Invoice;
import pl.commercelink.invoicing.api.InvoiceDirection;
import pl.commercelink.invoicing.api.InvoicingProvider;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.InvoiceSyncPreview;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Does for every received delivery without a purchase invoice what the operator does on its page: "Połącz" by the
 * supplier's order number, "Synchronizuj", save. Only a perfect match ({@link InvoiceAutoMatch}) is saved; any other
 * delivery is left exactly as it was, not even linked.
 */
@Slf4j
@Service
public class InvoiceAutoSyncService {

    // every delivery costs a few calls to the invoicing system and the load balancer cuts a long request off
    static final Duration BUDGET = Duration.ofSeconds(20);

    private enum Outcome { SYNCED, NO_INVOICE, SEVERAL_INVOICES, NEEDS_REVIEW }

    private final StoresRepository storesRepository;
    private final InvoicingProviderFactory invoicingProviderFactory;
    private final DeliveriesRepository deliveriesRepository;
    private final InvoiceSyncPreviewBuilder previewBuilder;
    private final InvoiceLinkingService invoiceLinkingService;
    private final InvoiceSyncService invoiceSyncService;
    private final Clock clock;

    @Autowired
    public InvoiceAutoSyncService(StoresRepository storesRepository, InvoicingProviderFactory invoicingProviderFactory,
                                  DeliveriesRepository deliveriesRepository, InvoiceSyncPreviewBuilder previewBuilder,
                                  InvoiceLinkingService invoiceLinkingService, InvoiceSyncService invoiceSyncService) {
        this(storesRepository, invoicingProviderFactory, deliveriesRepository, previewBuilder, invoiceLinkingService,
                invoiceSyncService, Clock.systemUTC());
    }

    InvoiceAutoSyncService(StoresRepository storesRepository, InvoicingProviderFactory invoicingProviderFactory,
                           DeliveriesRepository deliveriesRepository, InvoiceSyncPreviewBuilder previewBuilder,
                           InvoiceLinkingService invoiceLinkingService, InvoiceSyncService invoiceSyncService, Clock clock) {
        this.storesRepository = storesRepository;
        this.invoicingProviderFactory = invoicingProviderFactory;
        this.deliveriesRepository = deliveriesRepository;
        this.previewBuilder = previewBuilder;
        this.invoiceLinkingService = invoiceLinkingService;
        this.invoiceSyncService = invoiceSyncService;
        this.clock = clock;
    }

    /**
     * @param continueAfter the reception time the previous run stopped at. The deliveries it left unchanged stay in
     *                      the backlog, so without it every run would spend its time on the same oldest ones.
     */
    public InvoiceAutoSyncResult run(String storeId, LocalDateTime continueAfter) {
        Store store = storesRepository.findById(storeId);
        InvoicingProvider provider = store == null ? null : invoicingProviderFactory.get(store);
        if (provider == null) {
            return InvoiceAutoSyncResult.notConfigured();
        }

        List<Delivery> backlog = deliveriesRepository.findToSettle(storeId).stream()
                .filter(delivery -> continueAfter == null || delivery.getReceivedAt().isAfter(continueAfter))
                .sorted(Comparator.comparing(Delivery::getReceivedAt))
                .toList();

        List<String> synced = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        int noInvoice = 0;
        int severalInvoices = 0;
        int needsReview = 0;
        int remaining = 0;
        LocalDateTime lastChecked = null;
        long deadline = clock.millis() + BUDGET.toMillis();
        for (int i = 0; i < backlog.size(); i++) {
            if (clock.millis() >= deadline) {
                remaining = backlog.size() - i;
                break;
            }
            Delivery delivery = backlog.get(i);
            try {
                switch (linkAndSync(storeId, provider, delivery)) {
                    case SYNCED -> synced.add(delivery.getShortenedDeliveryId());
                    case NO_INVOICE -> noInvoice++;
                    case SEVERAL_INVOICES -> severalInvoices++;
                    case NEEDS_REVIEW -> needsReview++;
                }
            } catch (RuntimeException e) {
                // one delivery the invoicing system cannot answer for must not stop all the others
                log.error("Purchase invoice of delivery {} could not be linked and synced automatically", delivery.getDeliveryId(), e);
                failed.add(delivery.getShortenedDeliveryId());
            }
            lastChecked = delivery.getReceivedAt();
        }
        return new InvoiceAutoSyncResult(true, synced, noInvoice, severalInvoices, needsReview, failed, remaining,
                remaining > 0 ? lastChecked : null);
    }

    private Outcome linkAndSync(String storeId, InvoicingProvider provider, Delivery delivery) {
        if (delivery.isAwaitingApproval()) {
            return Outcome.NEEDS_REVIEW;
        }
        // a provisional number is the app's placeholder, not the one the supplier puts on the invoice
        if (StringUtils.isBlank(delivery.getExternalDeliveryId()) || delivery.isExternalDeliveryIdProvisional()) {
            return Outcome.NO_INVOICE;
        }
        List<Invoice> invoices = provider.fetchInvoicesByOrderId(delivery.getExternalDeliveryId(), InvoiceDirection.Purchase);
        if (invoices == null || invoices.isEmpty()) {
            return Outcome.NO_INVOICE;
        }
        if (invoices.size() > 1) {
            return Outcome.SEVERAL_INVOICES;
        }
        Invoice invoice = invoices.get(0);
        InvoiceSyncPreview preview = previewBuilder.build(storeId, delivery.getDeliveryId(), invoice.id());
        if (preview == null || !InvoiceAutoMatch.isPerfect(preview)) {
            return Outcome.NEEDS_REVIEW;
        }
        if (!invoiceLinkingService.linkUnlessInvoiced(storeId, delivery.getDeliveryId(), invoice)) {
            return Outcome.NEEDS_REVIEW;
        }
        invoiceSyncService.apply(storeId, preview);
        return Outcome.SYNCED;
    }
}
