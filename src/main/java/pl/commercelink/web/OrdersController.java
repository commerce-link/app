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
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.pricelist.PricelistFinder;
import pl.commercelink.products.StoreCategories;
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
import pl.commercelink.web.orders.CustomerView;
import pl.commercelink.web.orders.Money;
import pl.commercelink.web.orders.MoveTargetView;
import pl.commercelink.web.orders.OrderBackLink;
import pl.commercelink.web.orders.OrderConfirmPages;
import pl.commercelink.web.orders.OrderFlash;
import pl.commercelink.web.orders.OrderLabels;
import pl.commercelink.web.orders.OrderLinks;
import pl.commercelink.web.orders.OrderNotice;
import pl.commercelink.web.orders.OrderPageModel;
import pl.commercelink.web.orders.OrderPageModelFactory;
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
import java.time.temporal.ChronoUnit;
import pl.commercelink.inventory.deliveries.DropshipItemLookup;
import pl.commercelink.inventory.supplier.SupplierChoice;
import pl.commercelink.inventory.supplier.SupplierLabels;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
    private OrderReferenceResolver orderReferenceResolver;

    @GetMapping("/dashboard/orders")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String orders(@RequestParam MultiValueMap<String, String> params, Locale locale, Model model) {
        Optional<String> legacy = OrderListQuery.legacyRedirect(params);
        if (legacy.isPresent()) {
            return "redirect:" + legacy.get();
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
            orderFilters.create(actor(), form.isSharedWithStore(), form.getLabel(), form.toConditions());
            return safeReturnTo(form.getReturnTo());
        });
    }

    @PostMapping("/dashboard/orders/filters/update")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String updateOrderFilter(@RequestParam String filterId, OrderFilterForm form, RedirectAttributes redirectAttributes,
                                    Model model, Locale locale, HttpServletResponse response) {
        return filterAction(form.getReturnTo(), redirectAttributes, model, locale, response, form, filterId, () -> {
            orderFilters.update(actor(), filterId, form.isSharedWithStore(), form.getLabel(), form.toConditions());
            return safeReturnTo(form.getReturnTo());
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
        addFilterEditAttributes(model, safeListReturnTo(returnTo), filterId, formOf(filter, shared), locale);
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
        Map<String, String> byField = filter.getConditionsByField();
        OrderFilterForm form = new OrderFilterForm();
        form.setLabel(filter.getLabel());
        form.setSharedWithStore(shared);
        form.setStatus(byField.get(OrderFilterField.Status.name()));
        form.setShipmentType(byField.get(OrderFilterField.ShipmentType.name()));
        form.setPaymentSource(byField.get(OrderFilterField.PaymentSource.name()));
        form.setShippingDue(byField.get(OrderFilterField.ShippingDue.name()));
        form.setSourceName(byField.get(OrderFilterField.SourceName.name()));
        form.setShippingPostalCode(byField.get(OrderFilterField.ShippingPostalCode.name()));
        return form;
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
        model.addAttribute("order", order);
        return "orders/details";
    }

    @GetMapping("/dashboard/orders/{orderId}/status")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String statusPage(@PathVariable String orderId, Model model, RedirectAttributes redirectAttributes,
                             Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        if (order.hasOneOfStatuses(OrderStatus.Completed, OrderStatus.Cancelled)) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("order.status.error.closed", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
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
        if (order.hasOneOfStatuses(OrderStatus.Completed, OrderStatus.Cancelled)) {
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
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage(refusal, null, locale));
            return "redirect:/dashboard/orders/" + orderId;
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
        return "redirect:/dashboard/orders/" + orderId;
    }

    /** The settings dialog as its own page, for a browser without JavaScript. */
    @GetMapping("/dashboard/orders/{orderId}/settings")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String settingsPage(@PathVariable String orderId, Model model, RedirectAttributes redirectAttributes,
                               Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        if (order.hasOneOfStatuses(OrderStatus.Completed, OrderStatus.Cancelled)) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("order.settings.error.closed", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
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
        String refusal = existingOrder.hasOneOfStatuses(OrderStatus.Completed, OrderStatus.Cancelled) ? "order.settings.error.closed"
                : fulfilmentTypeChanged && !existingOrder.canChangeFulfilmentType(items) ? "order.fulfilment.type.locked"
                : null;
        if (refusal != null) {
            String error = messageSource.getMessage(refusal, null, locale);
            if (async) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                return settingsFragment(existingOrder, items, model, error, null);
            }
            redirectAttributes.addFlashAttribute("errorMessage", error);
            return "redirect:/dashboard/orders/" + orderId;
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
        return "redirect:/dashboard/orders/" + orderId;
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
    public String confirmDeleteOrder(@PathVariable String orderId, Model model, Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        String message = order.isMarketplaceOrder()
                ? messageSource.getMessage("order.page.delete.confirm.marketplace", new Object[]{order.getSource().getName()}, locale)
                : messageSource.getMessage("order.page.delete.confirm.message", null, locale);
        return OrderConfirmPages.render(model, new ConfirmAction(
                messageSource.getMessage("order.page.delete.confirm.title", new Object[]{order.getShortenedOrderId()}, locale),
                message, messageSource.getMessage("order.page.delete.confirm.action", null, locale),
                "/dashboard/orders/" + orderId + "/delete", "/dashboard/orders/" + orderId),
                orderPageTitle(order, locale));
    }

    @GetMapping("/dashboard/orders/{orderId}/cancel")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String confirmCancelOrder(@PathVariable String orderId, Model model, Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        return OrderConfirmPages.render(model, new ConfirmAction(
                messageSource.getMessage("order.page.cancel.confirm.title", new Object[]{order.getShortenedOrderId()}, locale),
                messageSource.getMessage("order.page.cancel.confirm.message", null, locale),
                messageSource.getMessage("order.page.cancel.confirm.action", null, locale),
                "/dashboard/orders/" + orderId + "/cancel", "/dashboard/orders/" + orderId),
                orderPageTitle(order, locale));
    }

    private String orderPageTitle(Order order, Locale locale) {
        return messageSource.getMessage("order.page.title", new Object[]{order.getShortenedOrderId()}, locale);
    }

    @PostMapping("/dashboard/orders/{orderId}/add-items")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String addOrderItems(@PathVariable String orderId, @ModelAttribute AddItemsForm form,
                                RedirectAttributes redirectAttributes, Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        // the same reasons the page greys the "add items" button with; a stale page must not get past them
        List<OrderItem> items = orderItemsRepository.findByOrderId(orderId);
        String locked = OrderPageModelFactory.addItemsLockedKey(order,
                !dropshipItemLookup.itemIdsInDropshipDeliveries(getStoreId(), items).isEmpty());
        if (locked != null) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage(locked, null, locale));
            return "redirect:/dashboard/orders/" + orderId;
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
        return "redirect:/dashboard/orders/" + orderId;
    }

    private AvailabilityAndPrice pricelistEntry(AddItemsForm.Entry entry) {
        return pricelistFinder.findByPimId(getStoreId(), entry.getCatalogId(), entry.getPimId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/dashboard/orders/{orderId}/collection")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String getOrderCollectionProtocol(@PathVariable("orderId") String orderId, Model model) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        return renderOrderCollectionProtocol(order, model);
    }

    @GetMapping("/dashboard/store/{storeId}/orders/{orderId}/collection")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String getOrderCollectionProtocolForSuperAdmin(@PathVariable("storeId") String storeId, @PathVariable("orderId") String orderId, Model model) {
        Order order = requireOrder(ordersRepository, storeId, orderId);
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
        model.addAttribute("order", requireOrder(ordersRepository, getStoreId(), orderId));
        model.addAttribute("orderItems", orderItemsRepository.findByOrderId(orderId));

        return "orderCard";
    }

    @GetMapping("/dashboard/store/{storeId}/orders/{orderId}/card")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String getOrderCardForSuperAdmin(@PathVariable("storeId") String storeId, @PathVariable("orderId") String orderId, Model model) {
        model.addAttribute("order", requireOrder(ordersRepository, storeId, orderId));
        model.addAttribute("orderItems", orderItemsRepository.findByOrderId(orderId));

        return "orderCard";
    }

    // Without JavaScript the "Wystaw" menu entry leads here instead of opening the issue dialog: the same question with
    // the same "send to the customer" checkbox, posting to the unchanged invoicing endpoint.
    @GetMapping("/dashboard/orders/{orderId}/invoicing")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String confirmInvoice(@PathVariable String orderId, @RequestParam DocumentType documentType, Model model,
                                 Locale locale, RedirectAttributes redirectAttributes) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        if (!order.getIssuableDocumentTypes().contains(documentType)) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("error.message.no.eligible.invoice.to.create", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }
        return OrderConfirmPages.renderInvoicing(model, orderId, documentType, orderPageTitle(order, locale));
    }

    @PostMapping("/dashboard/orders/{orderId}/invoicing")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String createInvoice(@PathVariable String orderId, @RequestParam DocumentType documentType, @RequestParam(defaultValue = "false") boolean send, Locale locale, RedirectAttributes redirectAttributes) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);

        if (!order.getIssuableDocumentTypes().contains(documentType)) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("error.message.no.eligible.invoice.to.create", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }

        invoiceCreationEventPublisher.publish(order, documentType, send);
        issueStarted(redirectAttributes, orderId, "order.documents.issue.started", locale);

        return "redirect:/dashboard/orders/" + orderId;
    }

    @GetMapping("/dashboard/orders/{orderId}/goods-out")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String confirmGoodsOut(@PathVariable String orderId, Model model, Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        return OrderConfirmPages.render(model, new ConfirmAction(
                messageSource.getMessage("order.documents.goods.issue.confirm.title", null, locale),
                messageSource.getMessage("order.documents.goods.issue.confirm.message", null, locale),
                messageSource.getMessage("order.documents.goods.issue.confirm.action", null, locale),
                "/dashboard/orders/" + orderId + "/goods-out", "/dashboard/orders/" + orderId, false),
                orderPageTitle(order, locale));
    }

    @PostMapping("/dashboard/orders/{orderId}/goods-out")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String issueGoodsOut(@PathVariable String orderId, Locale locale, RedirectAttributes redirectAttributes) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);

        if (order.getDocumentByType(DocumentType.GoodsIssue).isPresent()) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("error.message.goods.issue.already.exists", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }

        String createdBy = CustomSecurityContext.getLoggedInUser()
                .map(CustomUser::getName)
                .orElse("System");
        goodsOutEventPublisher.publish(order, createdBy);
        issueStarted(redirectAttributes, orderId, "order.documents.goods.issue.started", locale);

        return "redirect:/dashboard/orders/" + orderId;
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
    public String saveOrderItem(@PathVariable String orderId, @PathVariable String itemId, @ModelAttribute OrderItem updatedItem, Model model) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        List<OrderItem> orderItems = orderItemsRepository.findByOrderId(orderId);

        Optional<OrderItem> op = orderItems.stream()
                .filter(i -> i.getItemId().equals(itemId))
                .findFirst();

        if (op.isPresent()) {
            OrderItem orderItem = op.get();

            boolean wasService = orderItem.isService();
            boolean serviceFlagLocked = orderItem.hasSupplierAllocation();
            boolean priceLocked = !order.getDocuments().isEmpty();

            String postedDeliveryId = StringUtils.trimToNull(updatedItem.getDeliveryId());
            boolean deliveryIdChanged = postedDeliveryId != null && !postedDeliveryId.equals(orderItem.getDeliveryId());
            if (deliveryIdChanged) {
                Store store = storesRepository.findById(getStoreId());
                // Same rules as the "assign supplier" modal: a connection identity or a typed name.
                SupplierChoice.Resolution resolution = supplierChoice.resolve(store, postedDeliveryId, null);
                if (!resolution.accepted()) {
                    model.addAttribute("errorMessage", messageSource.getMessage(
                            resolution.errorCode(), resolution.errorArgs(), LocaleContextHolder.getLocale()));
                    return showOrderItemDetails(order, orderItem, model);
                }
                postedDeliveryId = resolution.identity();
                updatedItem.setDeliveryId(postedDeliveryId);
                if (!ExternalSupplierBinding.of(store, List.of(order)).permits(orderId, postedDeliveryId)) {
                    model.addAttribute("errorMessage",
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
        return "redirect:/dashboard/orders/" + orderId;
    }

    private String refuseSupplier(String orderId, AssignSupplierForm form, Store store, boolean async,
                                  HttpServletResponse response, Model model, RedirectAttributes redirectAttributes,
                                  String reason) {
        if (async) {
            response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
            return supplierFormFragment(orderId, form, store, model, reason);
        }
        redirectAttributes.addFlashAttribute("errorMessage", reason);
        return "redirect:/dashboard/orders/" + orderId;
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
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("order.item.clear.assign.blocked", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }
        orderItem.removeFulfilment();
        orderItemsRepository.save(orderItem);
        OrderFlash.saved(redirectAttributes, messageSource.getMessage("order.item.supplier.cleared", null, locale));
        return "redirect:/dashboard/orders/" + orderId;
    }

    @GetMapping("/dashboard/orders/{orderId}/clear-supplier")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String confirmClearSupplier(@PathVariable String orderId, @RequestParam String itemId, Model model, Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        OrderItem item = requireItem(orderId, itemId);
        String action = UriComponentsBuilder.fromPath("/dashboard/orders/" + orderId + "/clear-supplier")
                .queryParam("itemId", itemId).encode().build().toUriString();
        return OrderConfirmPages.render(model, new ConfirmAction(
                messageSource.getMessage("order.item.clear.confirm.title", new Object[]{item.getName()}, locale),
                messageSource.getMessage("order.item.clear.confirm.message", null, locale),
                messageSource.getMessage("order.item.clear.confirm.action", null, locale),
                action, "/dashboard/orders/" + orderId), orderPageTitle(order, locale));
    }

    @PostMapping("/dashboard/orders/{orderId}/assign-sku")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String assignSku(@PathVariable String orderId, @RequestParam String itemId, @RequestParam String sku,
                            RedirectAttributes redirectAttributes, Locale locale) {
        requireOrder(ordersRepository, getStoreId(), orderId);
        OrderItem orderItem = requireItem(orderId, itemId);
        // The same rule the menu shows: the SKU feeds marketplace item keys and RMA, so it is set before fulfilment only.
        if (!orderItem.isNew() || orderItem.isGroup()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("order.item.assign.sku.locked", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }
        orderItem.setSku(sku);
        orderItemsRepository.save(orderItem);
        return "redirect:/dashboard/orders/" + orderId;
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
            String code = "error.message." + e.getMessage();
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage(code, null, locale));
        }
        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/split-group")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String splitGroupItem(@PathVariable String orderId, @ModelAttribute SplitGroupForm form,
                                 RedirectAttributes redirectAttributes, Locale locale) {
        requireOrder(ordersRepository, getStoreId(), orderId);
        requireItem(orderId, form.getItemId());
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
    public String toggleConsolidation(@PathVariable String orderId, @RequestParam String itemId,
                                      RedirectAttributes redirectAttributes, Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        // The flag is only read when the closing invoice is issued; after that a change would silently not apply.
        if (order.isInvoiced()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("order.item.consolidation.locked", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }
        OrderItem orderItem = requireItem(orderId, itemId);
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
        requireOrder(ordersRepository, getStoreId(), orderId);
        ordersManager.deleteOrder(getStoreId(), orderId);
        return "redirect:/dashboard/orders";
    }

    @PostMapping("/dashboard/orders/{orderId}/cancel")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String cancelOrder(@PathVariable String orderId, RedirectAttributes redirectAttributes, Locale locale) {
        requireOrder(ordersRepository, getStoreId(), orderId);
        try {
            ordersManager.cancelOrder(getStoreId(), orderId);
            OrderFlash.saved(redirectAttributes, messageSource.getMessage("order.cancelled", null, locale));
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("error.message.order.cannot.be.cancelled", null, locale));
        }
        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/removeSelectedItemsFromOrder")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String removeSelectedItemsFromOrder(@PathVariable String orderId, @ModelAttribute OrderItemsForm form,
                                               RedirectAttributes redirectAttributes, Locale locale) {
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

    /** A complete action says how many items changed; a partial one warns and says why the rest was left. */
    private String bulk(BulkAction action, String orderId, OrderItemsForm form, RedirectAttributes redirectAttributes,
                        Locale locale, Function<List<String>, OrdersManager.Result> run) {
        requireOrder(ordersRepository, getStoreId(), orderId);
        List<String> selected = form.getSelectedOrderItemIds();
        if (selected.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("order.bulk.none.selected", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }
        BulkActionResult result = BulkActionResult.of(action, run.apply(selected));
        String message = result.message(messageSource, locale);
        if (result.complete()) {
            OrderFlash.saved(redirectAttributes, message);
        } else {
            OrderFlash.warning(redirectAttributes, message);
        }
        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/splitOrder")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String splitOrder(@PathVariable String orderId, @ModelAttribute OrderItemsForm form,
                             RedirectAttributes redirectAttributes, Locale locale) {
        requireOrder(ordersRepository, getStoreId(), orderId);
        try {
            Order newOrder = ordersManager.splitOrder(getStoreId(), orderId, form.getSelectedOrderItemIds());
            OrderFlash.saved(redirectAttributes, messageSource.getMessage("order.bulk.split.done",
                    new Object[]{form.getSelectedOrderItemIds().size()}, locale));
            return "redirect:/dashboard/orders/" + newOrder.getOrderId();
        } catch (IllegalStateException e) {
            String code = "error.message." + e.getMessage();
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage(code, null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }
    }

    @PostMapping("/dashboard/orders/{orderId}/moveItemsToOrder")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String moveItemsToOrder(@PathVariable String orderId, @ModelAttribute OrderItemsForm form,
                                   @RequestParam(required = false) String targetOrderId,
                                   RedirectAttributes redirectAttributes, Locale locale) {
        requireOrder(ordersRepository, getStoreId(), orderId);
        List<String> selected = form.getSelectedOrderItemIds();
        String refusal = selected.isEmpty() ? "order.bulk.none.selected" : null;
        OrderReferenceResolver.Resolution resolution = refusal != null ? null
                : orderReferenceResolver.resolve(getStoreId(), targetOrderId);
        if (refusal == null && resolution.outcome() == OrderReferenceResolver.Outcome.AMBIGUOUS) {
            refusal = "order.move.ambiguous";
        } else if (refusal == null && !resolution.isFound()) {
            refusal = "order.move.not.found";
        } else if (refusal == null && resolution.order().getOrderId().equals(orderId)) {
            refusal = "order.move.self";
        }
        if (refusal != null) {
            Object[] args = resolution != null ? new Object[]{resolution.candidates()} : null;
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage(refusal, args, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }
        try {
            Order target = ordersManager.moveOrderItemsToOrder(getStoreId(), orderId, resolution.order().getOrderId(), selected);
            OrderFlash.saved(redirectAttributes, messageSource.getMessage("order.bulk.move.done",
                    new Object[]{selected.size(), ConversionUtil.getShortenedId(orderId)}, locale));
            return "redirect:/dashboard/orders/" + target.getOrderId();
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("error.message." + e.getMessage(), null, locale));
            return "redirect:/dashboard/orders/" + orderId;
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
                : target.canBeSplit() ? null : messageSource.getMessage("order.move.target.locked", null, locale);
        // the amount leaves the server fully formatted, so the dialog script only prints it
        String amount = messageSource.getMessage("general.currency.amount", new Object[]{Money.format(target.getTotalPrice())}, locale);
        String status = target.getStatus() == null ? null
                : messageSource.getMessage(OrderLabels.status(target.getStatus()), null, locale);
        return ResponseEntity.ok(MoveTargetView.of(target, orderItemsRepository.findByOrderId(target.getOrderId()).size(),
                status, amount, reason));
    }

    @PostMapping("/dashboard/orders/{orderId}/updateSerialNumbers")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String updateSerialNumbers(@PathVariable String orderId, @ModelAttribute OrderItemsForm form) {
        requireOrder(ordersRepository, getStoreId(), orderId);
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

        return "redirect:/dashboard/orders/" + orderId;
    }

    @GetMapping("/dashboard/orders/{orderId}/address")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String showAddressDetails(@PathVariable String orderId, @RequestParam String type, Model model,
                                     RedirectAttributes redirectAttributes, Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        // the customer card hides "Edit" for the same reasons; a typed address must not reach a form that cannot be saved
        String locked = order.hasOneOfStatuses(OrderStatus.Completed, OrderStatus.Cancelled) ? "order.address.error.closed"
                : CustomerView.lockedKey(order, "billing".equals(type));
        if (locked != null) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage(locked, null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }
        model.addAttribute("order", order);
        model.addAttribute("type", type);
        return "orderAddressDetails";
    }

    @PostMapping("/dashboard/orders/{orderId}/updateAddressDetails")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String updateAddressDetails(@PathVariable String orderId, @RequestParam String type, @ModelAttribute("order") Order updatedOrder, RedirectAttributes redirectAttributes, Locale locale) {
        Order existingOrder = requireOrder(ordersRepository, getStoreId(), orderId);
        if (existingOrder.hasOneOfStatuses(OrderStatus.Completed, OrderStatus.Cancelled)) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("order.address.error.closed", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }
        // once a label exists (or the parcel is on its way) the address is fixed; the page greys the edit link with the same reason
        if ("shipping".equals(type) && !existingOrder.canOperatorChangeShippingAddress()) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage(CustomerView.lockedKey(existingOrder, false), null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }
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
    public String updateReview(@PathVariable String orderId, @ModelAttribute("order") Order updatedOrder,
                               RedirectAttributes redirectAttributes, Locale locale) {
        Order existingOrder = requireOrder(ordersRepository, getStoreId(), orderId);
        if (updatedOrder.getReview() != null) {
            existingOrder.setReview(updatedOrder.getReview());
        }
        orderLifecycle.update(existingOrder);
        OrderFlash.saved(redirectAttributes, messageSource.getMessage("order.review.saved", null, locale));
        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/updatePayments")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String updatePayments(@PathVariable String orderId, @ModelAttribute("order") Order updatedOrder,
                                 RedirectAttributes redirectAttributes, Locale locale) {
        Order existingOrder = requireOrder(ordersRepository, getStoreId(), orderId);
        List<Payment> posted = updatedOrder.getPayments() == null ? List.of() : updatedOrder.getPayments();
        if (posted.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("order.payments.edit.empty", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }
        List<Payment> payments = posted.stream().filter(Payment::isComplete).collect(Collectors.toList());
        if (payments.isEmpty()) {
            // every row was cleared: the order keeps one placeholder payment carrying the chosen method
            payments.add(posted.get(0));
        }
        existingOrder.setPayments(payments);
        orderLifecycle.update(existingOrder);
        OrderFlash.saved(redirectAttributes, messageSource.getMessage("order.payments.saved", null, locale));
        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/addPayment")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String addPayment(@PathVariable String orderId,
                             @ModelAttribute AddPaymentForm form,
                             RedirectAttributes redirectAttributes,
                             Locale locale) {
        Order existingOrder = requireOrder(ordersRepository, getStoreId(), orderId);

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

        orderLifecycle.update(existingOrder);
        OrderFlash.saved(redirectAttributes, messageSource.getMessage("order.payments.added", null, locale));
        return "redirect:/dashboard/orders/" + orderId;
    }

    @PostMapping("/dashboard/orders/{orderId}/updateShipments")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String updateShipments(@PathVariable String orderId, @ModelAttribute("order") Order updatedOrder,
                                  RedirectAttributes redirectAttributes, Locale locale) {
        Order existingOrder = requireOrder(ordersRepository, getStoreId(), orderId);
        // OrderLifecycle.update never persists cancelled orders, so publishing here
        // would announce a shipment change that was never saved
        if (existingOrder.getStatus() == OrderStatus.Cancelled) {
            return "redirect:/dashboard/orders/" + orderId;
        }
        // the dialog always posts at least one row; nothing posted is a stale page, not "delete every shipment"
        if (updatedOrder.getShipments() == null || updatedOrder.getShipments().isEmpty()) {
            return "redirect:/dashboard/orders/" + orderId;
        }
        List<String> shipmentDataBeforeUpdate = shipmentDataSnapshot(existingOrder);
        List<Shipment> shipments = updatedOrder.getShipments().stream()
                .filter(s -> s.hasShippingData() || s.hasCollectionData() || s.isDeliveredToCollectionPoint())
                .collect(Collectors.toList());
        if (shipments.isEmpty()) {
            shipments.add(updatedOrder.getShipments().get(0));
        }
        List<Shipment> previousShipments = existingOrder.getShipments();
        shipments.forEach(shipment -> previousShipments.stream()
                .filter(previous -> previous.hasTrackingNo(shipment.getTrackingNo()))
                .findFirst()
                .ifPresent(shipment::inheritTrackingSubscriptionFrom));
        // The operator's edit is authoritative: replaceShipments would re-inherit the previous collection
        // point and force the type back to PickupPoint, making a change of delivery type impossible.
        shipments.forEach(shipment -> shipment.setCollectionPointCode(StringUtils.trimToNull(shipment.getCollectionPointCode())));
        existingOrder.setShipments(shipments);
        shipmentTrackingSubscriber.subscribe(getStoreId(), existingOrder);
        orderLifecycle.update(existingOrder);
        boolean hasNotifiableShipmentData = existingOrder.getShipments().stream()
                .anyMatch(s -> s.hasShippingData() || s.hasCollectionData());
        boolean shipmentDataChanged = !shipmentDataBeforeUpdate.equals(shipmentDataSnapshot(existingOrder));
        if (hasNotifiableShipmentData && shipmentDataChanged) {
            orderLifecycleEventPublisher.publish(existingOrder, OrderLifecycleEventType.ShipmentCreated);
        }
        OrderFlash.saved(redirectAttributes, messageSource.getMessage("order.shipments.saved", null, locale));
        return "redirect:/dashboard/orders/" + orderId;
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
    public String addReceipt(@PathVariable String orderId, @ModelAttribute Document document,
                             RedirectAttributes redirectAttributes, Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        String locked = OrderPageModelFactory.addDocumentLockedKey(order, document.getType());
        if (locked != null) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage(locked, null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }
        // the dialog marks the number as required; a blank one would add a row nobody can unpin (removal matches on it)
        if (StringUtils.isBlank(document.getNumber()) || document.getType() == null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("order.documents.add.error.number", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }
        document.setNumber(document.getNumber().trim());
        document.setLink(StringUtils.trimToNull(document.getLink()));
        order.addDocument(document);
        orderLifecycle.update(order);
        OrderFlash.saved(redirectAttributes, messageSource.getMessage("order.documents.added", null, locale));
        return "redirect:/dashboard/orders/" + orderId;
    }

    @GetMapping("/dashboard/orders/{orderId}/removeDocument")
    @PreAuthorize("hasRole('ADMIN')")
    public String confirmRemoveDocument(@PathVariable String orderId, @RequestParam DocumentType type,
                                        @RequestParam(required = false) String number, Model model, Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        boolean present = order.getDocuments().stream()
                .anyMatch(d -> d.getType() == type && Objects.equals(d.getNumber(), number));
        if (!present) {
            return "redirect:/dashboard/orders/" + orderId;
        }
        return OrderConfirmPages.render(model, new ConfirmAction(
                messageSource.getMessage("order.documents.unpin.confirm.title", new Object[]{number}, locale),
                messageSource.getMessage("order.documents.unpin.confirm.message", null, locale),
                messageSource.getMessage("order.documents.unpin.confirm.action", null, locale),
                OrderLinks.removeDocumentPath(orderId, type, number), "/dashboard/orders/" + orderId),
                orderPageTitle(order, locale));
    }

    @PostMapping("/dashboard/orders/{orderId}/removeDocument")
    @PreAuthorize("hasRole('ADMIN')")
    public String removeDocument(@PathVariable String orderId, @RequestParam DocumentType type,
                                 @RequestParam(required = false) String number,
                                 RedirectAttributes redirectAttributes, Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);

        if (order.hasOneOfStatuses(OrderStatus.Completed, OrderStatus.Cancelled) || !order.removeDocument(type, number)) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("error.message.document.cannot.be.removed", null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }

        // saving via OrderLifecycle would re-trigger automatic invoice generation for delivered orders
        ordersRepository.save(order);
        OrderFlash.saved(redirectAttributes, messageSource.getMessage("order.documents.unpinned", null, locale));
        return "redirect:/dashboard/orders/" + orderId;
    }

    @GetMapping("/dashboard/orders/{orderId}/cancelShipment")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String confirmCancelShipment(@PathVariable String orderId, Model model, Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        return OrderConfirmPages.render(model, new ConfirmAction(
                messageSource.getMessage("order.shipments.cancel.confirm.title", null, locale),
                messageSource.getMessage("order.shipments.cancel.confirm.message", null, locale),
                messageSource.getMessage("order.shipments.cancel.confirm.action", null, locale),
                "/dashboard/orders/" + orderId + "/cancelShipment", "/dashboard/orders/" + orderId),
                orderPageTitle(order, locale));
    }

    @PostMapping("/dashboard/orders/{orderId}/cancelShipment")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String cancelShipment(@PathVariable String orderId,
                                 RedirectAttributes redirectAttributes, Locale locale) {
        Order order = requireOrder(ordersRepository, getStoreId(), orderId);
        Optional<Shipment> sent = order.firstShipmentWithShippingData();
        // the same shipment ShipmentCancelService picks; its English errors never reach the operator
        String refusal = sent.isEmpty() ? "order.shipments.cancel.error.no.data"
                : sent.get().getExternalId() == null ? "order.shipments.cancel.error.no.package" : null;
        if (refusal != null) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage(refusal, null, locale));
            return "redirect:/dashboard/orders/" + orderId;
        }
        try {
            shipmentCancelService.cancelShipping(orderId, getStoreId());
            OrderFlash.saved(redirectAttributes, messageSource.getMessage("shipment.cancel.success", null, locale));
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

}
