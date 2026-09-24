package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.web.bind.WebDataBinder;
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
        form.rememberSaved(new Product("cat", "pim-1", null, null, null, null, null, null));

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
        form.rememberSaved(new Product("cat", null, null, null, null, "RTX 4060", null, null));
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
    void incompleteRepeatedRowsAreKeyedByTheIdOfTheFieldToFill() {
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
                // The filter has a name and lacks its value: the error sits at the first field that is missing.
                .containsEntry("customAttributeFilter-0-value", "product.error.filter.incomplete")
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

    private static Product saved(String pimId, String ean, String manufacturerCode) {
        Product product = new Product("cat", pimId, ean, manufacturerCode, "Gigabyte", "RTX 5080", "Gigabyte RTX 5080",
                "Ultra Premium");
        product.setMarketplaces(new java.util.LinkedList<>(List.of("allegro")));
        return product;
    }

    /**
     * OD-1: a product can hold a PIM entry its own two codes do not lead to (the entry matched a sibling EAN of the
     * inventory key, or the PIM changed its GTINs since). Its identity is only at stake when the codes change, so the
     * PIM is asked then and only then -- a price change must not be refused because of it.
     */
    @Test
    void thePimIsAskedOnlyWhenAnIdentifierChanges() {
        // given
        ProductForm unchanged = ProductForm.from(saved("pim-1", "4719331361600", "GV-N5080"));
        unchanged.setSuggestedRetailPrice("5 199");
        // the same codes as saved, as they come back typed with spaces and in lower case
        ProductForm retyped = ProductForm.from(saved("pim-1", "4719331361600", "GV-N5080"));
        retyped.setEan(" 4719331361600 ");
        retyped.setManufacturerCode("gv-n5080");
        ProductForm changed = ProductForm.from(saved("pim-1", "4719331361600", "GV-N5080"));
        changed.setEan("4719331361601");

        // when / then
        assertThat(unchanged.validate(LABELS, GROUPS, MARKETPLACES, check -> Optional.of("pim-2"))).isEmpty();
        assertThat(retyped.validate(LABELS, GROUPS, MARKETPLACES, check -> Optional.of("pim-2"))).isEmpty();
        assertThat(changed.validate(LABELS, GROUPS, MARKETPLACES, check -> Optional.of("pim-2")))
                .containsEntry("ean", "product.error.pim.changed");
    }

    /**
     * RF-4 (variant A): the 8--14 digit rule applies to an EAN being entered; a product saved before the rule existed
     * keeps its EAN through a save that does not touch it. An EAN or a code is still required from every product.
     */
    @Test
    void aLegacyEanIsCheckedOnlyOnceItIsChanged() {
        // given
        ProductForm unchanged = ProductForm.from(saved(null, "590 123", null));
        unchanged.setName("Renamed");
        ProductForm changed = ProductForm.from(saved(null, "590 123", null));
        changed.setEan("590 124");
        ProductForm cleared = ProductForm.from(saved(null, "590 123", null));
        cleared.setEan("");

        // when / then
        assertThat(unchanged.validate(LABELS, GROUPS, MARKETPLACES, check -> Optional.empty())).isEmpty();
        assertThat(changed.validate(LABELS, GROUPS, MARKETPLACES, check -> Optional.empty()))
                .containsEntry("ean", "product.error.ean.invalid");
        assertThat(cleared.validate(LABELS, GROUPS, MARKETPLACES, check -> Optional.empty()))
                .containsEntry("ean", "product.error.identifier.required");
    }

    /** RF-29: a filter complete but for its comparison ("—") is an error of the operator, where the summary links. */
    @Test
    void aFilterWithoutItsOperatorIsAnErrorOfTheOperator() {
        // given
        ProductForm form = valid();
        ProductCustomAttributeFilter filter = new ProductCustomAttributeFilter();
        filter.setCategory("Płyty główne");
        filter.setName("Socket");
        filter.setValue("AM5");
        filter.setOperator("");
        form.getCustomAttributesFilters().add(filter);

        // when / then
        assertThat(form.validate(LABELS, GROUPS, MARKETPLACES, check -> Optional.of("pim-1")))
                .containsOnlyKeys("customAttributeFilter-0-operator")
                .containsEntry("customAttributeFilter-0-operator", "product.error.filter.incomplete");
    }

    /**
     * OD-2: a product priced by a group its category no longer lists (or lists in another case) keeps it through a
     * save that leaves the field alone; only a group newly chosen must come from the list.
     */
    @Test
    void aSavedPricingGroupOutsideTheListPassesWhileANewOneMustBeListed() {
        // given
        Product ultra = saved("pim-1", "4719331361600", "GV-N5080");
        ultra.setPricingGroup("Ultra");
        ProductForm kept = ProductForm.from(ultra);
        Product lowerCase = saved("pim-1", "4719331361600", "GV-N5080");
        lowerCase.setPricingGroup("ultra premium");
        ProductForm keptLowerCase = ProductForm.from(lowerCase);
        ProductForm changed = ProductForm.from(ultra);
        changed.setPricingGroup("Mega");
        ProductForm created = valid();
        created.setPricingGroup("Ultra");

        // when / then
        assertThat(kept.validate(LABELS, GROUPS, MARKETPLACES, check -> Optional.of("pim-1"))).isEmpty();
        assertThat(keptLowerCase.validate(LABELS, GROUPS, MARKETPLACES, check -> Optional.of("pim-1"))).isEmpty();
        assertThat(changed.validate(LABELS, GROUPS, MARKETPLACES, check -> Optional.of("pim-1")))
                .containsEntry("pricingGroup", "product.error.group.unknown");
        assertThat(created.validate(LABELS, GROUPS, MARKETPLACES, check -> Optional.of("pim-1")))
                .containsEntry("pricingGroup", "product.error.group.unknown");
    }

    /**
     * N1: the "existing*" fields are set by rememberSaved() from the saved product and are never meant to come from
     * the request; a setter reachable from a POST would be trusted the day some other code path stops calling
     * rememberSaved() first. binder.bind() is the request path itself, so this pins the field as request-proof rather
     * than trusting the call order in the controller.
     */
    @Test
    void existingFieldsAreNotBindableFromTheRequest() {
        // given
        ProductForm target = ProductForm.from(saved("pim-1", "4719331361600", "GV-N5080"));
        WebDataBinder binder = new WebDataBinder(target);
        MutablePropertyValues posted = new MutablePropertyValues(Map.of(
                "existingPimId", "forged-pim",
                "existingEan", "0000000000000",
                "existingManufacturerCode", "FORGED",
                "existingPricingGroup", "Forged",
                "existingLabel", "Forged label"));
        posted.add("existingMarketplaces", new String[]{"forged-marketplace"});

        // when
        binder.bind(posted);

        // then
        assertThat(target.getExistingPimId()).isEqualTo("pim-1");
        assertThat(target.getExistingEan()).isEqualTo("4719331361600");
        assertThat(target.getExistingManufacturerCode()).isEqualTo("GV-N5080");
        assertThat(target.getExistingPricingGroup()).isEqualTo("Ultra Premium");
        assertThat(target.getExistingLabel()).isEqualTo("RTX 5080");
        assertThat(target.getExistingMarketplaces()).containsExactly("allegro");
    }

    /**
     * OD-6: an approval for a marketplace the store is no longer connected to comes back with the form and stays;
     * approving a marketplace the store does not have is still refused.
     */
    @Test
    void anApprovalForAnUnconnectedMarketplaceSurvivesWhileANewOneIsRefused() {
        // given
        Product product = saved("pim-1", "4719331361600", "GV-N5080");
        product.setMarketplaces(new java.util.LinkedList<>(List.of("allegro", "Morele")));
        ProductForm kept = ProductForm.from(product);
        ProductForm added = ProductForm.from(product);
        added.setMarketplaces(List.of("allegro", "Morele", "Wish"));

        // when / then
        assertThat(kept.validate(LABELS, GROUPS, MARKETPLACES, check -> Optional.of("pim-1"))).isEmpty();
        assertThat(added.validate(LABELS, GROUPS, MARKETPLACES, check -> Optional.of("pim-1")))
                .containsEntry("marketplaces", "product.error.marketplace.unknown");
    }
}
