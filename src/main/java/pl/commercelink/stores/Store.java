package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.taxonomy.ProductGroup;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;

@DynamoDBTable(tableName = "Stores")
public class Store {

    @DynamoDBHashKey(attributeName = "storeId")
    private String storeId;
    @DynamoDBAttribute(attributeName = "name")
    private String name;
    @DynamoDBAttribute(attributeName = "branding")
    private Branding branding;
    @DynamoDBAttribute(attributeName = "invoicing")
    private InvoicingConfiguration invoicingConfiguration;
    @DynamoDBAttribute(attributeName = "marketplaces")
    private List<MarketplaceIntegration> marketplaces = new LinkedList<>();
    @DynamoDBAttribute(attributeName = "payments")
    private List<PaymentIntegration> payments = new LinkedList<>();
    @DynamoDBAttribute(attributeName = "notifications")
    private List<StoreNotification> notifications = new LinkedList<>();
    @DynamoDBAttribute(attributeName = "bankAccounts")
    private List<BankAccount> bankAccounts = new LinkedList<>();
    @DynamoDBAttribute(attributeName = "clientNotifications")
    private ClientNotificationsConfiguration clientNotificationsConfiguration;
    @DynamoDBAttribute(attributeName = "integrations")
    private List<Integration> integrations = new LinkedList<>();
    @DynamoDBAttribute(attributeName = "apiKey")
    private String apiKey;
    @DynamoDBAttribute(attributeName = "fulfilment")
    private FulfilmentConfiguration fulfilmentConfiguration;
    @DynamoDBAttribute(attributeName = "billingDetails")
    private BillingDetails billingDetails;
    @DynamoDBAttribute(attributeName = "shippingDetails")
    private List<ShippingDetails> shippingDetails = new LinkedList<>();
    @DynamoDBAttribute(attributeName = "checkout")
    private CheckoutConfiguration checkoutConfiguration;
    @DynamoDBAttribute(attributeName = "rma")
    private RMAConfiguration rmaConfiguration;
    @DynamoDBAttribute(attributeName = "warehouse")
    private WarehouseConfiguration warehouseConfiguration;
    @DynamoDBAttribute(attributeName = "reporting")
    private ReportingConfiguration reportingConfiguration;
    @DynamoDBAttribute(attributeName = "shipping")
    private ShippingConfiguration shippingConfiguration;
    @DynamoDBAttribute(attributeName = "demo")
    private DemoStoreMetadata demo;
    @DynamoDBVersionAttribute
    private Long version;

    public Store() {
    }

    @DynamoDBIgnore
    public MarketplaceIntegration getMarketplaceIntegration(String marketplaceName) {
        Optional<MarketplaceIntegration> integration = marketplaces.stream()
                .filter(i -> marketplaceName.equals(i.getName()))
                .findFirst();
        return integration.orElse(null);
    }

    @DynamoDBIgnore
    public void updateLastFetchedAt(String marketplaceName) {
        marketplaces.forEach(marketplace -> {
            if (marketplaceName.equals(marketplace.getName())) {
                marketplace.setLastFetchedAt(LocalDateTime.now());
            }
        });
    }

    @DynamoDBIgnore
    public void markConnectionAsRestored(String marketplace) {
        marketplaces.forEach(m -> {
            if (marketplace.equals(m.getName())) {
                m.setLoggedIn(true);
            }
        });
        String tokenName = marketplace.toLowerCase() + "_marketplace";
        notifications.removeIf(n -> n.getType() == StoreNotificationType.UNAUTHENTICATED && tokenName.equals(n.getObject()));
    }

    @DynamoDBIgnore
    public void markConnectionAsLost(String marketplace) {
        marketplaces.forEach(m -> {
            if (marketplace.equals(m.getName())) {
                m.setLoggedIn(false);
            }
        });
        String tokenName = marketplace.toLowerCase() + "_marketplace";
        StoreNotification notification = new StoreNotification(
                StoreNotificationSeverity.WARNING,
                StoreNotificationType.UNAUTHENTICATED,
                tokenName,
                "Your connection to " + marketplace + " marketplace has expired, reauthenticate it in the settings");

        if (!notifications.contains(notification)) {
            notifications.add(notification);
        }
    }

    @DynamoDBIgnore
    public ShippingDetails getDefaultShippingDetails() {
        return shippingDetails.stream()
                .filter(ShippingDetails::is_default)
                .findFirst()
                .orElse(null);
    }

    @DynamoDBIgnore
    public BankAccount getDefaultBankAccount() {
        return bankAccounts.stream()
                .filter(BankAccount::is_default)
                .findFirst()
                .orElse(null);
    }

    @DynamoDBIgnore
    public String getConfigurationValue(IntegrationType type) {
        return integrations.stream()
                .filter(config -> config.getType() == type)
                .map(Integration::getName)
                .findFirst()
                .orElse(null);
    }

    @DynamoDBIgnore
    public void removeIntegration(IntegrationType type) {
        integrations.removeIf(config -> config.getType() == type);
    }

    @DynamoDBIgnore
    public void removeMarketplaceIntegration(String marketplaceName) {
        marketplaces.removeIf(m -> marketplaceName.equals(m.getName()));
        String tokenName = marketplaceName.toLowerCase() + "_marketplace";
        notifications.removeIf(n -> n.getType() == StoreNotificationType.UNAUTHENTICATED && tokenName.equals(n.getObject()));
    }

    @DynamoDBIgnore
    public Optional<PaymentIntegration> getDefaultPaymentIntegration() {
        return payments.stream().filter(PaymentIntegration::is_default).findFirst();
    }

    @DynamoDBIgnore
    public PaymentIntegration getPaymentIntegration(String paymentName) {
        return payments.stream()
                .filter(p -> paymentName.equals(p.getName()))
                .findFirst()
                .orElse(null);
    }

    @DynamoDBIgnore
    public PaymentIntegration getPaymentIntegrationOrDefault(String paymentOptionId) {
        if (isBlank(paymentOptionId)) {
            return getDefaultPaymentIntegration()
                    .orElseThrow(() -> new IllegalStateException("No default payment provider configured for store: " + storeId));
        }
        PaymentIntegration integration = getPaymentIntegration(paymentOptionId);
        if (integration == null) {
            throw new IllegalStateException("Payment integration not found: " + paymentOptionId);
        }
        return integration;
    }

    @DynamoDBIgnore
    public void addPaymentIntegration(String paymentName) {
        if (getPaymentIntegration(paymentName) != null) {
            return;
        }
        boolean isFirst = payments.isEmpty();
        payments.add(new PaymentIntegration(paymentName, isFirst));
    }

    @DynamoDBIgnore
    public void setDefaultPaymentIntegration(String paymentName) {
        payments.forEach(p -> p.set_default(paymentName.equals(p.getName())));
    }

    @DynamoDBIgnore
    public void removePaymentIntegration(String paymentName) {
        boolean wasDefault = payments.stream()
                .anyMatch(p -> paymentName.equals(p.getName()) && p.is_default());
        payments.removeIf(p -> paymentName.equals(p.getName()));
        if (wasDefault && !payments.isEmpty()) {
            payments.get(0).set_default(true);
        }
    }

    @DynamoDBIgnore
    public void setConfigurationValue(IntegrationType type, String value) {
        integrations.removeIf(config -> config.getType() == type);
        integrations.add(new Integration(type, value));
    }

    @DynamoDBIgnore
    public String getSecretesName(IntegrationType integrationType){
        return getStoreId() + "-" + getConfigurationValue(integrationType);
    }

    @DynamoDBIgnore
    public String getSecretesName(String integrationName){
        return getStoreId() + "-" + integrationName.toLowerCase();
    }

    @DynamoDBIgnore
    public boolean hasIntegration(IntegrationType integrationType) {
        return integrations.stream().anyMatch(integration -> integration.getType() == integrationType);
    }

    @DynamoDBIgnore
    public boolean hasActiveMarketplaceIntegration(String marketplace) {
        return marketplaces.stream().anyMatch(integration -> marketplace.equals(integration.getName()) && integration.isLoggedIn());
    }

    @DynamoDBIgnore
    public boolean hasDocumentsGenerationEnabled() {
        return warehouseConfiguration != null && warehouseConfiguration.isDocumentsGenerationEnabled();
    }

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public List<MarketplaceIntegration> getMarketplaces() {
        return marketplaces;
    }

    public void setMarketplaces(List<MarketplaceIntegration> marketplaces) {
        this.marketplaces = marketplaces;
    }

    public List<PaymentIntegration> getPayments() {
        return payments;
    }

    public void setPayments(List<PaymentIntegration> payments) {
        this.payments = payments;
    }

    public List<StoreNotification> getNotifications() {
        return notifications;
    }

    public void setNotifications(List<StoreNotification> notifications) {
        this.notifications = notifications;
    }

    public Branding getBranding() { return branding; }
    
    public void setBranding(Branding branding) { this.branding = branding; }

    public List<BankAccount> getBankAccounts() { return bankAccounts; }

    public void setBankAccounts(List<BankAccount> bankAccounts) { this.bankAccounts = bankAccounts; }

    public InvoicingConfiguration getInvoicingConfiguration() {
        return invoicingConfiguration;
    }

    public void setInvoicingConfiguration(InvoicingConfiguration invoicingConfiguration) {
        this.invoicingConfiguration = invoicingConfiguration;
    }

    public ClientNotificationsConfiguration getClientNotificationsConfiguration() {
        return clientNotificationsConfiguration;
    }

    public void setClientNotificationsConfiguration(ClientNotificationsConfiguration clientNotificationsConfiguration) {
        this.clientNotificationsConfiguration = clientNotificationsConfiguration;
    }

    public List<Integration> getIntegrations() {
        return integrations;
    }

    public void setIntegrations(List<Integration> integrations) {
        this.integrations = integrations;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public FulfilmentConfiguration getFulfilmentConfiguration() {
        return fulfilmentConfiguration;
    }

    public void setFulfilmentConfiguration(FulfilmentConfiguration fulfilmentConfiguration) {
        this.fulfilmentConfiguration = fulfilmentConfiguration;
    }

    public BillingDetails getBillingDetails() {
        return billingDetails;
    }

    public void setBillingDetails(BillingDetails billingDetails) {
        this.billingDetails = billingDetails;
    }

    public List<ShippingDetails> getShippingDetails() {
        return shippingDetails;
    }

    public void setShippingDetails(List<ShippingDetails> shippingDetails) {
        this.shippingDetails = shippingDetails;
    }

    public CheckoutConfiguration getCheckoutConfiguration() {
        return checkoutConfiguration;
    }

    public void setCheckoutConfiguration(CheckoutConfiguration checkoutConfiguration) {
        this.checkoutConfiguration = checkoutConfiguration;
    }

    public RMAConfiguration getRmaConfiguration() {
        return rmaConfiguration;
    }

    public void setRmaConfiguration(RMAConfiguration rmaConfiguration) {
        this.rmaConfiguration = rmaConfiguration;
    }

    public WarehouseConfiguration getWarehouseConfiguration() {
        return warehouseConfiguration;
    }

    public void setWarehouseConfiguration(WarehouseConfiguration warehouseConfiguration) {
        this.warehouseConfiguration = warehouseConfiguration;
    }

    public ReportingConfiguration getReportingConfiguration() {
        return reportingConfiguration;
    }

    public void setReportingConfiguration(ReportingConfiguration reportingConfiguration) {
        this.reportingConfiguration = reportingConfiguration;
    }

    public ShippingConfiguration getShippingConfiguration() {
        return shippingConfiguration;
    }

    public void setShippingConfiguration(ShippingConfiguration shippingConfiguration) {
        this.shippingConfiguration = shippingConfiguration;
    }

    public DemoStoreMetadata getDemo() {
        return demo;
    }

    public void setDemo(DemoStoreMetadata demo) {
        this.demo = demo;
    }

    @DynamoDBIgnore
    public boolean isDemoExpired(Instant now) {
        return demo != null && demo.getExpiresAt() != null && Instant.parse(demo.getExpiresAt()).isBefore(now);
    }

    @DynamoDBIgnore
    public boolean isGoogleAdsConversionsEnabled(String token) {
        if (reportingConfiguration == null
                || !reportingConfiguration.isGoogleAdsEnabled()
                || isBlank(reportingConfiguration.getGoogleAdsToken())) {
            return false;
        }
        return reportingConfiguration.getGoogleAdsToken().equals(token);
    }

    @DynamoDBIgnore
    public boolean isPositionConsolidationEnabled() {
        return Optional.ofNullable(invoicingConfiguration)
                .map(InvoicingConfiguration::isPositionsConsolidation)
                .orElse(false);
    }

    @DynamoDBIgnore
    public FulfilmentType getDefaultFulfilmentType() {
        return Optional.ofNullable(fulfilmentConfiguration)
                .map(FulfilmentConfiguration::getDefaultFulfilmentType)
                .orElse(FulfilmentType.WarehouseFulfilment);
    }

    @DynamoDBIgnore
    public List<ProductCategory> getEnabledProductCategories() {
        List<ProductGroup> enabledProductGroups = Optional.ofNullable(fulfilmentConfiguration)
                .map(FulfilmentConfiguration::getEnabledProductGroups)
                .orElse(Collections.emptyList());

        return ProductCategory.values(enabledProductGroups);
    }

    @DynamoDBIgnore
    public List<String> getEnabledProviders() {
        return supplierNamesMatching(connection -> true);
    }

    @DynamoDBIgnore
    public boolean isEnabledSupplier(String supplier) {
        return getEnabledProviders().stream().anyMatch(supplier::equalsIgnoreCase);
    }

    @DynamoDBIgnore
    public List<String> getOwnSupplierNames() {
        return supplierNamesByMode(ConnectionMode.OWN);
    }

    @DynamoDBIgnore
    public List<String> getGlobalSupplierNames() {
        return supplierNamesByMode(ConnectionMode.GLOBAL);
    }

    @DynamoDBIgnore
    public List<String> getManualSupplierNames() {
        return supplierNamesByMode(ConnectionMode.MANUAL);
    }

    @DynamoDBIgnore
    public List<String> getOwnAndManualSupplierNames() {
        return supplierNamesMatching(connection ->
                connection.getMode() == ConnectionMode.OWN || connection.getMode() == ConnectionMode.MANUAL);
    }

    @DynamoDBIgnore
    public List<String> ownAndManualSupplierNames(SupplierScope scope) {
        return supplierNamesMatching(connection ->
                (connection.getMode() == ConnectionMode.OWN || connection.getMode() == ConnectionMode.MANUAL)
                        && scope.includes(connection));
    }

    @DynamoDBIgnore
    public boolean hasOwnOrManualSupplierConnections() {
        return !getOwnAndManualSupplierNames().isEmpty();
    }

    @DynamoDBIgnore
    public List<StoreSupplierConnection> getOwnAndManualConnections() {
        return Optional.ofNullable(fulfilmentConfiguration)
                .map(FulfilmentConfiguration::getSupplierConnections)
                .orElse(Collections.emptyList())
                .stream()
                .filter(connection -> connection.getMode() == ConnectionMode.OWN
                        || connection.getMode() == ConnectionMode.MANUAL)
                .collect(Collectors.toList());
    }

    @DynamoDBIgnore
    public List<String> supplierNames(ConnectionMode mode, SupplierScope scope) {
        return supplierNamesMatching(connection -> connection.getMode() == mode && scope.includes(connection));
    }

    @DynamoDBIgnore
    public boolean hasOwnSupplierConnections() {
        return !getOwnSupplierNames().isEmpty();
    }

    @DynamoDBIgnore
    public boolean canUseGlobalSuppliers() {
        return Optional.ofNullable(fulfilmentConfiguration)
                .map(FulfilmentConfiguration::isCanUseGlobalSuppliers)
                .orElse(false);
    }

    @DynamoDBIgnore
    public Optional<Integer> getInventoryCacheTtlMinutes() {
        return Optional.ofNullable(fulfilmentConfiguration)
                .map(FulfilmentConfiguration::getInventoryCacheTtlMinutes);
    }

    private List<String> supplierNamesByMode(ConnectionMode mode) {
        return supplierNamesMatching(connection -> connection.getMode() == mode);
    }

    private List<String> supplierNamesMatching(Predicate<StoreSupplierConnection> filter) {
        return Optional.ofNullable(fulfilmentConfiguration)
                .map(FulfilmentConfiguration::getSupplierConnections)
                .orElse(Collections.emptyList())
                .stream()
                .filter(filter)
                .map(StoreSupplierConnection::getSupplierName)
                .collect(Collectors.toList());
    }

    @DynamoDBIgnore
    public List<ShippingDetails> getPickUpAddresses() {
        return Optional.ofNullable(shippingConfiguration)
                .map(ShippingConfiguration::getPickUpAddresses)
                .orElse(Collections.emptyList())
                .stream()
                .sorted(Comparator.comparing(ShippingDetails::is_default).reversed())
                .collect(Collectors.toList());
    }

    @DynamoDBIgnore
    public ShippingDetails getPickUpAddress(String pickupAddressId) {
        return Optional.ofNullable(shippingConfiguration)
                .map(config -> config.getPickUpAddress(pickupAddressId))
                .orElse(null);
    }

    @DynamoDBIgnore
    public Optional<ShippingDetails> getDefaultPickupAddress() {
        return Optional.ofNullable(shippingConfiguration).flatMap(ShippingConfiguration::getDefaultPickUpAddress);
    }

    @DynamoDBIgnore
    public Optional<ShippingDetails> getDefaultSenderAddress() {
        return Optional.ofNullable(shippingConfiguration).flatMap(ShippingConfiguration::getDefaultSenderAddress);
    }

    @DynamoDBIgnore
    public List<PackageTemplate> getPackageTemplates() {
        return Optional.ofNullable(shippingConfiguration)
                .map(ShippingConfiguration::getPackageTemplates)
                .orElse(Collections.emptyList())
                .stream()
                .sorted(Comparator.comparing(PackageTemplate::isDefault).reversed())
                .collect(Collectors.toList());
    }

    @DynamoDBIgnore
    public PackageTemplate getPackageTemplate(String packageTemplateId) {
        return Optional.ofNullable(shippingConfiguration)
                .map(config -> config.getPackageTemplate(packageTemplateId))
                .orElseThrow(() -> new IllegalArgumentException("Invalid packageTemplateId"));
    }
}
