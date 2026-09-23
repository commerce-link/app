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

import static pl.commercelink.taxonomy.UnifiedProductIdentifiers.unifyEan;
import static pl.commercelink.taxonomy.UnifiedProductIdentifiers.unifyMfn;

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
    /** Set by the controller from the saved product (never trusted from the request); null for a new product. */
    private String existingPimId;
    private String existingLabel;
    private String existingEan;
    private String existingManufacturerCode;
    private String existingPricingGroup;
    private List<String> existingMarketplaces = List.of();

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
        form.rememberSaved(product);
        return form;
    }

    /**
     * What the saved product holds, which the submitted values are compared with: a value kept as it was saved is not
     * held to the rules a new value must meet. Null (a product being created) forgets all of it.
     */
    public void rememberSaved(Product saved) {
        existingPimId = saved == null ? null : saved.getPimId();
        existingLabel = saved == null ? null : saved.getLabel();
        existingEan = saved == null ? null : saved.getEan();
        existingManufacturerCode = saved == null ? null : saved.getManufacturerCode();
        existingPricingGroup = saved == null ? null : saved.getPricingGroup();
        existingMarketplaces = saved == null ? List.of() : new ArrayList<>(saved.getMarketplaces());
    }

    /**
     * Whether the chosen pricing group is one of the category's, regardless of case (the price list matches groups
     * that way). The page offers a group outside the list as an option of its own, so the select never falls back
     * to another group -- and another price -- by itself (OD-2).
     */
    public boolean pricingGroupListedIn(List<String> pricingGroups) {
        return pricingGroup != null && pricingGroups.stream().anyMatch(group -> group.equalsIgnoreCase(pricingGroup));
    }

    /** The id of a field of the row at {@code index}; an error is keyed by it, so the summary links to the field. */
    public static String fieldId(String repeatId, int index, String field) {
        return repeatId + "-" + index + "-" + field;
    }

    /** Neither identifier was given; either one of the two fields would fix it, so both may carry the message. */
    static final String IDENTIFIER_REQUIRED = "product.error.identifier.required";

    /**
     * What is wrong with the identifiers of a product, or null when nothing is: a product is known by its EAN or by
     * its manufacturer code, and an EAN that is given is 8--14 digits. The bulk-add review edits the same two fields
     * and answers with the same messages.
     */
    static String identifierError(String ean, String manufacturerCode) {
        return identifierError(ean, manufacturerCode, true);
    }

    /** @param checkEanFormat false for an EAN kept as it was saved, which may predate the 8--14 digit rule (RF-4) */
    private static String identifierError(String ean, String manufacturerCode, boolean checkEanFormat) {
        if (StringUtils.isBlank(ean) && StringUtils.isBlank(manufacturerCode)) {
            return IDENTIFIER_REQUIRED;
        }
        if (checkEanFormat && StringUtils.isNotBlank(ean) && !ean.trim().matches("\\d{8,14}")) {
            return "product.error.ean.invalid";
        }
        return null;
    }

    /** Whether the EAN differs from the saved one, compared the way the product stores it (trimmed, unified). */
    private boolean eanChanged() {
        return !Objects.equals(unifyEan(StringUtils.trimToNull(ean)), unifyEan(StringUtils.trimToNull(existingEan)));
    }

    private boolean manufacturerCodeChanged() {
        return !Objects.equals(unifyMfn(StringUtils.trimToNull(manufacturerCode)),
                unifyMfn(StringUtils.trimToNull(existingManufacturerCode)));
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
        String identifierError = identifierError(ean, manufacturerCode, eanChanged());
        if (identifierError != null) {
            errors.put("ean", identifierError);
        } else if (StringUtils.isNotBlank(existingPimId) && (eanChanged() || manufacturerCodeChanged())) {
            // Asked only when an identifier changes: the saved entry may have been matched through a sibling EAN of
            // the inventory key, which the product's own codes do not lead to, and that is no reason to refuse a save
            // that leaves the identity as it is (OD-1).
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
        // The group the product was saved with stays allowed although the category no longer lists it, like the label.
        if (!pricingGroupListedIn(pricingGroups) && (pricingGroup == null || !pricingGroup.equals(existingPricingGroup))) {
            errors.put("pricingGroup", "product.error.group.unknown");
        }
        // An approval for a marketplace the store has been disconnected from comes back hidden and stays (OD-6);
        // only a marketplace newly approved must be one the store is connected to.
        if (!marketplaces.stream().allMatch(name -> storeMarketplaces.contains(name) || existingMarketplaces.contains(name))) {
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
                errors.put(fieldId(FILTER, index, missingField(filter)), "product.error.filter.incomplete");
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
        return availabilityOf(availabilityType);
    }

    /** The way of pricing a posted value names, or empty for a value no product can have. */
    static Optional<ProductAvailabilityType> availabilityOf(String value) {
        try {
            return Optional.of(ProductAvailabilityType.valueOf(StringUtils.defaultString(value)));
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

    /**
     * The field of an unfinished filter the summary should lead to: the first one left to fill, in the order of the
     * page (RF-29). The PIM category is a picker without a field id of its own, so a filter lacking only that one
     * points at its name, next to it.
     */
    private static String missingField(ProductCustomAttributeFilter filter) {
        if (StringUtils.isBlank(filter.getName())) {
            return "name";
        }
        if (StringUtils.isBlank(filter.getValue())) {
            return "value";
        }
        if (!ProductCustomAttributeFilter.Operator.isKnown(filter.getOperator())) {
            return "operator";
        }
        return "name";
    }

    private static boolean isEmpty(ProductCustomAttributeFilter filter) {
        return StringUtils.isBlank(filter.getCategory()) && StringUtils.isBlank(filter.getName())
                && StringUtils.isBlank(filter.getValue()) && StringUtils.isBlank(filter.getOperator());
    }
}
