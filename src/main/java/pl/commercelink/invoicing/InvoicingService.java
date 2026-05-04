package pl.commercelink.invoicing;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.invoicing.api.*;
import pl.commercelink.orders.*;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.event.OrderEvent;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.starter.email.EmailClient;
import pl.commercelink.stores.InvoicingConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class InvoicingService {

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private OrdersRepository ordersRepository;

    @Autowired
    private OrderLifecycleEventPublisher orderLifecycleEventPublisher;

    @Autowired
    private OrderItemsRepository orderItemsRepository;

    @Autowired
    private InvoicingProviderFactory invoicingProviderFactory;

    @Autowired
    private EmailClient emailClient;

    @Autowired
    private OrderEventsRepository orderEventsRepository;

    @Autowired
    private MessageSource messageSource;

    public OperationResult createProforma(Basket basket, Locale locale, boolean send) {
        Store store = storesRepository.findById(basket.getStoreId());
        InvoicingConfiguration invoicingConfiguration = store.getInvoicingConfiguration();

        if (basket.getBillingDetails() == null || !basket.getBillingDetails().isProperlyFilled()) {
            return new OperationResult(null, null, null, messageSource.getMessage("error.message.billing.details.missing", null, locale));
        }

        List<OrderItem> orderItems = basket.getBasketItems().stream()
                .map(i -> OrderItem.fromBasketItem(null, i))
                .collect(Collectors.toList());

        basket.resolveDeliveryOption(store).ifPresent(opt ->
                orderItems.add(OrderItem.fromDeliveryOption(null, opt))
        );

        InvoicingProvider invoicingProvider = invoicingProviderFactory.get(store);
        Invoice invoice = invoicingProvider.createInvoice(InvoiceRequest.standardInvoice()
                .invoiceKind(InvoiceKind.Proforma)
                .orderId(basket.getBasketId())
                .sellDate(LocalDate.now())
                .billingParty(basket.getBillingDetails().toBillingParty())
                .positions(createInvoicePositions(orderItems, invoicingConfiguration))
                .paidAmount(0)
                .paymentTerms(invoicingConfiguration.getPaymentTerms())
                .splitPaymentsEnabled(invoicingConfiguration.isSplitPaymentsEnabled())
                .send(send)
                .build()
        );

        sendInvoiceIfRequested(send, invoicingProvider, invoice, basket);

        return new OperationResult(invoice.id(), invoice.number(), invoice.viewUrl(), null);
    }

    public OperationResult createInvoice(Order order, DocumentType documentType, boolean send) {
        Store store = storesRepository.findById(order.getStoreId());
        InvoicingConfiguration invoicingConfiguration = store.getInvoicingConfiguration();

        if (order.getBillingDetails() == null || !order.getBillingDetails().isProperlyFilled()) {
            return new OperationResult(null, null, null, "Billing details are missing");
        }

        List<OrderItem> orderItems = orderItemsRepository.findByOrderId(order.getOrderId());

        LocalDate sellDate = getSellDate(order, documentType);

        OperationResult op;
        if (documentType == DocumentType.InvoiceAdvance) {
            op = createAdvanceInvoice(store, order, sellDate, invoicingConfiguration, send);
        } else if (documentType == DocumentType.InvoiceFinal) {
            op = createFinalInvoice(store, order, sellDate, invoicingConfiguration, send);
        } else {
            op = createInvoice(store, order, documentType.toInvoiceKind(), sellDate, orderItems, invoicingConfiguration, send);
        }

        if (op.hasError()) {
            return op;
        }

        order.addDocument(new Document(op.getInvoiceId(), op.getInvoiceNo(), op.getInvoiceUrl(), documentType));
        ordersRepository.save(order);

        orderLifecycleEventPublisher.publish(order, OrderLifecycleEventType.InvoiceCreated);

        return op;
    }

    private OperationResult createAdvanceInvoice(Store store, Order order, LocalDate sellDate, InvoicingConfiguration invoicingConfiguration, boolean send) {
        String wmsOrderNo = getWMSOrderNo(order);
        if (StringUtils.isBlank(wmsOrderNo)) {
            return new OperationResult(null, null, null, "WMS order number is missing");
        }

        InvoicingProvider invoicingProvider = invoicingProviderFactory.get(store);
        Invoice invoice = invoicingProvider.createInvoice(InvoiceRequest.advanceInvoice()
                .orderId(order.getOrderId())
                .wmsOrderNo(wmsOrderNo)
                .sellDate(sellDate)
                .paidAmount(order.getPaidAmount())
                .billingParty(order.getBillingDetails().toBillingParty())
                .splitPaymentsEnabled(invoicingConfiguration.isSplitPaymentsEnabled())
                .send(send)
                .build()
        );

        sendInvoiceIfRequested(send, invoicingProvider, invoice, order, false, invoicingConfiguration);

        return new OperationResult(invoice.id(), invoice.number(), invoice.viewUrl(), null);
    }

    private OperationResult createFinalInvoice(Store store, Order order, LocalDate sellDate, InvoicingConfiguration invoicingConfiguration, boolean send) {
        String wmsOrderNo = getWMSOrderNo(order);
        if (StringUtils.isBlank(wmsOrderNo)) {
            return new OperationResult(null, null, null, "WMS order number is missing");
        }

        List<String> associatedInvoices = getAssociatedInvoiceNumbers(order);
        if (associatedInvoices.isEmpty()) {
            return new OperationResult(null, null, null, "Associated advance invoices are missing");
        }

        InvoicingProvider invoicingProvider = invoicingProviderFactory.get(store);
        Invoice invoice = invoicingProvider.createInvoice(InvoiceRequest.finalInvoice()
                .orderId(order.getOrderId())
                .wmsOrderNo(wmsOrderNo)
                .billingParty(order.getBillingDetails().toBillingParty())
                .leftToPay(order.getUnpaidAmount())
                .invoiceNumbers(associatedInvoices)
                .splitPaymentsEnabled(invoicingConfiguration.isSplitPaymentsEnabled())
                .send(send)
                .build()
        );

        sendInvoiceIfRequested(send, invoicingProvider, invoice, order, false, invoicingConfiguration);

        return new OperationResult(invoice.id(), invoice.number(), invoice.viewUrl(), null);
    }

    private OperationResult createInvoice(
            Store store,
            Order order,
            InvoiceKind invoiceKind,
            LocalDate sellDate,
            List<OrderItem> orderItems,
            InvoicingConfiguration invoicingConfiguration,
            boolean send
    ) {
        InvoicingProvider invoicingProvider = invoicingProviderFactory.get(store);
        Invoice invoice = invoicingProvider.createInvoice(InvoiceRequest.standardInvoice()
                .invoiceKind(invoiceKind)
                .orderId(order.getOrderId())
                .sellDate(sellDate)
                .billingParty(order.getBillingDetails().toBillingParty())
                .positions(createInvoicePositions(orderItems, invoicingConfiguration))
                .paidAmount(order.getPaidAmount())
                .paymentTerms(invoicingConfiguration.getPaymentTerms())
                .splitPaymentsEnabled(invoicingConfiguration.isSplitPaymentsEnabled())
                .send(send)
                .build()
        );

        sendInvoiceIfRequested(send, invoicingProvider, invoice, order, invoiceKind == InvoiceKind.Proforma, invoicingConfiguration);

        return new OperationResult(invoice.id(), invoice.number(), invoice.viewUrl(), null);
    }

    private LocalDate getSellDate(Order order, DocumentType documentType) {
        LocalDate sellDate;
        if (documentType == DocumentType.InvoiceAdvance) {
            sellDate = order.getOrderedAt().toLocalDate();
        } else {
            sellDate = order.getShipments().stream()
                    .map(Shipment::getShippedAt)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(LocalDateTime.now()).toLocalDate();
        }
        return sellDate;
    }

    private List<String> getAssociatedInvoiceNumbers(Order order) {
        return order.getDocuments().stream()
                .filter(r -> r.getType() == DocumentType.InvoiceAdvance)
                .map(Document::getNumber)
                .collect(Collectors.toList());
    }

    private String getWMSOrderNo(Order order) {
        return order.getDocuments().stream()
                .filter(r -> r.getType() == DocumentType.Order)
                .map(Document::getNumber)
                .findFirst()
                .orElse(null);
    }

    private List<InvoicePosition> createInvoicePositions(List<OrderItem> orderItems, InvoicingConfiguration invoicingConfiguration) {
        List<InvoicePosition> positions = new LinkedList<>();

        if (hasConsolidatedOrderItems(orderItems)) {
            positions.add(
                    InvoicePositionConverter.fromOrderItems(
                            invoicingConfiguration.getPositionsConsolidationPrefix(),
                            getConsolidatedOrderItems(orderItems)
                    )
            );
        }

        positions.addAll(orderItems.stream()
                .filter(i -> !i.isConsolidated())
                .map(InvoicePositionConverter::fromOrderItem)
                .toList()
        );

        return positions;
    }

    private boolean hasConsolidatedOrderItems(List<OrderItem> orderItems) {
        return orderItems.stream().anyMatch(OrderItem::isConsolidated);
    }

    private List<OrderItem> getConsolidatedOrderItems(List<OrderItem> orderItems) {
        return orderItems.stream().filter(OrderItem::isConsolidated).collect(Collectors.toList());
    }

    private void sendInvoiceIfRequested(boolean send, InvoicingProvider invoicingProvider, Invoice invoice, Order order, boolean isProforma, InvoicingConfiguration invoicingConfiguration) {
        boolean includeAttachment = isProforma || invoicingConfiguration.isSendInvoicesAsAttachment();
        sendInvoiceIfRequested(send, invoicingProvider, invoice,
                order.getStoreId(), order.getOrderId(), order.getBillingDetails().getEmail(), order.getBillingDetails().getName(), isProforma, includeAttachment);
        if (send) {
            orderEventsRepository.save(new OrderEvent(order.getOrderId(), EventType.email, EmailNotificationType.ORDER_INVOICE.name(), LocalDateTime.now()));
        }
    }

    private void sendInvoiceIfRequested(boolean send, InvoicingProvider invoicingProvider, Invoice invoice, Basket basket) {
        sendInvoiceIfRequested(send, invoicingProvider, invoice,
                basket.getStoreId(), basket.getBasketId(), basket.getBillingDetails().getEmail(), basket.getBillingDetails().getName(), true, true);
    }

    private void sendInvoiceIfRequested(boolean send, InvoicingProvider invoicingProvider, Invoice invoice,
                                        String storeId, String orderId, String email, String name, boolean isProforma, boolean includeAttachment) {
        if (!send) {
            return;
        }

        byte[] invoicePdfBytes = includeAttachment ? invoicingProvider.fetchInvoicePdf(invoice.id()) : null;
        InvoiceEmailNotification msg = new InvoiceEmailNotification(email, name, orderId, invoicePdfBytes, invoice.number());
        EmailNotificationType type = isProforma ? EmailNotificationType.ORDER_INVOICE_PROFORMA : EmailNotificationType.ORDER_INVOICE;
        emailClient.send(storeId, type, msg);
    }

    public static class OperationResult {
        private String invoiceId;
        private String invoiceNo;
        private String invoiceUrl;
        private String errorMessage;

        public OperationResult(String invoiceId, String invoiceNo, String invoiceUrl, String errorMessage) {
            this.invoiceId = invoiceId;
            this.invoiceNo = invoiceNo;
            this.invoiceUrl = invoiceUrl;
            this.errorMessage = errorMessage;
        }

        public String getInvoiceId() {
            return invoiceId;
        }

        public String getInvoiceNo() {
            return invoiceNo;
        }

        public String getInvoiceUrl() {
            return invoiceUrl;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public boolean hasError() {
            return errorMessage != null && !errorMessage.isEmpty();
        }
    }
}
