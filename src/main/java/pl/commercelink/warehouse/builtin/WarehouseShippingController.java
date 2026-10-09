package pl.commercelink.warehouse.builtin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pl.commercelink.inventory.deliveries.DeliveredPredicate;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.ShippingForm;
import pl.commercelink.shipping.AbstractShippingController;
import pl.commercelink.shipping.ShipmentCreationCheckRequest;
import pl.commercelink.shipping.ShipmentOwnerType;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.util.List;
import java.util.stream.Collectors;
import pl.commercelink.shipping.DeliveryTarget;
import pl.commercelink.shipping.ShippingPageView;

@Controller
@RequestMapping("/dashboard/warehouse/shipping")
@PreAuthorize("!hasRole('SUPER_ADMIN')")
public class WarehouseShippingController extends AbstractShippingController {

    private static final String WAREHOUSE_TAKEN_KEY = "shipping.create.warehouse.taken";

    @Autowired
    private DeliveredPredicate deliveredPredicate;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @PostMapping("")
    public String initiate(@RequestParam("selectedItemIds") List<String> itemIds, Model model) {
        List<WarehouseItem> warehouseItems = itemIds.stream()
                .map(id -> warehouseRepository.findById(getStoreId(), id))
                .toList();

        if (!deliveredPredicate.isFromSameSource(getStoreId(), warehouseItems)) {
            model.addAttribute("errorMessage", "All selected items must have the same provider.");
            return "redirect:/dashboard/warehouse/items";
        }

        ShippingForm shippingForm = new ShippingForm(null, "warehouse");
        shippingForm.setOrderItemIds(warehouseItems.stream().map(WarehouseItem::getItemId).collect(Collectors.toList()));

        List<ShippingDetails> shippingDetailsList = retrieveRMACentersShippingDetailsList(warehouseItems.get(0).getDeliveryId());

        return renderShippingForm(getStore(), shippingForm, shippingDetailsList, model);
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
    protected ShipmentCreationCheckRequest.ShipmentCreationCheckRequestBuilder creationSeed(ShippingForm form) {
        return ShipmentCreationCheckRequest.builder()
                .ownerType(ShipmentOwnerType.WAREHOUSE)
                .pickUpAddressId(form.getPickUpAddressId())
                .itemIds(List.copyOf(form.getOrderItemIds()))
                .receiver(form.getShippingDetails())
                .issuedBy(CustomSecurityContext.getLoggedInUserName());
    }

    /** Items another shipment holds are still listed (their goods-out follows the confirmation): its hold refuses. */
    @Override
    protected String goneMessageKey() {
        return WAREHOUSE_TAKEN_KEY;
    }

    @Override
    protected String startedMessageKey() {
        return "shipping.create.started.warehouse";
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
