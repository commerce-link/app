package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.CategoryDefinitionType;
import pl.commercelink.products.CategoryDefinitions;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryBasicsFormTest {

    private static CategoryBasicsForm valid() {
        CategoryBasicsForm form = new CategoryBasicsForm();
        form.setName("GPU");
        form.setSequenceNumber("3");
        form.setType("Managed");
        form.setMaxQty("1");
        form.setLabels(List.of("RTX 5060", "RTX 5070"));
        return form;
    }

    @Test
    void validFormHasNoErrorsAndMapsToBasics() {
        // given
        CategoryBasicsForm form = valid();

        // when
        Map<String, String> errors = form.validate(Set.of("CPU"), null);
        CategoryDefinitions.Basics basics = form.toBasics();

        // then
        assertThat(errors).isEmpty();
        assertThat(basics.type()).isEqualTo(CategoryDefinitionType.Managed);
        assertThat(basics.sequenceNumber()).isEqualTo(3);
        assertThat(basics.maxQty()).isEqualTo(1);
        assertThat(basics.labels()).containsExactly("RTX 5060", "RTX 5070");
    }

    @Test
    void nameMustBeUniqueInTheCatalogIgnoringCase() {
        // given
        CategoryBasicsForm form = valid();
        form.setName("cpu");

        // when / then
        assertThat(form.validate(Set.of("CPU"), null)).containsEntry("name", "catalog.category.name.duplicate");
    }

    /**
     * Categories saved before the name was checked may share one; the check may not stop such a category from being
     * saved at all, only from being renamed onto a name another one carries.
     */
    @Test
    void anExistingDuplicateNameIsAcceptedWhileItIsNotChanged() {
        // given
        CategoryBasicsForm form = valid();
        form.setName(" gpu ");

        // when / then
        assertThat(form.validate(Set.of("GPU"), "GPU")).isEmpty();
    }

    @Test
    void renamingOntoTheNameOfAnotherCategoryIsRefused() {
        // given
        CategoryBasicsForm form = valid();
        form.setName("CPU");

        // when / then
        assertThat(form.validate(Set.of("CPU"), "GPU")).containsEntry("name", "catalog.category.name.duplicate");
    }

    @Test
    void numbersAreValidatedAsTextInRange() {
        // given
        CategoryBasicsForm form = valid();
        form.setSequenceNumber("abc");
        form.setMaxQty("0");

        // when
        Map<String, String> errors = form.validate(Set.of(), null);

        // then
        assertThat(errors).containsEntry("sequenceNumber", "catalog.category.sequence.invalid")
                .containsEntry("maxQty", "catalog.category.maxQty.invalid");
    }

    @Test
    void emptySequenceMeansNextFreeNumberOnCreation() {
        // given
        CategoryBasicsForm form = valid();
        form.setSequenceNumber("");

        // when / then
        assertThat(form.validate(Set.of(), null)).isEmpty();
        assertThat(form.toBasics().sequenceNumber()).isZero();
    }

    @Test
    void duplicateLabelsAreAnError() {
        // given
        CategoryBasicsForm form = valid();
        form.setLabels(List.of("RTX 5060", " RTX 5060 ", ""));

        // when / then
        assertThat(form.validate(Set.of(), null)).containsEntry("labels", "catalog.category.labels.duplicate");
    }

    @Test
    void unknownTypeIsAnError() {
        // given
        CategoryBasicsForm form = valid();
        form.setType("Weird");

        // when / then
        assertThat(form.validate(Set.of(), null)).containsEntry("type", "catalog.category.type.invalid");
    }

    @Test
    void fromCopiesTheDefinition() {
        // given
        CategoryDefinition category = new CategoryDefinition().withName("GPU").withGeneratedId().withSequenceNumber(2).withMaxQty(3);
        category.setGroupingOrder(List.of("A", "B"));
        category.setPimCategoryIds(List.of("pim-1"));
        category.setRequiredDuringOrder(true);

        // when
        CategoryBasicsForm form = CategoryBasicsForm.from(category);

        // then
        assertThat(form.getName()).isEqualTo("GPU");
        assertThat(form.getSequenceNumber()).isEqualTo("2");
        assertThat(form.getMaxQty()).isEqualTo("3");
        assertThat(form.getLabels()).containsExactly("A", "B");
        assertThat(form.getPimCategoryIds()).containsExactly("pim-1");
        assertThat(form.isRequiredDuringOrder()).isTrue();
        assertThat(form.isDeletionProtection()).isTrue();
    }

    /**
     * An unticked checkbox is absent from the POST, so the bound field must start false; the form offered for a new
     * category ticks the protection itself.
     */
    @Test
    void aNewCategoryIsOfferedProtectedWhileTheBoundFieldStartsUnticked() {
        // when
        CategoryBasicsForm bound = new CategoryBasicsForm();
        CategoryBasicsForm offered = CategoryBasicsForm.forNewCategory();

        // then
        assertThat(bound.isDeletionProtection()).isFalse();
        assertThat(offered.isDeletionProtection()).isTrue();
        assertThat(offered.getType()).isEqualTo("Managed");
        assertThat(offered.getMaxQty()).isEqualTo("1");
    }
}
