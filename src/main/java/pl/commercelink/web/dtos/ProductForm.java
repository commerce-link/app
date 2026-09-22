package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.products.PriceDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductAvailabilityType;
import pl.commercelink.products.ProductCustomAttribute;
import pl.commercelink.products.ProductCustomAttributeFilter;
import pl.commercelink.starter.dynamodb.Metadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * One catalog product. Numbers arrive as text; identity (EAN, manufacturer code) may change only while it still
 * resolves to the same PIM entry, because the price list and the offer are keyed by PIM id.
 */
@Getter
@Setter
public class ProductForm {

    /** The identifiers as they would be saved, asked of the PIM catalog to see whether the entry behind them changed. */
    public record PimCheck(String ean, String mfn) {
    }

    /** Repeat ids of the lists at the bottom of the page; also the first part of every field id and error key there. */
    public static final String ATTRIBUTE = "customAttribute";
    public static final String FILTER = "customAttributeFilter";
    public static final String METADATA = "metadata";
    public static final String QUICK_FILTER = "quickFilter";

    private String name;
    private String ean;
    private String manufacturerCode;
    private String label;
    /** An unticked checkbox is absent from the POST, so a bound form reads as "off" unless the box came back. */
    private boolean enabled;
    private boolean service;
    /** Taken from PIM or from the inventory when the product is prefilled; not typed and not editable. */
    private String brand;
    private String availabilityType = ProductAvailabilityType.BasedOnSupply.name();
    private String suggestedRetailPrice = "0";
    private String maxRetailPrice = "0";
    private String maxRetailPriceSuppliers;
    private String estimatedDeliveryDays = "0";
    private String pricingGroup;
    private String stockExpectedQty = "0";
    private String restockPricePromo = "0";
    private String restockPriceStandard = "0";
    private List<String> marketplaces = new ArrayList<>();
    private String recommendation;
    private List<String> quickFilters = new ArrayList<>();
    private List<ProductCustomAttribute> customAttributes = new ArrayList<>();
    private List<ProductCustomAttributeFilter> customAttributesFilters = new ArrayList<>();
    private List<Metadata> metadata = new ArrayList<>();
    /** Set by the controller from the saved product (never trusted from the request). */
    private String existingPimId;
    private String existingLabel;

    /**
     * The lists are read without a null check everywhere below, and a request can hand a list property an empty value,
     * which binds as null; an empty list is what "nothing sent" means here.
     */
    public void setMarketplaces(List<String> marketplaces) {
        this.marketplaces = orEmpty(marketplaces);
    }

    public void setQuickFilters(List<String> quickFilters) {
        this.quickFilters = orEmpty(quickFilters);
    }

    public void setCustomAttributes(List<ProductCustomAttribute> customAttributes) {
        this.customAttributes = orEmpty(customAttributes);
    }

    public void setCustomAttributesFilters(List<ProductCustomAttributeFilter> customAttributesFilters) {
        this.customAttributesFilters = orEmpty(customAttributesFilters);
    }

    public void setMetadata(List<Metadata> metadata) {
        this.metadata = orEmpty(metadata);
    }

    /** The form offered for a new product: active, priced by the group every category has. */
    public static ProductForm forNewProduct() {
        ProductForm form = new ProductForm();
        form.enabled = true;
        form.pricingGroup = PriceDefinition.DEFAULT_PRICING_GROUP;
        return form;
    }

    public static ProductForm from(Product product) {
        ProductForm form = new ProductForm();
        form.name = product.getName();
        form.ean = product.getEan();
        form.manufacturerCode = product.getManufacturerCode();
        form.label = product.getLabel();
        form.enabled = product.isEnabled();
        form.service = product.isService();
        form.brand = product.getBrand();
        form.availabilityType = (product.getAvailabilityType() == null
                ? ProductAvailabilityType.BasedOnSupply : product.getAvailabilityType()).name();
        form.suggestedRetailPrice = String.valueOf(product.getSuggestedRetailPrice());
        form.maxRetailPrice = String.valueOf(product.getMaxRetailPrice());
        form.maxRetailPriceSuppliers = product.getMaxRetailPriceSuppliers();
        form.estimatedDeliveryDays = String.valueOf(product.getEstimatedDeliveryDays());
        form.pricingGroup = product.getPricingGroup();
        form.stockExpectedQty = String.valueOf(product.getStockExpectedQty());
        form.restockPricePromo = String.valueOf(product.getRestockPricePromo());
        form.restockPriceStandard = String.valueOf(product.getRestockPriceStandard());
        form.marketplaces = new ArrayList<>(product.getMarketplaces());
        form.recommendation = product.getRecommendation();
        form.quickFilters = new ArrayList<>(product.getQuickFilters());
        form.customAttributes = new ArrayList<>(product.getCustomAttributes());
        form.customAttributesFilters = new ArrayList<>(product.getCustomAttributesFilters());
        form.metadata = new ArrayList<>(product.getMetadata());
        form.existingPimId = product.getPimId();
        form.existingLabel = product.getLabel();
        return form;
    }

    /** The id of a field of the row at {@code index}; an error is keyed by it, so the summary links to the field. */
    public static String fieldId(String repeatId, int index, String field) {
        return repeatId + "-" + index + "-" + field;
    }

    /**
     * @param categoryLabels   the labels the category offers, or empty when it groups by nothing and any label passes
     * @param pricingGroups    the pricing groups of the category; the product must land in one of them
     * @param storeMarketplaces the marketplaces the store is connected to; only those may approve the product
     * @param pimIdFor         the PIM entry the submitted identifiers resolve to, asked only for a saved product
     */
    public Map<String, String> validate(List<String> categoryLabels, List<String> pricingGroups,
                                        List<String> storeMarketplaces, Function<PimCheck, Optional<String>> pimIdFor) {
        Map<String, String> errors = new LinkedHashMap<>();
        FormRules.requireText(errors, "name", name, "product.error.name.required");
        if (StringUtils.isBlank(ean) && StringUtils.isBlank(manufacturerCode)) {
            errors.put("ean", "product.error.identifier.required");
        } else if (StringUtils.isNotBlank(ean) && !ean.trim().matches("\\d{8,14}")) {
            errors.put("ean", "product.error.ean.invalid");
        } else if (StringUtils.isNotBlank(existingPimId)) {
            Optional<String> resolved = pimIdFor.apply(
                    new PimCheck(StringUtils.trimToNull(ean), StringUtils.trimToNull(manufacturerCode)));
            if (!Objects.equals(existingPimId, resolved.orElse(null))) {
                errors.put("ean", "product.error.pim.changed");
            }
        }
        // A label the category no longer offers stays allowed while it is the one the product was saved with: the page
        // says so with an option of its own instead of moving the product to another label behind the operator's back.
        if (!categoryLabels.isEmpty() && !categoryLabels.contains(StringUtils.trim(label))
                && !Objects.equals(StringUtils.trim(label), existingLabel)) {
            errors.put("label", "product.error.label.notInList");
        }
        if (parseAvailability().isEmpty()) {
            errors.put("availabilityType", "product.error.availability.invalid");
        }
        Optional<Integer> minimumPrice = amount(errors, "suggestedRetailPrice", suggestedRetailPrice);
        amount(errors, "maxRetailPrice", maxRetailPrice);
        amount(errors, "estimatedDeliveryDays", estimatedDeliveryDays);
        amount(errors, "stockExpectedQty", stockExpectedQty);
        amount(errors, "restockPricePromo", restockPricePromo);
        amount(errors, "restockPriceStandard", restockPriceStandard);
        if (parseAvailability().filter(type -> type == ProductAvailabilityType.AlwaysAvailable).isPresent()
                && minimumPrice.filter(value -> value > 0).isEmpty()) {
            errors.put("suggestedRetailPrice", "product.error.srp.requiredForFixed");
        }
        if (pricingGroup == null || pricingGroups.stream().noneMatch(group -> group.equalsIgnoreCase(pricingGroup))) {
            errors.put("pricingGroup", "product.error.group.unknown");
        }
        if (!storeMarketplaces.containsAll(marketplaces)) {
            errors.put("marketplaces", "product.error.marketplace.unknown");
        }
        for (int index = 0; index < customAttributes.size(); index++) {
            ProductCustomAttribute attribute = customAttributes.get(index);
            if (!isEmpty(attribute) && !attribute.isComplete()) {
                errors.put(fieldId(ATTRIBUTE, index, "name"), "product.error.attribute.incomplete");
            }
        }
        for (int index = 0; index < customAttributesFilters.size(); index++) {
            ProductCustomAttributeFilter filter = customAttributesFilters.get(index);
            if (!isEmpty(filter) && !filter.isComplete()) {
                errors.put(fieldId(FILTER, index, "name"), "product.error.filter.incomplete");
            }
        }
        for (int index = 0; index < metadata.size(); index++) {
            Metadata entry = metadata.get(index);
            if ((StringUtils.isNotBlank(entry.getKey()) || StringUtils.isNotBlank(entry.getValue()))
                    && !entry.isComplete()) {
                errors.put(fieldId(METADATA, index, "key"), "product.error.metadata.incomplete");
            }
        }
        return errors;
    }

    public void applyTo(Product product) {
        product.setName(name.trim());
        product.setEan(StringUtils.trimToNull(ean));
        product.setManufacturerCode(StringUtils.trimToNull(manufacturerCode));
        product.setLabel(StringUtils.trimToNull(label));
        product.setEnabled(enabled);
        product.setService(service);
        product.setAvailabilityType(parseAvailability().orElse(ProductAvailabilityType.BasedOnSupply));
        product.setSuggestedRetailPrice(FormNumbers.integer(suggestedRetailPrice).orElse(0));
        product.setMaxRetailPrice(FormNumbers.integer(maxRetailPrice).orElse(0));
        product.setMaxRetailPriceSuppliers(StringUtils.trimToNull(maxRetailPriceSuppliers));
        product.setEstimatedDeliveryDays(FormNumbers.integer(estimatedDeliveryDays).orElse(0));
        product.setPricingGroup(pricingGroup);
        product.setStockExpectedQty(FormNumbers.integer(stockExpectedQty).orElse(0));
        product.setRestockPricePromo(FormNumbers.integer(restockPricePromo).orElse(0));
        product.setRestockPriceStandard(FormNumbers.integer(restockPriceStandard).orElse(0));
        product.setMarketplaces(new LinkedList<>(marketplaces));
        product.setRecommendation(StringUtils.trimToNull(recommendation));
        product.setQuickFilters(quickFilters.stream().map(StringUtils::trim).filter(StringUtils::isNotEmpty)
                .collect(Collectors.toCollection(LinkedList::new)));
        product.setCustomAttributes(customAttributes.stream().filter(ProductCustomAttribute::isComplete)
                .collect(Collectors.toCollection(LinkedList::new)));
        product.setCustomAttributesFilters(customAttributesFilters.stream().filter(ProductCustomAttributeFilter::isComplete)
                .collect(Collectors.toCollection(LinkedList::new)));
        product.setMetadata(metadata.stream().filter(Metadata::isComplete)
                .collect(Collectors.toCollection(LinkedList::new)));
    }

    /**
     * The product as it is created: its category comes from the address, its id from the application. The PIM id is
     * not one of them — the controller resolves it from the catalog, so a forged one cannot claim another entry.
     */
    public Product toNewProduct(String categoryId) {
        Product product = new Product(categoryId, null, null, null, null, null, null, pricingGroup);
        applyTo(product);
        product.setBrand(StringUtils.trimToNull(brand));
        return product;
    }

    public boolean hasStockOrMarketplaceValues() {
        return FormNumbers.integer(stockExpectedQty).orElse(0) > 0
                || FormNumbers.integer(restockPricePromo).orElse(0) > 0
                || FormNumbers.integer(restockPriceStandard).orElse(0) > 0
                || !marketplaces.isEmpty();
    }

    public boolean hasClientData() {
        return StringUtils.isNotBlank(recommendation)
                || quickFilters.stream().anyMatch(StringUtils::isNotBlank)
                || customAttributes.stream().anyMatch(attribute -> !isEmpty(attribute))
                || customAttributesFilters.stream().anyMatch(filter -> !isEmpty(filter))
                || metadata.stream().anyMatch(entry -> StringUtils.isNotBlank(entry.getKey())
                        || StringUtils.isNotBlank(entry.getValue()));
    }

    private Optional<ProductAvailabilityType> parseAvailability() {
        try {
            return Optional.of(ProductAvailabilityType.valueOf(StringUtils.defaultString(availabilityType)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** An empty field counts as zero, so "no limit" does not have to be typed out. */
    private static Optional<Integer> amount(Map<String, String> errors, String field, String value) {
        Optional<Integer> parsed = FormNumbers.integer(StringUtils.isBlank(value) ? "0" : value)
                .filter(amount -> amount >= 0);
        if (parsed.isEmpty()) {
            errors.put(field, "product.error.amount.invalid");
        }
        return parsed;
    }

    private static <T> List<T> orEmpty(List<T> values) {
        return values == null ? new ArrayList<>() : values;
    }

    private static boolean isEmpty(ProductCustomAttribute attribute) {
        return StringUtils.isBlank(attribute.getName()) && StringUtils.isBlank(attribute.getValue());
    }

    private static boolean isEmpty(ProductCustomAttributeFilter filter) {
        return StringUtils.isBlank(filter.getCategory()) && StringUtils.isBlank(filter.getName())
                && StringUtils.isBlank(filter.getValue()) && StringUtils.isBlank(filter.getOperator());
    }
}
