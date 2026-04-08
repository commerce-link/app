package pl.commercelink.shipping;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import pl.commercelink.rest.client.HttpClientException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.shipping.api.ShippingEstimate;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.orders.*;
import pl.commercelink.orders.rma.RMACenter;
import pl.commercelink.orders.rma.RMACentersRepository;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.util.Locale;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractShippingController {

    @Autowired
    private DeliveriesRepository deliveriesRepository;

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private ShippingService shippingService;

    @Autowired
    private RMACentersRepository rmaCentersRepository;

    @Autowired
    private OrdersRepository ordersRepository;

    @Autowired
    protected MessageSource messageSource;

    @PostMapping("/template")
    public String loadTemplates(@ModelAttribute ShippingForm form, Model model) {
        Store store = getStore();

        List<ParcelForm> parcels = shippingService.retrieveParcelsListBasedOnPackageTemplate(
                calculateShippingInsurance(form),
                form.getPackageTemplateId(),
                store
        );
        parcels.addAll(Collections.nCopies(4, ParcelForm.empty()));
        form.setParcels(parcels);

        double defaultCodAmount = 0;
        if (form.getShippingEntityType().equals("orders")) {
            Order order = ordersRepository.findById(getStoreId(), form.getShippingEntityId());
            defaultCodAmount = order.getUnpaidAmount();
        }
        form.setCashOnDeliveryAmount(defaultCodAmount);

        return renderShippingForm(store, form, retrieveShippingDetailsList(form), model);
    }

    @PostMapping("/estimate")
    public String estimateShipping(@ModelAttribute ShippingForm form, Model model) {
        Store store = getStore();

        try {
            List<ShippingEstimate> estimates = shippingService.estimateServicePrices(form, store);
            model.addAttribute("servicePrices", estimates);
        } catch (HttpClientException ex) {
            return handleHttpClientException(ex, store, form, model);
        }

        return renderShippingForm(store, form, retrieveShippingDetailsList(form), model);
    }

    @PostMapping("/create")
    public String createShipping(@ModelAttribute ShippingForm form, RedirectAttributes redirectAttributes, Locale locale) {
        Store store = getStore();
        OperationResult<List<Shipment>> result = shippingService.createShipping(form, store);
        if (!result.isSuccess()) {
            redirectAttributes.addFlashAttribute("errorMessage", result.getMessage());
            return "redirect:" + form.getShippingAction();
        }
        List<Shipment> shipments = result.getPayload();
        onShippingCreated(form, shipments);

        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("shipping.create.success", null, locale));

        return "redirect:" + getEntityUrl(form);
    }

    protected String renderShippingForm(Store store, ShippingForm shippingForm, List<ShippingDetails> shippingDetailsList, Model model) {
        model.addAttribute("shippingForm", shippingForm);
        model.addAttribute("shippingEntityId", shippingForm.getShippingEntityId());
        model.addAttribute("shippingDetailsList", shippingDetailsList);
        model.addAttribute("pickUpAddresses", store.getPickUpAddresses());
        model.addAttribute("packageTemplates", store.getPackageTemplates());

        return "shipping";
    }

    protected String handleHttpClientException(HttpClientException ex, Store store, ShippingForm form, Model model) {
        String error = ex.getResponseBody();
        if (StringUtils.isBlank(error)) {
            error = ex.getMessage();
        }
        model.addAttribute("errorMessage", error);

        List<ShippingDetails> shippingDetailsList = retrieveShippingDetailsList(form);

        return renderShippingForm(store, form, shippingDetailsList, model);
    }

    protected List<ShippingDetails> retrieveRMACentersShippingDetailsList(String deliveryId) {
        var delivery = deliveriesRepository.findById(getStoreId(), deliveryId);

        return rmaCentersRepository.findByProviderName(getStoreId(), delivery.getProvider())
                .stream()
                .map(RMACenter::getShippingDetails)
                .collect(Collectors.toList());
    }

    private String getEntityUrl(ShippingForm form) {
        String url = "/dashboard/" + form.getShippingEntityType();
        String entityId = form.getShippingEntityId();
        if (StringUtils.isNotBlank(entityId)) {
            url += "/" + entityId;
        }
        return url;
    }

    protected Store getStore() {
        return storesRepository.findById(getStoreId());
    }

    protected String getStoreId() {
        return CustomSecurityContext.getStoreId();
    }

    protected abstract double calculateShippingInsurance(ShippingForm form);

    protected abstract List<ShippingDetails> retrieveShippingDetailsList(ShippingForm form);

    protected abstract void onShippingCreated(ShippingForm form, List<Shipment> shipments);

}

