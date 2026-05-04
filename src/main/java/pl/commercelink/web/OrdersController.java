package pl.commercelink.web;

import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.invoicing.InvoiceCreationEventPublisher;
import pl.commercelink.orders.*;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.orders.imports.OrderImporter;
import pl.commercelink.orders.imports.OrderReferenceType;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.pricelist.Pricelist;
import pl.commercelink.pricelist.PricelistRepository;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.rest.client.HttpClientException;
import pl.commercelink.shipping.ShipmentCancelService;
import pl.commercelink.shipping.api.ShippingException;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.GoodsOutEventPublisher;
import pl.commercelink.web.dtos.ClientDataDto;
import pl.commercelink.web.dtos.OrderItemsForm;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class OrdersController extends BaseController {

    @Autowired
    private Inventory inventory;

    @Autowired
    private ProductCatalogRepository productCatalogRepository;

    @Autowired
    private OrdersRepository ordersRepository;

    @Autowired
    private OrderItemsRepository orderItemsRepository;

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private BasketsRepository basketsRepository;

    @Autowired
    private OrdersManager ordersManager;

    @Autowired
    private OrderLifecycle orderLifecycle;

    @Autowired
    private PricelistRepository pricelistRepository;

    @Autowired
    private InvoiceCreationEventPublisher invoiceCreationEventPublisher;

    @Autowired
    private List<OrderImporter> orderImporters;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private OrderEventsRepository orderEventsRepository;

    @Autowired
    private ShipmentCancelService shipmentCancelService;

    @Autowired
    private GoodsOutEventPublisher goodsOutEventPublisher;

    @GetMapping("/dashboard/orders")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String orders(@RequestParam(required = false) List<String> statuses,
                        @RequestParam(required = false, defaultValue = "false") boolean showAll,
                        Model model) {
        // Fetch all active orders once (excluding Completed)
        List<Order> allActiveOrders = ordersRepository.findAllActiveOrders(getStoreId())
                .stream()
                .filter(order -> order.getStatus() != OrderStatus.Completed)
                .sorted(Comparator.comparing(Order::getEstimatedShippingAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        List<OrderStatus> statusEnums = null;
        List<String> selectedStatusList;

        if (showAll) {
            // Show all orders regardless of status
            statusEnums = null;
            selectedStatusList = Collections.emptyList();
        } else if (statuses != null && !statuses.isEmpty()) {
            statusEnums = statuses.stream()
                    .map(OrderStatus::valueOf)
                    .collect(Collectors.toList());
            selectedStatusList = statuses;
        } else {
            // Default to Assembled status, fall back to Assembly, then New
            long assembledCount = allActiveOrders.stream().filter(o -> o.getStatus() == OrderStatus.Assembled).count();
            long assemblyCount = allActiveOrders.stream().filter(o -> o.getStatus() == OrderStatus.Assembly).count();

            if (assembledCount > 0) {
                statusEnums = Collections.singletonList(OrderStatus.Assembled);
                selectedStatusList = Arrays.asList(OrderStatus.Assembled.name());
            } else if (assemblyCount > 0) {
                statusEnums = Collections.singletonList(OrderStatus.Assembly);
                selectedStatusList = Arrays.asList(OrderStatus.Assembly.name());
            } else {
                statusEnums = Collections.singletonList(OrderStatus.New);
                selectedStatusList = Arrays.asList(OrderStatus.New.name());
            }
        }

        // Filter by status if specified
        List<Order> filteredOrders = allActiveOrders;
        if (statusEnums != null && !statusEnums.isEmpty()) {
            List<OrderStatus> finalStatusEnums = statusEnums;
            filteredOrders = allActiveOrders.stream()
                    .filter(order -> finalStatusEnums.contains(order.getStatus()))
                    .collect(Collectors.toList());
        }

        // Group filtered orders by status
        Map<OrderStatus, List<Order>> ordersByStatus = filteredOrders.stream()
                .collect(Collectors.groupingBy(Order::getStatus));

        // Calculate order counts for ALL statuses (for filter display)
        Map<OrderStatus, Long> itemCountsByStatus = new HashMap<>();
        Map<OrderStatus, List<Order>> allOrdersByStatus = allActiveOrders.stream()
                .collect(Collectors.groupingBy(Order::getStatus));

        for (Map.Entry<OrderStatus, List<Order>> entry : allOrdersByStatus.entrySet()) {
            long orderCount = entry.getValue().size();
            itemCountsByStatus.put(entry.getKey(), orderCount);
        }

        // Add each status enum value to model for template access
        Arrays.stream(OrderStatus.values()).forEach(s -> model.addAttribute(s.name() + "Status", s));

        // Exclude Completed status from filter options
        List<OrderStatus> availableStatuses = Arrays.stream(OrderStatus.values())
                .filter(status -> status != OrderStatus.Completed)
                .collect(Collectors.toList());

        model.addAttribute("liveOrders", filteredOrders);
        model.addAttribute("ordersByStatus", ordersByStatus);
        model.addAttribute("itemCountsByStatus", itemCountsByStatus);
        model.addAttribute("statuses", availableStatuses);
        model.addAttribute("selectedStatuses", selectedStatusList);
        return "orders";
    }

    @GetMapping("/dashboard/orders/new/from-basket")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String submitOrderBasedOnBasket(@RequestParam("basketId") String basketId, Model model) {
        Optional<Basket> basketOpt = basketsRepository.findById(getStoreId(), basketId);
        if (!basketOpt.isPresent()) {
            model.addAttribute("error", "Basket not found");
            return "error";
        }

        Basket basket = basketOpt.get();
        BillingDetails billingDetails = Optional.ofNullable(basket.getBillingDetails())
                .orElseGet(BillingDetails::_default);
        ShippingDetails shippingDetails = Optional.ofNullable(basket.getShippingDetails())
                .orElseGet(ShippingDetails::_default);

        Store store = storesRepository.findById(getStoreId());
        ShipmentType shipmentType = basket.resolveDeliveryOption(store)
                .map(DeliveryOption::getType)
                .orElse(ShipmentType.Courier);

        ClientDataDto form = new ClientDataDto();
        form.setOrderReference(basketId);
        form.setOrderReferenceType(OrderReferenceType.Basket);
        form.setShipmentType(shipmentType);
        form.setBillingDetails(billingDetails);
        form.setShippingDetails(shippingDetails);

        model.addAttribute("form", form);
        model.addAttribute("shipmentTypes", ShipmentType.values());
        model.addAttribute("orderSourceTypes", OrderSourceType.values());
        model.addAttribute("paymentSources", PaymentSource.values());

        return "newOrder_clientDataCollection";
    }

    @PostMapping("/dashboard/orders/new/fulfilment")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String submitOrder(@ModelAttribute ClientDataDto dto, Model model) {
        OrderImporter importer = getImporter(dto);
        Order order = importer._import(getStoreId(), dto);

        return "redirect:/dashboard/orders/" + order.getOrderId();
    }

    public OrderImporter getImporter(ClientDataDto clientDataDto) {
        return orderImporters.stream()
                .filter(i -> i.supports(getStoreId(), clientDataDto))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown order importer"));
    }



    @GetMapping("/dashboard/orders/{orderId}")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String getOrderDetails(@PathVariable("orderId") String orderId, @ModelAttribute("catalogId") String catalogId, Model model) {
        Order existingOrder = ordersRepository.findById(getStoreId(), orderId);
        return showOrderDetails(existingOrder, catalogId, model);
    }

    @GetMapping("/dashboard/store/{storeId}/orders/{orderId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String getOrderDetailsForSuperAdmin(@PathVariable("storeId") String storeId, @PathVariable("orderId") String orderId, @ModelAttribute("catalogId") String catalogId, Model model) {
        Order existingOrder = ordersRepository.findById(storeId, orderId);
        return showOrderDetails(existingOrder, catalogId, model);
    }

    @PostMapping("/dashboard/orders/{orderId}/select-catalog")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String onCatalogChange(@PathVariable String orderId, @RequestParam String catalogId, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("catalogId", catalogId);
        redirectAttributes.addFlashAttribute("openModal", true);
        return "redirect:/dashboard/orders/" + orderId + "#orderItemsForm";
    }

    @PostMapping("/dashboard/orders/{orderId}/add-item/pricelist")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String addOrderItemFromPriceList(@PathVariable String orderId,
                                            @RequestParam String itemCatalogId, @RequestParam String itemPricelistId,
                                            @RequestParam String category, @RequestParam String itemLabel, @RequestParam String itemName) {
        Store store = storesRepository.findById(getStoreId());
        Order order = ordersRepository.findById(getStoreId(), orderId);

        Pricelist pricelist = pricelistRepository.find(itemCatalogId, itemPricelistId);
        Optional<AvailabilityAndPrice> op = pricelist.findByCategoryLabelAndName(ProductCategory.valueOf(category), itemLabel, itemName);

        op.ifPresent(availabilityAndPrice -> ordersManager.addOrderItem(store, order, availabilityAndPrice));

        return "redirect:/dashboard/orders/" + order.getOrderId() + "#orderItemsForm";
    }

    @PostMapping("/dashboard/orders/{orderId}/add-item/inventory")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String addOrderItemFromInventory(@PathVariable String orderId, @RequestParam(required = false) String itemEan, @RequestParam(required = false) String itemManufacturerCode) {
        Store store = storesRepository.findById(getStoreId());
        Order order = ordersRepository.findById(getStoreId(), orderId);

        MatchedInventory matchedInventory = inventory.withEnabledSuppliersOnly(getStoreId())
                .findByInventoryKey(new InventoryKey(itemEan.trim(), itemManufacturerCode.trim()));
        ordersManager.addOrderItem(store, order, matchedInventory);

        return "redirect:/dashboard/orders/" + order.getOrderId() + "#orderItemsForm";
    }

    private String showOrderDetails(Order order, String catalogId, Model model) {
        return showOrderDetails(order, orderItemsRepository.findByOrderId(order.getOrderId()), catalogId, model);
    }

    private String showOrderDetails(Order order, List<OrderItem> orderItems, String catalogId, Model model) {
        List<ProductCatalog> catalogs = productCatalogRepository.findAll(order.getStoreId());

        Pricelist pricelist = Pricelist.empty();
        if (Strings.isNotBlank(catalogId)) {
            String newestPricelistId = pricelistRepository.findNewestPricelistIdCached(catalogId);
            pricelist = pricelistRepository.find(catalogId, newestPricelistId);
        }

        Store store = storesRepository.findById(order.getStoreId());

        List<DocumentType> manualDocumentTypes = order.isB2B()
                ? Arrays.asList(DocumentType.InvoiceVat, DocumentType.InvoiceAdvance, DocumentType.InvoiceFinal)
                : Arrays.asList(DocumentType.Receipt, DocumentType.InvoicePersonal);

        List<OrderItem> serialUpdateItems = orderItems.stream()
                .filter(i -> i.hasOneOfTheStatuses(FulfilmentStatus.Delivered))
                .filter(i -> i.getCategory() != ProductCategory.Services)
                .collect(Collectors.toList());

        model.addAttribute("order", order);
        model.addAttribute("orderEvents", orderEventsRepository.findByOrderId(order.getOrderId()));
        model.addAttribute("orderItemsForm", new OrderItemsForm(orderItems));
        model.addAttribute("serialUpdateItems", serialUpdateItems);
        model.addAttribute("orderFinancials", new OrderFinancials(order, orderItems));
        model.addAttribute("orderStatuses", Arrays.stream(OrderStatus.values())
                .filter(status -> status != OrderStatus.Completed || order.getStatus() == OrderStatus.Completed)
                .collect(Collectors.toList()));
        model.addAttribute("orderReviewStatuses", OrderReviewStatus.values());
        model.addAttribute("receiptTypes", manualDocumentTypes);
        model.addAttribute("paymentSources", PaymentSource.values());
        model.addAttribute("paymentStatuses", PaymentStatus.values());
        model.addAttribute("shipmentTypes", ShipmentType.values());
        model.addAttribute("categories", store.getEnabledProductCategories());
        model.addAttribute("fulfilmentStatuses", FulfilmentStatus.values());
        model.addAttribute("fulfilmentTypes", FulfilmentType.values());
        model.addAttribute("isCompletedOrder", order.getStatus() == OrderStatus.Completed || isSuperAdmin());
        model.addAttribute("isNewOrder", order.getStatus() == OrderStatus.New);
        model.addAttribute("canDeleteOrder", order.hasStatus(OrderStatus.New) && orderItems.isEmpty() && !order.isInvoiced());
        model.addAttribute("hasWarehouseDocument", order.getDocumentByType(DocumentType.GoodsIssue).isPresent());
        model.addAttribute("hasWarehouseDocumentsEnabled", store.hasDocumentsGenerationEnabled());
        model.addAttribute("isInvoiced", order.isInvoiced());
        model.addAttribute("isSuperAdmin", isSuperAdmin());
        model.addAttribute("isAdmin", isAdmin());

        model.addAttribute("catalogs", catalogs);
        model.addAttribute("catalogId", catalogId);
        model.addAttribute("pricelistId", pricelist.getPricelistId());
        model.addAttribute("availabilityAndPrices", pricelist.getAvailabilityAndPrices());
        model.addAttribute("availableCategories", pricelist.getAvailableCategories());

        DocumentType nextDocumentToIssue = order.getNextDocumentToIssue().orElse(null);
        model.addAttribute("nextInvoiceToIssue", nextDocumentToIssue);
        model.addAttribute("canAddDocumentManually", manualDocumentTypes.contains(nextDocumentToIssue));

        return "orderDetails";
    }

    @GetMapping("/dashboard/orders/{orderId}/collection")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String getOrderCollectionProtocol(@PathVariable("orderId") String orderId, Model model) {
        Order order = ordersRepository.findById(getStoreId(), orderId);
        return renderOrderCollectionProtocol(order, model);
    }

    @GetMapping("/dashboard/store/{storeId}/orders/{orderId}/collection")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String getOrderCollectionProtocolForSuperAdmin(@PathVariable("storeId") String storeId, @PathVariable("orderId") String orderId, Model model) {
        Order order = ordersRepository.findById(storeId, orderId);
        return renderOrderCollectionProtocol(order, model);
    }

    private String renderOrderCollectionProtocol(Order order, Model model) {
        Store store = storesRepository.findById(order.getStoreId());
        List<OrderItem> orderItems = orderItemsRepository.findByOrderId(order.getOrderId());

        model.addAttribute("store", store);
        model.addAttribute("orderId", order.getOrderId());
        model.addAttribute("collectedAt", LocalDate.now());
        model.addAttribute("location", "Kraków, PL");
        model.addAttribute("orderItems", orderItems);

        return "orderPersonalCollection";
    }

    @GetMapping("/dashboard/orders/{orderId}/card")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String getOrderCard(@PathVariable("orderId") String orderId, Model model) {
        model.addAttribute("order", ordersRepository.findById(getStoreId(), orderId));
        model.addAttribute("orderItems", orderItemsRepository.findByOrderId(orderId));

        return "orderCard";
    }

    @GetMapping("/dashboard/store/{storeId}/orders/{orderId}/card")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String getOrderCardForSuperAdmin(@PathVariable("storeId") String storeId, @PathVariable("orderId") String orderId, Model model) {
        model.addAttribute("order", ordersRepository.findById(storeId, orderId));
        model.addAttribute("orderItems", orderItemsRepository.findByOrderId(orderId));

        return "orderCard";
    }

    @PostMapping("/dashboard/orders/{orderId}/invoicing")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String createInvoice(@PathVariable String orderId, @RequestParam(defaultValue = "false") boolean send, Locale locale, RedirectAttributes redirectAttributes) {
        Order order = ordersRepository.findById(getStoreId(), orderId);

        if (!order.getNextInvoiceToIssue().isPresent()) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("error.message.no.eligible.invoice.to.create", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }

        invoiceCreationEventPublisher.publish(order, send);
        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("invoice.generation.started", null, locale));

        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/goods-out")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String issueGoodsOut(@PathVariable String orderId, Locale locale, RedirectAttributes redirectAttributes) {
        Order order = ordersRepository.findById(getStoreId(), orderId);

        if (order.getDocumentByType(DocumentType.GoodsIssue).isPresent()) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("error.message.goods.issue.already.exists", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }

        String createdBy = CustomSecurityContext.getLoggedInUser()
                .map(CustomUser::getName)
                .orElse("System");
        goodsOutEventPublisher.publish(order, createdBy);
        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("goods.issue.generation.started", null, locale));

        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/updateOrderInfo")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String updateOrderInfo(@PathVariable String orderId, @ModelAttribute("order") Order updatedOrder, RedirectAttributes redirectAttributes, Locale locale) {
        Order existingOrder = ordersRepository.findById(getStoreId(), orderId);

        if (!existingOrder.canTransitionToDelivered(updatedOrder.getStatus())) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("error.message.delivered.requires.shipment.data", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }

        if (updatedOrder.getStatus() == OrderStatus.Completed) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("error.message.completed.cannot.be.set.manually", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }

        existingOrder.setStatus(updatedOrder.getStatus());
        existingOrder.setEmailNotificationsEnabled(updatedOrder.isEmailNotificationsEnabled());
        existingOrder.setEstimatedAssemblyAt(updatedOrder.getEstimatedAssemblyAt());
        existingOrder.setEstimatedShippingAt(updatedOrder.getEstimatedShippingAt());
        existingOrder.setAffiliateId(updatedOrder.getAffiliateId());
        existingOrder.setGclid(updatedOrder.getGclid());
        existingOrder.setComment(updatedOrder.getComment());
        existingOrder.setFulfilmentType(updatedOrder.getFulfilmentType());
        return save(existingOrder);
    }

    @GetMapping("/dashboard/orders/{orderId}/items/{itemId}")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String getOrderItem(@PathVariable String orderId, @PathVariable String itemId, Model model) {
        Order order = ordersRepository.findById(getStoreId(), orderId);
        OrderItem orderItem = orderItemsRepository.findById(orderId, itemId);
        return showOrderItemDetails(order, orderItem, model);
    }

    @PostMapping("/dashboard/orders/{orderId}/items/{itemId}/save")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String saveOrderItem(@PathVariable String orderId, @PathVariable String itemId, @ModelAttribute OrderItem updatedItem, Model model) {
        Order order = ordersRepository.findById(getStoreId(), orderId);
        List<OrderItem> orderItems = orderItemsRepository.findByOrderId(orderId);

        Optional<OrderItem> op = orderItems.stream()
                .filter(i -> i.getItemId().equals(itemId))
                .findFirst();

        if (op.isPresent()) {
            OrderItem orderItem = op.get();

            orderItem.update(updatedItem);
            orderItemsRepository.save(orderItem);

            order.setTotalPrice(new OrderFinancials(order, orderItems).getTotalPrice());
            orderLifecycle.update(order, orderItems);
        }

        return "redirect:/dashboard/orders/" + orderId;
    }

    private String showOrderItemDetails(Order order, OrderItem orderItem, Model model) {
        Store store = storesRepository.findById(order.getStoreId());

        model.addAttribute("orderId", order.getOrderId());
        model.addAttribute("orderItem", orderItem);
        model.addAttribute("categories", store.getEnabledProductCategories());
        model.addAttribute("fulfilmentStatuses", FulfilmentStatus.values());
        model.addAttribute("isCompletedOrder", order.getStatus() == OrderStatus.Completed);

        return "orderItem";
    }

    @PostMapping("/dashboard/orders/{orderId}/delete")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String deleteOrder(@PathVariable String orderId) {
        ordersManager.deleteOrder(getStoreId(), orderId);
        return "redirect:/dashboard/orders";
    }

    @PostMapping("/dashboard/orders/{orderId}/removeSelectedItemsFromOrder")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String removeSelectedItemsFromOrder(@PathVariable String orderId, @ModelAttribute OrderItemsForm form, Model model) {
        OrdersManager.Result result = ordersManager.removeFromOrder(getStoreId(), orderId, form.getSelectedOrderItemIds());
        return showOrderDetails(result.getOrder(), result.getOrderItems(), null, model);
    }

    @PostMapping("/dashboard/orders/{orderId}/moveSelectedItemsToAllocation")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String moveSelectedItemsToAllocation(@PathVariable String orderId, @ModelAttribute OrderItemsForm form, Model model) {
        OrdersManager.Result result = ordersManager.moveItemsToAllocation(getStoreId(), orderId, form.getSelectedOrderItemIds());
        return showOrderDetails(result.getOrder(), result.getOrderItems(), null, model);
    }

    @PostMapping("/dashboard/orders/{orderId}/moveSelectedItemsToTheWarehouse")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String moveSelectedItemsToTheWarehouse(@PathVariable String orderId, @ModelAttribute OrderItemsForm form, Model model) {
        OrdersManager.Result result = ordersManager.moveOrderItemsToTheWarehouse(getStoreId(), orderId, form.getSelectedOrderItemIds());
        return showOrderDetails(result.getOrder(), result.getOrderItems(), null, model);
    }

    @PostMapping("/dashboard/orders/{orderId}/moveSelectedItemsToTheWarehouseForRMA")
    public String moveSelectedItemsToTheWarehouseForRMA(@PathVariable String orderId, @ModelAttribute OrderItemsForm form, Model model) {
        OrdersManager.Result result = ordersManager.moveOrderItemsToTheWarehouseForRMA(getStoreId(), orderId, form.getSelectedOrderItemIds());
        return showOrderDetails(result.getOrder(), result.getOrderItems(), null, model);
    }

    @PostMapping("/dashboard/orders/{orderId}/updateSerialNumbers")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String updateSerialNumbers(@PathVariable String orderId, @ModelAttribute OrderItemsForm form) {
        Map<String, String> serialByItemId = form.getOrderItems().stream()
                .filter(i -> Strings.isNotBlank(i.getSerialNo()))
                .collect(Collectors.toMap(
                        OrderItem::getItemId,
                        OrderItem::getSerialNo
                ));

        for (OrderItem item : orderItemsRepository.findByOrderId(orderId)) {
            if (item.getCategory() != ProductCategory.Services) {
                item.setSerialNo(serialByItemId.get(item.getItemId()));
                orderItemsRepository.save(item);
            }
        }

        return "redirect:/dashboard/orders/" + orderId;
    }

    @GetMapping("/dashboard/orders/{orderId}/address")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String showAddressDetails(@PathVariable String orderId, @RequestParam String type, Model model) {
        Order order = ordersRepository.findById(getStoreId(), orderId);
        model.addAttribute("order", order);
        model.addAttribute("type", type);
        return "orderAddressDetails";
    }

    @PostMapping("/dashboard/orders/{orderId}/updateAddressDetails")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String updateAddressDetails(@PathVariable String orderId, @RequestParam String type, @ModelAttribute("order") Order updatedOrder, RedirectAttributes redirectAttributes, Locale locale) {
        Order existingOrder = ordersRepository.findById(getStoreId(), orderId);
        if ("billing".equals(type) && updatedOrder.getBillingDetails() != null) {
            if (existingOrder.isInvoiced()) {
                redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("error.message.billing.details.locked", null, locale));
                return "redirect:/dashboard/orders/" + orderId;
            }
            existingOrder.setBillingDetails(updatedOrder.getBillingDetails());
        }
        if ("shipping".equals(type) && updatedOrder.getShippingDetails() != null) {
            existingOrder.setShippingDetails(updatedOrder.getShippingDetails());
        }
        ordersRepository.save(existingOrder);
        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/updateReview")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String updateReview(@PathVariable String orderId, @ModelAttribute("order") Order updatedOrder, Model model) {
        Order existingOrder = ordersRepository.findById(getStoreId(), orderId);
        if (updatedOrder.getReview() != null) {
            existingOrder.setReview(updatedOrder.getReview());
        }
        return save(existingOrder);
    }

    @PostMapping("/dashboard/orders/{orderId}/updatePayments")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String updatePayments(@PathVariable String orderId, @ModelAttribute("order") Order updatedOrder, Model model) {
        Order existingOrder = ordersRepository.findById(getStoreId(), orderId);
        if (updatedOrder.getPayments() != null) {
            List<Payment> payments = updatedOrder.getPayments().stream()
                    .filter(Payment::isComplete)
                    .collect(Collectors.toList());

            if (payments.isEmpty()) {
                payments.add(updatedOrder.getPayments().get(0));
            }

            existingOrder.setPayments(payments);
        }
        return save(existingOrder);
    }

    @PostMapping("/dashboard/orders/{orderId}/updateShipments")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String updateShipments(@PathVariable String orderId, @ModelAttribute("order") Order updatedOrder, Model model) {
        Order existingOrder = ordersRepository.findById(getStoreId(), orderId);
        if (updatedOrder.getShipments() != null) {
            List<Shipment> shipments = updatedOrder.getShipments().stream()
                    .filter(s -> s.hasShippingData() || s.hasCollectionData())
                    .collect(Collectors.toList());

            if (shipments.isEmpty()) {
                shipments.add(updatedOrder.getShipments().get(0));
            }

            existingOrder.setShipments(shipments);
        }
        return save(existingOrder);
    }

    @PostMapping("/dashboard/orders/{orderId}/addReceipt")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String addReceipt(@PathVariable String orderId, @ModelAttribute Document document) {
        Order order = ordersRepository.findById(getStoreId(), orderId);
        order.addDocument(document);
        return save(order);
    }

    @PostMapping("/dashboard/orders/{orderId}/markAsPaid")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String markOrderAsPaid(@PathVariable String orderId) {
        Order order = ordersRepository.findById(getStoreId(), orderId);
        order.markAsPaid();
        ordersRepository.save(order);
        return "redirect:/dashboard/payments";
    }

    @PostMapping("/dashboard/orders/{orderId}/cancelShipment")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String cancelShipment(@PathVariable String orderId,
                                 RedirectAttributes redirectAttributes, Locale locale) {
        try {
            shipmentCancelService.cancelShipping(orderId, getStoreId());
            redirectAttributes.addFlashAttribute("successMessage",
                    messageSource.getMessage("shipment.cancel.success", null, locale));
        } catch (HttpClientException ex) {
            return handleHttpClientException(ex, orderId, redirectAttributes);
        } catch (ShippingException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/dashboard/orders/" + orderId;
    }

    private String handleHttpClientException(HttpClientException ex, String orderId,
                                             RedirectAttributes redirectAttributes) {
        String error = ex.getResponseBody();
        error = Strings.isBlank(error) ? ex.getMessage() : error;
        redirectAttributes.addFlashAttribute("errorMessage", error);
        return "redirect:/dashboard/orders/" + orderId;
    }

    public String save(Order order) {
        orderLifecycle.update(order);
        return "redirect:/dashboard/orders/" + order.getOrderId();
    }

}
