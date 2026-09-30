package pl.commercelink.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.invoicing.InvoiceCreationEventPublisher;
import pl.commercelink.orders.*;
import pl.commercelink.orders.filters.model.OrderFilter;
import pl.commercelink.orders.filters.exceptions.OrderFilterException;

import pl.commercelink.orders.filters.FilterActor;
import pl.commercelink.orders.filters.OrderFilterField;
import pl.commercelink.orders.filters.services.OrderFiltersService;

import pl.commercelink.orders.filters.ShippingDue;
import pl.commercelink.orders.filters.services.ListOrderFiltersView;
import pl.commercelink.orders.fulfilment.ExternalSupplierBinding;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.orders.imports.BasketOrderImporter;
import pl.commercelink.orders.pos.PosOrderCreator;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.pricelist.PricelistFinder;
import pl.commercelink.products.StoreCategories;
import pl.commercelink.receipts.ReceiptAttempt;
import pl.commercelink.receipts.ReceiptAttemptService;
import pl.commercelink.receipts.ReceiptLock;
import pl.commercelink.rest.client.HttpClientException;
import pl.commercelink.shipping.ShipmentCancelService;
import pl.commercelink.shipping.ShipmentTrackingSubscriber;
import pl.commercelink.shipping.api.ShippingException;
import pl.commercelink.starter.dynamodb.OptimisticLockingExhaustedException;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.GoodsOutEventPublisher;
import pl.commercelink.web.dtos.AddItemsForm;
import pl.commercelink.web.dtos.AddPaymentForm;
import pl.commercelink.web.dtos.AssignSupplierForm;
import pl.commercelink.web.dtos.FormNumbers;
import pl.commercelink.web.dtos.ClientDataDto;
import pl.commercelink.web.dtos.OrderFilterForm;
import pl.commercelink.web.dtos.OrderItemsForm;
import pl.commercelink.web.dtos.SplitGroupForm;
import pl.commercelink.starter.util.ConversionUtil;
import pl.commercelink.web.orders.BulkAction;
import pl.commercelink.web.orders.BulkActionResult;
import pl.commercelink.web.orders.BulkReason;
import pl.commercelink.web.orders.CustomerView;
import pl.commercelink.web.orders.ItemSaleLock;
import pl.commercelink.web.orders.Money;
import pl.commercelink.web.orders.MoveTargetView;
import pl.commercelink.web.orders.OrderAddressForm;
import pl.commercelink.web.orders.OrderBackLink;
import pl.commercelink.web.orders.OrderConfirmPages;
import pl.commercelink.web.orders.OrderFlash;
import pl.commercelink.web.orders.OrderLabels;
import pl.commercelink.web.orders.OrderPaymentForm;
import pl.commercelink.web.orders.OrderShipmentForm;
import pl.commercelink.web.orders.OrderLinks;
import pl.commercelink.web.orders.OrderPrintView;
import pl.commercelink.web.orders.OrderNotice;
import pl.commercelink.web.orders.OrderPageModel;
import pl.commercelink.web.orders.OrderPageModelFactory;
import pl.commercelink.web.orders.OrderItemRow;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.DeliveryRedirectResolver;
import pl.commercelink.web.orders.OrderStatusOptions;
import pl.commercelink.web.settings.SettingsPaths;
import pl.commercelink.web.orders.OrderListQuery;
import pl.commercelink.web.settings.ConfirmAction;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import pl.commercelink.inventory.deliveries.DropshipItemLookup;
import pl.commercelink.inventory.supplier.SupplierChoice;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

@Controller
public class OrdersController extends BaseController {

    @Autowired
    private Inventory inventory;

    @Autowired
    private StoreCategories storeCategories;

    @Autowired
    private OrdersRepository ordersRepository;

    @Autowired
    private OrderItemsRepository orderItemsRepository;

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private ShipmentCarrierOptions shipmentCarrierOptions;

    @Autowired
    private SupplierLabels supplierLabels;

    @Autowired
    private SupplierChoice supplierChoice;

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
    private ShipmentCancelService shipmentCancelService;

    @Autowired
    private OrderEventsRepository orderEventsRepository;

    @Autowired
    private GoodsOutEventPublisher goodsOutEventPublisher;

    @Autowired
    private TaxonomyCache taxonomyCache;

    @Autowired
    private OrderLifecycleEventPublisher orderLifecycleEventPublisher;
    @Autowired
    private DropshipItemLookup dropshipItemLookup;
    @Autowired
    private ShipmentTrackingSubscriber shipmentTrackingSubscriber;

    @Autowired
    private OrderFiltersService orderFilters;

    @Autowired
    private OrderListService orderListService;

    @Autowired
    private OrderPageModelFactory pageModelFactory;

    @Autowired
    private DeliveryRedirectResolver deliveryRedirectResolver;

    @Autowired
    private DeliveriesRepository deliveriesRepository;

    @Autowired
    private OrderReferenceResolver orderReferenceResolver;

    @Autowired
    private ReceiptAttemptService receiptAttemptService;

    @GetMapping("/dashboard/orders")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String orders(@RequestParam MultiValueMap<String, String> params, Locale locale, Model model) {
        Optional<String> legacy = OrderListQuery.legacyRedirect(params);
        if (legacy.isPresent()) {
            return "redirect:" + legacy.get();
        }
        if (OrderListQuery.isEntry(params)) {
            Optional<String> start = orderListService.defaultFilterHref(actor());
            if (start.isPresent()) {
                return "redirect:" + start.get();
            }
        }
        OrderListQuery query = OrderListQuery.parse(params);
        addListAttributes(model, query, locale);
        return "orders/list";
    }

    @GetMapping("/dashboard/orders/list")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String ordersList(@RequestParam MultiValueMap<String, String> params, Locale locale, Model model) {
        addListAttributes(model, OrderListQuery.parse(params), locale);
        return "orders/list :: results";
    }

    private void addListAttributes(Model model, OrderListQuery query, Locale locale) {
        addFilterFormAttributes(model, query.returnTo(), locale);
        model.addAttribute("page", orderListService.page(actor(), query, LocalDate.now(), locale));
    }

    static final String FILTERS_PATH = "/dashboard/orders/filters";

    @PostMapping("/dashboard/orders/filters")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String createOrderFilter(OrderFilterForm form, RedirectAttributes redirectAttributes, Model model, Locale locale,
                                    HttpServletResponse response) {
        return filterAction(form.getReturnTo(), redirectAttributes, model, locale, response, form, null, () -> {
            OrderFilter created = orderFilters.create(actor(), form.isSharedWithStore(), form.getLabel(), form.toConditions());
            if (form.isOpenByDefault()) {
                orderFilters.setDefault(actor(), created.getId());
            }
            return safeReturnTo(form.getReturnTo());
        });
    }

    @PostMapping("/dashboard/orders/filters/update")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String updateOrderFilter(@RequestParam String filterId, OrderFilterForm form, RedirectAttributes redirectAttributes,
                                    Model model, Locale locale, HttpServletResponse response) {
        return filterAction(form.getReturnTo(), redirectAttributes, model, locale, response, form, filterId, () -> {
            OrderFilter updated = orderFilters.update(actor(), filterId, form.isSharedWithStore(), form.getLabel(), form.toConditions());
            if (form.isOpenByDefault()) {
                orderFilters.setDefault(actor(), filterId);
            } else {
                orderFilters.clearDefault(actor(), filterId);
            }
            String target = safeReturnTo(form.getReturnTo());
            OrderListQuery list = parseReturnTo(listOf(target));
            if (!filterId.equals(list.filterId())) {
                return target;
            }
            // the list carries the statuses the filter ticked when it was chosen; after an edit they would be stale
            String listBack = list.withStatuses(OrderListService.filterStatuses(updated)).href();
            return target.startsWith(FILTERS_PATH) ? filtersPage(listBack) : listBack;
        });
    }

    /** "Ustaw jako domyślny" on the management page: the user's orders list opens with this filter from now on. */
    @PostMapping(FILTERS_PATH + "/default")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String setDefaultOrderFilter(@RequestParam String filterId, @RequestParam(required = false) String returnTo,
                                        RedirectAttributes redirectAttributes, Model model, Locale locale,
                                        HttpServletResponse response) {
        return filterAction(returnTo, redirectAttributes, model, locale, response, null, null, () -> {
            orderFilters.setDefault(actor(), filterId);
            return safeReturnTo(returnTo);
        });
    }

    /** "Nie otwieraj domyślnie": the user's orders list opens unfiltered again. */
    @PostMapping(FILTERS_PATH + "/default/clear")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String clearDefaultOrderFilter(@RequestParam String filterId, @RequestParam(required = false) String returnTo,
                                          RedirectAttributes redirectAttributes, Model model, Locale locale,
                                          HttpServletResponse response) {
        return filterAction(returnTo, redirectAttributes, model, locale, response, null, null, () -> {
            orderFilters.clearDefault(actor(), filterId);
            return safeReturnTo(returnTo);
        });
    }

    @PostMapping("/dashboard/orders/filters/delete")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String deleteOrderFilter(@RequestParam String filterId, @RequestParam(required = false) String returnTo,
                                    RedirectAttributes redirectAttributes, Model model, Locale locale,
                                    HttpServletResponse response) {
        return filterAction(returnTo, redirectAttributes, model, locale, response, null, null, () -> {
            orderFilters.delete(actor(), filterId);
            String target = safeReturnTo(returnTo);
            OrderListQuery list = parseReturnTo(listOf(target));
            String listBack = filterId.equals(list.filterId()) ? list.withFilterId(null).href() : list.href();
            return target.startsWith(FILTERS_PATH) ? filtersPage(listBack) : listBack;
        });
    }

    /** The no-JS confirmation page for the delete link (data-cl-confirm opens the shared dialog with JavaScript). */
    @GetMapping("/dashboard/orders/filters/delete")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String confirmDeleteOrderFilter(@RequestParam String filterId, @RequestParam(required = false) String returnTo,
                                           Locale locale, Model model) {
        OrderFilter filter = orderFilters.list(actor()).byId(filterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String back = safeReturnTo(returnTo);
        String actionPath = "/dashboard/orders/filters/delete?filterId=" + URLEncoder.encode(filter.getId(), StandardCharsets.UTF_8)
                + "&returnTo=" + URLEncoder.encode(back, StandardCharsets.UTF_8);
        model.addAttribute("confirm", new ConfirmAction(
                messageSource.getMessage("orders.filters.delete.title", new Object[]{filter.getLabel()}, locale),
                messageSource.getMessage("orders.filters.delete.message", null, locale),
                messageSource.getMessage("orders.filters.delete.action", null, locale),
                actionPath, back));
        // the way back names where "Anuluj" goes: the management page, or the list for an older link
        model.addAttribute("backLabel", messageSource.getMessage(back.startsWith(FILTERS_PATH) ? "orders.filters.edit.back"
                : "orders.filters.page.back", null, locale));
        return "settings-confirm";
    }

    /**
     * Filter management as its own page (design system: a list of records in a card, editing on a subpage). {@code returnTo}
     * is the list address the user came from; "‹ Zamówienia" goes back to it, and every form on this page and its subpages
     * returns here, to {@link #filtersPage(String)}.
     */
    @GetMapping(FILTERS_PATH)
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String orderFiltersPage(@RequestParam(required = false) String returnTo, Locale locale, Model model) {
        String list = safeListReturnTo(returnTo);
        addFilterFormAttributes(model, filtersPage(list), locale);
        model.addAttribute("listHref", list);
        return "orders/filters";
    }

    /** "Nowy filtr" from the management page: the empty form as a subpage. */
    @GetMapping(FILTERS_PATH + "/add")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String addOrderFilterPage(@RequestParam(required = false) String returnTo, Locale locale, Model model) {
        addFilterEditAttributes(model, safeListReturnTo(returnTo), null, new OrderFilterForm(), locale);
        return "orders/filter-edit";
    }

    /** "Edytuj" from the management page: the filter's form as a subpage; a filter the user cannot see is a 404. */
    @GetMapping(FILTERS_PATH + "/{filterId}/edit")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String editOrderFilterPage(@PathVariable String filterId, @RequestParam(required = false) String returnTo,
                                      Locale locale, Model model) {
        ListOrderFiltersView filters = orderFilters.list(actor());
        OrderFilter filter = filters.byId(filterId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        boolean shared = filters.sharedWithStore().stream().anyMatch(f -> f.getId().equals(filterId));
        if (shared && !isAdmin()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        OrderFilterForm form = formOf(filter, shared);
        form.setOpenByDefault(filters.isDefault(filterId));
        addFilterEditAttributes(model, safeListReturnTo(returnTo), filterId, form, locale);
        return "orders/filter-edit";
    }

    private void addFilterEditAttributes(Model model, String list, String filterId, OrderFilterForm form, Locale locale) {
        addFilterFormAttributes(model, filtersPage(list), locale);
        model.addAttribute("listHref", list);
        model.addAttribute("filterId", filterId);
        model.addAttribute("filterForm", form);
        model.addAttribute("formAction", filterId == null ? FILTERS_PATH : FILTERS_PATH + "/update");
        model.addAttribute("pageTitle", filterId == null
                ? messageSource.getMessage("orders.filters.new", null, locale)
                : messageSource.getMessage("orders.filters.edit.title", new Object[]{form.getLabel()}, locale));
    }

    private static OrderFilterForm formOf(OrderFilter filter, boolean shared) {
        Map<String, List<String>> byField = filter.getConditionsByField();
        OrderFilterForm form = new OrderFilterForm();
        form.setLabel(filter.getLabel());
        form.setSharedWithStore(shared);
        form.setStatus(byField.get(OrderFilterField.Status.name()));
        form.setShipmentType(byField.get(OrderFilterField.ShipmentType.name()));
        form.setPaymentSource(byField.get(OrderFilterField.PaymentSource.name()));
        form.setShippingDue(first(byField, OrderFilterField.ShippingDue));
        form.setSourceName(byField.get(OrderFilterField.SourceName.name()));
        form.setShippingPostalCode(first(byField, OrderFilterField.ShippingPostalCode));
        return form;
    }

    private static String first(Map<String, List<String>> byField, OrderFilterField field) {
        return byField.getOrDefault(field.name(), List.of()).stream().findFirst().orElse(null);
    }

    @InitBinder("orderFilterForm")
    void bindFilterForm(WebDataBinder binder) {
        OrdersControllerBinding.bindFilterForm(binder);
    }

    /**
     * Runs a filter change and redirects to where the user came from. A rejected create or update ({@code form} given)
     * re-renders the filter subpage with a 422, the rejection and what the user typed; a rejected delete goes back as a
     * flash for the page it returns to.
     */
    private String filterAction(String returnTo, RedirectAttributes redirectAttributes, Model model, Locale locale,
                                HttpServletResponse response, OrderFilterForm form, String filterId, Supplier<String> action) {
        String rejection;
        try {
            return "redirect:" + action.get();
        } catch (OrderFilterException rejected) {
            rejection = messageSource.getMessage(rejected.getMessageKey(), rejected.getMessageArguments(), locale);
        } catch (OptimisticLockingExhaustedException e) {
            rejection = messageSource.getMessage("orders.filters.error.conflict", null, locale);
        }
        if (form != null) {
            addFilterEditAttributes(model, listOf(safeReturnTo(returnTo)), filterId, form, locale);
            model.addAttribute("filterError", rejection);
            response.setStatus(422);
            return "orders/filter-edit";
        }
        redirectAttributes.addFlashAttribute("filterError", rejection);
        return "redirect:" + safeReturnTo(returnTo);
    }

    private void addFilterFormAttributes(Model model, String returnTo, Locale locale) {
        model.addAttribute("filters", orderFilters.list(actor()));
        model.addAttribute("canManageStoreFilters", isAdmin());
        // the list shows only open orders, so a filter can only name an open status
        model.addAttribute("statuses", OrderListService.OPEN);
        model.addAttribute("shipmentTypes", ShipmentType.values());
        model.addAttribute("paymentSources", PaymentSource.values());
        model.addAttribute("shippingDueOptions", ShippingDue.values());
        model.addAttribute("marketplaces", connectedMarketplaceNames());
        model.addAttribute("returnTo", returnTo);
    }

    /**
     * Only the list's own address, or the filter management page carrying one, may be returned to (spec §7.2);
     * anything else falls back to the bare list.
     */
    static String safeReturnTo(String returnTo) {
        if (returnTo != null && (returnTo.equals(FILTERS_PATH) || returnTo.startsWith(FILTERS_PATH + "?"))) {
            return filtersPage(listOf(returnTo));
        }
        return safeListReturnTo(returnTo);
    }

    /** The list address only: the management page's own back link and returnTo parameter. */
    static String safeListReturnTo(String returnTo) {
        if (returnTo == null || returnTo.length() > 300) {
            return OrderListQuery.PATH;
        }
        boolean ownPath = returnTo.equals(OrderListQuery.PATH) || returnTo.startsWith(OrderListQuery.PATH + "?");
        return ownPath && !returnTo.contains("//") ? returnTo : OrderListQuery.PATH;
    }

    /** The management page that returns to the given list address. */
    static String filtersPage(String listReturnTo) {
        return FILTERS_PATH + "?returnTo=" + URLEncoder.encode(safeListReturnTo(listReturnTo), StandardCharsets.UTF_8);
    }

    /** The list address a return address leads back to: itself, or the management page's returnTo. */
    static String listOf(String returnTo) {
        if (returnTo != null && (returnTo.equals(FILTERS_PATH) || returnTo.startsWith(FILTERS_PATH + "?"))) {
            List<String> inner = queryOf(returnTo).get("returnTo");
            return safeListReturnTo(inner == null || inner.isEmpty() ? null : inner.get(0));
        }
        return safeListReturnTo(returnTo);
    }

    private static MultiValueMap<String, String> queryOf(String href) {
        return UriComponentsBuilder.fromUriString(href).build().getQueryParams().entrySet().stream()
                .collect(LinkedMultiValueMap::new,
                        (map, e) -> e.getValue().forEach(v -> map.add(e.getKey(), v == null ? "" : URLDecoder.decode(v, StandardCharsets.UTF_8))),
                        LinkedMultiValueMap::addAll);
    }

    /** A malformed address (an unbalanced "%" from a truncated returnTo) falls back to the bare list rather than
     * throwing out of a filter action. */
    private static OrderListQuery parseReturnTo(String href) {
        try {
            return OrderListQuery.parse(queryOf(href));
        } catch (IllegalArgumentException e) {
            return OrderListQuery.parse(queryOf(OrderListQuery.PATH));
        }
    }

    private FilterActor actor() {
        return new FilterActor(getStoreId(), getUserId(), isAdmin());
    }

    private List<String> connectedMarketplaceNames() {
        Store store = storesRepository.findById(getStoreId());
        if (store == null) {
            return List.of();
        }
        return store.getMarketplaces().stream()
                .map(MarketplaceIntegration::getName)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
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
    public String getOrderDetails(@PathVariable("orderId") String orderId, @RequestParam(required = false) String returnTo,
                                  Model model, Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        return showOrderDetails(order, new OrderPageModelFactory.Viewer(false, isAdmin(), OrderBackLink.sanitize(returnTo)),
                model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/orders/{orderId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String getOrderDetailsForSuperAdmin(@PathVariable("storeId") String storeId, @PathVariable("orderId") String orderId,
                                               Model model, Locale locale) {
        Order order = requireOrder(ordersRepository, storeId, orderId);
        return showOrderDetails(order, new OrderPageModelFactory.Viewer(true, false, null), model, locale);
    }

    private String showOrderDetails(Order order, OrderPageModelFactory.Viewer viewer, Model model, Locale locale) {
        List<OrderItem> items = orderItemsRepository.findByOrderId(order.getOrderId());
        OrderPageModel page = pageModelFactory.build(order, items, viewer, locale);
        model.addAttribute("page", page);
        model.addAttribute("settings", page.settings());
        model.addAttribute("orderId", order.getOrderId());
        return "orders/details";
    }

    @GetMapping("/dashboard/orders/{orderId}/status")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String statusPage(@PathVariable String orderId, Model model, RedirectAttributes redirectAttributes,
                             Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        if (order.isClosed()) {
            return refuse(redirectAttributes, orderId, "order.status.error.closed", locale);
        }
        model.addAttribute("statusOptions", OrderStatusOptions.of(order));
        model.addAttribute("orderId", orderId);
        model.addAttribute("shortId", order.getShortenedOrderId());
        return "orders/status";
    }

    @PostMapping("/dashboard/orders/{orderId}/status")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String changeStatus(@PathVariable String orderId, @RequestParam(required = false) String status,
                               RedirectAttributes redirectAttributes, Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        OrderStatus requested = Arrays.stream(OrderStatus.values())
                .filter(candidate -> candidate.name().equals(status)).findFirst().orElse(null);
        String refusal = null;
        if (order.isClosed()) {
            refusal = "order.status.error.closed";
        } else if (requested == null) {
            refusal = "order.status.error.none";
        } else if (!order.canTransitionToDelivered(requested)) {
            refusal = "error.message.delivered.requires.shipment.data";
        } else if (requested == OrderStatus.Completed) {
            refusal = "error.message.completed.cannot.be.set.manually";
        } else if (requested == OrderStatus.Cancelled) {
            refusal = "error.message.cancelled.cannot.be.set.manually";
        }
        if (refusal != null) {
            return refuse(redirectAttributes, orderId, refusal, locale);
        }
        order.setStatus(requested);
        // the lifecycle may move the order on at once (New with every item delivered becomes Assembled), as it did
        // after the old settings form; the operator is told instead of wondering why the choice did not stick
        orderLifecycle.update(order);
        String chosen = messageSource.getMessage(OrderLabels.status(requested), null, locale);
        if (order.getStatus() != requested) {
            OrderFlash.warning(redirectAttributes, messageSource.getMessage("order.status.changed.auto",
                    new Object[]{chosen, messageSource.getMessage(OrderLabels.status(order.getStatus()), null, locale)}, locale));
        } else {
            OrderFlash.saved(redirectAttributes, messageSource.getMessage("order.status.changed", new Object[]{chosen}, locale));
        }
        return details(orderId);
    }

    /** The settings dialog as its own page, for a browser without JavaScript. */
    @GetMapping("/dashboard/orders/{orderId}/settings")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String settingsPage(@PathVariable String orderId, Model model, RedirectAttributes redirectAttributes,
                               Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        if (order.isClosed()) {
            return refuse(redirectAttributes, orderId, "order.settings.error.closed", locale);
        }
        List<OrderItem> items = orderItemsRepository.findByOrderId(orderId);
        model.addAttribute("settings", pageModelFactory.settings(order, items, false));
        model.addAttribute("orderId", orderId);
        model.addAttribute("shortId", order.getShortenedOrderId());
        return "orders/settings";
    }

    @PostMapping("/dashboard/orders/{orderId}/updateOrderInfo")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String updateOrderInfo(@PathVariable String orderId, @ModelAttribute("order") Order updatedOrder,
                                  @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                  HttpServletResponse response, Model model, RedirectAttributes redirectAttributes,
                                  Locale locale) {
        Order existingOrder = requireOrder(ordersRepository, getStoreId(), orderId);
        List<OrderItem> items = orderItemsRepository.findByOrderId(orderId);
        boolean async = SettingsPaths.isAsync(requestedWith);

        FulfilmentType requestedFulfilmentType = updatedOrder.getFulfilmentType();
        boolean fulfilmentTypeChanged = requestedFulfilmentType != null
                && requestedFulfilmentType != existingOrder.getFulfilmentType();
        String refusal = existingOrder.isClosed() ? "order.settings.error.closed"
                : fulfilmentTypeChanged && !existingOrder.canChangeFulfilmentType(items) ? "order.fulfilment.type.locked"
                : null;
        if (refusal != null) {
            if (async) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                return settingsFragment(existingOrder, items, model, messageSource.getMessage(refusal, null, locale), null);
            }
            return refuse(redirectAttributes, orderId, refusal, locale);
        }

        // the status has its own dialog and endpoint (…/status); a status posted here by an old page is ignored
        existingOrder.setEmailNotificationsEnabled(updatedOrder.isEmailNotificationsEnabled());
        existingOrder.setEstimatedAssemblyAt(updatedOrder.getEstimatedAssemblyAt());
        existingOrder.setEstimatedShippingAt(updatedOrder.getEstimatedShippingAt());
        existingOrder.setPreferredShippingAt(updatedOrder.getPreferredShippingAt());
        existingOrder.setAffiliateId(updatedOrder.getAffiliateId());
        existingOrder.setGclid(updatedOrder.getGclid());
        existingOrder.setComment(updatedOrder.getComment());
        if (fulfilmentTypeChanged) {
            existingOrder.setFulfilmentType(requestedFulfilmentType);
        }
        orderLifecycle.update(existingOrder);

        String saved = messageSource.getMessage("order.settings.saved", null, locale);
        if (async) {
            return settingsFragment(existingOrder, items, model, null, saved);
        }
        OrderFlash.saved(redirectAttributes, saved);
        return details(orderId);
    }

    private String settingsFragment(Order order, List<OrderItem> items, Model model, String error, String saved) {
        model.addAttribute("settings", pageModelFactory.settings(order, items, false));
        model.addAttribute("orderId", order.getOrderId());
        model.addAttribute("shortId", order.getShortenedOrderId());
        model.addAttribute("settingsError", error);
        model.addAttribute("settingsSaved", saved);
        return "orders/details/settings :: dialogForm";
    }

    @GetMapping("/dashboard/orders/{orderId}/delete")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String confirmDeleteOrder(@PathVariable String orderId, Model model, RedirectAttributes redirectAttributes,
                                     Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        // the same refusal as deleteOrder: the page must not offer a confirmation the POST would refuse
        ReceiptLock receiptLock = receiptAttemptService.receiptLock(order);
        if (receiptLock.locks()) {
            return refuse(redirectAttributes, orderId, receiptLock.key(DELETE_LOCKED_RECEIPT), locale);
        }
        return confirmPage(model, order, "order.page.delete", new Object[]{order.getShortenedOrderId()},
                OrderPageModelFactory.deleteMessage(order, messageSource, locale),
                "/dashboard/orders/" + orderId + "/delete", true, locale);
    }

    @GetMapping("/dashboard/orders/{orderId}/cancel")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String confirmCancelOrder(@PathVariable String orderId, Model model, RedirectAttributes redirectAttributes,
                                     Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        // the same refusal as cancelOrder: the page must not offer a confirmation the POST would refuse
        ReceiptLock receiptLock = receiptAttemptService.receiptLock(order);
        if (receiptLock.locks()) {
            return refuse(redirectAttributes, orderId, receiptLock.key(CANCEL_LOCKED_RECEIPT), locale);
        }
        return confirmPage(model, order, "order.page.cancel", new Object[]{order.getShortenedOrderId()},
                OrderPageModelFactory.cancelMessage(receiptAttemptService.hasFiscalisedReceipt(order), messageSource, locale),
                "/dashboard/orders/" + orderId + "/cancel", true, locale);
    }

    private static final String CANCEL_LOCKED_RECEIPT = "order.page.cancel.locked.receipt";
    private static final String DELETE_LOCKED_RECEIPT = "order.page.delete.locked.receipt";

    private String orderPageTitle(Order order, Locale locale) {
        return messageSource.getMessage("order.page.title", new Object[]{order.getShortenedOrderId()}, locale);
    }

    private static String details(String orderId) {
        return "redirect:/dashboard/orders/" + orderId;
    }

    /** One refusal: the reason as the layout's error flash, back to the order. */
    private String refuse(RedirectAttributes redirectAttributes, String orderId, String key, Locale locale, Object... args) {
        redirectAttributes.addFlashAttribute("errorMessage",
                messageSource.getMessage(key, args.length == 0 ? null : args, locale));
        return details(orderId);
    }

    /** A no-JavaScript confirmation page whose texts are the dialog's: <prefix>.confirm.title / .message / .action. */
    private String confirmPage(Model model, Order order, String prefix, Object[] titleArgs, String message,
                               String actionPath, boolean destructive, Locale locale) {
        return OrderConfirmPages.render(model, new ConfirmAction(
                messageSource.getMessage(prefix + ".confirm.title", titleArgs, locale),
                message != null ? message : messageSource.getMessage(prefix + ".confirm.message", null, locale),
                messageSource.getMessage(prefix + ".confirm.action", null, locale),
                actionPath, "/dashboard/orders/" + order.getOrderId(), destructive), orderPageTitle(order, locale));
    }

    /** The type is issuable now: the same check the invoicing menu and the no-JS confirmation both rely on. */
    private boolean issuable(Order order, DocumentType type) {
        return order.getIssuableDocumentTypes().contains(type);
    }

    @PostMapping("/dashboard/orders/{orderId}/add-items")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String addOrderItems(@PathVariable String orderId, @ModelAttribute AddItemsForm form,
                                RedirectAttributes redirectAttributes, Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        // the same reasons the page greys the "add items" button with; a stale page must not get past them
        List<OrderItem> items = orderItemsRepository.findByOrderId(orderId);
        String locked = OrderPageModelFactory.addItemsLockedKey(order,
                !dropshipItemLookup.itemIdsInDropshipDeliveries(getStoreId(), items).isEmpty(),
                receiptAttemptService.receiptLock(order));
        if (locked != null) {
            return refuse(redirectAttributes, orderId, locked, locale);
        }
        Store store = storesRepository.findById(getStoreId());
        InventoryView inventoryView = inventory.withEnabledSuppliersOnly(getStoreId());

        // every entry is resolved before anything is saved, so an unknown pricelist row rejects the whole batch
        List<OrderItemDraft> drafts = form.entries().stream()
                .map(entry -> entry.isFromPricelist()
                        ? OrderItemDraft.of(pricelistEntry(entry), entry.getQty())
                        : OrderItemDraft.of(inventoryView.findByInventoryKey(entry.inventoryKey()), entry.getQty()))
                .toList();

        ordersManager.addOrderItems(store, order, drafts);
        return details(orderId);
    }

    private AvailabilityAndPrice pricelistEntry(AddItemsForm.Entry entry) {
        return pricelistFinder.findByPimId(getStoreId(), entry.getCatalogId(), entry.getPimId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/dashboard/orders/{orderId}/collection")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String getOrderCollectionProtocol(@PathVariable("orderId") String orderId, Model model) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        return renderOrderCollectionProtocol(order, false, model);
    }

    @GetMapping("/dashboard/store/{storeId}/orders/{orderId}/collection")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String getOrderCollectionProtocolForSuperAdmin(@PathVariable("storeId") String storeId, @PathVariable("orderId") String orderId, Model model) {
        Order order = requireOrder(ordersRepository, storeId, orderId);
        return renderOrderCollectionProtocol(order, true, model);
    }

    private String renderOrderCollectionProtocol(Order order, boolean superAdmin, Model model) {
        Store store = storesRepository.findById(order.getStoreId());
        List<OrderItem> orderItems = orderItemsRepository.findByOrderId(order.getOrderId());

        // the place is fixed text since the page was introduced; the store address is not used for it
        model.addAttribute("print", OrderPrintView.collection(order, orderItems, store, LocalDate.now(), "Kraków, PL",
                OrderLinks.of(order, superAdmin), supplierLabels.forStore(store).withWarehouse(warehouseLabel())));
        return "orders/collection";
    }

    @GetMapping("/dashboard/orders/{orderId}/card")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String getOrderCard(@PathVariable("orderId") String orderId, Model model) {
        return renderOrderCard(requireOrder(ordersRepository, getStoreId(), orderId), false, model);
    }

    @GetMapping("/dashboard/store/{storeId}/orders/{orderId}/card")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String getOrderCardForSuperAdmin(@PathVariable("storeId") String storeId, @PathVariable("orderId") String orderId, Model model) {
        return renderOrderCard(requireOrder(ordersRepository, storeId, orderId), true, model);
    }

    /** The store's own warehouse as the printouts name an item's supplier, in the request's language. */
    private String warehouseLabel() {
        return OrderPageModelFactory.warehouseLabel(messageSource, LocaleContextHolder.getLocale());
    }

    private String renderOrderCard(Order order, boolean superAdmin, Model model) {
        List<OrderItem> orderItems = orderItemsRepository.findByOrderId(order.getOrderId());
        model.addAttribute("print", OrderPrintView.card(order, orderItems, OrderLinks.of(order, superAdmin),
                supplierLabels.forStoreId(order.getStoreId()).withWarehouse(warehouseLabel())));
        return "orders/card";
    }

    // Without JavaScript the "Wystaw" menu entry leads here instead of opening the issue dialog: the same question with
    // the same "send to the customer" checkbox, posting to the unchanged invoicing endpoint.
    @GetMapping("/dashboard/orders/{orderId}/invoicing")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String confirmInvoice(@PathVariable String orderId, @RequestParam DocumentType documentType, Model model,
                                 Locale locale, RedirectAttributes redirectAttributes) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        if (!issuable(order, documentType)) {
            return refuse(redirectAttributes, orderId, "error.message.no.eligible.invoice.to.create", locale);
        }
        if (receiptAttemptService.blocksManualReceipt(getStoreId(), orderId)) {
            return refuse(redirectAttributes, orderId, "receipts.invoicing.blocked", locale);
        }
        return OrderConfirmPages.renderInvoicing(model, orderId, documentType, orderPageTitle(order, locale));
    }

    @PostMapping("/dashboard/orders/{orderId}/invoicing")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String createInvoice(@PathVariable String orderId, @RequestParam DocumentType documentType, @RequestParam(defaultValue = "false") boolean send, Locale locale, RedirectAttributes redirectAttributes) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);

        if (!issuable(order, documentType)) {
            return refuse(redirectAttributes, orderId, "error.message.no.eligible.invoice.to.create", locale);
        }
        // an e-receipt attempt is issuing, fiscalised or closed by hand: it is the sale's document, and an invoice
        // from the invoicing system would be a second one for the same sale (the menu leaves the types out too)
        if (receiptAttemptService.blocksManualReceipt(getStoreId(), orderId)) {
            return refuse(redirectAttributes, orderId, "receipts.invoicing.blocked", locale);
        }

        invoiceCreationEventPublisher.publish(order, documentType, send);
        issueStarted(redirectAttributes, orderId, "order.documents.issue.started", locale);

        return details(orderId);
    }

    @GetMapping("/dashboard/orders/{orderId}/goods-out")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String confirmGoodsOut(@PathVariable String orderId, Model model, Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        return confirmPage(model, order, "order.documents.goods.issue", null, null,
                "/dashboard/orders/" + orderId + "/goods-out", false, locale);
    }

    @PostMapping("/dashboard/orders/{orderId}/goods-out")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String issueGoodsOut(@PathVariable String orderId, Locale locale, RedirectAttributes redirectAttributes) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);

        if (order.getDocumentByType(DocumentType.GoodsIssue).isPresent()) {
            return refuse(redirectAttributes, orderId, "error.message.goods.issue.already.exists", locale);
        }

        String createdBy = CustomSecurityContext.getLoggedInUser()
                .map(CustomUser::getName)
                .orElse("System");
        goodsOutEventPublisher.publish(order, createdBy);
        issueStarted(redirectAttributes, orderId, "order.documents.goods.issue.started", locale);

        return details(orderId);
    }

    // Issuing an invoice or a WZ runs asynchronously in the invoicing/warehouse system; the number only appears once
    // that finishes, so the notice offers a link to reload instead of the page waiting or polling for it.
    private void issueStarted(RedirectAttributes redirectAttributes, String orderId, String key, Locale locale) {
        OrderFlash.savedWithLink(redirectAttributes, messageSource.getMessage(key, null, locale),
                "/dashboard/orders/" + orderId + "#dokumenty",
                messageSource.getMessage("order.documents.refresh", null, locale));
    }

    @GetMapping("/dashboard/orders/{orderId}/items/{itemId}")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String getOrderItem(@PathVariable String orderId, @PathVariable String itemId, Model model) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        OrderItem orderItem = requireItem(orderId, itemId);
        return showOrderItemDetails(order, orderItem, model);
    }

    @PostMapping("/dashboard/orders/{orderId}/items/{itemId}/save")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String saveOrderItem(@PathVariable String orderId, @PathVariable String itemId, @ModelAttribute OrderItem updatedItem,
                                @RequestParam(required = false) String customSupplier, Model model) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        List<OrderItem> orderItems = orderItemsRepository.findByOrderId(orderId);

        Optional<OrderItem> op = orderItems.stream()
                .filter(i -> i.getItemId().equals(itemId))
                .findFirst();

        if (op.isPresent()) {
            OrderItem orderItem = op.get();
            // the item page of a closed order is read-only; a replayed or hand-made post must not change it either
            if (order.isClosed()) {
                model.addAttribute("itemError",
                        messageSource.getMessage("order.item.error.closed", null, LocaleContextHolder.getLocale()));
                return showOrderItemDetails(order, orderItem, model);
            }

            boolean wasService = orderItem.isService();
            boolean serviceFlagLocked = orderItem.hasSupplierAllocation();
            // an e-receipt being issued has frozen the item prices it sends, as a document already on the order has
            ReceiptLock receiptLock = receiptAttemptService.receiptLock(order);
            boolean priceLocked = !order.getDocuments().isEmpty() || receiptLock.locks();
            // The sale document (or the e-receipt being issued) lists the item's name, quantity and VAT rate: a post
            // that changes any of them is refused, not silently trimmed, so the operator learns the save did not
            // happen; an unchanged value passes and the stored one is put back to rule out any drift.
            ItemSaleLock saleLock = ItemSaleLock.of(order, receiptLock);
            if (saleLock != null) {
                if (ItemSaleLock.changes(orderItem, updatedItem)) {
                    model.addAttribute("itemError",
                            messageSource.getMessage(saleLock.refusalKey(), null, LocaleContextHolder.getLocale()));
                    return showOrderItemDetails(order, orderItem, model);
                }
                ItemSaleLock.keep(orderItem, updatedItem);
            }

            // The page offers the supplier as the assign-supplier dialog does: a connection, "Other supplier…" with a
            // typed name, or none. Without JavaScript the typed-name field is always shown, so a name typed there
            // counts even when the select was left on "none".
            String postedDeliveryId = StringUtils.trimToNull(updatedItem.getDeliveryId());
            if (SupplierChoice.CUSTOM.equals(postedDeliveryId) || (postedDeliveryId == null && StringUtils.isNotBlank(customSupplier))) {
                postedDeliveryId = StringUtils.trimToNull(customSupplier);
            }
            // a value that names a supplier delivery is not a supplier choice: the page shows it locked, and a
            // hand-made post must not swap the delivery for a supplier either
            if (holdsDelivery(order, orderItem)) {
                postedDeliveryId = orderItem.getDeliveryId();
            }
            updatedItem.setDeliveryId(postedDeliveryId);
            boolean deliveryIdChanged = postedDeliveryId != null && !postedDeliveryId.equals(orderItem.getDeliveryId());
            if (deliveryIdChanged) {
                Store store = storesRepository.findById(getStoreId());
                // Same rules as the "assign supplier" modal: a connection identity or a typed name.
                SupplierChoice.Resolution resolution = supplierChoice.resolve(store, postedDeliveryId, null);
                if (!resolution.accepted()) {
                    model.addAttribute("itemError", messageSource.getMessage(
                            resolution.errorCode(), resolution.errorArgs(), LocaleContextHolder.getLocale()));
                    return showOrderItemDetails(order, orderItem, model);
                }
                postedDeliveryId = resolution.identity();
                updatedItem.setDeliveryId(postedDeliveryId);
                if (!ExternalSupplierBinding.of(store, List.of(order)).permits(orderId, postedDeliveryId)) {
                    model.addAttribute("itemError",
                            messageSource.getMessage("order.item.assign.supplier.routed", null, LocaleContextHolder.getLocale()));
                    return showOrderItemDetails(order, orderItem, model);
                }
            }

            if (StringUtils.isBlank(updatedItem.getCategory())) {
                updatedItem.setCategory(null);
            }
            if (serviceFlagLocked) {
                updatedItem.setService(orderItem.isService());
            }
            if (priceLocked) {
                updatedItem.setPrice(orderItem.getPrice());
            }
            // Same lock as the item menu's toggle: the flag is only read when the closing invoice is issued, so after
            // that a change would silently not apply; the page shows the box disabled and the stored value stays.
            if (order.isInvoiced() || receiptLock.locks()) {
                updatedItem.setConsolidated(orderItem.isConsolidated());
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

        return details(orderId);
    }

    @PostMapping("/dashboard/orders/{orderId}/assign-supplier")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String assignSupplier(@PathVariable String orderId, @ModelAttribute AssignSupplierForm form,
                                 @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                 HttpServletRequest request, HttpServletResponse response, Model model,
                                 RedirectAttributes redirectAttributes, Locale locale) {
        boolean async = SettingsPaths.isAsync(requestedWith);
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        OrderItem orderItem = requireItem(orderId, form.getItemId());
        Store store = storesRepository.findById(getStoreId());

        if (!orderItem.isReleasable()) {
            return refuseSupplier(orderId, form, store, async, response, model, redirectAttributes,
                    messageSource.getMessage("order.item.assign.supplier.blocked", null, locale));
        }
        Optional<Double> typed = FormNumbers.decimal(form.getCost()).filter(value -> value >= 0);
        if (typed.isEmpty()) {
            return refuseSupplier(orderId, form, store, async, response, model, redirectAttributes,
                    messageSource.getMessage("order.item.assign.cost.invalid", null, locale));
        }
        SupplierChoice.Resolution resolution = supplierChoice.resolve(store, form.getSupplier(), form.getCustomSupplier());
        if (!resolution.accepted()) {
            return refuseSupplier(orderId, form, store, async, response, model, redirectAttributes,
                    messageSource.getMessage(resolution.errorCode(), resolution.errorArgs(), locale));
        }
        String supplier = resolution.identity();
        if (!ExternalSupplierBinding.of(store, List.of(order)).permits(orderId, supplier)) {
            return refuseSupplier(orderId, form, store, async, response, model, redirectAttributes,
                    messageSource.getMessage("order.item.assign.supplier.routed", null, locale));
        }
        Taxonomy taxonomy = taxonomyCache.findByMfn(form.getManufacturerCode());
        String ean = taxonomy != null ? taxonomy.ean() : null;
        if (Strings.isBlank(ean)) {
            // the refusal stays in the dialog instead of opening the item's edit page
            return refuseSupplier(orderId, form, store, async, response, model, redirectAttributes,
                    messageSource.getMessage("order.item.ean.not.found", null, locale));
        }

        // a gross price is turned net with the item's own VAT, as the old dialog did in the browser
        double cost = form.isGross() && orderItem.getTax() >= 1 ? typed.get() / orderItem.getTax() : typed.get();
        orderItem.setManufacturerCode(form.getManufacturerCode());
        orderItem.setCost(cost);
        orderItem.setDeliveryId(supplier);
        orderItem.setEan(ean);
        orderItem.markAsInAllocation();
        orderItemsRepository.save(orderItem);

        String saved = messageSource.getMessage("order.item.supplier.assigned", null, locale);
        if (async) {
            OrderFlash.forNextPage(request, response, "/dashboard/orders/" + orderId, new OrderNotice(OrderLabels.OK, saved, null, null));
            model.addAttribute("supplierRedirect", "/dashboard/orders/" + orderId);
            return supplierFormFragment(orderId, form, store, model, null);
        }
        OrderFlash.saved(redirectAttributes, saved);
        return details(orderId);
    }

    private String refuseSupplier(String orderId, AssignSupplierForm form, Store store, boolean async,
                                  HttpServletResponse response, Model model, RedirectAttributes redirectAttributes,
                                  String reason) {
        if (async) {
            response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
            return supplierFormFragment(orderId, form, store, model, reason);
        }
        redirectAttributes.addFlashAttribute("errorMessage", reason);
        return details(orderId);
    }

    private String supplierFormFragment(String orderId, AssignSupplierForm form, Store store, Model model, String error) {
        model.addAttribute("orderId", orderId);
        model.addAttribute("supplierForm", form);
        model.addAttribute("supplierError", error);
        model.addAttribute("suppliers", supplierLabels.forStore(store).options());
        return "orders/details/item-dialogs :: supplierForm";
    }

    /** An item is addressed by its order; the order has been checked against the session's store before this. */
    private OrderItem requireItem(String orderId, String itemId) {
        OrderItem item = itemId == null ? null : orderItemsRepository.findById(orderId, itemId);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return item;
    }

    @PostMapping("/dashboard/orders/{orderId}/clear-supplier")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String clearSupplier(@PathVariable String orderId, @RequestParam String itemId,
                                RedirectAttributes redirectAttributes, Locale locale) {
        requireOrder(ordersRepository, getStoreId(), orderId);
        OrderItem orderItem = requireItem(orderId, itemId);
        if (!orderItem.isReleasable()) {
            return refuse(redirectAttributes, orderId, "order.item.clear.assign.blocked", locale);
        }
        orderItem.removeFulfilment();
        orderItemsRepository.save(orderItem);
        OrderFlash.saved(redirectAttributes, messageSource.getMessage("order.item.supplier.cleared", null, locale));
        return details(orderId);
    }

    @GetMapping("/dashboard/orders/{orderId}/clear-supplier")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String confirmClearSupplier(@PathVariable String orderId, @RequestParam String itemId, Model model, Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        OrderItem item = requireItem(orderId, itemId);
        String action = UriComponentsBuilder.fromPath("/dashboard/orders/" + orderId + "/clear-supplier")
                .queryParam("itemId", itemId).encode().build().toUriString();
        return confirmPage(model, order, "order.item.clear", new Object[]{item.getName()}, null, action, true, locale);
    }

    @PostMapping("/dashboard/orders/{orderId}/assign-sku")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String assignSku(@PathVariable String orderId, @RequestParam String itemId, @RequestParam String sku,
                            RedirectAttributes redirectAttributes, Locale locale) {
        requireOrder(ordersRepository, getStoreId(), orderId);
        OrderItem orderItem = requireItem(orderId, itemId);
        // The same rule the menu shows: the SKU feeds marketplace item keys and RMA, so it is set before fulfilment only.
        if (!orderItem.isNew() || orderItem.isGroup()) {
            return refuse(redirectAttributes, orderId, "order.item.assign.sku.locked", locale);
        }
        orderItem.setSku(sku);
        orderItemsRepository.save(orderItem);
        return details(orderId);
    }

    @PostMapping("/dashboard/orders/{orderId}/assign-warehouse")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String assignFromWarehouse(@PathVariable String orderId, @RequestParam String itemId,
                                      @RequestParam String warehouseItemId,
                                      RedirectAttributes redirectAttributes, Locale locale) {
        requireOrder(ordersRepository, getStoreId(), orderId);
        requireItem(orderId, itemId);
        try {
            ordersManager.assignFromWarehouse(getStoreId(), orderId, itemId, warehouseItemId);
        } catch (IllegalStateException e) {
            return refuse(redirectAttributes, orderId, "error.message." + e.getMessage(), locale);
        }
        return details(orderId);
    }

    @PostMapping("/dashboard/orders/{orderId}/split-group")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String splitGroupItem(@PathVariable String orderId, @ModelAttribute SplitGroupForm form,
                                 RedirectAttributes redirectAttributes, Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        // splitting a set replaces its line with the components' names, quantities and prices: the sale document (or
        // the e-receipt being issued) lists the set, so the menu greys it with the same reason
        ItemSaleLock saleLock = ItemSaleLock.of(order, receiptAttemptService.receiptLock(order));
        if (saleLock != null) {
            return refuse(redirectAttributes, orderId, saleLock.splitGroupRefusalKey(), locale);
        }
        requireItem(orderId, form.getItemId());
        try {
            ordersManager.splitGroupItem(orderId, form.getItemId(), form.toComponents());
        } catch (IllegalStateException e) {
            return refuse(redirectAttributes, orderId, "error.message." + e.getMessage(), locale);
        }
        return details(orderId);
    }

    @PostMapping("/dashboard/orders/{orderId}/toggle-consolidation")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String toggleConsolidation(@PathVariable String orderId, @RequestParam String itemId,
                                      RedirectAttributes redirectAttributes, Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        // The flag is only read when the closing invoice is issued; after that a change would silently not apply.
        if (order.isInvoiced()) {
            return refuse(redirectAttributes, orderId, "order.item.consolidation.locked", locale);
        }
        ReceiptLock receiptLock = receiptAttemptService.receiptLock(order);
        if (receiptLock.locks()) {
            return refuse(redirectAttributes, orderId, receiptLock.key("order.item.consolidation.locked.receipt"), locale);
        }
        OrderItem orderItem = requireItem(orderId, itemId);
        orderItem.toggleConsolidation();
        orderItemsRepository.save(orderItem);
        return details(orderId);
    }

    private String showOrderItemDetails(Order order, OrderItem orderItem, Model model) {
        model.addAttribute("orderId", order.getOrderId());
        model.addAttribute("shortId", order.getShortenedOrderId());
        model.addAttribute("orderItem", orderItem);
        model.addAttribute("categories", storeCategories.namesFor(order.getStoreId()));
        model.addAttribute("categoryGroups", storeCategories.groupsFor(order.getStoreId()));
        model.addAttribute("isCompletedOrder", order.isClosed());
        model.addAttribute("serviceFlagLocked", orderItem.hasSupplierAllocation());
        ReceiptLock receiptLock = receiptAttemptService.receiptLock(order);
        boolean priceLocked = !order.getDocuments().isEmpty() || receiptLock.locks();
        boolean consolidationLocked = order.isInvoiced() || receiptLock.locks();
        // name, quantity and VAT rate are read-only with the reason; saveOrderItem refuses a post that changes them
        ItemSaleLock saleLock = ItemSaleLock.of(order, receiptLock);
        model.addAttribute("saleLock", saleLock);
        model.addAttribute("priceLocked", priceLocked);
        // a closing document or the e-receipt says why; any other document (advance invoice, WZ) keeps the old reason
        model.addAttribute("priceLockedKey", !priceLocked ? null
                : saleLock != null ? saleLock.priceKey() : "order.item.form.price.locked");
        model.addAttribute("consolidationLocked", consolidationLocked);
        model.addAttribute("consolidationLockedKey", !consolidationLocked ? null
                : order.isInvoiced() ? "order.item.consolidation.locked"
                : receiptLock.key("order.item.consolidation.locked.receipt"));
        model.addAttribute("statusKey", OrderLabels.itemStatus(orderItem.getStatus()));
        model.addAttribute("statusTone", OrderLabels.tone(orderItem.getStatus()));

        // The header shows the item's delivery as its row in the items table does (supplier label, or the short id of
        // a supplier delivery with its link).
        OrderItemRow.Delivery delivery = pageModelFactory.delivery(order, orderItem,
                orderItemsRepository.findByOrderId(order.getOrderId()), new OrderPageModelFactory.Viewer(false, isAdmin(), null),
                LocaleContextHolder.getLocale());
        boolean deliveryHeld = holdsDelivery(order, orderItem);
        model.addAttribute("delivery", delivery);
        model.addAttribute("deliveryHeld", deliveryHeld);

        // The supplier is chosen like in the assign-supplier dialog: a stored identity that is not an enabled
        // connection (a typed name, a switched-off connection) comes back as "Other supplier…" with the name filled.
        // A delivery id is never offered as a typed supplier name; the page keeps it as it is.
        SupplierLabelMap labels = supplierLabels.forStore(storesRepository.findById(order.getStoreId()));
        String deliveryId = StringUtils.trimToNull(orderItem.getDeliveryId());
        boolean offered = deliveryId != null && labels.options().stream().anyMatch(option -> option.identity().equals(deliveryId));
        model.addAttribute("suppliers", labels.options());
        model.addAttribute("supplierCurrent", offered ? deliveryId : null);
        model.addAttribute("supplierCustom", offered || deliveryHeld ? null : deliveryId);

        return "orders/item";
    }

    /**
     * Whether the item's deliveryId is a supplier delivery's id. The item's status says so once it is claimed, ordered
     * or delivered (DeliveryRedirectResolver, as the items table links it); a new item holding one is only possible
     * through old data, so there the delivery itself is looked up.
     */
    private boolean holdsDelivery(Order order, OrderItem item) {
        String deliveryId = StringUtils.trimToNull(item.getDeliveryId());
        if (deliveryId == null) {
            return false;
        }
        return deliveryRedirectResolver.pointsToDelivery(item)
                || (item.isNew() && deliveriesRepository.findById(order.getStoreId(), deliveryId) != null);
    }

    @PostMapping("/dashboard/orders/{orderId}/delete")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String deleteOrder(@PathVariable String orderId, RedirectAttributes redirectAttributes, Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        // the attempt would go on issuing a receipt for an order that no longer exists
        ReceiptLock receiptLock = receiptAttemptService.receiptLock(order);
        if (receiptLock.locks()) {
            return refuse(redirectAttributes, orderId, receiptLock.key(DELETE_LOCKED_RECEIPT), locale);
        }
        try {
            ordersManager.deleteOrder(getStoreId(), orderId);
        } catch (IllegalStateException e) {
            // a page left open while the order gained an item or an invoice: the reason, not an error page
            return refuse(redirectAttributes, orderId, "order.page.delete.unavailable", locale);
        }
        return "redirect:/dashboard/orders";
    }

    @PostMapping("/dashboard/orders/{orderId}/cancel")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String cancelOrder(@PathVariable String orderId, RedirectAttributes redirectAttributes, Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        // an e-receipt attempt owns the sale but its outcome is not on the order yet: a cancel now could leave a
        // fiscalised receipt on a cancelled order nobody was warned about (the header greys "Anuluj" with this reason)
        ReceiptLock receiptLock = receiptAttemptService.receiptLock(order);
        if (receiptLock.locks()) {
            return refuse(redirectAttributes, orderId, receiptLock.key(CANCEL_LOCKED_RECEIPT), locale);
        }
        try {
            ordersManager.cancelOrder(getStoreId(), orderId);
            // the cancel saves the order outside its lifecycle: the bell hears about the new status here
            receiptAttemptService.reconcileDeadAttemptAlerts(getStoreId(), orderId);
            OrderFlash.saved(redirectAttributes, messageSource.getMessage("order.cancelled", null, locale));
        } catch (IllegalStateException e) {
            return refuse(redirectAttributes, orderId, "error.message.order.cannot.be.cancelled", locale);
        }
        return details(orderId);
    }

    @PostMapping("/dashboard/orders/{orderId}/removeSelectedItemsFromOrder")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String removeSelectedItemsFromOrder(@PathVariable String orderId, @ModelAttribute OrderItemsForm form,
                                               RedirectAttributes redirectAttributes, Locale locale) {
        // the page leaves "Usuń" out of an invoiced order (its invoice or receipt lists the items); a stale page or a
        // hand-made request must not get past that either
        if (requireOrder(ordersRepository, getStoreId(), orderId).isInvoiced()) {
            return refuse(redirectAttributes, orderId, "order.items.remove.locked.invoiced", locale);
        }
        String locked = receiptLockedKey(orderId);
        if (locked != null) {
            return refuse(redirectAttributes, orderId, locked, locale);
        }
        return bulk(BulkAction.REMOVE, orderId, form, redirectAttributes, locale,
                ids -> ordersManager.removeFromOrder(getStoreId(), orderId, ids));
    }

    @PostMapping("/dashboard/orders/{orderId}/moveSelectedItemsToAllocation")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String moveSelectedItemsToAllocation(@PathVariable String orderId, @ModelAttribute OrderItemsForm form,
                                                RedirectAttributes redirectAttributes, Locale locale) {
        return bulk(BulkAction.ALLOCATE, orderId, form, redirectAttributes, locale,
                ids -> ordersManager.moveItemsToAllocation(getStoreId(), orderId, ids));
    }

    @PostMapping("/dashboard/orders/{orderId}/moveSelectedItemsToTheWarehouse")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String moveSelectedItemsToTheWarehouse(@PathVariable String orderId, @ModelAttribute OrderItemsForm form,
                                                  RedirectAttributes redirectAttributes, Locale locale) {
        return bulk(BulkAction.TO_WAREHOUSE, orderId, form, redirectAttributes, locale,
                ids -> ordersManager.moveOrderItemsToTheWarehouse(getStoreId(), orderId, ids));
    }

    @PostMapping("/dashboard/orders/{orderId}/moveSelectedItemsToTheWarehouseForRMA")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String moveSelectedItemsToTheWarehouseForRMA(@PathVariable String orderId, @ModelAttribute OrderItemsForm form,
                                                        RedirectAttributes redirectAttributes, Locale locale) {
        return bulk(BulkAction.TO_WAREHOUSE_RMA, orderId, form, redirectAttributes, locale,
                ids -> ordersManager.moveOrderItemsToTheWarehouseForRMA(getStoreId(), orderId, ids));
    }

    /**
     * Without JavaScript a bulk action is confirmed on a page listing the chosen items, as the dialog does with JS;
     * rendering it changes nothing. Moving to another order has its own field and is not confirmed here.
     */
    @PostMapping("/dashboard/orders/{orderId}/bulk-confirm")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String confirmBulk(@PathVariable String orderId, @RequestParam BulkAction action,
                              @ModelAttribute OrderItemsForm form, Model model, RedirectAttributes redirectAttributes,
                              Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        List<String> selected = form.getSelectedOrderItemIds();
        if (selected.isEmpty() || action == BulkAction.MOVE) {
            return refuse(redirectAttributes, orderId, "order.bulk.none.selected", locale);
        }
        List<OrderItem> items = orderItemsRepository.findByOrderId(orderId).stream()
                .filter(item -> selected.contains(item.getItemId()))
                .toList();
        if (items.isEmpty()) {
            return refuse(redirectAttributes, orderId, "order.bulk.none.selected", locale);
        }
        // the texts are the dialog's, whose "{n}" is a plain placeholder the script fills in, not a MessageFormat argument
        model.addAttribute("title", messageSource.getMessage(action.confirmTitleKey(), null, locale));
        model.addAttribute("message", messageSource.getMessage(action.confirmMessageKey(), null, locale)
                .replace("{n}", String.valueOf(items.size())));
        model.addAttribute("confirmLabel", messageSource.getMessage(action.confirmActionKey(), null, locale));
        model.addAttribute("danger", action.danger());
        model.addAttribute("actionPath", "/dashboard/orders/" + orderId + "/" + action.path());
        model.addAttribute("items", items);
        model.addAttribute("orderId", orderId);
        model.addAttribute("backLabel", orderPageTitle(order, locale));
        return "orders/bulk-confirm";
    }

    /** A complete action says how many items changed; a partial one warns and says why the rest was left. */
    private String bulk(BulkAction action, String orderId, OrderItemsForm form, RedirectAttributes redirectAttributes,
                        Locale locale, Function<List<String>, OrdersManager.Result> run) {
        requireOrder(ordersRepository, getStoreId(), orderId);
        List<String> selected = form.getSelectedOrderItemIds();
        if (selected.isEmpty()) {
            return refuse(redirectAttributes, orderId, "order.bulk.none.selected", locale);
        }
        BulkActionResult result = BulkActionResult.of(action, run.apply(selected));
        String message = result.message(messageSource, locale);
        if (result.complete()) {
            OrderFlash.saved(redirectAttributes, message);
        } else {
            OrderFlash.warning(redirectAttributes, message);
        }
        return details(orderId);
    }

    @PostMapping("/dashboard/orders/{orderId}/splitOrder")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String splitOrder(@PathVariable String orderId, @ModelAttribute OrderItemsForm form,
                             RedirectAttributes redirectAttributes, Locale locale) {
        String locked = receiptLockedKey(orderId);
        if (locked != null) {
            return refuse(redirectAttributes, orderId, locked, locale);
        }
        try {
            Order newOrder = ordersManager.splitOrder(getStoreId(), orderId, form.getSelectedOrderItemIds());
            OrderFlash.saved(redirectAttributes, messageSource.getMessage("order.bulk.split.done",
                    new Object[]{form.getSelectedOrderItemIds().size()}, locale));
            return "redirect:/dashboard/orders/" + newOrder.getOrderId();
        } catch (IllegalStateException e) {
            return refuse(redirectAttributes, orderId, "error.message." + e.getMessage(), locale);
        }
    }

    @PostMapping("/dashboard/orders/{orderId}/moveItemsToOrder")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String moveItemsToOrder(@PathVariable String orderId, @ModelAttribute OrderItemsForm form,
                                   @RequestParam(required = false) String targetOrderId,
                                   RedirectAttributes redirectAttributes, Locale locale) {
        String locked = receiptLockedKey(orderId);
        if (locked != null) {
            return refuse(redirectAttributes, orderId, locked, locale);
        }
        List<String> selected = form.getSelectedOrderItemIds();
        if (selected.isEmpty()) {
            return refuse(redirectAttributes, orderId, "order.bulk.none.selected", locale);
        }
        OrderReferenceResolver.Resolution resolution = orderReferenceResolver.resolve(getStoreId(), targetOrderId);
        if (resolution.outcome() == OrderReferenceResolver.Outcome.AMBIGUOUS) {
            return refuse(redirectAttributes, orderId, "order.move.ambiguous", locale, resolution.candidates());
        }
        if (!resolution.isFound()) {
            return refuse(redirectAttributes, orderId, "order.move.not.found", locale, resolution.candidates());
        }
        if (resolution.order().getOrderId().equals(orderId)) {
            return refuse(redirectAttributes, orderId, "order.move.self", locale, resolution.candidates());
        }
        // the target's e-receipt has frozen its items as well
        if (receiptAttemptService.receiptLock(resolution.order()).locks()) {
            return refuse(redirectAttributes, orderId, "order.move.target.locked.receipt", locale);
        }
        try {
            Order target = ordersManager.moveOrderItemsToOrder(getStoreId(), orderId, resolution.order().getOrderId(), selected);
            OrderFlash.saved(redirectAttributes, messageSource.getMessage("order.bulk.move.done",
                    new Object[]{selected.size(), ConversionUtil.getShortenedId(orderId)}, locale));
            return "redirect:/dashboard/orders/" + target.getOrderId();
        } catch (IllegalStateException e) {
            return refuse(redirectAttributes, orderId, "error.message." + e.getMessage(), locale);
        }
    }

    /** The "move to an existing order" dialog previews the order the typed number points to before anything moves. */
    @GetMapping("/dashboard/orders/{orderId}/move-target")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<MoveTargetView> moveTarget(@PathVariable String orderId, @RequestParam(required = false) String q,
                                                     Locale locale) {
        requireOrder(ordersRepository, getStoreId(), orderId);
        OrderReferenceResolver.Resolution resolution = orderReferenceResolver.resolve(getStoreId(), q);
        if (resolution.outcome() == OrderReferenceResolver.Outcome.AMBIGUOUS) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(MoveTargetView.ambiguous(
                    messageSource.getMessage("order.move.ambiguous", new Object[]{resolution.candidates()}, locale)));
        }
        if (!resolution.isFound()) {
            return ResponseEntity.notFound().build();
        }
        Order target = resolution.order();
        String reason = target.getOrderId().equals(orderId) ? messageSource.getMessage("order.move.self", null, locale)
                : !target.canBeSplit() ? messageSource.getMessage("order.move.target.locked", null, locale)
                : receiptAttemptService.receiptLock(target).locks() ? messageSource.getMessage("order.move.target.locked.receipt", null, locale)
                : null;
        // the amount leaves the server fully formatted, so the dialog script only prints it
        String amount = messageSource.getMessage("general.currency.amount", new Object[]{Money.format(target.getTotalPrice())}, locale);
        String status = target.getStatus() == null ? null
                : messageSource.getMessage(OrderLabels.status(target.getStatus()), null, locale);
        return ResponseEntity.ok(MoveTargetView.of(target, orderItemsRepository.findByOrderId(target.getOrderId()).size(),
                status, amount, reason));
    }

    @PostMapping("/dashboard/orders/{orderId}/updateSerialNumbers")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String updateSerialNumbers(@PathVariable String orderId, @ModelAttribute OrderItemsForm form,
                                      RedirectAttributes redirectAttributes, Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        if (order.isClosed()) {
            return refuse(redirectAttributes, orderId, "order.item.error.closed", locale);
        }
        // The dialog lists delivered products only; an item missing from the form keeps its number.
        Map<String, String> postedByItemId = new HashMap<>();
        form.getOrderItems().stream()
                .filter(posted -> posted.getItemId() != null)
                .forEach(posted -> postedByItemId.put(posted.getItemId(), StringUtils.trimToNull(posted.getSerialNo())));

        for (OrderItem item : orderItemsRepository.findByOrderId(orderId)) {
            if (item.isProduct() && postedByItemId.containsKey(item.getItemId())) {
                item.setSerialNo(postedByItemId.get(item.getItemId()));
                orderItemsRepository.save(item);
            }
        }

        return details(orderId);
    }

    /** The address dialog of the customer card as its own page, for a browser without JavaScript. */
    @GetMapping("/dashboard/orders/{orderId}/address")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String showAddressDetails(@PathVariable String orderId, @RequestParam String type, Model model,
                                     RedirectAttributes redirectAttributes, Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        if (!OrderAddressForm.isType(type)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        // the customer card hides "Edit" for the same reasons; a typed address must not reach a form that cannot be saved
        boolean billingType = "billing".equals(type);
        String locked = order.isClosed() ? "order.address.error.closed"
                : CustomerView.lockedKey(order, billingType,
                        billingType ? receiptAttemptService.receiptLock(order) : ReceiptLock.NONE);
        if (locked != null) {
            return refuse(redirectAttributes, orderId, locked, locale);
        }
        OrderAddressForm form = OrderAddressForm.BILLING.equals(type)
                ? OrderAddressForm.billing(orderId, order.getBillingDetails())
                : OrderAddressForm.shipping(orderId, order.getShippingDetails());
        return addressPage(order, form, model);
    }

    /**
     * Saves the billing or the shipping address from the customer card's dialog (async: 422 with the errors next to the
     * fields, 200 once saved, the dialog then reloads the page) or from the address page without JavaScript.
     */
    @PostMapping("/dashboard/orders/{orderId}/updateAddressDetails")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String updateAddressDetails(@PathVariable String orderId, @RequestParam String type,
                                       @ModelAttribute("order") Order updatedOrder,
                                       @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                       HttpServletRequest request, HttpServletResponse response, Model model,
                                       RedirectAttributes redirectAttributes, Locale locale) {
        Order existingOrder = requireOrder(ordersRepository, getStoreId(), orderId);
        boolean async = SettingsPaths.isAsync(requestedWith);
        if (!OrderAddressForm.isType(type)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        boolean billing = OrderAddressForm.BILLING.equals(type);
        OrderAddressForm posted = billing ? OrderAddressForm.billing(orderId, updatedOrder.getBillingDetails())
                : OrderAddressForm.shipping(orderId, updatedOrder.getShippingDetails());

        // once an invoice or a label exists the address is fixed; the card hides "Edit" with the same reason
        String refusal = existingOrder.isClosed() ? "order.address.error.closed"
                : CustomerView.lockedKey(existingOrder, billing,
                        billing ? receiptAttemptService.receiptLock(existingOrder) : ReceiptLock.NONE);
        if (refusal != null) {
            if (async) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                model.addAttribute("address", posted.withRefusal(messageSource.getMessage(refusal, null, locale)));
                return "orders/details/address :: dialogForm";
            }
            return refuse(redirectAttributes, orderId, refusal, locale);
        }

        OrderAddressForm saved = billing ? OrderAddressForm.billing(orderId, existingOrder.getBillingDetails())
                : OrderAddressForm.shipping(orderId, existingOrder.getShippingDetails());
        Map<String, String> errors = posted.validate(saved);
        if (!errors.isEmpty()) {
            if (async) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                model.addAttribute("address", posted.withErrors(errors));
                return "orders/details/address :: dialogForm";
            }
            return addressPage(existingOrder, posted.withErrors(errors), model);
        }

        if (billing) {
            BillingDetails details = updatedOrder.getBillingDetails() != null ? updatedOrder.getBillingDetails() : new BillingDetails();
            details.setCountry(posted.countryToStore(saved));
            existingOrder.setBillingDetails(details);
        } else {
            ShippingDetails details = updatedOrder.getShippingDetails() != null ? updatedOrder.getShippingDetails() : new ShippingDetails();
            details.setCountry(posted.countryToStore(saved));
            existingOrder.setShippingDetails(details);
        }
        ordersRepository.save(existingOrder);

        String notice = messageSource.getMessage(billing ? "order.address.billing.saved" : "order.address.shipping.saved",
                null, locale);
        if (async) {
            // the dialog reloads the page it is on (keeping its returnTo), which takes this notice
            OrderFlash.forNextPage(request, response, "/dashboard/orders/" + orderId,
                    new OrderNotice(OrderLabels.OK, notice, null, null));
            model.addAttribute("address", posted);
            return "orders/details/address :: dialogForm";
        }
        OrderFlash.saved(redirectAttributes, notice);
        return details(orderId);
    }

    private String addressPage(Order order, OrderAddressForm form, Model model) {
        model.addAttribute("address", form);
        model.addAttribute("shortId", order.getShortenedOrderId());
        return "orders/address";
    }

    @PostMapping("/dashboard/orders/{orderId}/updateReview")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String updateReview(@PathVariable String orderId, @ModelAttribute("order") Order updatedOrder,
                               RedirectAttributes redirectAttributes, Locale locale) {
        Order existingOrder = requireOrder(ordersRepository, getStoreId(), orderId);
        if (existingOrder.isClosed()) {
            return refuse(redirectAttributes, orderId, "order.review.error.closed", locale);
        }
        OrderReview posted = updatedOrder.getReview();
        // "not collected" posts an empty status: saving the dialog unchanged must not start collecting a review
        if (posted != null && posted.getStatus() != null) {
            existingOrder.setReview(posted);
        }
        orderLifecycle.update(existingOrder);
        OrderFlash.saved(redirectAttributes, messageSource.getMessage("order.review.saved", null, locale));
        return details(orderId);
    }

    @PostMapping("/dashboard/orders/{orderId}/addPayment")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String addPayment(@PathVariable String orderId,
                             @ModelAttribute AddPaymentForm form,
                             RedirectAttributes redirectAttributes,
                             Locale locale) {
        Order existingOrder = requireOrder(ordersRepository, getStoreId(), orderId);
        // the closed page offers no "Dodaj wpłatę"; a cancelled order would drop the payment while saying it was added
        if (existingOrder.isClosed()) {
            return refuse(redirectAttributes, orderId, closedPaymentsKey(existingOrder), locale);
        }

        String invalid = form.validate();
        if (invalid != null) {
            return refuse(redirectAttributes, orderId, invalid, locale);
        }
        PaymentDirection direction = form.getDirection() != null ? form.getDirection() : PaymentDirection.Incoming;
        // the sign follows the direction, as in a payment's own dialog: a refund is stored negative however it was
        // typed, and money that came in cannot be negative
        if (direction == PaymentDirection.Incoming && form.amount() < 0) {
            return refuse(redirectAttributes, orderId, "order.payments.error.negative", locale);
        }
        double bankAmount = direction == PaymentDirection.Outgoing ? -Math.abs(form.amount()) : form.amount();

        double amount = form.isFeeIncluded()
                ? bankAmount
                : bankAmount + form.fee();

        Payment target = existingOrder.getPayments().stream()
                .filter(Payment::isUnsettled)
                .findFirst()
                .orElseGet(() -> {
                    Payment p = new Payment();
                    existingOrder.addPayment(p);
                    return p;
                });

        target.setSource(form.getSource());
        target.setDirection(direction);
        target.setReferenceNo(form.getReferenceNo());
        target.setName(form.getName());
        target.setAmount(amount);
        target.setFee(form.fee());
        target.setBankTransactionNo(form.getBankTransactionNo());
        target.setBankTransactionDate(form.getBankTransactionDate());

        orderLifecycle.update(existingOrder);
        OrderFlash.saved(redirectAttributes, messageSource.getMessage("order.payments.added", null, locale));
        return details(orderId);
    }

    /** A payment's dialog as its own page, for a browser without JavaScript. */
    @GetMapping("/dashboard/orders/{orderId}/payments/{key}")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String showPayment(@PathVariable String orderId, @PathVariable String key, Model model,
                              RedirectAttributes redirectAttributes, Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        int index = paymentIndex(order, key);
        // the card offers no "Edit" on a closed order
        if (order.isClosed()) {
            return refuse(redirectAttributes, orderId, closedPaymentsKey(order), locale);
        }
        String methodLocked = OrderPaymentForm.methodLockedKey(order, receiptAttemptService.receiptLock(order));
        return paymentPage(order, OrderPaymentForm.of(orderId, index, order.getPayments().get(index), methodLocked), model);
    }

    /**
     * Saves one payment from its dialog (async: 422 with the errors next to the fields, 200 once saved, the dialog then
     * reloads the page) or from the payment page without JavaScript. version is the fingerprint of the payment the form
     * showed, so a payment changed meanwhile is not overwritten. Only the payment at index is replaced; the others stay
     * the objects they were. Saved through the lifecycle, as the whole-list save was: a changed amount may settle the
     * order or take it back to unpaid.
     */
    @PostMapping("/dashboard/orders/{orderId}/payments")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String savePayment(@PathVariable String orderId, @RequestParam int index,
                              @RequestParam(required = false) String version,
                              @RequestParam(required = false) PaymentSource source,
                              @RequestParam(required = false) String name, @RequestParam(required = false) String amount,
                              @RequestParam(required = false) String fee,
                              @RequestParam(required = false) String referenceNo,
                              @RequestParam(required = false) String bankTransactionNo,
                              @RequestParam(required = false) String bankTransactionDate,
                              @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                              HttpServletRequest request, HttpServletResponse response, Model model,
                              RedirectAttributes redirectAttributes, Locale locale) {
        Order existingOrder = requireOrder(ordersRepository, getStoreId(), orderId);
        boolean async = SettingsPaths.isAsync(requestedWith);
        List<Payment> current = existingOrder.getPayments() == null ? List.of() : existingOrder.getPayments();
        boolean known = index >= 0 && index < current.size();
        // the sale's document fixes the method (owner decision Q3); read once, for the check and the form shown again
        String methodLocked = OrderPaymentForm.methodLockedKey(existingOrder,
                receiptAttemptService.receiptLock(existingOrder));
        OrderPaymentForm posted = new OrderPaymentForm(orderId, index, version, known && current.get(index).isUnsettled(),
                known && OrderPaymentForm.isRefund(current.get(index)), source, name, amount, fee, referenceNo,
                bankTransactionNo, bankTransactionDate, null, null, methodLocked,
                known ? OrderPaymentForm.positiveRefundShift(current.get(index)) : null);
        String refusal = existingOrder.isClosed() ? closedPaymentsKey(existingOrder)
                : !known || !OrderPaymentForm.version(current.get(index)).equals(version) ? "order.payments.error.stale"
                : null;
        if (refusal != null) {
            if (async) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                model.addAttribute("payment", posted.withRefusal(messageSource.getMessage(refusal, null, locale)));
                return "orders/details/payments :: dialogForm";
            }
            return refuse(redirectAttributes, orderId, refusal, locale);
        }
        Map<String, String> errors = posted.validate(current.get(index));
        if (!errors.isEmpty()) {
            if (async) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                model.addAttribute("payment", posted.withErrors(errors));
                return "orders/details/payments :: dialogForm";
            }
            return paymentPage(existingOrder, posted.withErrors(errors), model);
        }

        List<Payment> payments = new ArrayList<>(current);
        payments.set(index, posted.toPayment(current.get(index)));
        existingOrder.setPayments(payments);
        orderLifecycle.update(existingOrder);

        String notice = messageSource.getMessage("order.payments.saved", new Object[]{index + 1}, locale);
        if (async) {
            // the dialog reloads the page it is on (keeping its returnTo), which takes this notice
            OrderFlash.forNextPage(request, response, "/dashboard/orders/" + orderId,
                    new OrderNotice(OrderLabels.OK, notice, null, null));
            model.addAttribute("payment", posted);
            return "orders/details/payments :: dialogForm";
        }
        OrderFlash.saved(redirectAttributes, notice);
        return details(orderId);
    }

    @GetMapping("/dashboard/orders/{orderId}/payments/{index}/remove")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String confirmRemovePayment(@PathVariable String orderId, @PathVariable int index,
                                       @RequestParam(required = false) String version, Model model,
                                       RedirectAttributes redirectAttributes, Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        String refusal = removePaymentRefusal(order, index, version);
        if (refusal != null) {
            return refuse(redirectAttributes, orderId, refusal, locale);
        }
        return confirmPage(model, order, "order.payments.remove", new Object[]{index + 1},
                messageSource.getMessage(OrderPageModelFactory.removePaymentMessageKey(order), null, locale),
                "/dashboard/orders/" + orderId + "/payments/" + index + "/remove?version=" + version, true, locale);
    }

    /**
     * Removes one payment. Removing the only one leaves a pending payment with its method, as the whole-list save did
     * when every row was cleared: the order never loses how the customer pays, and "Dodaj wpłatę" fills it again.
     */
    @PostMapping("/dashboard/orders/{orderId}/payments/{index}/remove")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String removePayment(@PathVariable String orderId, @PathVariable int index,
                                @RequestParam(required = false) String version,
                                RedirectAttributes redirectAttributes, Locale locale) {
        Order existingOrder = requireOrder(ordersRepository, getStoreId(), orderId);
        String refusal = removePaymentRefusal(existingOrder, index, version);
        if (refusal != null) {
            return refuse(redirectAttributes, orderId, refusal, locale);
        }
        List<Payment> payments = new ArrayList<>(existingOrder.getPayments());
        Payment removed = payments.remove(index);
        if (payments.isEmpty()) {
            payments.add(new Payment(removed.getSource()));
        }
        existingOrder.setPayments(payments);
        orderLifecycle.update(existingOrder);
        OrderFlash.saved(redirectAttributes, messageSource.getMessage("order.payments.removed", new Object[]{index + 1}, locale));
        return details(orderId);
    }

    /** The index of one of the order's payments, or 404. */
    private static int paymentIndex(Order order, String key) {
        try {
            int index = Integer.parseInt(key);
            if (order.getPayments() != null && index >= 0 && index < order.getPayments().size()) {
                return index;
            }
        } catch (NumberFormatException ignored) {
            // falls through to 404
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    /**
     * Payments of a closed order are not added, edited or removed, as its shipments are not: the card offers none of
     * these actions, and a cancelled order would lose the save anyway (OrderLifecycle.update does not persist it). This
     * replaces the decision of 2026-09-27 that let a completed order take the change (review 2, R-27).
     */
    private static String closedPaymentsKey(Order order) {
        return order.getStatus() == OrderStatus.Cancelled ? "order.payments.error.cancelled"
                : "order.payments.error.closed";
    }

    /** A closed order refuses the removal (see closedPaymentsKey), and so does a payment changed meanwhile. */
    private static String removePaymentRefusal(Order order, int index, String version) {
        if (order.isClosed()) {
            return closedPaymentsKey(order);
        }
        List<Payment> payments = order.getPayments() == null ? List.of() : order.getPayments();
        if (index < 0 || index >= payments.size() || !OrderPaymentForm.version(payments.get(index)).equals(version)) {
            return "order.payments.error.stale";
        }
        return OrderPageModelFactory.removePaymentLockedKey(order, index);
    }

    private String paymentPage(Order order, OrderPaymentForm form, Model model) {
        model.addAttribute("payment", form);
        model.addAttribute("shortId", order.getShortenedOrderId());
        return "orders/payment";
    }

    /** A shipment's dialog as its own page, for a browser without JavaScript: "new" or the shipment's index. */
    @GetMapping("/dashboard/orders/{orderId}/shipments/{key}")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String showShipment(@PathVariable String orderId, @PathVariable String key, Model model,
                               RedirectAttributes redirectAttributes, Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        Integer index = shipmentIndex(order, key);
        // the card offers no "Edit" or "Add shipment" on a closed order
        if (order.isClosed()) {
            return refuse(redirectAttributes, orderId, "order.shipments.error.closed", locale);
        }
        OrderShipmentForm form = index == null ? OrderShipmentForm.blank(order, shipmentCarriers(order))
                : OrderShipmentForm.of(orderId, index, order.getShipments().get(index), shipmentCarriers(order));
        return shipmentPage(order, form, model);
    }

    /**
     * Saves one shipment from its dialog (async: 422 with the errors next to the fields, 200 once saved, the dialog then
     * reloads the page) or from the shipment page without JavaScript. index is absent for a new shipment; version is the
     * fingerprint of the shipment the form showed, so a shipment changed meanwhile (tracking, another operator) is not
     * overwritten. The other shipments of the order stay as they are.
     */
    @PostMapping("/dashboard/orders/{orderId}/shipments")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String saveShipment(@PathVariable String orderId, @RequestParam(required = false) Integer index,
                               @RequestParam(required = false) String version, @RequestParam(required = false) ShipmentType type,
                               @RequestParam(required = false) String carrier, @RequestParam(required = false) String trackingNo,
                               @RequestParam(required = false) String collectionPointCode,
                               @RequestParam(required = false) String trackingUrl,
                               @RequestParam(required = false) String shippedDate,
                               @RequestParam(required = false) String deliveredDate,
                               @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                               HttpServletRequest request, HttpServletResponse response, Model model,
                               RedirectAttributes redirectAttributes, Locale locale) {
        Order existingOrder = requireOrder(ordersRepository, getStoreId(), orderId);
        boolean async = SettingsPaths.isAsync(requestedWith);
        List<Shipment> current = existingOrder.getShipments();
        boolean stale = index != null && (index < 0 || index >= current.size()
                || !OrderShipmentForm.version(current.get(index)).equals(version));
        Shipment before = index == null || stale ? null : current.get(index);
        OrderShipmentForm posted = new OrderShipmentForm(orderId, index, version, type, carrier, trackingNo,
                collectionPointCode, trackingUrl, shippedDate, deliveredDate,
                shipmentCarriers(existingOrder), null, null, before != null && before.getExternalId() != null, null);
        // the card hides "Edit" on a closed order; besides, OrderLifecycle.update never persists a cancelled one
        String refusal = existingOrder.isClosed() ? "order.shipments.error.closed"
                : stale ? "order.shipments.error.stale" : null;
        if (refusal != null) {
            if (async) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                model.addAttribute("shipment", posted.withRefusal(messageSource.getMessage(refusal, null, locale)));
                return "orders/details/shipments :: dialogForm";
            }
            return refuse(redirectAttributes, orderId, refusal, locale);
        }
        Map<String, String> errors = new LinkedHashMap<>(posted.validate(before));
        // a double click or a second operator would store the same parcel twice and announce it twice
        if (index == null && StringUtils.isNotBlank(trackingNo) && current.stream().anyMatch(s -> s.hasTrackingNo(trackingNo))) {
            errors.putIfAbsent(posted.field("trackingNo"), "order.shipments.error.duplicateTracking");
        }
        if (!errors.isEmpty()) {
            if (async) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                model.addAttribute("shipment", posted.withErrors(errors));
                return "orders/details/shipments :: dialogForm";
            }
            return shipmentPage(existingOrder, posted.withErrors(errors), model);
        }

        List<Shipment> shipments = new ArrayList<>(current);
        // a new shipment fills the only placeholder (the customer's choice kept in place of a removed shipment, or the
        // one every order is created with) instead of standing next to it and holding the order back from Shipping
        boolean fillsPlaceholder = index == null && existingOrder.onlyPlaceholder().isPresent();
        Shipment saved = posted.toShipment(before);
        if (fillsPlaceholder) {
            shipments.set(0, saved);
        } else if (index == null) {
            shipments.add(saved);
        } else {
            shipments.set(index, saved);
        }
        storeShipments(existingOrder, shipments, saved, before);

        String notice = index == null ? messageSource.getMessage("order.shipments.added", null, locale)
                : messageSource.getMessage("order.shipments.saved", new Object[]{index + 1}, locale);
        if (async) {
            // the dialog reloads the page it is on (keeping its returnTo), which takes this notice
            OrderFlash.forNextPage(request, response, "/dashboard/orders/" + orderId,
                    new OrderNotice(OrderLabels.OK, notice, null, null));
            model.addAttribute("shipment", posted);
            return "orders/details/shipments :: dialogForm";
        }
        OrderFlash.saved(redirectAttributes, notice);
        return details(orderId);
    }

    @GetMapping("/dashboard/orders/{orderId}/shipments/{index}/remove")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String confirmRemoveShipment(@PathVariable String orderId, @PathVariable int index,
                                        @RequestParam(required = false) String version, Model model,
                                        RedirectAttributes redirectAttributes, Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        String refusal = removeShipmentRefusal(order, index, version);
        if (refusal != null) {
            return refuse(redirectAttributes, orderId, refusal, locale);
        }
        // the same text and button as the card's confirmation dialog: they say when the removal delivers the order
        return OrderConfirmPages.render(model, new ConfirmAction(
                messageSource.getMessage("order.shipments.remove.confirm.title", new Object[]{index + 1}, locale),
                messageSource.getMessage(OrderPageModelFactory.removeShipmentMessageKey(order, index), null, locale),
                messageSource.getMessage(OrderPageModelFactory.removeShipmentActionKey(order, index), null, locale),
                "/dashboard/orders/" + orderId + "/shipments/" + index + "/remove?version=" + version,
                "/dashboard/orders/" + orderId, true), orderPageTitle(order, locale));
    }

    /**
     * Removes one shipment. No "shipment created" notice goes out: nothing was shipped by removing a record. The only
     * shipment is not dropped but goes back to waiting to be shipped: a placeholder keeps how the customer asked to
     * receive the order (type, pickup point), which the customer card, the client page and the dropship flow read from
     * the shipment, and the order keeps a shipment to deliver. Once no shipment has shipping data left, the shipping
     * e-mail is forgotten (as "Cancel courier order" does), so the customer gets it with the number of the shipment
     * added next instead of keeping a link to the removed one. Removing the last undelivered shipment while the others
     * are delivered delivers the order; the confirmation says so (OrderPageModelFactory.removeShipmentMessageKey).
     */
    @PostMapping("/dashboard/orders/{orderId}/shipments/{index}/remove")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String removeShipment(@PathVariable String orderId, @PathVariable int index,
                                 @RequestParam(required = false) String version,
                                 RedirectAttributes redirectAttributes, Locale locale) {
        Order existingOrder = requireOrder(ordersRepository, getStoreId(), orderId);
        String refusal = removeShipmentRefusal(existingOrder, index, version);
        if (refusal != null) {
            return refuse(redirectAttributes, orderId, refusal, locale);
        }
        List<Shipment> shipments = new ArrayList<>(existingOrder.getShipments());
        Shipment removed = shipments.remove(index);
        if (shipments.isEmpty()) {
            shipments.add(Shipment.placeholderFor(removed));
        }
        storeShipments(existingOrder, shipments, null, null);
        if (removed.hasShippingData() && existingOrder.firstShipmentWithShippingData().isEmpty()) {
            orderEventsRepository.deleteByOrderIdAndName(orderId, EmailNotificationType.ORDER_SHIPPING.name());
        }
        OrderFlash.saved(redirectAttributes, messageSource.getMessage("order.shipments.removed", new Object[]{index + 1}, locale));
        return details(orderId);
    }

    /** "new" is a new shipment (null); anything else must be the index of one of the order's shipments. */
    private static Integer shipmentIndex(Order order, String key) {
        if ("new".equals(key)) {
            return null;
        }
        try {
            int index = Integer.parseInt(key);
            if (index >= 0 && index < order.getShipments().size()) {
                return index;
            }
        } catch (NumberFormatException ignored) {
            // falls through to 404
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private static String removeShipmentRefusal(Order order, int index, String version) {
        if (order.isClosed()) {
            return "order.shipments.error.closed";
        }
        List<Shipment> shipments = order.getShipments();
        if (index < 0 || index >= shipments.size() || !OrderShipmentForm.version(shipments.get(index)).equals(version)) {
            return "order.shipments.error.stale";
        }
        return OrderPageModelFactory.removeLockedKey(order, index);
    }

    /**
     * Stores the order's new list of shipments: subscribes the new tracking numbers and saves through the lifecycle (a
     * delivery date on every shipment moves the order to Delivered). saved is the shipment just added or edited (null
     * for a removal) and before its previous state (null for a new one): only its own shipping data, when new or
     * changed, is announced (the e-mail to the customer, the number to the marketplace), never a shipment already
     * announced that merely sits next to it, nor a corrected date of one already announced.
     */
    private void storeShipments(Order existingOrder, List<Shipment> shipments, Shipment saved, Shipment before) {
        existingOrder.setShipments(shipments);
        shipmentTrackingSubscriber.subscribe(getStoreId(), existingOrder);
        orderLifecycle.update(existingOrder);
        boolean notifiable = saved != null && isAnnounceable(saved);
        boolean changed = saved != null && (before == null || !isAnnounceable(before)
                || !shipmentData(saved).equals(shipmentData(before)));
        if (notifiable && changed) {
            orderLifecycleEventPublisher.publish(existingOrder, OrderLifecycleEventType.ShipmentCreated);
        }
    }

    private List<String> shipmentCarriers(Order order) {
        Store store = storesRepository.findById(getStoreId());
        return store == null ? List.of() : shipmentCarrierOptions.forOrder(order, store);
    }

    private String shipmentPage(Order order, OrderShipmentForm form, Model model) {
        model.addAttribute("shipment", form);
        model.addAttribute("shortId", order.getShortenedOrderId());
        return "orders/shipment";
    }

    /**
     * Why the order's items cannot be removed, split off or moved for its e-receipt, or null: the attempt's frozen
     * request lists them (OrderPageModelFactory greys the same actions with the same reason).
     */
    private String receiptLockedKey(String orderId) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        ReceiptLock receiptLock = receiptAttemptService.receiptLock(order);
        return receiptLock.locks() ? receiptLock.key(BulkReason.RECEIPT_ISSUING.key()) : null;
    }

    /** The receipt row was written by an e-receipt attempt (its document id is the attempt's key), not typed in. */
    private boolean isAutomaticReceipt(Order order, DocumentType type, String number) {
        if (type != DocumentType.Receipt) {
            return false;
        }
        List<ReceiptAttempt> attempts = receiptAttemptService.attemptsOf(order.getStoreId(), order.getOrderId());
        return order.getDocuments().stream()
                .filter(d -> d.getType() == DocumentType.Receipt && Objects.equals(d.getNumber(), number))
                .anyMatch(d -> attempts.stream().anyMatch(a -> a.getReceiptKey().equals(d.getId())));
    }

    /** A shipment the customer and the marketplace are told about: it went out, with its number or for collection. */
    private static boolean isAnnounceable(Shipment s) {
        return s.hasShippingData() || s.hasCollectionData();
    }

    /**
     * What the marketplace is told about a shipment (ShipmentUpdate: number, carrier, link). The shipped date is not
     * part of it: a corrected date of a shipment already announced would send the same shipOrder again.
     */
    private static String shipmentData(Shipment s) {
        return String.join("|",
                String.valueOf(s.getType()),
                Objects.toString(s.getCarrier(), ""),
                Objects.toString(s.getTrackingNo(), ""),
                Objects.toString(s.getTrackingUrl(), ""));
    }

    @PostMapping("/dashboard/orders/{orderId}/addReceipt")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String addReceipt(@PathVariable String orderId, @ModelAttribute Document document,
                             RedirectAttributes redirectAttributes, Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        // an attempt that is issuing, fiscalised or closed by hand already owns the order's receipt: a typed one would
        // be a second receipt for the same sale
        if (document.getType() == DocumentType.Receipt && receiptAttemptService.blocksManualReceipt(getStoreId(), orderId)) {
            return refuse(redirectAttributes, orderId, "receipts.document.add.live", locale);
        }
        String locked = OrderPageModelFactory.addDocumentLockedKey(order, document.getType(),
                receiptAttemptService.receiptLock(order));
        if (locked != null) {
            return refuse(redirectAttributes, orderId, locked, locale);
        }
        // the dialog marks the number as required; a blank one would add a row nobody can unpin (removal matches on it)
        if (StringUtils.isBlank(document.getNumber()) || document.getType() == null) {
            return refuse(redirectAttributes, orderId, "order.documents.add.error.number", locale);
        }
        // the link is shown to every user of the store as a clickable address: only a web address is accepted
        String link = StringUtils.trimToNull(document.getLink());
        if (link != null && OrderPageModelFactory.safeWebUrl(link) == null) {
            return refuse(redirectAttributes, orderId, "order.documents.add.error.link", locale);
        }
        // a typed document has no id, as the dialog posts none: an e-receipt attempt's key posted here would make it
        // pass for that attempt's own document, hidden from the card and impossible to unpin
        document.setId(null);
        document.setNumber(document.getNumber().trim());
        document.setLink(link);
        order.addDocument(document);
        orderLifecycle.update(order);
        OrderFlash.saved(redirectAttributes, messageSource.getMessage("order.documents.added", null, locale));
        return details(orderId);
    }

    @GetMapping("/dashboard/orders/{orderId}/removeDocument")
    @PreAuthorize("hasRole('ADMIN')")
    public String confirmRemoveDocument(@PathVariable String orderId, @RequestParam DocumentType type,
                                        @RequestParam(required = false) String number, Model model, Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        boolean present = order.getDocuments().stream()
                .anyMatch(d -> d.getType() == type && Objects.equals(d.getNumber(), number));
        if (!present || isAutomaticReceipt(order, type, number)) {
            return details(orderId);
        }
        return confirmPage(model, order, "order.documents.unpin", new Object[]{number}, null,
                OrderLinks.removeDocumentPath(orderId, type, number), true, locale);
    }

    @PostMapping("/dashboard/orders/{orderId}/removeDocument")
    @PreAuthorize("hasRole('ADMIN')")
    public String removeDocument(@PathVariable String orderId, @RequestParam DocumentType type,
                                 @RequestParam(required = false) String number,
                                 RedirectAttributes redirectAttributes, Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);

        // an automatically issued receipt's document id is the attempt's own key; removing it here would strand the
        // order's document without ever touching the attempt, so the attempt would still poll or hold its result
        if (isAutomaticReceipt(order, type, number)) {
            return refuse(redirectAttributes, orderId, "receipts.document.remove.automatic", locale);
        }

        if (order.isClosed() || !order.removeDocument(type, number)) {
            return refuse(redirectAttributes, orderId, "error.message.document.cannot.be.removed", locale);
        }

        // saving via OrderLifecycle would re-trigger automatic invoice generation for delivered orders
        ordersRepository.save(order);
        // so the bell hears here that the order may have lost the document that settled its dead e-receipt attempts
        receiptAttemptService.reconcileDeadAttemptAlerts(order);
        OrderFlash.saved(redirectAttributes, messageSource.getMessage("order.documents.unpinned", null, locale));
        return details(orderId);
    }

    @GetMapping("/dashboard/orders/{orderId}/cancelShipment")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String confirmCancelShipment(@PathVariable String orderId, Model model, Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        return confirmPage(model, order, "order.shipments.cancel", null, null,
                "/dashboard/orders/" + orderId + "/cancelShipment", true, locale);
    }

    @PostMapping("/dashboard/orders/{orderId}/cancelShipment")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String cancelShipment(@PathVariable String orderId,
                                 RedirectAttributes redirectAttributes, Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        Optional<Shipment> sent = order.firstShipmentWithShippingData();
        // the same shipment ShipmentCancelService picks; its English errors never reach the operator
        String refusal = sent.isEmpty() ? "order.shipments.cancel.error.no.data"
                : sent.get().getExternalId() == null ? "order.shipments.cancel.error.no.package"
                : sent.get().isCancellationInProgress(LocalDateTime.now()) ? "order.shipments.cancel.error.pending" : null;
        if (refusal != null) {
            return refuse(redirectAttributes, orderId, refusal, locale);
        }
        try {
            shipmentCancelService.cancelShipping(orderId, getStoreId());
            OrderFlash.saved(redirectAttributes, messageSource.getMessage("shipment.cancel.requested", null, locale));
        } catch (HttpClientException ex) {
            return handleHttpClientException(ex, orderId, redirectAttributes);
        } catch (ShippingException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return details(orderId);
    }

    private String handleHttpClientException(HttpClientException ex, String orderId,
                                             RedirectAttributes redirectAttributes) {
        String error = ex.getResponseBody();
        error = Strings.isBlank(error) ? ex.getMessage() : error;
        redirectAttributes.addFlashAttribute("errorMessage", error);
        return details(orderId);
    }

}
