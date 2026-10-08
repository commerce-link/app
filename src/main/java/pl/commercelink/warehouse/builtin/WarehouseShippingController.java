package pl.commercelink.warehouse.builtin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.inventory.deliveries.DeliveredPredicate;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.ShippingForm;
import pl.commercelink.shipping.AbstractShippingController;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import pl.commercelink.shipping.DeliveryTarget;
import pl.commercelink.shipping.ShippingPageView;

@Controller
@RequestMapping("/dashboard/warehouse/shipping")
@PreAuthorize("!hasRole('SUPER_ADMIN')")
public class WarehouseShippingController extends AbstractShippingController {

    @Autowired
    private DeliveredPredicate deliveredPredicate;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private WarehouseGoodsOutService warehouseGoodsOutService;

    @PostMapping("")
    public String initiate(@RequestParam(name = "selectedItemIds", required = false) List<String> itemIds,
                           @RequestParam MultiValueMap<String, String> view, Locale locale, RedirectAttributes ra,
                           Model model) {
        // a refusal returns to the list view the operator acted from, posted by selection-actions.js, read with the
        // store's WMS flag as the list reads its address
        Store store = getStore();
        String back = WarehouseListQuery.parse(view, store).href();
        if (itemIds == null || itemIds.isEmpty()) {
            return refuse(ra, back, locale, "warehouse.error.select.at.least.one");
        }
        List<WarehouseItem> warehouseItems = itemIds.stream()
                .map(id -> warehouseRepository.findById(getStoreId(), id))
                .toList();
        if (warehouseItems.contains(null)) {
            return refuse(ra, back, locale, "warehouse.error.not.found");
        }

        Optional<WarehouseItem> refused = WarehouseBulkAction.SHIP.firstRefused(warehouseItems);
        if (refused.isPresent()) {
            WarehouseItem item = refused.get();
            String allowed = WarehouseBulkAction.SHIP.allowed().stream()
                    .map(s -> messageSource.getMessage(WarehouseStatuses.labelKey(s), null, locale))
                    .collect(Collectors.joining(", "));
            return refuse(ra, back, locale, "warehouse.error.status",
                    item.getName(),
                    messageSource.getMessage(WarehouseStatuses.labelKey(item.getStatus()), null, locale),
                    messageSource.getMessage("warehouse.bulk.ship.label", null, locale),
                    allowed);
        }

        Optional<WarehouseItem> withoutDelivery = deliveredPredicate.firstWithoutDelivery(getStoreId(), warehouseItems);
        if (withoutDelivery.isPresent()) {
            return refuse(ra, back, locale, "warehouse.error.no.delivery", withoutDelivery.get().getName(),
                    messageSource.getMessage("warehouse.bulk.ship.label", null, locale));
        }

        if (!deliveredPredicate.isFromSameSource(getStoreId(), warehouseItems)) {
            return refuse(ra, back, locale, "warehouse.error.same.source");
        }

        ShippingForm shippingForm = new ShippingForm(null, "warehouse");
        shippingForm.setOrderItemIds(warehouseItems.stream().map(WarehouseItem::getItemId).collect(Collectors.toList()));

        List<ShippingDetails> shippingDetailsList = retrieveRMACentersShippingDetailsList(warehouseItems.get(0).getDeliveryId());

        return renderShippingForm(store, shippingForm, shippingDetailsList, model);
    }

    private String refuse(RedirectAttributes ra, String target, Locale locale, String key, Object... args) {
        ra.addFlashAttribute("settingsErrorMessage", messageSource.getMessage(key, args, locale));
        return "redirect:" + target;
    }

    @Override
    protected double calculateShippingInsurance(ShippingForm form) {
        return 0.0;
    }

    @Override
    protected List<ShippingDetails> retrieveShippingDetailsList(ShippingForm form) {
        List<WarehouseItem> warehouseItems = form.getOrderItemIds().stream()
                .map(item -> warehouseRepository.findById(getStoreId(), item))
                .toList();
        return retrieveRMACentersShippingDetailsList(warehouseItems.get(0).getDeliveryId());
    }

    @Override
    protected void onShippingCreated(ShippingForm form, List<Shipment> shipments) {
        warehouseGoodsOutService.issueGoodsOutForExternalService(
                getStoreId(),
                form.getOrderItemIds(),
                form.getShippingDetails(),
                CustomSecurityContext.getLoggedInUserName()
        );
    }

    @Override
    protected DeliveryTarget resolveDeliveryTarget(ShippingForm form) {
        return new DeliveryTarget(null, null, null);
    }

    @Override
    protected ShippingPageView pageView(ShippingForm form) {
        return new ShippingPageView("/dashboard/warehouse", "nav.warehouse", null,
                "shipping.lead.warehouse", form.getOrderItemIds().size());
    }
}
