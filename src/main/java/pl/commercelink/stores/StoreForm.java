package pl.commercelink.stores;

import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
public class StoreForm {

    private Store store;
    private MultipartFile logoFile;
    private String defaultBankAccountId;
    private String shippingProvider;
    private String invoicingSoftwareProvider;
    private String wmsProvider;
    private Map<String, String> providerConfiguration;
    private int defaultShippingDetailIndex;
    private String defaultPickupAddressId;
    private String defaultSenderAddressId;

    // payments
    private String paymentProviderName;

    // marketplaces
    private String marketplace;

    public StoreForm() {
        this.providerConfiguration = new HashMap<>();
    }

    public StoreForm(Store store) {
        this.store = store;
        this.shippingProvider = store.getConfigurationValue(IntegrationType.SHIPPING_PROVIDER);
        this.invoicingSoftwareProvider =  store.getConfigurationValue(IntegrationType.INVOICING_PROVIDER);
        this.wmsProvider = store.getConfigurationValue(IntegrationType.WMS_PROVIDER);
        this.paymentProviderName = store.getConfigurationValue(IntegrationType.PAYMENT_PROVIDER);
    }

    public Store getStore() {
        return store;
    }

    public void setStore(Store store) {
        this.store = store;
    }

    public MultipartFile getLogoFile() {
        return logoFile;
    }

    public void setLogoFile(MultipartFile logoFile) {
        this.logoFile = logoFile;
    }

    public boolean hasLogoFile() {
        return logoFile != null && !logoFile.isEmpty();
    }

    public String getShippingProvider() {
        return shippingProvider;
    }

    public void setShippingProvider(String shippingProvider) {
        this.shippingProvider = shippingProvider;
    }

    public String getInvoicingSoftwareProvider() {
        return invoicingSoftwareProvider;
    }

    public void setInvoicingSoftwareProvider(String invoicingSoftwareProvider) {
        this.invoicingSoftwareProvider = invoicingSoftwareProvider;
    }

    public String getDefaultBankAccountId() {
        return defaultBankAccountId;
    }

    public void setDefaultBankAccountId(String defaultBankAccountId) {
        this.defaultBankAccountId = defaultBankAccountId;
    }

    public Map<String, String> getProviderConfiguration() {
        return providerConfiguration;
    }

    public void setProviderConfiguration(Map<String, String> providerConfiguration) {
        this.providerConfiguration = providerConfiguration;
    }

    public String getPaymentProviderName() {
        return paymentProviderName;
    }

    public void setPaymentProviderName(String paymentProviderName) {
        this.paymentProviderName = paymentProviderName;
    }

    public int getDefaultShippingDetailIndex() {
        return defaultShippingDetailIndex;
    }

    public void setDefaultShippingDetailIndex(int defaultShippingDetailIndex) {
        this.defaultShippingDetailIndex = defaultShippingDetailIndex;
    }

    public String getDefaultPickupAddressId() {
        return defaultPickupAddressId;
    }

    public void setDefaultPickupAddressId(String defaultPickupAddressId) {
        this.defaultPickupAddressId = defaultPickupAddressId;
    }

    public String getDefaultSenderAddressId() {
        return defaultSenderAddressId;
    }

    public void setDefaultSenderAddressId(String defaultSenderAddressId) {
        this.defaultSenderAddressId = defaultSenderAddressId;
    }

    public String getMarketplace() {
        return marketplace;
    }

    public void setMarketplace(String marketplace) {
        this.marketplace = marketplace;
    }

    public String getWmsProvider() {
        return wmsProvider;
    }

    public void setWmsProvider(String wmsProvider) {
        this.wmsProvider = wmsProvider;
    }
}
