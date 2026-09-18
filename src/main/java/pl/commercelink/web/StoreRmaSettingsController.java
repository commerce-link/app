package pl.commercelink.web;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.orders.rma.RMAReturnOption;
import pl.commercelink.orders.rma.RMAShippingService;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.AuthorizedCarrier;
import pl.commercelink.stores.RMAConfiguration;
import pl.commercelink.stores.ShippingConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.PickerOption;
import pl.commercelink.web.settings.RmaReadiness;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;
import pl.commercelink.web.settings.WarehouseAddressView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Settings › RMA: the carrier customers ship returned goods with. The store comes from the session (ADMIN) or the path
 * (SUPER_ADMIN), never from the form. The chosen carrier is stored as a copy of an authorised carrier, so removing it
 * from the authorised list on the shipping page leaves the copy in use; the page shows that instead of hiding it.
 */
@Controller
@RequiredArgsConstructor
public class StoreRmaSettingsController {

    private static final String VIEW = "store-rma";
    private static final String FORM_FRAGMENT = VIEW + " :: rmaForm";
    private static final String CARRIER_FIELD = "carrierId";

    private final StoresRepository storesRepository;
    private final RMAShippingService rmaShippingService;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/store/rma")
    @PreAuthorize("hasRole('ADMIN')")
    public String rma(Model model, Locale locale) {
        return render(CustomSecurityContext.getStoreId(), null, Map.of(), model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/rma")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminRma(@PathVariable String storeId, Model model, Locale locale) {
        return render(storeId, null, Map.of(), model, locale);
    }

    @PostMapping("/dashboard/store/rma")
    @PreAuthorize("hasRole('ADMIN')")
    public String saveRma(@RequestParam(required = false) String carrierId,
                          @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                          Model model, Locale locale, RedirectAttributes redirectAttributes,
                          HttpServletResponse response) {
        return save(CustomSecurityContext.getStoreId(), carrierId, SettingsPaths.isAsync(requestedWith), model, locale,
                redirectAttributes, response);
    }

    @PostMapping("/dashboard/store/{storeId}/rma")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminSaveRma(@PathVariable String storeId, @RequestParam(required = false) String carrierId,
                                    @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                    Model model, Locale locale, RedirectAttributes redirectAttributes,
                                    HttpServletResponse response) {
        return save(storeId, carrierId, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes,
                response);
    }

    private String save(String storeId, String carrierId, boolean async, Model model, Locale locale,
                        RedirectAttributes redirectAttributes, HttpServletResponse response) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return render(storeId, null, Map.of(), model, locale);
        }

        // Only a carrier on the authorised list is accepted: not an empty choice, which would switch customer returns
        // off (their link answers 404), and not the saved copy of a carrier removed from the list since.
        Optional<AuthorizedCarrier> chosen = authorizedCarriers(store).stream()
                .filter(carrier -> Objects.equals(carrier.getId(), carrierId))
                .findFirst();
        if (chosen.isEmpty()) {
            String errorKey = StringUtils.isBlank(carrierId)
                    ? "rma.settings.carrier.required"
                    : "rma.settings.carrier.unauthorized";
            String view = render(storeId, carrierId, Map.of(CARRIER_FIELD, errorKey), model, locale);
            if (async) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                return FORM_FRAGMENT;
            }
            return view;
        }

        AuthorizedCarrier carrier = chosen.get();
        RMAConfiguration configuration = new RMAConfiguration();
        configuration.setCarrier(new AuthorizedCarrier(carrier.getId(), carrier.getName(), carrier.getDisplayName()));
        store.setRmaConfiguration(configuration);
        storesRepository.save(store);

        String successMessage = messageSource.getMessage("rma.settings.update.success", null, locale);
        if (async) {
            render(storeId, null, Map.of(), model, locale);
            model.addAttribute("savedMessage", successMessage);
            return FORM_FRAGMENT;
        }
        SettingsFlash.onRedirect(redirectAttributes, successMessage);
        return "redirect:" + SettingsPaths.store(storeId, "/rma");
    }

    /**
     * @param submittedId the carrier of a rejected submission, selected again; null selects the saved carrier
     */
    private String render(String storeId, String submittedId, Map<String, String> errors, Model model, Locale locale) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            model.addAttribute("error", "Store not found");
            return "error";
        }

        AuthorizedCarrier saved = savedCarrier(store);
        List<AuthorizedCarrier> authorized = authorizedCarriers(store);
        boolean savedAuthorized = saved != null && authorized.stream()
                .anyMatch(carrier -> Objects.equals(carrier.getId(), saved.getId()));

        // Once a carrier is saved there is no empty option to fall back to: choosing it would switch customer returns
        // off. A saved carrier no longer on the authorised list stays first, marked, so the select does not silently
        // jump to another carrier.
        List<PickerOption> options = new ArrayList<>();
        if (saved == null) {
            options.add(new PickerOption("", messageSource.getMessage("rma.settings.carrier.placeholder", null, locale)));
        } else if (!savedAuthorized) {
            options.add(new PickerOption(saved.getId(),
                    messageSource.getMessage("rma.settings.carrier.unauthorized.option", new Object[]{label(saved)}, locale)));
        }
        authorized.forEach(carrier -> options.add(new PickerOption(carrier.getId(), label(carrier))));

        String shippingHref = SettingsPaths.store(storeId, "/shipping");
        List<String> returnTemplates = rmaShippingService.getAvailableReturnOptions(store).stream()
                .map(RMAReturnOption::getName)
                .toList();
        String receivingAddress = store.getDefaultPickupAddress()
                .map(details -> WarehouseAddressView.of(details, locale, ""))
                .map(view -> Stream.of(view.title(), view.addressLine())
                        .filter(StringUtils::isNotBlank)
                        .collect(Collectors.joining(" · ")))
                .filter(StringUtils::isNotBlank)
                .orElse(null);

        model.addAttribute("carrierOptions", options);
        model.addAttribute("carrierId", submittedId != null ? submittedId : saved == null ? "" : saved.getId());
        model.addAttribute("hasAuthorizedCarriers", !authorized.isEmpty());
        model.addAttribute("errors", errors);
        model.addAttribute("readiness", new RmaReadiness(saved == null ? null : label(saved), savedAuthorized,
                returnTemplates, receivingAddress, shippingHref));
        model.addAttribute("formAction", SettingsPaths.store(storeId, "/rma"));
        return VIEW;
    }

    private static AuthorizedCarrier savedCarrier(Store store) {
        return store.getRmaConfiguration() == null ? null : store.getRmaConfiguration().getCarrier();
    }

    private static List<AuthorizedCarrier> authorizedCarriers(Store store) {
        ShippingConfiguration shipping = store.getShippingConfiguration();
        return shipping == null || shipping.getAuthorizedCarriers() == null ? List.of() : shipping.getAuthorizedCarriers();
    }

    private static String label(AuthorizedCarrier carrier) {
        return StringUtils.isNotBlank(carrier.getDisplayName()) ? carrier.getDisplayName() : carrier.getName();
    }
}
