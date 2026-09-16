package pl.commercelink.web;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.invoicing.InvoicingProviderFactory;
import pl.commercelink.inventory.supplier.StoreSupplierConnectionService;
import pl.commercelink.inventory.supplier.SupplierConnectionViewFactory;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.marketplace.MarketplaceConnectionService;
import pl.commercelink.marketplace.MarketplaceProviderFactory;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.payments.PaymentProviderFactory;
import pl.commercelink.printing.PrintProviderRegistry;
import pl.commercelink.products.PimCategoryOptions;
import pl.commercelink.shipping.ShippingProviderFactory;
import pl.commercelink.shipping.api.Carrier;
import pl.commercelink.shipping.api.ShippingProviderDescriptor;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.stores.*;
import pl.commercelink.web.settings.SettingsPage;
import pl.commercelink.web.settings.StoreSettingsOverviewFactory;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.dtos.CarrierSelectionForm;
import pl.commercelink.web.dtos.BrandingForm;
import pl.commercelink.web.dtos.CompanyDetailsForm;
import pl.commercelink.web.dtos.ConnectedIntegration;
import pl.commercelink.web.dtos.FulfilmentSettingsForm;
import pl.commercelink.web.dtos.ParcelForm;
import pl.commercelink.web.dtos.PrinterForm;

import java.util.*;
import java.util.stream.Collectors;

@Controller
public class StoreController {

    private static final String ASYNC_FORM_HEADER = "X-Requested-With";
    private static final String ASYNC_FORM_HEADER_VALUE = "fetch";
    private static final String COMPANY_DETAILS_FORM_FRAGMENT = "store-company-details :: companyDetailsForm";
    private static final String BRANDING_FORM_FRAGMENT = "store-branding :: brandingForm";

    @Value("${scheduling.min-interval-minutes}")
    private int scheduleMinIntervalMinutes;

    @Autowired
    private MarketplaceConnectionService marketplaceConnectionService;

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

    @Autowired
    private StoreSupplierConnectionService storeSupplierConnectionService;

    @Autowired
    private SupplierConnectionViewFactory supplierConnectionViewFactory;

    @Value("${api.domain}")
    private String apiDomain;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private PrintProviderRegistry printProviderRegistry;

    @Autowired
    private PimCategoryOptions pimCategoryOptions;

    @Autowired
    private StoreSettingsOverviewFactory storeSettingsOverviewFactory;

    @GetMapping("/dashboard/store")
    @PreAuthorize("hasRole('ADMIN')")
    public String store(Model model) {
        Store store = storesRepository.findById(getStoreId());

        StoreForm form = new StoreForm(store);
        model.addAttribute("form", form);
        model.addAttribute("isSuperAdmin", false);
        model.addAttribute("overview", storeSettingsOverviewFactory.build(store, UserRole.ADMIN));
        return "store";
    }

    @GetMapping("/dashboard/store/branding")
    @PreAuthorize("hasRole('ADMIN')")
    public String storeBranding(Model model) {
        return renderStoreBranding(getStoreId(), null, Map.of(), model);
    }

    @GetMapping("/dashboard/store/{storeId}/branding")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminStoreBranding(@PathVariable String storeId, Model model) {
        return renderStoreBranding(storeId, null, Map.of(), model);
    }

    private String renderStoreBranding(String storeId, BrandingForm submitted, Map<String, String> errors, Model model) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            model.addAttribute("error", "Store not found");
            return "error";
        }

        model.addAttribute("form", submitted != null ? submitted : BrandingForm.from(store));
        model.addAttribute("errors", errors);
        model.addAttribute("formAction", brandingPath(storeId));
        Branding branding = store.getBranding();
        model.addAttribute("hasLogo", branding != null && branding.getLogo() != null);
        model.addAttribute("logoUrl", "/StoreLogo/" + storeId
                + (branding != null && branding.getLogoVersion() != null ? "?v=" + branding.getLogoVersion() : ""));
        model.addAttribute("logoMaxBytes", BrandingForm.LOGO_MAX_BYTES);
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
        model.addAttribute("shippingWebhookUrl", shippingWebhookUrl(storeId, form.getShippingProvider()));
        model.addAttribute("webhookTokenMissing", webhookTokenMissing(store, form.getShippingProvider()));
        model.addAttribute("connectedIntegrations", connectedIntegration(form.getShippingProvider()));
        return "store-shipping";
    }

    private String shippingWebhookUrl(String storeId, String providerName) {
        if (StringUtils.isBlank(providerName)) {
            return null;
        }
        String domain = StringUtils.removeEnd(apiDomain, "/");
        return domain + "/Store/" + storeId + "/Webhooks/Shipping/" + providerName;
    }

    // password fields are masked in the UI configuration, so read the stored configuration to tell "empty" from "hidden"
    boolean webhookTokenMissing(Store store, String providerName) {
        if (StringUtils.isBlank(providerName)) {
            return false;
        }
        ShippingProviderDescriptor descriptor = shippingProviderFactory.getDescriptor(providerName);
        if (descriptor == null || descriptor.configurationFields().stream().noneMatch(f -> "webhookToken".equals(f.key()))) {
            return false;
        }
        Map<String, String> configuration = shippingProviderFactory.loadConfiguration(store, providerName);
        return configuration == null || StringUtils.isBlank(configuration.get("webhookToken"));
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

        if (store.getClientNotificationsConfiguration() == null) {
            store.setClientNotificationsConfiguration(new ClientNotificationsConfiguration());
        }

        StoreForm form = new StoreForm(store);

        model.addAttribute("form", form);
        return "store-notification";
    }

    @GetMapping("/dashboard/store/fulfilment")
    @PreAuthorize("hasRole('ADMIN')")
    public String storeFulfilmentConfiguration(Model model) {
        return renderStoreFulfilmentConfiguration(getStoreId(), model);
    }

    @GetMapping("/dashboard/store/{storeId}/fulfilment")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminStoreFulfilmentConfiguration(@PathVariable String storeId, Model model) {
        return renderStoreFulfilmentConfiguration(storeId, model);
    }

    private String renderStoreFulfilmentConfiguration(String storeId, Model model) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            model.addAttribute("error", "Store not found");
            return "error";
        }
        if (store.getFulfilmentConfiguration() == null) {
            store.setFulfilmentConfiguration(new FulfilmentConfiguration());
        }

        Map<String, List<ProviderField>> supplierFields = storeSupplierConnectionService.configurationFields();

        StoreForm form = new StoreForm(store);
        Map<String, Map<String, String>> configurations = storeSupplierConnectionService.configurationsForUI(store);

        model.addAttribute("form", form);
        model.addAttribute("settings", FulfilmentSettingsForm.from(store));
        model.addAttribute("fulfilmentTypes", FulfilmentType.values());
        model.addAttribute("supplierFields", supplierFields);
        // Published on the external section's root as data-suppliers-with-stored-config (see
        // fragments/supplier-section.html), keyed by connection identity, so the modal's JS derives
        // password requiredness per connection on every open. The markup itself never carries it:
        // the modal is rendered once, before any connection is picked, and is never re-rendered by
        // an async section swap.
        Set<String> suppliersWithStoredConfig = storeSupplierConnectionService.suppliersWithStoredConfiguration(store);
        model.addAttribute("suppliersWithStoredConfigJoined", String.join(";", suppliersWithStoredConfig));
        // The modal's credential inputs are grouped per supplier type, so two connections of one
        // type share one group: the values travel per connection instead, as a JSON blob on each
        // table row, and the modal fills the group from the row it is editing.
        model.addAttribute("supplierConfigurations", SupplierSectionModel.configurationPayloads(configurations));
        model.addAttribute("connectionModes", Arrays.stream(ConnectionMode.values())
                .filter(mode -> mode != ConnectionMode.MANUAL)
                .toList());
        model.addAttribute("isSuperAdmin", isSuperAdmin());
        model.addAttribute("scheduleMinIntervalMinutes", scheduleMinIntervalMinutes);

        SupplierConnectionViewFactory.SupplierConnectionViews views = supplierConnectionViewFactory.views(store);
        model.addAttribute("externalConnections", views.external());
        model.addAttribute("manualConnections", views.manual());
        model.addAttribute("basePath", isSuperAdmin()
                ? "/dashboard/store/" + storeId
                : "/dashboard/store");

        // A supplier type may be connected several times (one per label), so the Add dropdown no
        // longer removes already-connected types -- it always offers every registered type.
        List<String> allSupplierNames = supplierRegistry.getExternalSupplierNames();
        model.addAttribute("availableSuppliers", allSupplierNames);
        // Rendered once as a data attribute on the stable section container so the page script can
        // recompute the Add dropdown after an async swap without a second request.
        model.addAttribute("allSupplierNames", allSupplierNames);

        return "store-fulfilment";
    }

    @GetMapping("/dashboard/store/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public String storeCategories(Model model) {
        return renderStoreCategories(getStoreId(), model);
    }

    @GetMapping("/dashboard/store/{storeId}/categories")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminStoreCategories(@PathVariable String storeId, Model model) {
        return renderStoreCategories(storeId, model);
    }

    private String renderStoreCategories(String storeId, Model model) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            model.addAttribute("error", "Store not found");
            return "error";
        }

        model.addAttribute("storeId", storeId);
        model.addAttribute("categoryNames", pimCategoryOptions.topLevelNames());
        model.addAttribute("enabledCategories", store.getEnabledCategories());
        model.addAttribute("isSuperAdmin", isSuperAdmin());

        return "store-categories";
    }

    @PostMapping("/dashboard/store/categories")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public String updateStoreCategories(@RequestParam String storeId,
                                        @RequestParam(required = false) List<String> enabledCategories,
                                        Locale locale, RedirectAttributes redirectAttributes) {
        String targetStoreId = isSuperAdmin() ? storeId : getStoreId();

        Store store = storesRepository.findById(targetStoreId);
        if (store == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Store not found.");
            return redirectToCategories(targetStoreId);
        }
        if (store.getFulfilmentConfiguration() == null) {
            store.setFulfilmentConfiguration(new FulfilmentConfiguration());
        }
        store.getFulfilmentConfiguration().setEnabledCategories(
                enabledCategories != null ? enabledCategories : new ArrayList<>());
        storesRepository.save(store);

        redirectAttributes.addFlashAttribute("successMessage",
                messageSource.getMessage("store.categories.update.success", null, locale));
        return redirectToCategories(targetStoreId);
    }

    private String redirectToCategories(String storeId) {
        return isSuperAdmin()
                ? String.format("redirect:/dashboard/store/%s/categories", storeId)
                : "redirect:/dashboard/store/categories";
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

        // a store created by StoreCreationService has no checkout configuration until this page is saved once
        CheckoutConfiguration checkoutConfiguration = Objects.requireNonNullElseGet(
                store.getCheckoutConfiguration(), CheckoutConfiguration::new);
        store.setCheckoutConfiguration(checkoutConfiguration);
        checkoutConfiguration.getDeliveryOptions().add(new DeliveryOption());
        checkoutConfiguration.getDeliveryOptions().add(new DeliveryOption());

        StoreForm form = new StoreForm(store);
        form.setProviderConfiguration(new HashMap<>());

        List<ConnectedIntegration> integrations = store.getPayments().stream()
                .map(p -> new ConnectedIntegration(p.getName(), true, p.is_default()))
                .toList();

        model.addAttribute("form", form);
        model.addAttribute("availableProviders", paymentProviderFactory.availableProviders());
        model.addAttribute("selectedProviderName", form.getPaymentProviderName());
        model.addAttribute("shipmentTypes", ShipmentType.values());
        model.addAttribute("connectedIntegrations", integrations);

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

        MarketplaceSectionModel.render(marketplaceConnectionService, store, null, model);
        model.addAttribute("basePath", SupplierSectionModel.basePath(storeId));
        model.addAttribute("isSuperAdmin", isSuperAdmin());
        model.addAttribute("allMarketplaces", marketplaceProviderFactory.availableProviders());
        model.addAttribute("marketplaceConfigurations", marketplaceConnectionService.configurationsForUI(store));
        model.addAttribute("marketplacesWithStoredConfig", marketplaceConnectionService.marketplacesWithStoredConfiguration(store));
        model.addAttribute("scheduleMinIntervalMinutes", marketplaceConnectionService.minIntervalMinutes());

        return "store-marketplaces";
    }

    @GetMapping("/dashboard/store/company-details")
    @PreAuthorize("hasRole('ADMIN')")
    public String storeCompanyDetails(Model model, Locale locale) {
        return renderStoreCompanyDetails(getStoreId(), null, Map.of(), model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/company-details")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminStoreCompanyDetails(@PathVariable String storeId, Model model, Locale locale) {
        return renderStoreCompanyDetails(storeId, null, Map.of(), model, locale);
    }

    private String renderStoreCompanyDetails(String storeId, CompanyDetailsForm submitted, Map<String, String> errors,
                                             Model model, Locale locale) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            model.addAttribute("error", "Store not found");
            return "error";
        }

        CompanyDetailsForm form = submitted != null ? submitted : CompanyDetailsForm.from(store.getBillingDetails());
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("countries", CompanyDetailsForm.countryOptions(form.getCountry(), locale));
        model.addAttribute("formAction", companyDetailsPath(storeId));
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
        if (store.getRmaConfiguration() == null) {
            store.setRmaConfiguration(new RMAConfiguration());
        }

        StoreForm form = new StoreForm(store);

        List<AuthorizedCarrier> carriers = store.getShippingConfiguration() != null
                ? store.getShippingConfiguration().getAuthorizedCarriers()
                : Collections.emptyList();

        model.addAttribute("form", form);
        model.addAttribute("carrierTypes", carriers);
        return "store-rma";
    }

    // The store is taken from the session (ADMIN) or the path (SUPER_ADMIN), never from the submitted form.
    @PostMapping("/dashboard/store/branding")
    @PreAuthorize("hasRole('ADMIN')")
    public String updateStoreBranding(@ModelAttribute BrandingForm form,
                                      @RequestHeader(value = ASYNC_FORM_HEADER, required = false) String requestedWith,
                                      Model model, Locale locale, RedirectAttributes redirectAttributes,
                                      HttpServletResponse response) {
        return saveStoreBranding(getStoreId(), form, ASYNC_FORM_HEADER_VALUE.equals(requestedWith), model, locale,
                redirectAttributes, response);
    }

    @PostMapping("/dashboard/store/{storeId}/branding")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminUpdateStoreBranding(@PathVariable String storeId, @ModelAttribute BrandingForm form,
                                                @RequestHeader(value = ASYNC_FORM_HEADER, required = false) String requestedWith,
                                                Model model, Locale locale, RedirectAttributes redirectAttributes,
                                                HttpServletResponse response) {
        return saveStoreBranding(storeId, form, ASYNC_FORM_HEADER_VALUE.equals(requestedWith), model, locale,
                redirectAttributes, response);
    }

    private String saveStoreBranding(String storeId, BrandingForm form, boolean async, Model model, Locale locale,
                                     RedirectAttributes redirectAttributes, HttpServletResponse response) {
        Map<String, String> errors = form.validate();
        Store store = storesRepository.findById(storeId);
        if (store == null || !errors.isEmpty()) {
            String view = renderStoreBranding(storeId, form, errors, model);
            if (async && store != null) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                return BRANDING_FORM_FRAGMENT;
            }
            return view;
        }

        form.applyTo(store);
        // Only after the whole form is valid: storing a logo replaces the previous file straight away.
        Branding branding = store.getBranding();
        form.logoUpload().ifPresentOrElse(
                upload -> {
                    branding.setLogo(storesRepository.storeLogo(storeId, "logo." + upload.type().extension(), upload.content()));
                    branding.setLogoVersion(System.currentTimeMillis());
                },
                () -> {
                    if (form.isRemoveLogo() && branding.getLogo() != null) {
                        storesRepository.removeLogo(storeId);
                        branding.setLogo(null);
                        branding.setLogoVersion(null);
                    }
                });
        storesRepository.save(store);

        String successMessage = messageSource.getMessage("store.branding.update.success", null, locale);
        if (async) {
            renderStoreBranding(storeId, BrandingForm.from(store), Map.of(), model);
            model.addAttribute("savedMessage", successMessage);
            return BRANDING_FORM_FRAGMENT;
        }
        redirectAttributes.addFlashAttribute("successMessage", successMessage);
        return "redirect:" + brandingPath(storeId);
    }

    private String brandingPath(String storeId) {
        return isSuperAdmin()
                ? String.format("/dashboard/store/%s/branding", storeId)
                : "/dashboard/store/branding";
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
        // Renders under a non-tile URL, so SettingsPageAdvice cannot recognise it from the request path;
        // set it explicitly (a handler's model attribute overrides the advice's value).
        model.addAttribute("settingsPage", SettingsPage.forTile("/shipping",
                isSuperAdmin() ? UserRole.SUPER_ADMIN : UserRole.ADMIN, storeId));
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
        if (existingStore.getClientNotificationsConfiguration() == null) {
            existingStore.setClientNotificationsConfiguration(new ClientNotificationsConfiguration());
        }

        ClientNotificationsConfiguration clientNotificationsConfiguration = form.getStore().getClientNotificationsConfiguration();
        if (clientNotificationsConfiguration != null) {
            clientNotificationsConfiguration.setSupportedTemplates(existingStore.getClientNotificationsConfiguration().getSupportedTemplates());
            existingStore.setClientNotificationsConfiguration(clientNotificationsConfiguration);
        }
        storesRepository.save(existingStore);
        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("store.notification.update.success",null , locale));

        return isSuperAdmin()
                ? String.format("redirect:/dashboard/store/%s/notification", form.getStore().getStoreId())
                : "redirect:/dashboard/store/notification";
    }

    @PostMapping("/dashboard/store/payments/checkout/edit")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public String updateStoreCheckoutConfiguration(@ModelAttribute StoreForm form, Locale locale, RedirectAttributes redirectAttributes) {
        Store existingStore = storesRepository.findById(form.getStore().getStoreId());
        CheckoutConfiguration checkoutConfiguration = form.getStore().getCheckoutConfiguration();
        checkoutConfiguration.getDeliveryOptions().removeIf(o -> !o.isComplete());
        existingStore.setCheckoutConfiguration(checkoutConfiguration);

        storesRepository.save(existingStore);
        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("store.checkout.settings.update.success", null, locale));

        return isSuperAdmin()
                ? String.format("redirect:/dashboard/store/%s/payments", form.getStore().getStoreId())
                : "redirect:/dashboard/store/payments";
    }

    // The store is taken from the session (ADMIN) or the path (SUPER_ADMIN), never from the submitted form.
    @PostMapping("/dashboard/store/company-details")
    @PreAuthorize("hasRole('ADMIN')")
    public String updateStoreCompanyDetails(@ModelAttribute CompanyDetailsForm form,
                                            @RequestHeader(value = ASYNC_FORM_HEADER, required = false) String requestedWith,
                                            Model model, Locale locale, RedirectAttributes redirectAttributes,
                                            HttpServletResponse response) {
        return saveStoreCompanyDetails(getStoreId(), form, ASYNC_FORM_HEADER_VALUE.equals(requestedWith), model, locale,
                redirectAttributes, response);
    }

    @PostMapping("/dashboard/store/{storeId}/company-details")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminUpdateStoreCompanyDetails(@PathVariable String storeId, @ModelAttribute CompanyDetailsForm form,
                                                      @RequestHeader(value = ASYNC_FORM_HEADER, required = false) String requestedWith,
                                                      Model model, Locale locale, RedirectAttributes redirectAttributes,
                                                      HttpServletResponse response) {
        return saveStoreCompanyDetails(storeId, form, ASYNC_FORM_HEADER_VALUE.equals(requestedWith), model, locale,
                redirectAttributes, response);
    }

    // A form sent by static/js/async-form.js gets only the re-rendered form back (422 with errors, 200 once saved) and
    // swaps it in place; a plain submit without JavaScript keeps the full page render and the redirect after saving.
    private String saveStoreCompanyDetails(String storeId, CompanyDetailsForm form, boolean async, Model model,
                                           Locale locale, RedirectAttributes redirectAttributes,
                                           HttpServletResponse response) {
        Map<String, String> errors = form.validate();
        Store store = storesRepository.findById(storeId);
        if (store == null || !errors.isEmpty()) {
            String view = renderStoreCompanyDetails(storeId, form, errors, model, locale);
            if (async && store != null) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                return COMPANY_DETAILS_FORM_FRAGMENT;
            }
            return view;
        }

        store.setBillingDetails(form.applyTo(store.getBillingDetails()));
        storesRepository.save(store);
        String successMessage = messageSource.getMessage("store.company.details.update.success", null, locale);
        if (async) {
            renderStoreCompanyDetails(storeId, CompanyDetailsForm.from(store.getBillingDetails()), Map.of(), model, locale);
            model.addAttribute("savedMessage", successMessage);
            return COMPANY_DETAILS_FORM_FRAGMENT;
        }
        redirectAttributes.addFlashAttribute("successMessage", successMessage);
        return "redirect:" + companyDetailsPath(storeId);
    }

    private String companyDetailsPath(String storeId) {
        return isSuperAdmin()
                ? String.format("/dashboard/store/%s/company-details", storeId)
                : "/dashboard/store/company-details";
    }

    @PostMapping("/dashboard/store/rma")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public String updateStoreReturnSettings(@ModelAttribute StoreForm form, Locale locale, RedirectAttributes redirectAttributes) {
        Store existingStore = storesRepository.findById(form.getStore().getStoreId());

        String selectedCarrierName = form.getStore().getRmaConfiguration().getCarrier() != null
                ? form.getStore().getRmaConfiguration().getCarrier().getName()
                : null;

        RMAConfiguration rmaConfiguration = new RMAConfiguration();
        if (selectedCarrierName != null && existingStore.getShippingConfiguration() != null) {
            existingStore.getShippingConfiguration().getAuthorizedCarriers().stream()
                    .filter(c -> c.getName().equals(selectedCarrierName))
                    .findFirst()
                    .ifPresent(rmaConfiguration::setCarrier);
        }
        existingStore.setRmaConfiguration(rmaConfiguration);

        storesRepository.save(existingStore);
        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("store.fulfilment.settings.update.success", null, locale));

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

        ShippingDetails blankRow = new ShippingDetails();
        blankRow.set_default(false);
        store.getShippingDetails().add(blankRow);

        StoreForm form = new StoreForm(store);

        model.addAttribute("form", form);
        model.addAttribute("availableProviders", printProviderRegistry.availableProviders());
        return "store-warehouse";
    }

    @PostMapping("/dashboard/store/warehouse/edit")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public String updateStoreWarehouse(@ModelAttribute StoreForm form, Locale locale, RedirectAttributes redirectAttributes) {
        Store existingStore = storesRepository.findById(form.getStore().getStoreId());
        WarehouseConfiguration existingConfiguration = existingStore.getWarehouseConfiguration();
        if (existingConfiguration == null) {
            existingConfiguration = new WarehouseConfiguration();
            existingStore.setWarehouseConfiguration(existingConfiguration);
        }
        WarehouseConfiguration submitted = form.getStore().getWarehouseConfiguration();
        existingConfiguration.setWarehouseId(submitted.getWarehouseId());
        existingConfiguration.setCostCenterId(submitted.getCostCenterId());
        existingConfiguration.setDocumentsGenerationEnabled(submitted.isDocumentsGenerationEnabled());

        existingStore.setShippingDetails(shippingDetailsWithDefault(
                form.getStore().getShippingDetails(), form.getDefaultShippingDetailIndex()));

        storesRepository.save(existingStore);
        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("store.warehouse.update.success", null, locale));

        return isSuperAdmin()
                ? String.format("redirect:/dashboard/store/%s/warehouse", form.getStore().getStoreId())
                : "redirect:/dashboard/store/warehouse";
    }

    private List<ShippingDetails> shippingDetailsWithDefault(List<ShippingDetails> submitted, int defaultIndex) {
        ShippingDetails chosen = defaultIndex >= 0 && defaultIndex < submitted.size()
                ? submitted.get(defaultIndex)
                : null;

        List<ShippingDetails> kept = submitted.stream()
                .filter(ShippingDetails::isProperlyFilled)
                .collect(Collectors.toList());

        kept.forEach(details -> details.set_default(false));

        if (kept.contains(chosen)) {
            chosen.set_default(true);
        } else if (!kept.isEmpty()) {
            kept.getFirst().set_default(true);
        }

        return kept;
    }

    @PostMapping("/dashboard/store/warehouse/printers/add")
    @PreAuthorize("hasRole('ADMIN')")
    public String addWarehousePrinter(@ModelAttribute PrinterForm form, Locale locale, RedirectAttributes redirectAttributes) {
        Store store = storesRepository.findById(getStoreId());
        if (store.getWarehouseConfiguration() == null) {
            store.setWarehouseConfiguration(new WarehouseConfiguration());
        }

        Printer printer = new Printer();
        printer.setName(form.getName());
        printer.setProviderName(form.getProviderName());
        printer.setSettings(form.getSettings());
        store.getWarehouseConfiguration().addPrinter(printer);

        storesRepository.save(store);
        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("store.warehouse.printers.add.success", null, locale));
        return "redirect:/dashboard/store/warehouse";
    }

    @PostMapping("/dashboard/store/warehouse/printers/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteWarehousePrinter(@RequestParam String name, Locale locale, RedirectAttributes redirectAttributes) {
        Store store = storesRepository.findById(getStoreId());
        if (store.getWarehouseConfiguration() != null) {
            store.getWarehouseConfiguration().removePrinter(name);
        }

        storesRepository.save(store);
        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("store.warehouse.printers.delete.success", null, locale));
        return "redirect:/dashboard/store/warehouse";
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

    private String getStoreId() { return CustomSecurityContext.getStoreId(); }

    private boolean isSuperAdmin() { return CustomSecurityContext.hasRole("SUPER_ADMIN"); }

    private List<ConnectedIntegration> connectedIntegration(String providerName) {
        return providerName != null ? List.of(new ConnectedIntegration(providerName, true)) : List.of();
    }

}
