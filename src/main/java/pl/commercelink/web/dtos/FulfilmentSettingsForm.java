package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;

/**
 * Backs the fulfilment settings edit modal on the store fulfilment screen (see
 * fragments/fulfilment-settings-section.html) and the read-only display it saves back into.
 * Deliberately narrower than {@link pl.commercelink.stores.StoreForm}: this form carries only the
 * settings the modal edits, not the supplier connections (which have their own per-supplier
 * endpoints) or anything else on {@link FulfilmentConfiguration}.
 */
@Getter
@Setter
public class FulfilmentSettingsForm {

    private int orderAssemblyDays;
    private int orderRealizationDays;
    private boolean automatedFulfilment;
    private FulfilmentType defaultFulfilmentType;
    private boolean canUseGlobalSuppliers;
    private Integer inventoryCacheTtlMinutes;

    public static FulfilmentSettingsForm from(Store store) {
        FulfilmentConfiguration config = store.getFulfilmentConfiguration() != null
                ? store.getFulfilmentConfiguration() : new FulfilmentConfiguration();
        FulfilmentSettingsForm form = new FulfilmentSettingsForm();
        form.setOrderAssemblyDays(config.getOrderAssemblyDays());
        form.setOrderRealizationDays(config.getOrderRealizationDays());
        form.setAutomatedFulfilment(config.isAutomatedFulfilment());
        form.setDefaultFulfilmentType(config.getDefaultFulfilmentType());
        // The two super-admin-only fields are read off the store's own resolving accessors
        // (rather than the raw configuration) so a store without any fulfilment configuration yet
        // still reports its real defaults instead of false/null.
        form.setCanUseGlobalSuppliers(store.canUseGlobalSuppliers());
        form.setInventoryCacheTtlMinutes(store.getInventoryCacheTtlMinutes().orElse(null));
        return form;
    }

    /**
     * Builds the {@link FulfilmentConfiguration} to submit to
     * {@code StoreSupplierConnectionService.applyStoreSettings}, which overwrites
     * {@code canUseGlobalSuppliers}/{@code inventoryCacheTtlMinutes} for a non-super-admin caller
     * and the product groups/categories/supplier connections regardless -- this form only needs to
     * carry what it actually edits.
     */
    public FulfilmentConfiguration toFulfilmentConfiguration() {
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setOrderAssemblyDays(orderAssemblyDays);
        config.setOrderRealizationDays(orderRealizationDays);
        config.setAutomatedFulfilment(automatedFulfilment);
        config.setDefaultFulfilmentType(defaultFulfilmentType);
        config.setCanUseGlobalSuppliers(canUseGlobalSuppliers);
        config.setInventoryCacheTtlMinutes(inventoryCacheTtlMinutes);
        return config;
    }
}
