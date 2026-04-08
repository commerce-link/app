package pl.commercelink.web;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.invoicing.InvoicingProviderFactory;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.marketplace.MarketplaceProviderFactory;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.payments.PaymentProviderFactory;
import pl.commercelink.taxonomy.ProductGroup;
import pl.commercelink.shipping.ShippingProviderFactory;
import pl.commercelink.shipping.api.Carrier;
import pl.commercelink.stores.*;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.dtos.CarrierSelectionForm;
import pl.commercelink.web.dtos.ConnectedIntegration;
import pl.commercelink.web.dtos.ParcelForm;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Controller
public class StoreController {

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private ShippingProviderFactory shippingProviderFactory;

    @Autowired
    private InvoicingProviderFactory invoicingProviderFactory;

    @Autowired
    private PaymentProviderFactory paymentProviderFactory;

    @Autowired
    private MarketplaceProviderFactory marketplaceProviderFactory;

    @Autowired
    private SupplierRegistry supplierRegistry;

    @Value("${app.domain}")
    private String appDomain;

    @Value("${api.domain}")
    private String apiDomain;

    @Autowired
    private MessageSource messageSource;

    @GetMapping("/dashboard/store")
    @PreAuthorize("hasRole('ADMIN')")
    public String store(Model model) {
        Store store = storesRepository.findById(getStoreId());

        StoreForm form = new StoreForm(store);
        model.addAttribute("form", form);
        model.addAttribute("isSuperAdmin", false);
        return "store";
    }

    @GetMapping("/dashboard/store/branding")
    @PreAuthorize("hasRole('ADMIN')")
    public String storeBranding(Model model) {
        return renderStoreBranding(getStoreId(), model);
    }

    @GetMapping("/dashboard/store/{storeId}/branding")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminStoreBranding(@PathVariable String storeId, Model model) {
        return renderStoreBranding(storeId, model);
    }

    private String renderStoreBranding(String storeId, Model model) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            model.addAttribute("error", "Store not found");
            return "error";
        }

        if (store.getBranding() == null) {
            store.setBranding(new Branding());
        }
        StoreForm form = new StoreForm(store);

        model.addAttribute("form", form);
        model.addAttribute("backofficeDomain", appDomain);
        return "store-branding";
    }

    @GetMapping("/dashboard/store/invoicing")
    @PreAuthorize("hasRole('ADMIN')")
    public String storeInvoicing(Model model) {
        return renderStoreInvoicing(getStoreId(), model);
    }

    @GetMapping("/dashboard/store/{storeId}/invoicing")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminStoreInvoicing(@PathVariable String storeId, Model model) {
        return renderStoreInvoicing(storeId, model);
    }

    private String renderStoreInvoicing(String storeId, Model model) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            model.addAttribute("error", "Store not found");
            return "error";
        }

        store.getBankAccounts().add(new BankAccount());

        if (store.getInvoicingConfiguration() == null) {
            store.setInvoicingConfiguration(new InvoicingConfiguration());
        }

        StoreForm form = new StoreForm(store);
        form.setProviderConfiguration(invoicingProviderFactory.loadConfigurationForUI(store));

        model.addAttribute("form", form);
        model.addAttribute("availableProviders", invoicingProviderFactory.availableProviders());
        model.addAttribute("selectedProviderName", form.getInvoicingSoftwareProvider());
        model.addAttribute("connectedIntegrations", connectedIntegration(form.getInvoicingSoftwareProvider()));
        return "store-invoicing";
    }

    @GetMapping("/dashboard/store/shipping")
    @PreAuthorize("hasRole('ADMIN')")
    public String storeShipping(Model model) {
        return renderStoreShipping(getStoreId(), model);
    }

    @GetMapping("/dashboard/store/{storeId}/shipping")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminStoreShipping(@PathVariable String storeId, Model model) {
        return renderStoreShipping(storeId, model);
    }

    private String renderStoreShipping(String storeId, Model model) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            model.addAttribute("error", "Store not found");
            return "error";
        }

        if (store.getShippingConfiguration() == null) {
            store.setShippingConfiguration(new ShippingConfiguration());
        }

        ShippingDetails pickupAddress = new ShippingDetails();
        pickupAddress.setId(UUID.randomUUID().toString());
        pickupAddress.set_default(false);

        ShippingDetails senderAddress = new ShippingDetails();
        senderAddress.setId(UUID.randomUUID().toString());
        senderAddress.set_default(false);

        store.getShippingConfiguration().getPickUpAddresses().add(pickupAddress);
        store.getShippingConfiguration().getSenderAddresses().add(senderAddress);

        StoreForm form = new StoreForm(store);
        form.setProviderConfiguration(shippingProviderFactory.loadConfigurationForUI(store));

        model.addAttribute("form", form);
        model.addAttribute("availableProviders", shippingProviderFactory.availableProviders());
        model.addAttribute("selectedProviderName", form.getShippingProvider());
        model.addAttribute("connectedIntegrations", connectedIntegration(form.getShippingProvider()));
        return "store-shipping";
    }

    @GetMapping("/dashboard/store/shipping/templates/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String newTemplate(Model model) {
        return showEditPackageTemplate(model, null);
    }

    @GetMapping("/dashboard/store/shipping/templates/edit")
    @PreAuthorize("hasRole('ADMIN')")
    public String editTemplate(@RequestParam String templateId, Model model) {
        return showEditPackageTemplate(model, templateId);
    }

    private String showEditPackageTemplate(Model model, String templateId) {
        Store store = storesRepository.findById(getStoreId());
        ShippingConfiguration config = store.getShippingConfiguration();

        if (config == null) {
            config = new ShippingConfiguration();
            store.setShippingConfiguration(config);
        }

        PackageTemplate template;
        if (templateId == null) {
            template = new PackageTemplate("", new ArrayList<>(Collections.nCopies(3, Parcel.empty())));
        } else {
            template = config.getPackageTemplates().stream()
                    .filter(t -> t.getId().equals(templateId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Template not found"));
            template.getParcels().addAll(Collections.nCopies(2, Parcel.empty()));
        }

        ParcelForm form = new ParcelForm();
        form.setStoreId(store.getStoreId());
        form.setTemplateId(template.getId());
        form.setTemplateName(template.getName());
        form.setParcels(template.getParcels());

        model.addAttribute("form", form);
        model.addAttribute("isNew", templateId == null);

        return "shipping-template-edit";
    }

    @PostMapping("/dashboard/store/shipping/templates/edit")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public String updateShippingTemplates(@ModelAttribute ParcelForm form, Locale locale, RedirectAttributes redirectAttributes) {
        Store existingStore = storesRepository.findById(form.getStoreId());
        ShippingConfiguration config = existingStore.getShippingConfiguration();

        PackageTemplate template = config.getPackageTemplates().stream()
                .filter(t -> t.getId().equals(form.getTemplateId()))
                .findFirst()
                .orElse(null);

        List<Parcel> validParcels = form.getParcels().stream()
                .filter(Parcel::isComplete)
                .collect(Collectors.toList());

        if (template == null) {
            template = new PackageTemplate();
            template.setId(form.getTemplateId());
            template.setName(form.getTemplateName());
            template.setParcels(validParcels);

            config.getPackageTemplates().add(template);
        } else {
            template.setName(form.getTemplateName());
            template.setParcels(validParcels);
        }

        storesRepository.save(existingStore);
        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("store.shipping.package.template.update.success", null, locale));
        return isSuperAdmin()
                ? String.format("redirect:/dashboard/store/%s/shipping", existingStore.getStoreId())
                : "redirect:/dashboard/store/shipping";
    }

    @PostMapping("/dashboard/store/shipping/templates/default")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public String setDefaultTemplate(@RequestParam String storeId, @RequestParam String templateId) {
        Store store = storesRepository.findById(storeId);
        ShippingConfiguration shippingConfig = store.getShippingConfiguration();
        shippingConfig.getPackageTemplates().forEach(t -> t.setDefault(t.getId().equals(templateId)));

        storesRepository.save(store);
        return isSuperAdmin()
                ? String.format("redirect:/dashboard/store/%s/shipping", storeId)
                : "redirect:/dashboard/store/shipping";
    }

    @PostMapping("/dashboard/store/shipping/templates/delete")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public String deleteTemplate(@RequestParam String storeId, @RequestParam String templateId, Locale locale, RedirectAttributes redirectAttributes) {
        Store store = storesRepository.findById(storeId);
        ShippingConfiguration shippingConfig = store.getShippingConfiguration();
        shippingConfig.getPackageTemplates().removeIf(t -> t.getId().equals(templateId));
        storesRepository.save(store);

        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("shipping.template.delete.success", null, locale));

        return isSuperAdmin()
                ? String.format("redirect:/dashboard/store/%s/shipping", storeId)
                : "redirect:/dashboard/store/shipping";
    }

    @PostMapping("/dashboard/store/shipping/pickup-sender/save")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public String updateShippingConfiguration(@ModelAttribute StoreForm form, Locale locale, RedirectAttributes redirectAttributes) {
        Store existingStore = storesRepository.findById(form.getStore().getStoreId());
        ShippingConfiguration existingConfig = existingStore.getShippingConfiguration() != null
                ? existingStore.getShippingConfiguration()
                : new ShippingConfiguration();

        ShippingConfiguration formConfig = form.getStore().getShippingConfiguration();
        List<ShippingDetails> updatedPickupAddresses = formConfig.getPickUpAddresses().stream()
                .filter(ShippingDetails::isProperlyFilled)
                .peek(s -> s.set_default(s.getId().equals(form.getDefaultPickupAddressId())))
                .collect(Collectors.toList());

        existingConfig.setPickUpAddresses(updatedPickupAddresses);

        List<ShippingDetails> updatedSenderAddresses = formConfig.getSenderAddresses().stream()
                .filter(ShippingDetails::isProperlyFilled)
                .peek(s -> s.set_default(s.getId().equals(form.getDefaultSenderAddressId())))
                .collect(Collectors.toList());

        existingConfig.setSenderAddresses(updatedSenderAddresses);

        existingStore.setShippingConfiguration(existingConfig);
        storesRepository.save(existingStore);

        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("store.shipping.configuration.update.success", null, locale));

        return isSuperAdmin()
                ? String.format("redirect:/dashboard/store/%s/shipping", existingStore.getStoreId())
                : "redirect:/dashboard/store/shipping";
    }

    @GetMapping("/dashboard/store/notification")
    @PreAuthorize("hasRole('ADMIN')")
    public String storeNotification(Model model) {
        return renderStoreNotification(getStoreId(), model);
    }

    @GetMapping("/dashboard/store/{storeId}/notification")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminStoreNotification(@PathVariable String storeId, Model model) {
        return renderStoreNotification(storeId, model);
    }

    private String renderStoreNotification(String storeId, Model model) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            model.addAttribute("error", "Store not found");
            return "error";
        }

        if (store.getClientNotificationsConfig() == null) {
            store.setClientNotificationsConfig(new ClientNotificationsConfig());
        }

        StoreForm form = new StoreForm(store);

        model.addAttribute("form", form);
        return "store-notification";
    }

    @GetMapping("/dashboard/store/fulfillment")
    @PreAuthorize("hasRole('ADMIN')")
    public String storeFulfillmentSettings(Model model) {
        return renderStoreFulfillmentSettings(getStoreId(), model);
    }

    @GetMapping("/dashboard/store/{storeId}/fulfillment")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminStoreFulfillmentSettings(@PathVariable String storeId, Model model) {
        return renderStoreFulfillmentSettings(storeId, model);
    }

    private String renderStoreFulfillmentSettings(String storeId, Model model) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            model.addAttribute("error", "Store not found");
            return "error";
        }
        if (store.getFulfillmentSettings() == null) {
            store.setFulfillmentSettings(new FulfillmentSettings());
        }

        StoreForm form = new StoreForm(store);

        model.addAttribute("form", form);
        model.addAttribute("fulfilmentTypes", FulfilmentType.values());
        model.addAttribute("productGroupTypes", ProductGroup.values());
        model.addAttribute("supplierTypes", supplierRegistry.getExternalSupplierNames());

        return "store-fulfillment";
    }

    @GetMapping("/dashboard/store/payments")
    @PreAuthorize("hasRole('ADMIN')")
    public String storePayments(Model model) {
        return renderStorePayments(getStoreId(), model);
    }

    @GetMapping("/dashboard/store/{storeId}/payments")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminStorePayments(@PathVariable String storeId, Model model) {
        return renderStorePayments(storeId, model);
    }

    private String renderStorePayments(String storeId, Model model) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            model.addAttribute("error", "Store not found");
            return "error";
        }

        store.getCheckoutSettings().getDeliveryOptions().add(new DeliveryOption());
        store.getCheckoutSettings().getDeliveryOptions().add(new DeliveryOption());

        StoreForm form = new StoreForm(store);
        form.setProviderConfiguration(paymentProviderFactory.loadConfigurationForUI(store));

        model.addAttribute("form", form);
        model.addAttribute("availableProviders", paymentProviderFactory.availableProviders());
        model.addAttribute("selectedProviderName", form.getPaymentProviderName());
        model.addAttribute("shipmentTypes", ShipmentType.values());
        model.addAttribute("connectedIntegrations", connectedIntegration(form.getPaymentProviderName()));

        return "store-payments";
    }

    @GetMapping("/dashboard/store/marketplaces")
    @PreAuthorize("hasRole('ADMIN')")
    public String storeMarketplaces(Model model) {
        return renderStoreMarketplaces(getStoreId(), model);
    }

    @GetMapping("/dashboard/store/{storeId}/marketplaces")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminStoreMarketplaces(@PathVariable String storeId, Model model) {
        return renderStoreMarketplaces(storeId, model);
    }

    private String renderStoreMarketplaces(String storeId, Model model) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            model.addAttribute("error", "Store not found");
            return "error";
        }

        StoreForm form = new StoreForm(store);
        form.setProviderConfiguration(new HashMap<>());

        List<ConnectedIntegration> integrations = store.getMarketplaces().stream()
                .map(m -> new ConnectedIntegration(m.getName(), m.isLoggedIn()))
                .toList();

        model.addAttribute("form", form);
        model.addAttribute("availableProviders", marketplaceProviderFactory.availableProviders());
        model.addAttribute("selectedProviderName", form.getMarketplace());
        model.addAttribute("connectedIntegrations", integrations);

        return "store-marketplaces";
    }

    @GetMapping("/dashboard/store/company-details")
    @PreAuthorize("hasRole('ADMIN')")
    public String storeBillingShippingConfig(Model model) {
        return renderStoreBillingShippingConfig(getStoreId(), model);
    }

    @GetMapping("/dashboard/store/{storeId}/company-details")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminStoreBillingShippingConfig(@PathVariable String storeId, Model model) {
        return renderStoreBillingShippingConfig(storeId, model);
    }

    private String renderStoreBillingShippingConfig(String storeId, Model model) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            model.addAttribute("error", "Store not found");
            return "error";
        }

        ShippingDetails shippingDetails = new ShippingDetails();
        shippingDetails.set_default(false);
        store.getShippingDetails().add(shippingDetails);

        StoreForm form = new StoreForm(store);

        model.addAttribute("form", form);
        return "store-company-details";
    }

    @GetMapping("/dashboard/store/rma")
    @PreAuthorize("hasRole('ADMIN')")
    public String storeRMAConfig(Model model) {
        return renderStoreRMAConfig(getStoreId(), model);
    }

    @GetMapping("/dashboard/store/{storeId}/rma")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminRMAConfig(@PathVariable String storeId, Model model) {
        return renderStoreRMAConfig(storeId, model);
    }

    private String renderStoreRMAConfig(String storeId, Model model) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            model.addAttribute("error", "Store not found");
            return "error";
        }
        if (store.getRmaSettings() == null) {
            store.setRmaSettings(new RMASettings());
        }

        StoreForm form = new StoreForm(store);

        List<AuthorizedCarrier> carriers = store.getShippingConfiguration() != null
                ? store.getShippingConfiguration().getAuthorizedCarriers()
                : Collections.emptyList();

        model.addAttribute("form", form);
        model.addAttribute("carrierTypes", carriers);
        return "store-rma";
    }

    @PostMapping("/dashboard/store/branding/edit")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public String updateStoreBranding(@ModelAttribute StoreForm form,
                                      Locale locale,
                                      RedirectAttributes redirectAttributes) throws IOException {
        Store existingStore = storesRepository.findById(form.getStore().getStoreId());
        existingStore.setName(form.getStore().getName());
        existingStore.setBranding(createOrUpdateBranding(form, existingStore));

        storesRepository.save(existingStore);
        redirectAttributes.addFlashAttribute("successMessage",messageSource.getMessage("store.branding.update.success", null, locale) );

        return isSuperAdmin()
                ? String.format("redirect:/dashboard/store/%s/branding", form.getStore().getStoreId())
                : "redirect:/dashboard/store/branding";
    }

    @PostMapping("/dashboard/store/invoicing/edit")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public String updateStoreInvoicing(@ModelAttribute StoreForm form, Locale locale, RedirectAttributes redirectAttributes) {
        Store existingStore = storesRepository.findById(form.getStore().getStoreId());
        existingStore.setInvoicingConfiguration(form.getStore().getInvoicingConfiguration());

        List<BankAccount> validAccounts = form.getStore().getBankAccounts().stream()
                .filter(BankAccount::isComplete)
                .peek(account -> account.set_default(account.getId().equals(form.getDefaultBankAccountId())))
                .collect(Collectors.toList());
        existingStore.setBankAccounts(validAccounts);

        storesRepository.save(existingStore);
        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("store.invoicing.update.success",null, locale));

        return isSuperAdmin()
                ? String.format("redirect:/dashboard/store/%s/invoicing", form.getStore().getStoreId())
                : "redirect:/dashboard/store/invoicing";
    }

    @PostMapping("/dashboard/store/shipping/carriers/fetch")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public String fetchAvailableCarriers(@RequestParam String storeId, Model model) {
        Store store = storesRepository.findById(storeId);

        Set<String> currentCarrierIds = store.getShippingConfiguration() != null
                ? store.getShippingConfiguration().getAuthorizedCarriers().stream()
                    .map(AuthorizedCarrier::getId)
                    .collect(Collectors.toSet())
                : Collections.emptySet();

        List<Carrier> carriers = shippingProviderFactory.get(store).getAvailableCarriers();

        List<CarrierSelectionForm.CarrierSelection> selections = carriers.stream()
                .map(c -> {
                    CarrierSelectionForm.CarrierSelection s = new CarrierSelectionForm.CarrierSelection();
                    s.setId(c.id());
                    s.setName(c.name());
                    s.setDisplayName(c.displayName());
                    s.setSelected(currentCarrierIds.contains(c.id()));
                    return s;
                })
                .collect(Collectors.toList());

        model.addAttribute("availableCarriers", selections);
        return renderStoreShipping(storeId, model);
    }

    @PostMapping("/dashboard/store/shipping/carriers/save")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public String saveAuthorizedCarriers(@ModelAttribute CarrierSelectionForm form,
                                         Locale locale,
                                         RedirectAttributes redirectAttributes) {
        Store store = storesRepository.findById(form.getStoreId());

        if (store.getShippingConfiguration() == null) {
            store.setShippingConfiguration(new ShippingConfiguration());
        }

        List<AuthorizedCarrier> authorized = form.getCarriers().stream()
                .filter(CarrierSelectionForm.CarrierSelection::isSelected)
                .map(c -> new AuthorizedCarrier(c.getId(), c.getName(), c.getDisplayName()))
                .collect(Collectors.toList());

        store.getShippingConfiguration().setAuthorizedCarriers(authorized);
        storesRepository.save(store);

        redirectAttributes.addFlashAttribute("successMessage",
                messageSource.getMessage("store.shipping.carriers.save.success", null, locale));

        return isSuperAdmin()
                ? String.format("redirect:/dashboard/store/%s/shipping", form.getStoreId())
                : "redirect:/dashboard/store/shipping";
    }

    @PostMapping("/dashboard/store/notification/edit")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public String updateStoreNotification(@ModelAttribute StoreForm form, Locale locale, RedirectAttributes redirectAttributes) {
        Store existingStore = storesRepository.findById(form.getStore().getStoreId());
        if (existingStore.getClientNotificationsConfig() == null) {
            existingStore.setClientNotificationsConfig(new ClientNotificationsConfig());
        }

        ClientNotificationsConfig clientNotificationsConfig = form.getStore().getClientNotificationsConfig();
        if (clientNotificationsConfig != null) {
            clientNotificationsConfig.setSupportedTemplates(existingStore.getClientNotificationsConfig().getSupportedTemplates());
            existingStore.setClientNotificationsConfig(clientNotificationsConfig);
        }
        storesRepository.save(existingStore);
        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("store.notification.update.success",null , locale));

        return isSuperAdmin()
                ? String.format("redirect:/dashboard/store/%s/notification", form.getStore().getStoreId())
                : "redirect:/dashboard/store/notification";
    }

    @PostMapping("/dashboard/store/fulfillment")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public String updateStoreFulfillmentSettings(@ModelAttribute StoreForm form, Locale locale, RedirectAttributes redirectAttributes) {
        Store existingStore = storesRepository.findById(form.getStore().getStoreId());
        existingStore.setFulfillmentSettings(form.getStore().getFulfillmentSettings());

        storesRepository.save(existingStore);
        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("store.fulfillment.settings.update.success", null, locale));

        return isSuperAdmin()
                ? String.format("redirect:/dashboard/store/%s/fulfillment", form.getStore().getStoreId())
                : "redirect:/dashboard/store/fulfillment";
    }

    @PostMapping("/dashboard/store/payments/checkout/edit")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public String updateStoreCheckoutSettings(@ModelAttribute StoreForm form, Locale locale, RedirectAttributes redirectAttributes) {
        Store existingStore = storesRepository.findById(form.getStore().getStoreId());
        CheckoutSettings checkoutSettings = form.getStore().getCheckoutSettings();
        checkoutSettings.getDeliveryOptions().removeIf(o -> !o.isComplete());
        existingStore.setCheckoutSettings(checkoutSettings);

        storesRepository.save(existingStore);
        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("store.checkout.settings.update.success", null, locale));

        return isSuperAdmin()
                ? String.format("redirect:/dashboard/store/%s/payments", form.getStore().getStoreId())
                : "redirect:/dashboard/store/payments";
    }

    @PostMapping("/dashboard/store/company-details/edit")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public String updateBillingShippingConfiguration(@ModelAttribute StoreForm form, Locale locale, RedirectAttributes redirectAttributes) {
        Store existingStore = storesRepository.findById(form.getStore().getStoreId());

        if (form.getStore().getBillingDetails().isProperlyFilled()) {
            existingStore.setBillingDetails(form.getStore().getBillingDetails());
        }

        List<ShippingDetails> updatedShippingDetails =
                IntStream.range(0, form.getStore().getShippingDetails().size())
                        .mapToObj(i -> {
                            ShippingDetails detail = form.getStore().getShippingDetails().get(i);
                            detail.set_default(i == form.getDefaultShippingDetailIndex());
                            return detail;
                        })
                        .collect(Collectors.toList());
        existingStore.setShippingDetails(updatedShippingDetails.stream().filter(ShippingDetails::isProperlyFilled).collect(Collectors.toList()));

        storesRepository.save(existingStore);
        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("store.company.details.update.success", null, locale));

        return isSuperAdmin()
                ? String.format("redirect:/dashboard/store/%s/company-details", form.getStore().getStoreId())
                : "redirect:/dashboard/store/company-details";
    }

    @PostMapping("/dashboard/store/rma")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public String updateStoreReturnSettings(@ModelAttribute StoreForm form, Locale locale, RedirectAttributes redirectAttributes) {
        Store existingStore = storesRepository.findById(form.getStore().getStoreId());

        String selectedCarrierName = form.getStore().getRmaSettings().getCarrier() != null
                ? form.getStore().getRmaSettings().getCarrier().getName()
                : null;

        RMASettings rmaSettings = new RMASettings();
        if (selectedCarrierName != null && existingStore.getShippingConfiguration() != null) {
            existingStore.getShippingConfiguration().getAuthorizedCarriers().stream()
                    .filter(c -> c.getName().equals(selectedCarrierName))
                    .findFirst()
                    .ifPresent(rmaSettings::setCarrier);
        }
        existingStore.setRmaSettings(rmaSettings);

        storesRepository.save(existingStore);
        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("store.fulfillment.settings.update.success", null, locale));

        return isSuperAdmin()
                ? String.format("redirect:/dashboard/store/%s/rma", form.getStore().getStoreId())
                : "redirect:/dashboard/store/rma";
    }

    @GetMapping("/dashboard/store/warehouse")
    @PreAuthorize("hasRole('ADMIN')")
    public String storeWarehouse(Model model) {
        return renderStoreWarehouse(getStoreId(), model);
    }

    @GetMapping("/dashboard/store/{storeId}/warehouse")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminStoreWarehouse(@PathVariable String storeId, Model model) {
        return renderStoreWarehouse(storeId, model);
    }

    private String renderStoreWarehouse(String storeId, Model model) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            model.addAttribute("error", "Store not found");
            return "error";
        }

        if (store.getWarehouseConfiguration() == null) {
            store.setWarehouseConfiguration(new WarehouseConfiguration());
        }

        StoreForm form = new StoreForm(store);

        model.addAttribute("form", form);
        return "store-warehouse";
    }

    @PostMapping("/dashboard/store/warehouse/edit")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public String updateStoreWarehouse(@ModelAttribute StoreForm form, Locale locale, RedirectAttributes redirectAttributes) {
        Store existingStore = storesRepository.findById(form.getStore().getStoreId());
        existingStore.setWarehouseConfiguration(form.getStore().getWarehouseConfiguration());

        storesRepository.save(existingStore);
        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("store.warehouse.update.success", null, locale));

        return isSuperAdmin()
                ? String.format("redirect:/dashboard/store/%s/warehouse", form.getStore().getStoreId())
                : "redirect:/dashboard/store/warehouse";
    }

    @GetMapping("/dashboard/store/report")
    @PreAuthorize("hasRole('ADMIN')")
    public String storeReport(Model model) {
        return renderStoreReport(getStoreId(), model);
    }

    @GetMapping("/dashboard/store/{storeId}/report")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminStoreReport(@PathVariable String storeId, Model model) {
        return renderStoreReport(storeId, model);
    }

    private String renderStoreReport(String storeId, Model model) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            model.addAttribute("error", "Store not found");
            return "error";
        }

        if (store.getReportingConfiguration() == null) {
            store.setReportingConfiguration(new ReportingConfiguration());
        }

        StoreForm form = new StoreForm(store);
        model.addAttribute("form", form);
        model.addAttribute("apiDomain", apiDomain);

        return "store-report";
    }

    @PostMapping("/dashboard/store/report")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public String updateStoreReport(@ModelAttribute StoreForm form, Locale locale, RedirectAttributes redirectAttributes) {
        Store existingStore = storesRepository.findById(form.getStore().getStoreId());

        ReportingConfiguration config = form.getStore().getReportingConfiguration();
        if (config == null) {
            config = new ReportingConfiguration();
        }

        if (config.isGoogleAdsEnabled()) {
            if (StringUtils.isBlank(config.getGoogleAdsToken())) {
                config.setGoogleAdsToken(UUID.randomUUID().toString());
            }
        } else {
            config.setGoogleAdsToken(null);
            config.setGoogleAdsEnabled(false);
        }

        existingStore.setReportingConfiguration(config);
        storesRepository.save(existingStore);

        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("store.report.configuration.update.success", null, locale));

        return isSuperAdmin()
                ? String.format("redirect:/dashboard/store/%s/report", form.getStore().getStoreId())
                : "redirect:/dashboard/store/report";
    }

    private Branding createOrUpdateBranding(StoreForm form, Store store) throws IOException {
        Branding branding = new Branding();
        branding.setPrimaryColor(form.getStore().getBranding().getPrimaryColor());
        branding.setSecondaryColor(form.getStore().getBranding().getSecondaryColor());
        if (store.getBranding() != null && store.getBranding().getLogo() != null) {
            branding.setLogo(store.getBranding().getLogo());
        }

        if (form.hasLogoFile()) {
            branding.setLogo(saveLogo(form, store.getStoreId()));
        }

        return branding;
    }

    private String saveLogo(StoreForm form, String storeId) throws IOException {
        String fileName = form.getLogoFile().getOriginalFilename();
        byte[] bytes = form.getLogoFile().getBytes();
        return storesRepository.storeLogo(storeId, fileName, bytes);
    }

    private String getStoreId() { return CustomSecurityContext.getStoreId(); }

    private boolean isSuperAdmin() { return CustomSecurityContext.hasRole("SUPER_ADMIN"); }

    private List<ConnectedIntegration> connectedIntegration(String providerName) {
        return providerName != null ? List.of(new ConnectedIntegration(providerName, true)) : List.of();
    }

}
