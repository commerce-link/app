package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductAvailabilityType;
import pl.commercelink.products.ProductCustomAttribute;
import pl.commercelink.products.ProductCustomAttributeFilter;
import pl.commercelink.starter.dynamodb.Metadata;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ProductFormTest {

    private static final List<String> LABELS = List.of("RTX 5080", "RTX 5090");
    private static final List<String> GROUPS = List.of("Default", "Ultra Premium");
    private static final List<String> MARKETPLACES = List.of("allegro", "empik");

    private static ProductForm valid() {
        ProductForm form = new ProductForm();
        form.setName("Gigabyte RTX 5080");
        form.setEan("4719331361600");
        form.setManufacturerCode("GV-N5080");
        form.setLabel("RTX 5080");
        form.setEnabled(true);
        form.setAvailabilityType("BasedOnSupply");
        form.setSuggestedRetailPrice("4 999");
        form.setMaxRetailPrice("0");
        form.setEstimatedDeliveryDays("0");
        form.setPricingGroup("Ultra Premium");
        form.setStockExpectedQty("2");
        form.setRestockPricePromo("0");
        form.setRestockPriceStandard("0");
        form.setMarketplaces(List.of("allegro"));
        return form;
    }

    @Test
    void validFormAppliesToTheProduct() {
        // given
        ProductForm form = valid();
        form.getQuickFilters().addAll(List.of("gaming", ""));
        ProductCustomAttribute complete = new ProductCustomAttribute();
        complete.setName("chipset");
        complete.setValue("GB203");
        form.getCustomAttributes().add(complete);
        form.getCustomAttributes().add(new ProductCustomAttribute());
        Product product = new Product("cat", "pim-1", "4719331361600", "GV-N5080", "Gigabyte", "old", "old", "Default");

        // when
        Map<String, String> errors = form.validate(LABELS, GROUPS, MARKETPLACES, check -> Optional.of("pim-1"));
        form.applyTo(product);

        // then
        assertThat(errors).isEmpty();
        assertThat(product.getName()).isEqualTo("Gigabyte RTX 5080");
        assertThat(product.getSuggestedRetailPrice()).isEqualTo(4999);
        assertThat(product.getStockExpectedQty()).isEqualTo(2);
        assertThat(product.getPricingGroup()).isEqualTo("Ultra Premium");
        assertThat(product.getMarketplaces()).containsExactly("allegro");
        assertThat(product.getQuickFilters()).containsExactly("gaming");
        assertThat(product.getCustomAttributes()).hasSize(1);
        assertThat(product.getAvailabilityType()).isEqualTo(ProductAvailabilityType.BasedOnSupply);
    }

    @Test
    void nameAndAnIdentifierAreRequired() {
        // given
        ProductForm form = valid();
        form.setName(" ");
        form.setEan("");
        form.setManufacturerCode("");

        // when
        Map<String, String> errors = form.validate(LABELS, GROUPS, MARKETPLACES, check -> Optional.empty());

        // then
        assertThat(errors).containsEntry("name", "product.error.name.required")
                .containsEntry("ean", "product.error.identifier.required");
    }

    @Test
    void eanMustBeDigitsWhenGiven() {
        // given
        ProductForm form = valid();
        form.setEan("12ab");

        // when / then
        assertThat(form.validate(LABELS, GROUPS, MARKETPLACES, check -> Optional.empty()))
                .containsEntry("ean", "product.error.ean.invalid");
    }

    @Test
    void fixedPriceNeedsAMinimumPrice() {
        // given
        ProductForm form = valid();
        form.setAvailabilityType("AlwaysAvailable");
        form.setSuggestedRetailPrice("0");

        // when / then
        assertThat(form.validate(LABELS, GROUPS, MARKETPLACES, check -> Optional.empty()))
                .containsEntry("suggestedRetailPrice", "product.error.srp.requiredForFixed");
    }

    @Test
    void changingTheIdentifierToAnotherPimEntryIsAnError() {
        // given
        ProductForm form = valid();
        form.setExistingPimId("pim-1");

        // when / then
        assertThat(form.validate(LABELS, GROUPS, MARKETPLACES, check -> Optional.of("pim-2")))
                .containsEntry("ean", "product.error.pim.changed");
        assertThat(form.validate(LABELS, GROUPS, MARKETPLACES, check -> Optional.of("pim-1"))).isEmpty();
    }

    @Test
    void labelOutsideTheListIsOnlyAllowedWhenItWasAlreadySaved() {
        // given
        ProductForm form = valid();
        form.setLabel("RTX 4060");

        // when / then
        assertThat(form.validate(LABELS, GROUPS, MARKETPLACES, check -> Optional.of("pim-1")))
                .containsEntry("label", "product.error.label.notInList");
        form.setExistingLabel("RTX 4060");
        assertThat(form.validate(LABELS, GROUPS, MARKETPLACES, check -> Optional.of("pim-1"))).isEmpty();
    }

    @Test
    void unknownMarketplaceAndGroupAreRejected() {
        // given
        ProductForm form = valid();
        form.setMarketplaces(List.of("morele"));
        form.setPricingGroup("Nope");

        // when
        Map<String, String> errors = form.validate(LABELS, GROUPS, MARKETPLACES, check -> Optional.of("pim-1"));

        // then
        assertThat(errors).containsEntry("marketplaces", "product.error.marketplace.unknown")
                .containsEntry("pricingGroup", "product.error.group.unknown");
    }

    @Test
    void incompleteRepeatedRowsAreKeyedByTheIdOfTheirFirstField() {
        // given
        ProductForm form = valid();
        ProductCustomAttribute attribute = new ProductCustomAttribute();
        attribute.setName("chipset");
        form.getCustomAttributes().add(attribute);
        ProductCustomAttributeFilter filter = new ProductCustomAttributeFilter();
        filter.setName("RAM");
        form.getCustomAttributesFilters().add(filter);
        form.getMetadata().add(new Metadata("gtin", null));

        // when
        Map<String, String> errors = form.validate(LABELS, GROUPS, MARKETPLACES, check -> Optional.of("pim-1"));

        // then
        assertThat(errors).containsEntry("customAttribute-0-name", "product.error.attribute.incomplete")
                .containsEntry("customAttributeFilter-0-name", "product.error.filter.incomplete")
                .containsEntry("metadata-0-key", "product.error.metadata.incomplete");
    }

    @Test
    void emptyRepeatedRowsAreDroppedInsteadOfBeingReported() {
        // given
        ProductForm form = valid();
        form.getCustomAttributes().add(new ProductCustomAttribute());
        form.getCustomAttributesFilters().add(new ProductCustomAttributeFilter());
        form.getMetadata().add(new Metadata());
        Product product = new Product("cat", "pim-1", "4719331361600", "GV-N5080", "Gigabyte", "old", "old", "Default");

        // when
        Map<String, String> errors = form.validate(LABELS, GROUPS, MARKETPLACES, check -> Optional.of("pim-1"));
        form.applyTo(product);

        // then
        assertThat(errors).isEmpty();
        assertThat(product.getCustomAttributes()).isEmpty();
        assertThat(product.getCustomAttributesFilters()).isEmpty();
        assertThat(product.getMetadata()).isEmpty();
    }

    @Test
    void fromFormatsNumbersAndKnowsWhichExtrasAreFilled() {
        // given
        Product product = new Product("cat", "pim-1", "1", "m", "b", "l", "n", "Default");
        product.setSuggestedRetailPrice(4999);
        product.setRecommendation("Top pick");

        // when
        ProductForm form = ProductForm.from(product);

        // then
        assertThat(form.getSuggestedRetailPrice()).isEqualTo("4999");
        assertThat(form.getAvailabilityType()).isEqualTo("BasedOnSupply");
        assertThat(form.hasStockOrMarketplaceValues()).isFalse();
        assertThat(form.hasClientData()).isTrue();
        assertThat(form.getExistingPimId()).isEqualTo("pim-1");
    }

    @Test
    void aNewProductStartsEnabledWhileABoundOneStartsFromTheCheckboxes() {
        // given
        ProductForm blank = new ProductForm();

        // when
        ProductForm created = ProductForm.forNewProduct();

        // then
        assertThat(created.isEnabled()).isTrue();
        assertThat(created.getPricingGroup()).isEqualTo("Default");
        assertThat(blank.isEnabled()).isFalse();
    }

    @Test
    void theNewProductCarriesTheBrandItWasPrefilledWith() {
        // given
        ProductForm form = valid();
        form.setBrand("Gigabyte");

        // when
        Product product = form.toNewProduct("cat-1");

        // then
        assertThat(product.getCategoryId()).isEqualTo("cat-1");
        assertThat(product.getProductId()).isNotBlank();
        assertThat(product.getBrand()).isEqualTo("Gigabyte");
    }

    /** A request can hand a list property an empty value, which binds as null; nothing sent means an empty list. */
    @Test
    void listsClearedByTheRequestReadAsEmptyInsteadOfNull() {
        // given
        ProductForm form = valid();
        form.setMarketplaces(null);
        form.setQuickFilters(null);
        form.setCustomAttributes(null);
        form.setCustomAttributesFilters(null);
        form.setMetadata(null);
        Product product = new Product("cat", "pim-1", "4719331361600", "GV-N5080", "Gigabyte", "old", "old", "Default");

        // when
        Map<String, String> errors = form.validate(LABELS, GROUPS, MARKETPLACES, check -> Optional.of("pim-1"));
        form.applyTo(product);

        // then
        assertThat(errors).isEmpty();
        assertThat(form.hasClientData()).isFalse();
        assertThat(product.getMarketplaces()).isEmpty();
        assertThat(product.getQuickFilters()).isEmpty();
    }
}
