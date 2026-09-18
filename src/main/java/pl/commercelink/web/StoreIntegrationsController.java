package pl.commercelink.web;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.provider.ProviderFactory;
import pl.commercelink.shipping.ShippingProviderFactory;
import pl.commercelink.stores.*;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.dtos.IntegrationCredentialsForm;

import java.util.Locale;
import java.util.Map;

/**
 * The old integration panel ({@code fragments/integration-panel}), still used by the shipping page. Invoicing and
 * payments have their own pages ({@link StoreInvoicingSystemController}, {@link StorePaymentGatewayController}).
 */
@Controller
public class StoreIntegrationsController {

    private final StoresRepository storesRepository;
    private final ShippingProviderFactory shippingProviderFactory;
    private final MessageSource messageSource;

    public StoreIntegrationsController(StoresRepository storesRepository,
                                       ShippingProviderFactory shippingProviderFactory,
                                       MessageSource messageSource) {
        this.storesRepository = storesRepository;
        this.shippingProviderFactory = shippingProviderFactory;
        this.messageSource = messageSource;
    }

    @PostMapping("/dashboard/store/integrations/credentials")
    @PreAuthorize("hasRole('ADMIN')")
    public String saveIntegrationCredentials(@ModelAttribute IntegrationCredentialsForm form,
                                             Locale locale,
                                             RedirectAttributes redirectAttributes) {
        Store store = storesRepository.findById(CustomSecurityContext.getStoreId());
        String providerType = form.getProviderType();
        String providerName = form.getProviderName();
        Map<String, String> config = form.getProviderConfiguration();

        if (StringUtils.isBlank(providerName)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("store.integrations.credentials.missing", null, locale));
            return redirectToIntegrationSettings(providerType, store.getStoreId());
        }

        ProviderFactory<?, ?> factory = resolveFactory(providerType);
        factory.saveConfiguration(store, providerName, config);

        switch (providerType) {
            case "shipping" -> store.setConfigurationValue(IntegrationType.SHIPPING_PROVIDER, providerName);
        }

        storesRepository.save(store);
        redirectAttributes.addFlashAttribute("successMessage",
                messageSource.getMessage("store.integrations.credentials.success", null, locale));
        return redirectToIntegrationSettings(providerType, store.getStoreId());
    }

    @PostMapping("/dashboard/store/integrations/disconnect")
    @PreAuthorize("hasRole('ADMIN')")
    public String disconnectIntegration(@ModelAttribute IntegrationCredentialsForm form,
                                         Locale locale,
                                         RedirectAttributes redirectAttributes) {
        Store store = storesRepository.findById(CustomSecurityContext.getStoreId());
        String providerType = form.getProviderType();
        String providerName = form.getProviderName();

        if (StringUtils.isBlank(providerName)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("store.integrations.credentials.missing", null, locale));
            return redirectToIntegrationSettings(providerType, store.getStoreId());
        }

        ProviderFactory<?, ?> factory = resolveFactory(providerType);
        factory.deleteConfiguration(store, providerName);

        switch (providerType) {
            case "shipping" -> store.removeIntegration(IntegrationType.SHIPPING_PROVIDER);
        }

        storesRepository.save(store);
        redirectAttributes.addFlashAttribute("successMessage",
                messageSource.getMessage("store.integrations.disconnect.success", null, locale));
        return redirectToIntegrationSettings(providerType, store.getStoreId());
    }

    private ProviderFactory<?, ?> resolveFactory(String providerType) {
        return switch (providerType) {
            case "shipping" -> shippingProviderFactory;
            default -> throw new IllegalArgumentException("Unknown provider type: " + providerType);
        };
    }

    private String redirectToIntegrationSettings(String providerType, String storeId) {
        String path = switch (providerType) {
            case "shipping" -> "shipping";
            default -> throw new IllegalArgumentException("Unknown provider type: " + providerType);
        };
        return CustomSecurityContext.hasRole("SUPER_ADMIN")
                ? String.format("redirect:/dashboard/store/%s/%s", storeId, path)
                : String.format("redirect:/dashboard/store/%s", path);
    }
}
