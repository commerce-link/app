package pl.commercelink.web;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.ShipmentCarrierOptions;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.invoicing.InvoiceCreationEventPublisher;
import pl.commercelink.orders.*;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.orders.filters.model.OrderFilter;
import pl.commercelink.orders.filters.OrderFilterAccessDeniedException;
import pl.commercelink.orders.filters.OrderFilterInvalidException;
import pl.commercelink.orders.ListOpenOrdersHandler;
import pl.commercelink.orders.OrderStatusSelection;
import pl.commercelink.orders.filters.handlers.CreateOrderFilterHandler;
import pl.commercelink.orders.filters.handlers.DeleteOrderFilterHandler;
import pl.commercelink.orders.filters.FilterActor;
import pl.commercelink.orders.filters.handlers.ListOrderFiltersHandler;
import pl.commercelink.orders.filters.ShippingDue;
import pl.commercelink.orders.filters.handlers.UpdateOrderFilterHandler;
import pl.commercelink.orders.filters.VisibleOrderFilters;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.orders.imports.BasketOrderImporter;
import pl.commercelink.orders.pos.PosOrderCreator;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.pricelist.PricelistFinder;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.StoreCategories;
import pl.commercelink.rest.client.HttpClientException;
import pl.commercelink.shipping.ShipmentCancelService;
import pl.commercelink.shipping.api.ShippingException;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.GoodsOutEventPublisher;
import pl.commercelink.web.dtos.AddPaymentForm;
import pl.commercelink.web.dtos.ClientDataDto;
import pl.commercelink.web.dtos.OrderFilterForm;
import pl.commercelink.web.dtos.SavedOrderFilterView;
import pl.commercelink.web.dtos.OrderItemsForm;
import pl.commercelink.web.dtos.SplitGroupForm;
import pl.commercelink.web.dtos.SplitGroupPreviewDto;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import pl.commercelink.inventory.deliveries.DropshipItemLookup;

import java.util.*;
import java.util.stream.Collectors;

@Controller
public class OrdersController extends BaseController {

    @Autowired
    private ShipmentCarrierOptions shipmentCarrierOptions;

    @Autowired
    private Inventory inventory;

    @Autowired
    private ProductCatalogRepository productCatalogRepository;

    @Autowired
    private StoreCategories storeCategories;

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
    private PricelistFinder pricelistFinder;

    @Autowired
    private InvoiceCreationEventPublisher invoiceCreationEventPublisher;

    @Autowired
    private BasketOrderImporter basketOrderImporter;

    @Autowired
    private PosOrderCreator posOrderCreator;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private OrderEventsRepository orderEventsRepository;

    @Autowired
    private ShipmentCancelService shipmentCancelService;

    @Autowired
    private GoodsOutEventPublisher goodsOutEventPublisher;

    @Autowired
    private TaxonomyCache taxonomyCache;

    @Autowired
    private OrderLifecycleEventPublisher orderLifecycleEventPublisher;
    @Autowired
    private DropshipItemLookup dropshipItemLookup;

    @Autowired
    private ListOrderFiltersHandler listOrderFilters;

    @Autowired
    private CreateOrderFilterHandler createOrderFilter;

    @Autowired
    private UpdateOrderFilterHandler updateOrderFilter;

    @Autowired
    private DeleteOrderFilterHandler deleteOrderFilter;

    @Autowired
    private ListOpenOrdersHandler listOpenOrders;

    @GetMapping("/dashboard/orders")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String orders(@RequestParam(required = false) List<String> statuses,
                        @RequestParam(required = false, defaultValue = "false") boolean showAll,
                        @RequestParam(required = false) String filterKey,
                        Model model) {
        VisibleOrderFilters savedFilters = listOrderFilters.handle(actor());
        OrderFilter selectedFilter = savedFilters.byKey(filterKey).orElse(null);

        List<Order> openOrders = listOpenOrders.handle(getStoreId(), selectedFilter);

        boolean statusChosenExplicitly = statuses != null && !statuses.isEmpty();
        boolean showEveryStatus = showAll || (selectedFilter != null && !statusChosenExplicitly);
        OrderStatusSelection statusSelection = OrderStatusSelection.resolve(openOrders, statuses, showEveryStatus);
        List<Order> filteredOrders = statusSelection.narrow(openOrders);

        Arrays.stream(OrderStatus.values()).forEach(s -> model.addAttribute(s.name() + "Status", s));

        model.addAttribute("liveOrders", filteredOrders);
        model.addAttribute("ordersByStatus", filteredOrders.stream().collect(Collectors.groupingBy(Order::getStatus)));
        model.addAttribute("itemCountsByStatus",
                openOrders.stream().collect(Collectors.groupingBy(Order::getStatus, Collectors.counting())));
        model.addAttribute("statuses", Arrays.stream(OrderStatus.values())
                .filter(status -> status != OrderStatus.Completed && status != OrderStatus.Cancelled)
                .toList());
        model.addAttribute("selectedStatuses", statusSelection.selected());
        model.addAttribute("savedFilters", savedFilters.all().stream().map(SavedOrderFilterView::of).toList());
        model.addAttribute("selectedFilterKey", selectedFilter == null ? null : selectedFilter.getFilterKey());
        model.addAttribute("canManageStoreFilters", isAdmin());
        model.addAttribute("shipmentTypes", ShipmentType.values());
        model.addAttribute("paymentSources", PaymentSource.values());
        model.addAttribute("shippingDueOptions", ShippingDue.values());
        return "orders";
    }

    @PostMapping("/dashboard/orders/filters")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String createOrderFilter(OrderFilterForm form, RedirectAttributes redirectAttributes) {
        OrderFilter created = createOrderFilter.handle(
                actor(), form.isSharedWithStore(), form.getLabel(), form.toConditions());
        redirectAttributes.addAttribute("filterKey", created.getFilterKey());
        return "redirect:/dashboard/orders";
    }

    @PostMapping("/dashboard/orders/filters/update")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String updateOrderFilter(@RequestParam String filterKey, OrderFilterForm form,
                                    RedirectAttributes redirectAttributes) {
        OrderFilter updated = updateOrderFilter.handle(actor(), filterKey, form.getLabel(), form.toConditions());
        redirectAttributes.addAttribute("filterKey", updated.getFilterKey());
        return "redirect:/dashboard/orders";
    }

    @PostMapping("/dashboard/orders/filters/delete")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String deleteOrderFilter(@RequestParam String filterKey) {
        deleteOrderFilter.handle(actor(), filterKey);
        return "redirect:/dashboard/orders";
    }

    @ExceptionHandler({OrderFilterAccessDeniedException.class, OrderFilterInvalidException.class})
    public String orderFilterRejected(RuntimeException e, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", e.getMessage());
        return "redirect:/dashboard/orders";
    }

    private FilterActor actor() {
        return new FilterActor(getStoreId(), getUserId(), isAdmin());
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
    public String submitOrder(@ModelAttribute ClientDataDto dto) {
        Order order = basketOrderImporter._import(getStoreId(), dto);
        return "redirect:/dashboard/orders/" + order.getOrderId();
    }

    @PostMapping("/dashboard/orders/new/pos")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String createPosOrder(Locale locale, RedirectAttributes redirectAttributes) {
        OperationResult<Order> result = posOrderCreator.create(getStoreId(), locale);
        if (!result.isSuccess()) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage(result.getMessage(), null, locale));
            return "redirect:/dashboard/orders";
        }
        return "redirect:/dashboard/orders/" + result.getPayload().getOrderId();
    }

    @GetMapping("/dashboard/orders/{orderId}")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String getOrderDetails(@PathVariable("orderId") String orderId, Model model) {
        Order existingOrder = ordersRepository.findById(getStoreId(), orderId);
        return showOrderDetails(existingOrder, model);
    }

    @GetMapping("/dashboard/store/{storeId}/orders/{orderId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String getOrderDetailsForSuperAdmin(@PathVariable("storeId") String storeId, @PathVariable("orderId") String orderId, Model model) {
        Order existingOrder = ordersRepository.findById(storeId, orderId);
        return showOrderDetails(existingOrder, model);
    }

    @PostMapping("/dashboard/orders/{orderId}/add-item/pricelist")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String addOrderItemFromPriceList(@PathVariable String orderId,
                                            @RequestParam String catalogId, @RequestParam String pimId,
                                            @RequestParam(defaultValue = "1") int qty, @RequestParam int position) {
        Store store = storesRepository.findById(getStoreId());
        Order order = ordersRepository.findById(getStoreId(), orderId);

        AvailabilityAndPrice availabilityAndPrice = pricelistFinder.findByPimId(getStoreId(), catalogId, pimId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        ordersManager.addOrderItem(store, order, availabilityAndPrice, qty, position);
        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/add-item/inventory")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String addOrderItemFromInventory(@PathVariable String orderId,
                                            @RequestParam(required = false, defaultValue = "") String itemEan,
                                            @RequestParam(required = false, defaultValue = "") String itemManufacturerCode,
                                            @RequestParam(defaultValue = "1") int qty, @RequestParam int position) {
        Store store = storesRepository.findById(getStoreId());
        Order order = ordersRepository.findById(getStoreId(), orderId);

        MatchedInventory matchedInventory = inventory.withEnabledSuppliersOnly(getStoreId())
                .findByInventoryKey(new InventoryKey(itemEan.trim(), itemManufacturerCode.trim()));
        ordersManager.addOrderItem(store, order, matchedInventory, qty, position);

        return "redirect:/dashboard/orders/" + orderId;
    }

    private String showOrderDetails(Order order, Model model) {
        return showOrderDetails(order, orderItemsRepository.findByOrderId(order.getOrderId()), model);
    }

    private String resolveTaxonomyName(String mfn) {
        Taxonomy taxonomy = taxonomyCache.findByMfn(mfn);
        return taxonomy != null && taxonomy.name() != null ? taxonomy.name() : "";
    }

    private String showOrderDetails(Order order, List<OrderItem> orderItems, Model model) {
        List<ProductCatalog> catalogs = productCatalogRepository.findAll(order.getStoreId());

        Store store = storesRepository.findById(order.getStoreId());

        List<DocumentType> manualDocumentTypes = order.isB2B()
                ? Arrays.asList(DocumentType.InvoiceVat, DocumentType.InvoiceAdvance, DocumentType.InvoiceFinal)
                : Arrays.asList(DocumentType.Receipt, DocumentType.InvoicePersonal);

        List<OrderItem> serialUpdateItems = orderItems.stream()
                .filter(i -> i.hasOneOfTheStatuses(FulfilmentStatus.Delivered))
                .filter(OrderItem::isProduct)
                .collect(Collectors.toList());

        Map<String, SplitGroupPreviewDto> splitGroupPreviews = orderItems.stream()
                .filter(OrderItem::isNew)
                .filter(OrderItem::isGroup)
                .collect(Collectors.toMap(OrderItem::getItemId, i -> SplitGroupPreviewDto.from(i, this::resolveTaxonomyName)));

        model.addAttribute("order", order);
        model.addAttribute("orderEvents", orderEventsRepository.findByOrderId(order.getOrderId()));
        model.addAttribute("orderItemsForm", new OrderItemsForm(orderItems));
        model.addAttribute("serialUpdateItems", serialUpdateItems);
        model.addAttribute("splitGroupPreviews", splitGroupPreviews);
        model.addAttribute("orderFinancials", new OrderFinancials(order, orderItems));
        model.addAttribute("orderStatuses", Arrays.stream(OrderStatus.values())
                .filter(status -> (status != OrderStatus.Completed || order.getStatus() == OrderStatus.Completed)
                        && (status != OrderStatus.Cancelled || order.getStatus() == OrderStatus.Cancelled))
                .collect(Collectors.toList()));
        model.addAttribute("orderReviewStatuses", OrderReviewStatus.values());
        model.addAttribute("receiptTypes", manualDocumentTypes);
        model.addAttribute("paymentSources", PaymentSource.values());
        model.addAttribute("pendingPayment", order.getPayments().stream()
                .filter(Payment::isUnsettled)
                .findFirst()
                .orElse(null));
        model.addAttribute("shipmentTypes", ShipmentType.values());
        model.addAttribute("carrierOptions", shipmentCarrierOptions.forOrder(order, store));
        model.addAttribute("fulfilmentStatuses", FulfilmentStatus.values());
        model.addAttribute("fulfilmentTypes", FulfilmentType.values());
        model.addAttribute("isCompletedOrder", order.hasOneOfStatuses(OrderStatus.Completed, OrderStatus.Cancelled) || isSuperAdmin());
        model.addAttribute("isNewOrder", order.getStatus() == OrderStatus.New);
        model.addAttribute("canOrderShipment", !order.getStatus().isOneOf(OrderStatus.New, OrderStatus.Blocked, OrderStatus.Assembly));
        model.addAttribute("canDeleteOrder", order.hasStatus(OrderStatus.New) && orderItems.isEmpty() && !order.isInvoiced());
        model.addAttribute("canCancelOrder", order.canBeCancelled(orderItems));
        model.addAttribute("canSplitOrder", order.canBeSplit() && orderItems.size() > 1);
        model.addAttribute("fulfilmentTypeLocked", !order.canChangeFulfilmentType(orderItems));
        model.addAttribute("hasWarehouseDocument", order.getDocumentByType(DocumentType.GoodsIssue).isPresent());
        Set<String> dropshipItemIds = dropshipItemLookup.itemIdsInDropshipDeliveries(order.getStoreId(), orderItems);
        model.addAttribute("hasDropshipItems", !dropshipItemIds.isEmpty());
        model.addAttribute("hasWarehouseItems", orderItems.stream()
                .filter(OrderItem::isProduct)
                .anyMatch(item -> !dropshipItemIds.contains(item.getItemId())));
        model.addAttribute("hasWarehouseDocumentsEnabled", store.hasDocumentsGenerationEnabled());
        model.addAttribute("isInvoiced", order.isInvoiced());
        model.addAttribute("isSuperAdmin", isSuperAdmin());
        model.addAttribute("isAdmin", isAdmin());

        model.addAttribute("catalogs", catalogs);

        DocumentType nextDocumentToIssue = order.getNextDocumentToIssue().orElse(null);
        model.addAttribute("nextInvoiceToIssue", nextDocumentToIssue);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("canAddDocumentManually", manualDocumentTypes.contains(nextDocumentToIssue));
        model.addAttribute("issuableDocumentTypes", order.getIssuableDocumentTypes());

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
    public String createInvoice(@PathVariable String orderId, @RequestParam DocumentType documentType, @RequestParam(defaultValue = "false") boolean send, Locale locale, RedirectAttributes redirectAttributes) {
        Order order = ordersRepository.findById(getStoreId(), orderId);

        if (!order.getIssuableDocumentTypes().contains(documentType)) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("error.message.no.eligible.invoice.to.create", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }

        invoiceCreationEventPublisher.publish(order, documentType, send);
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

        if (updatedOrder.getStatus() == OrderStatus.Cancelled && existingOrder.getStatus() != OrderStatus.Cancelled) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("error.message.cancelled.cannot.be.set.manually", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }

        FulfilmentType requestedFulfilmentType = updatedOrder.getFulfilmentType();
        boolean fulfilmentTypeChanged = requestedFulfilmentType != null
                && requestedFulfilmentType != existingOrder.getFulfilmentType();
        if (fulfilmentTypeChanged
                && !existingOrder.canChangeFulfilmentType(orderItemsRepository.findByOrderId(orderId))) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("order.fulfilment.type.locked", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }

        existingOrder.setStatus(updatedOrder.getStatus());
        existingOrder.setEmailNotificationsEnabled(updatedOrder.isEmailNotificationsEnabled());
        existingOrder.setEstimatedAssemblyAt(updatedOrder.getEstimatedAssemblyAt());
        existingOrder.setEstimatedShippingAt(updatedOrder.getEstimatedShippingAt());
        existingOrder.setAffiliateId(updatedOrder.getAffiliateId());
        existingOrder.setGclid(updatedOrder.getGclid());
        existingOrder.setComment(updatedOrder.getComment());
        if (fulfilmentTypeChanged) {
            existingOrder.setFulfilmentType(requestedFulfilmentType);
        }
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

            boolean wasService = orderItem.isService();
            boolean serviceFlagLocked = orderItem.hasSupplierAllocation();
            boolean priceLocked = !order.getDocuments().isEmpty();

            if (StringUtils.isBlank(updatedItem.getCategory())) {
                updatedItem.setCategory(null);
            }
            if (serviceFlagLocked) {
                updatedItem.setService(orderItem.isService());
            }
            if (priceLocked) {
                updatedItem.setPrice(orderItem.getPrice());
            }
            orderItem.update(updatedItem);

            if (!wasService && orderItem.isService()) {
                orderItem.markAsWarehouseFulfilled();
                if (orderItem.getPosition() < PositionGroup.SERVICE_GROUP_START) {
                    orderItem.setPosition(PositionGroup.SERVICE_GROUP_START + orderItem.getPosition());
                }
            } else if (wasService && !orderItem.isService()) {
                if (OrderItem.GENERIC_WAREHOUSE_ORDER_NO.equals(orderItem.getDeliveryId())) {
                    orderItem.setDeliveryId(null);
                    orderItem.setStatus(FulfilmentStatus.New);
                }
                if (orderItem.getPosition() >= PositionGroup.SERVICE_GROUP_START && orderItem.getPosition() < PositionGroup.DELIVERY_POSITION) {
                    orderItem.setPosition(orderItem.getPosition() - PositionGroup.SERVICE_GROUP_START);
                }
            }

            orderItemsRepository.save(orderItem);

            order.setTotalPrice(new OrderFinancials(order, orderItems).getTotalPrice());
            orderLifecycle.update(order, orderItems);
        }

        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/assign-supplier")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String assignSupplier(@PathVariable String orderId, @RequestParam String itemId,
                                 @RequestParam String manufacturerCode, @RequestParam double cost,
                                 @RequestParam String supplier, Model model,
                                 RedirectAttributes redirectAttributes, Locale locale) {
        Order order = ordersRepository.findById(getStoreId(), orderId);
        OrderItem orderItem = orderItemsRepository.findById(orderId, itemId);

        if (!orderItem.isReleasable()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("order.item.assign.supplier.blocked", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }

        orderItem.setManufacturerCode(manufacturerCode);
        orderItem.setCost(cost);
        orderItem.setDeliveryId(supplier);

        Taxonomy taxonomy = taxonomyCache.findByMfn(orderItem.getManufacturerCode());
        String resolvedEan = taxonomy != null ? taxonomy.ean() : null;

        if (Strings.isBlank(resolvedEan)) {
            model.addAttribute("errorMessage", messageSource.getMessage("order.item.ean.not.found", null, locale));
            return showOrderItemDetails(order, orderItem, model);
        }

        orderItem.setEan(resolvedEan);
        orderItem.markAsInAllocation();
        orderItemsRepository.save(orderItem);

        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/clear-supplier")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String clearSupplier(@PathVariable String orderId, @RequestParam String itemId,
                                RedirectAttributes redirectAttributes, Locale locale) {
        OrderItem orderItem = orderItemsRepository.findById(orderId, itemId);
        if (!orderItem.isReleasable()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("order.item.clear.assign.blocked", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }
        orderItem.removeFulfilment();
        orderItemsRepository.save(orderItem);
        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/assign-sku")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String assignSku(@PathVariable String orderId, @RequestParam String itemId, @RequestParam String sku) {
        OrderItem orderItem = orderItemsRepository.findById(orderId, itemId);
        orderItem.setSku(sku);
        orderItemsRepository.save(orderItem);
        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/assign-warehouse")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String assignFromWarehouse(@PathVariable String orderId, @RequestParam String itemId,
                                      @RequestParam String warehouseItemId,
                                      RedirectAttributes redirectAttributes, Locale locale) {
        try {
            ordersManager.assignFromWarehouse(getStoreId(), orderId, itemId, warehouseItemId);
        } catch (IllegalStateException e) {
            String code = "error.message." + e.getMessage();
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage(code, null, locale));
        }
        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/split-group")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String splitGroupItem(@PathVariable String orderId, @ModelAttribute SplitGroupForm form,
                                 RedirectAttributes redirectAttributes, Locale locale) {
        try {
            ordersManager.splitGroupItem(orderId, form.getItemId(), form.toComponents());
        } catch (IllegalStateException e) {
            String code = "error.message." + e.getMessage();
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage(code, null, locale));
        }
        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/toggle-consolidation")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String toggleConsolidation(@PathVariable String orderId, @RequestParam String itemId) {
        OrderItem orderItem = orderItemsRepository.findById(orderId, itemId);
        orderItem.toggleConsolidation();
        orderItemsRepository.save(orderItem);
        return "redirect:/dashboard/orders/" + orderId;
    }

    private String showOrderItemDetails(Order order, OrderItem orderItem, Model model) {
        model.addAttribute("orderId", order.getOrderId());
        model.addAttribute("orderItem", orderItem);
        model.addAttribute("categories", storeCategories.namesFor(order.getStoreId()));
        model.addAttribute("categoryGroups", storeCategories.groupsFor(order.getStoreId()));
        model.addAttribute("fulfilmentStatuses", FulfilmentStatus.values());
        model.addAttribute("isCompletedOrder", order.hasOneOfStatuses(OrderStatus.Completed, OrderStatus.Cancelled));
        model.addAttribute("serviceFlagLocked", orderItem.hasSupplierAllocation());
        model.addAttribute("priceLocked", !order.getDocuments().isEmpty());

        return "orderItem";
    }

    @PostMapping("/dashboard/orders/{orderId}/delete")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String deleteOrder(@PathVariable String orderId) {
        ordersManager.deleteOrder(getStoreId(), orderId);
        return "redirect:/dashboard/orders";
    }

    @PostMapping("/dashboard/orders/{orderId}/cancel")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String cancelOrder(@PathVariable String orderId, RedirectAttributes redirectAttributes, Locale locale) {
        try {
            ordersManager.cancelOrder(getStoreId(), orderId);
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("error.message.order.cannot.be.cancelled", null, locale));
        }
        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/removeSelectedItemsFromOrder")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String removeSelectedItemsFromOrder(@PathVariable String orderId, @ModelAttribute OrderItemsForm form) {
        ordersManager.removeFromOrder(getStoreId(), orderId, form.getSelectedOrderItemIds());
        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/moveSelectedItemsToAllocation")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String moveSelectedItemsToAllocation(@PathVariable String orderId, @ModelAttribute OrderItemsForm form) {
        ordersManager.moveItemsToAllocation(getStoreId(), orderId, form.getSelectedOrderItemIds());
        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/moveSelectedItemsToTheWarehouse")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String moveSelectedItemsToTheWarehouse(@PathVariable String orderId, @ModelAttribute OrderItemsForm form,
                                                  RedirectAttributes redirectAttributes, Locale locale) {
        OrdersManager.Result result = ordersManager.moveOrderItemsToTheWarehouse(getStoreId(), orderId, form.getSelectedOrderItemIds());
        flashSkippedDropshipItems(result, redirectAttributes, locale);
        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/moveSelectedItemsToTheWarehouseForRMA")
    public String moveSelectedItemsToTheWarehouseForRMA(@PathVariable String orderId, @ModelAttribute OrderItemsForm form,
                                                        RedirectAttributes redirectAttributes, Locale locale) {
        OrdersManager.Result result = ordersManager.moveOrderItemsToTheWarehouseForRMA(getStoreId(), orderId, form.getSelectedOrderItemIds());
        flashSkippedDropshipItems(result, redirectAttributes, locale);
        return "redirect:/dashboard/orders/" + orderId;
    }

    private void flashSkippedDropshipItems(OrdersManager.Result result, RedirectAttributes redirectAttributes, Locale locale) {
        if (result.getSkippedDropshipItems() > 0) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("order.items.action.move.warehouse.dropship.error", null, locale));
        }
    }

    @PostMapping("/dashboard/orders/{orderId}/splitOrder")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String splitOrder(@PathVariable String orderId, @ModelAttribute OrderItemsForm form,
                             RedirectAttributes redirectAttributes, Locale locale) {
        try {
            Order newOrder = ordersManager.splitOrder(getStoreId(), orderId, form.getSelectedOrderItemIds());
            return "redirect:/dashboard/orders/" + newOrder.getOrderId();
        } catch (IllegalStateException e) {
            String code = "error.message." + e.getMessage();
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage(code, null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }
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
            if (item.isProduct()) {
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

    @PostMapping("/dashboard/orders/{orderId}/addPayment")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String addPayment(@PathVariable String orderId,
                             @ModelAttribute AddPaymentForm form,
                             RedirectAttributes redirectAttributes,
                             Locale locale) {
        Order existingOrder = ordersRepository.findById(getStoreId(), orderId);

        if (form.getBankAmount() == 0) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("error.message.payment.amount.invalid", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }

        if (form.getProcessingFee() < 0) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("error.message.payment.fee.invalid", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }

        double amount = form.isFeeIncluded()
                ? form.getBankAmount()
                : form.getBankAmount() + form.getProcessingFee();

        Payment target = existingOrder.getPayments().stream()
                .filter(Payment::isUnsettled)
                .findFirst()
                .orElseGet(() -> {
                    Payment p = new Payment();
                    existingOrder.addPayment(p);
                    return p;
                });

        target.setSource(form.getSource());
        target.setDirection(form.getDirection() != null ? form.getDirection() : PaymentDirection.Incoming);
        target.setReferenceNo(form.getReferenceNo());
        target.setName(form.getName());
        target.setAmount(amount);
        target.setFee(form.getProcessingFee());
        target.setBankTransactionNo(form.getBankTransactionNo());
        target.setBankTransactionDate(form.getBankTransactionDate());

        return save(existingOrder);
    }

    @PostMapping("/dashboard/orders/{orderId}/updateShipments")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String updateShipments(@PathVariable String orderId, @ModelAttribute("order") Order updatedOrder, Model model) {
        Order existingOrder = ordersRepository.findById(getStoreId(), orderId);
        // OrderLifecycle.update never persists cancelled orders, so publishing here
        // would announce a shipment change that was never saved
        if (existingOrder.getStatus() == OrderStatus.Cancelled) {
            return "redirect:/dashboard/orders/" + orderId;
        }
        List<String> shipmentDataBeforeUpdate = shipmentDataSnapshot(existingOrder);
        if (updatedOrder.getShipments() != null) {
            List<Shipment> shipments = updatedOrder.getShipments().stream()
                    .filter(s -> s.hasShippingData() || s.hasCollectionData() || s.isDeliveredToCollectionPoint())
                    .collect(Collectors.toList());

            if (shipments.isEmpty()) {
                shipments.add(updatedOrder.getShipments().get(0));
            }

            existingOrder.replaceShipments(shipments);
        }
        String view = save(existingOrder);
        boolean hasNotifiableShipmentData = existingOrder.getShipments().stream()
                .anyMatch(s -> s.hasShippingData() || s.hasCollectionData());
        boolean shipmentDataChanged = !shipmentDataBeforeUpdate.equals(shipmentDataSnapshot(existingOrder));
        if (hasNotifiableShipmentData && shipmentDataChanged) {
            orderLifecycleEventPublisher.publish(existingOrder, OrderLifecycleEventType.ShipmentCreated);
        }
        return view;
    }

    private List<String> shipmentDataSnapshot(Order order) {
        return order.getShipments().stream()
                .map(s -> String.join("|",
                        String.valueOf(s.getType()),
                        Objects.toString(s.getCarrier(), ""),
                        Objects.toString(s.getTrackingNo(), ""),
                        Objects.toString(s.getTrackingUrl(), ""),
                        // the shipments form round-trips shippedAt at minute precision,
                        // so sub-minute digits must not count as a data change
                        s.getShippedAt() == null ? "" : s.getShippedAt().truncatedTo(ChronoUnit.MINUTES).toString()))
                .collect(Collectors.toList());
    }

    @PostMapping("/dashboard/orders/{orderId}/addReceipt")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String addReceipt(@PathVariable String orderId, @ModelAttribute Document document) {
        Order order = ordersRepository.findById(getStoreId(), orderId);
        order.addDocument(document);
        return save(order);
    }

    @PostMapping("/dashboard/orders/{orderId}/removeDocument")
    @PreAuthorize("hasRole('ADMIN')")
    public String removeDocument(@PathVariable String orderId, @RequestParam DocumentType type,
                                 @RequestParam(required = false) String number,
                                 RedirectAttributes redirectAttributes, Locale locale) {
        Order order = ordersRepository.findById(getStoreId(), orderId);

        if (order.hasOneOfStatuses(OrderStatus.Completed, OrderStatus.Cancelled) || !order.removeDocument(type, number)) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("error.message.document.cannot.be.removed", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }

        // saving via OrderLifecycle would re-trigger automatic invoice generation for delivered orders
        ordersRepository.save(order);
        return "redirect:/dashboard/orders/" + orderId;
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
