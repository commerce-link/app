package pl.commercelink.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pl.commercelink.starter.util.PaginationUtil;
import pl.commercelink.web.dtos.InventoryItemView;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.deliveries.DeliveriesQueryService;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderIndexEntry;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.PastOrderFilter;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static pl.commercelink.starter.util.ConversionUtil.asLocallyFormattedDate;

@Controller
public class WebController {

    @Autowired
    private Inventory inventory;

    @Autowired
    private OrdersRepository ordersRepository;

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private DeliveriesQueryService deliveriesQueryService;

    @Autowired
    private TaxonomyCache taxonomyCache;

    @Autowired
    private PimCatalog pimCatalog;

    @Autowired
    private SupplierRegistry supplierRegistry;

    private static final int CLIENTS_PAGE_SIZE = 25;

    @GetMapping("/dashboard")
    public String index(Model model) {
        return "dashboard";
    }

    @GetMapping("/dashboard/clients")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String clients(@RequestParam(required = false) String orderId,
                          @RequestParam(required = false) String email,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderedAtStart,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderedAtEnd,
                          @RequestParam(required = false, defaultValue = "1") int page,
                          Model model) {
        List<OrderIndexEntry> pastOrders = Collections.emptyList();
        boolean hasSearchParams = isNotBlank(orderId) || isNotBlank(email) || orderedAtStart != null || orderedAtEnd != null;
        if (hasSearchParams) {
            PastOrderFilter filter = new PastOrderFilter(orderId, email, orderedAtStart, orderedAtEnd);
            pastOrders = ordersRepository.searchPastOrders(getStoreId(), filter);
        }

        List<OrderIndexEntry> paginatedPastOrders = PaginationUtil.paginate(pastOrders, page, CLIENTS_PAGE_SIZE, model);

        Map<String, Object> searchParams = new HashMap<>();
        searchParams.put("orderId", orderId);
        searchParams.put("email", email);
        searchParams.put("orderedAtStart", orderedAtStart);
        searchParams.put("orderedAtEnd", orderedAtEnd);

        model.addAttribute("pastOrders", paginatedPastOrders);
        model.addAttribute("searchParams", searchParams);

        return "clients";
    }

    @GetMapping("/dashboard/payments")
    @PreAuthorize("hasRole('ADMIN')")
    public String payments(Model model) {

        List<Delivery> unpaidDeliveries = deliveriesQueryService.fetchActiveDeliveriesWithAllocations(getStoreId())
                .stream()
                .filter(Delivery::isWaitingForPayment)
                .sorted(Comparator.comparing(Delivery::getPaymentDueDate))
                .collect(Collectors.toList());

        double unpaidDeliveriesAmountNet = unpaidDeliveries.stream()
                .map(Delivery::getUnpaidAmount)
                .mapToDouble(Price::netValue)
                .sum();
        double unpaidDeliveriesAmountGross = unpaidDeliveries.stream()
                .map(Delivery::getUnpaidAmount)
                .mapToDouble(Price::grossValue)
                .sum();

        List<Order> unpaidOrders = ordersRepository.findAllActiveOrders(CustomSecurityContext.getStoreId())
                .stream()
                .filter(o -> !o.isFullyPaid())
                .sorted(Comparator.comparing(Order::getEstimatedShippingAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        double unpaidOrdersAmountNet = unpaidOrders.stream()
                .mapToDouble(Order::getUnpaidAmountNet)
                .sum();
        double unpaidOrdersAmountGross = unpaidOrders.stream()
                .mapToDouble(Order::getUnpaidAmountGross)
                .sum();

        model.addAttribute("unpaidOrders", unpaidOrders);
        model.addAttribute("unpaidOrdersAmountNet", unpaidOrdersAmountNet);
        model.addAttribute("unpaidOrdersAmountGross", unpaidOrdersAmountGross);
        model.addAttribute("unpaidDeliveries", unpaidDeliveries);
        model.addAttribute("unpaidDeliveriesAmountNet", unpaidDeliveriesAmountNet);
        model.addAttribute("unpaidDeliveriesAmountGross", unpaidDeliveriesAmountGross);

        return "payments";
    }

    @GetMapping("/dashboard/inventory")
    public String inventory(Model model) {
        return mapInventory(model, inventory);
    }

    @GetMapping("/dashboard/inventory/check-price")
    public String checkProductPrice(
            @RequestParam(value = "mfn", required = false) String productCode,
            @RequestParam(value = "ean", required = false) String ean,
            @RequestParam(value = "pimId", required = false) String pimId,
            Model model) {

        InventoryView inventoryView = inventory.withEnabledSuppliersAndWarehouseData(getStoreId());

        MatchedInventory matchedInventory = null;
        if (pimId != null && !pimId.isEmpty()) {
            Optional<PimEntry> pimEntry = pimCatalog.findByPimId(pimId);
            if (pimEntry.isPresent()) {
                matchedInventory = inventoryView.findByInventoryKey(InventoryKey.fromPimEntry(pimEntry.get()));
            }
        } else if (productCode != null && !productCode.isEmpty()) {
            matchedInventory = inventoryView.findByProductCode(productCode);
        } else if (ean != null && !ean.isEmpty()) {
            matchedInventory = inventoryView.findByEan(ean);
        }

        mapProductPrice(model, matchedInventory);
        return mapInventory(model, inventory);
    }

    private String mapInventory(Model model, Inventory _inventory) {
        List<String> enabledSuppliers = getStoreId() != null ?
                new ArrayList<>(storesRepository.findById(getStoreId()).getEnabledProviders()) :
                new ArrayList<>(supplierRegistry.getAllSupplierNames());
        enabledSuppliers.add(SupplierRegistry.WAREHOUSE);

        Map<String, String> providerUpdateDates = _inventory.getMatchedSuppliers()
                .stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        p -> asLocallyFormattedDate(_inventory.getLastUpdateDate(p))
                ));

        model.addAttribute("enabledSuppliers", enabledSuppliers);
        model.addAttribute("inventorySize", _inventory.size());
        model.addAttribute("supplierUpdateEntries", providerUpdateDates.entrySet());
        model.addAttribute("taxonomyFileName", taxonomyCache.getFileName());
        model.addAttribute("taxonomySize", taxonomyCache.size());
        model.addAttribute("pimIndexSize", pimCatalog.findAll().size());

        return "inventory";
    }

    private void mapProductPrice(Model model, MatchedInventory matchedInventory) {
        if (matchedInventory != null && matchedInventory.hasAnyOffers()) {
            Taxonomy taxonomy = matchedInventory.getTaxonomy();

            model.addAttribute("ean", taxonomy.ean());
            model.addAttribute("mfn", taxonomy.mfn());
            model.addAttribute("name", taxonomy.name());
            model.addAttribute("brand", taxonomy.brand());
            model.addAttribute("lowestGrossPrice", matchedInventory.getLowestPrice().grossValue());
            model.addAttribute("medianGrossPrice", matchedInventory.getMedianPrice().grossValue());
            model.addAttribute("totalAvailableQty", matchedInventory.getTotalAvailableQty());
            model.addAttribute("inventoryItems", matchedInventory.getInventoryItems().stream()
                    .map(i -> new InventoryItemView(i.supplier(), i.ean(), i.mfn(), Price.fromNet(i.netPrice()).grossValue(), i.qty()))
                    .toList());
        } else {
            model.addAttribute("error", "Product not found");
        }
    }

    private String getStoreId() {
        return CustomSecurityContext.getStoreId();
    }
}
