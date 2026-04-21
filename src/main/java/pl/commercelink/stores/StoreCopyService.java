package pl.commercelink.stores;

import org.springframework.stereotype.Service;
import pl.commercelink.products.*;
import pl.commercelink.starter.util.UniqueIdentifierGenerator;
import pl.commercelink.templates.EmailTemplate;
import pl.commercelink.templates.EmailTemplatesRepository;

import org.apache.commons.lang3.StringUtils;

import java.util.*;

@Service
public class StoreCopyService {

    private final StoresRepository storesRepository;
    private final ProductCatalogRepository productCatalogRepository;
    private final ProductRepository productRepository;
    private final EmailTemplatesRepository emailTemplatesRepository;

    public StoreCopyService(StoresRepository storesRepository,
                            ProductCatalogRepository productCatalogRepository,
                            ProductRepository productRepository,
                            EmailTemplatesRepository emailTemplatesRepository) {
        this.storesRepository = storesRepository;
        this.productCatalogRepository = productCatalogRepository;
        this.productRepository = productRepository;
        this.emailTemplatesRepository = emailTemplatesRepository;
    }

    public Store copyStore(String sourceStoreId, String newStoreName) {
        if (StringUtils.isBlank(newStoreName)) {
            throw new IllegalArgumentException("Store name must not be blank");
        }

        Store source = storesRepository.findById(sourceStoreId);
        if (source == null) {
            throw new IllegalArgumentException("Source store not found: " + sourceStoreId);
        }

        String newStoreId = UniqueIdentifierGenerator.generate();
        System.out.println("[StoreCopy] Starting copy of store " + sourceStoreId + " -> " + newStoreId);

        Store newStore = copyStoreEntity(source, newStoreId, newStoreName);
        storesRepository.save(newStore);
        System.out.println("[StoreCopy] Store entity copied: " + newStoreName);

        copyEmailTemplates(source, newStoreId);
        copyProductCatalogs(sourceStoreId, newStoreId);

        System.out.println("[StoreCopy] Completed copy of store " + newStoreId);
        return newStore;
    }

    private Store copyStoreEntity(Store source, String newStoreId, String newStoreName) {
        Store target = new Store();
        target.setStoreId(newStoreId);
        target.setName(newStoreName);

        if (source.getBranding() != null) {
            Branding branding = new Branding();
            branding.setPrimaryColor(source.getBranding().getPrimaryColor());
            branding.setSecondaryColor(source.getBranding().getSecondaryColor());
            target.setBranding(branding);
        }

        target.setInvoicingConfiguration(source.getInvoicingConfiguration());
        target.setFulfilmentSettings(source.getFulfilmentSettings());
        target.setCheckoutSettings(source.getCheckoutSettings());
        target.setRmaSettings(source.getRmaSettings());
        if (source.getWarehouseConfiguration() != null) {
            WarehouseConfiguration warehouseConfig = new WarehouseConfiguration();
            warehouseConfig.setWarehouseId(source.getWarehouseConfiguration().getWarehouseId());
            warehouseConfig.setDocumentsGenerationEnabled(source.getWarehouseConfiguration().isDocumentsGenerationEnabled());
            target.setWarehouseConfiguration(warehouseConfig);
        }
        if (source.getShippingConfiguration() != null) {
            ShippingConfiguration shippingConfig = new ShippingConfiguration();
            shippingConfig.setPackageTemplates(new LinkedList<>(source.getShippingConfiguration().getPackageTemplates()));
            target.setShippingConfiguration(shippingConfig);
        }

        target.setClientNotificationsConfig(source.getClientNotificationsConfig());

        target.setApiKey(null);
        target.setBankAccounts(new LinkedList<>());
        target.setMarketplaces(new LinkedList<>());
        target.setIntegrations(new LinkedList<>());
        target.setNotifications(new LinkedList<>());

        return target;
    }

    private void copyProductCatalogs(String sourceStoreId, String newStoreId) {
        List<ProductCatalog> catalogs = productCatalogRepository.findAll(sourceStoreId);

        for (ProductCatalog sourceCatalog : catalogs) {
            Map<String, String> categoryIdMapping = new HashMap<>();

            ProductCatalog newCatalog = new ProductCatalog();
            newCatalog.setStoreId(newStoreId);
            newCatalog.setCatalogId(UniqueIdentifierGenerator.generate());
            newCatalog.setName(sourceCatalog.getName());
            newCatalog.setDeletionProtection(sourceCatalog.isDeletionProtection());

            List<CategoryDefinition> newCategories = new LinkedList<>();
            for (CategoryDefinition sourceCategory : sourceCatalog.getCategories()) {
                String newCategoryId = UniqueIdentifierGenerator.generate();
                categoryIdMapping.put(sourceCategory.getCategoryId(), newCategoryId);

                CategoryDefinition newCategory = new CategoryDefinition();
                newCategory.setCategoryId(newCategoryId);
                newCategory.setName(sourceCategory.getName());
                newCategory.setCategory(sourceCategory.getCategory());
                newCategory.setType(sourceCategory.getType());
                newCategory.setRequiredDuringOrder(sourceCategory.isRequiredDuringOrder());
                newCategory.setSequenceNumber(sourceCategory.getSequenceNumber());
                newCategory.setGroupingOrder(sourceCategory.getGroupingOrder());
                newCategory.setMaxQty(sourceCategory.getMaxQty());
                newCategory.setDeletionProtection(sourceCategory.isDeletionProtection());
                newCategory.setStockDefinition(sourceCategory.getStockDefinition());
                newCategory.setPriceDefinitions(sourceCategory.getPriceDefinitions());
                newCategory.setInventoryDefinitions(sourceCategory.getInventoryDefinitions());
                newCategory.setMarketplaceDefinitions(sourceCategory.getMarketplaceDefinitions());
                newCategory.setAvailabilityDefinition(sourceCategory.getAvailabilityDefinition());
                newCategory.setTypeChangedAt(sourceCategory.getTypeChangedAt());
                newCategories.add(newCategory);
            }
            newCatalog.setCategories(newCategories);
            productCatalogRepository.save(newCatalog);
            System.out.println("[StoreCopy] Copied catalog '" + sourceCatalog.getName() + "' with " + newCategories.size() + " categories");

            copyProducts(categoryIdMapping);
        }
    }

    private void copyProducts(Map<String, String> categoryIdMapping) {
        for (Map.Entry<String, String> entry : categoryIdMapping.entrySet()) {
            String oldCategoryId = entry.getKey();
            String newCategoryId = entry.getValue();

            List<Product> sourceProducts = productRepository.findAll(oldCategoryId);
            if (sourceProducts == null || sourceProducts.isEmpty()) {
                continue;
            }

            List<Product> newProducts = new ArrayList<>();
            for (Product source : sourceProducts) {
                Product target = new Product();
                target.setCategoryId(newCategoryId);
                target.setProductId(UUID.randomUUID().toString());
                target.setPimId(source.getPimId());
                target.setEan(source.getEan());
                target.setManufacturerCode(source.getManufacturerCode());
                target.setBrand(source.getBrand());
                target.setLabel(source.getLabel());
                target.setName(source.getName());
                target.setCategory(source.getCategory());
                target.setEnabled(source.isEnabled());
                target.setCustomAttributesFilters(copyList(source.getCustomAttributesFilters()));
                target.setCustomAttributes(copyList(source.getCustomAttributes()));
                target.setRecommendation(source.getRecommendation());
                target.setQuickFilters(copyList(source.getQuickFilters()));
                target.setProductPage(source.getProductPage());
                target.setSuggestedRetailPrice(source.getSuggestedRetailPrice());
                target.setAvailabilityType(source.getAvailabilityType());
                target.setStockExpectedQty(source.getStockExpectedQty());
                target.setRestockPricePromo(source.getRestockPricePromo());
                target.setRestockPriceStandard(source.getRestockPriceStandard());
                target.setEstimatedDeliveryDays(source.getEstimatedDeliveryDays());
                target.setPricingGroup(source.getPricingGroup());
                target.setMetadata(copyList(source.getMetadata()));
                target.setMarketplaces(copyList(source.getMarketplaces()));
                newProducts.add(target);
            }

            if (!newProducts.isEmpty()) {
                productRepository.batchSave(newProducts);
                System.out.println("[StoreCopy] Copied " + newProducts.size() + " products for category " + newCategoryId);
            }
        }
    }

    private void copyEmailTemplates(Store sourceStore, String newStoreId) {
        if (sourceStore.getClientNotificationsConfig() == null) {
            return;
        }

        Map<String, String> supportedTemplates = sourceStore.getClientNotificationsConfig().getSupportedTemplates();
        if (supportedTemplates == null || supportedTemplates.isEmpty()) {
            return;
        }

        for (String templateName : supportedTemplates.values()) {
            EmailTemplate source = emailTemplatesRepository.findByTemplateName(sourceStore.getStoreId(), templateName);
            if (source == null) {
                continue;
            }

            EmailTemplate target = new EmailTemplate();
            target.setStoreId(newStoreId);
            target.setTemplateName(source.getTemplateName());
            target.setType(source.getType());
            target.setSubject(source.getSubject());
            target.setTextBody(source.getTextBody());
            target.setAttachments(copyList(source.getAttachments()));
            target.setBccAddresses(copyList(source.getBccAddresses()));
            emailTemplatesRepository.save(target);
            System.out.println("[StoreCopy] Copied email template: " + templateName);
        }
    }

    private <T> List<T> copyList(List<T> source) {
        return source != null ? new LinkedList<>(source) : new LinkedList<>();
    }
}
