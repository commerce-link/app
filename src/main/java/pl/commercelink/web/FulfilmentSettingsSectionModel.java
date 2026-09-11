package pl.commercelink.web;

import org.springframework.ui.Model;
import pl.commercelink.stores.Store;
import pl.commercelink.web.dtos.FulfilmentSettingsForm;

/**
 * Renders the fulfilment settings section of the store fulfilment screen as a Thymeleaf fragment
 * (see fragments/fulfilment-settings-section.html), the same way {@link SupplierSectionModel}
 * renders the two supplier sections. Shared by the initial page render ({@link StoreController})
 * and the async save endpoint ({@link StoreFulfilmentSettingsController}) that swaps just this
 * section back in after a save instead of redirecting and reloading the whole page.
 *
 * <p>The async endpoint returns the no-argument view name ({@code section}) rather than the
 * parameterized {@code fulfilmentSettingsSection(...)} selector, for the same reason
 * {@code SupplierSectionModel} does: Spring's {@code ThymeleafView} rejects a view name carrying
 * positional fragment parameters, so every value the fragment needs is published as a model
 * attribute instead and the wrapper fragment reads it from there.
 */
public final class FulfilmentSettingsSectionModel {

    private FulfilmentSettingsSectionModel() {
    }

    /**
     * @param refreshExternalSuppliers whether {@code store.canUseGlobalSuppliers()} changed as a
     *                                 result of this save. The external suppliers section's mode
     *                                 column and the supplier modal's mode selector both depend on
     *                                 that flag, so the page script refreshes that section too when
     *                                 this travels back true -- see the fragment root's
     *                                 data-refresh-external-suppliers attribute.
     */
    public static String renderSection(Store store, boolean isSuperAdmin, String successMessage,
                                       boolean refreshExternalSuppliers, Model model) {
        model.addAttribute("sectionSettings", FulfilmentSettingsForm.from(store));
        model.addAttribute("sectionIsSuperAdmin", isSuperAdmin);
        model.addAttribute("sectionSuccessMessage", successMessage);
        model.addAttribute("sectionRefreshExternalSuppliers", refreshExternalSuppliers);
        // No-argument view name: ThymeleafView (unlike th:replace/th:insert) rejects a view name
        // carrying positional fragment parameters, so everything the fragment needs travels as a
        // model attribute and the controller selects a wrapper fragment that has none.
        return "fragments/fulfilment-settings-section :: section";
    }
}
