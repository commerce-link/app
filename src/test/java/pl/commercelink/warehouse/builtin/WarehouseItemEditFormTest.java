package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.warehouse.api.ItemCondition;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class WarehouseItemEditFormTest {

    private static WarehouseItem item(FulfilmentStatus status) {
        WarehouseItem item = new WarehouseItem("store-1", "d-1", "Dyski SSD", "Samsung 990 PRO 2TB", "8806094215038", "MZ-V9P2T0BW", 689.0, 3);
        item.setStatus(status);
        item.setComment("old");
        item.setSerialNo("S1, S2");
        return item;
    }

    @Test
    void ofFillsTheFieldsAsTheOperatorReadsThem() {
        // when
        WarehouseItemEditForm form = WarehouseItemEditForm.of(item(FulfilmentStatus.New));

        // then
        assertThat(form.getName()).isEqualTo("Samsung 990 PRO 2TB");
        assertThat(form.getCost()).isEqualTo("689,00");
        assertThat(form.getTax()).isEqualTo("23");
        assertThat(form.getQty()).isEqualTo("3");
        assertThat(form.getStatus()).isEqualTo("New");
        assertThat(form.getCondition()).isEqualTo("Sealed");
        assertThat(form.getUnitSystemCost()).isEmpty();
    }

    @Test
    void ofCopiesTheOptimisticLockVersion() {
        // given
        WarehouseItem versioned = item(FulfilmentStatus.Delivered);
        versioned.setVersion(5L);

        // when / then
        assertThat(WarehouseItemEditForm.of(versioned).getVersion()).isEqualTo("5");
        assertThat(WarehouseItemEditForm.of(item(FulfilmentStatus.Delivered)).getVersion()).isEqualTo(WarehouseItemEditForm.NO_VERSION);
    }

    @Test
    void destroyedItemAcceptsOnlyTheNameAndComment() {
        // given
        WarehouseItem stored = item(FulfilmentStatus.Destroyed);
        WarehouseItemEditForm form = WarehouseItemEditForm.of(stored);
        form.setComment("Flooded in hall B");
        form.setName("Forged");
        form.setStatus("Delivered");
        form.setQty("99");
        form.setCondition("Damaged");

        // when
        Map<String, String> errors = form.validate(FulfilmentStatus.Destroyed);
        WarehouseItem updated = form.toUpdate(stored);

        // then
        assertThat(errors).isEmpty();
        assertThat(updated.getComment()).isEqualTo("Flooded in hall B");
        assertThat(updated.getName()).isEqualTo("Forged");
        assertThat(updated.getStatus()).isEqualTo(FulfilmentStatus.Destroyed);
        assertThat(updated.getQty()).isEqualTo(3);
        assertThat(updated.getCondition()).isEqualTo(ItemCondition.Sealed);
    }

    @Test
    void deliveredItemKeepsQuantityCostAndStatus() {
        // given
        WarehouseItem stored = item(FulfilmentStatus.Delivered);
        WarehouseItemEditForm form = WarehouseItemEditForm.of(stored);
        form.setName("Renamed");
        form.setCondition("OpenBox");
        form.setUnitSystemCost("650,5");
        form.setQty("99");
        form.setStatus("New");

        // when
        WarehouseItem updated = form.toUpdate(stored);

        // then
        assertThat(form.validate(FulfilmentStatus.Delivered)).isEmpty();
        assertThat(updated.getName()).isEqualTo("Renamed");
        assertThat(updated.getCondition()).isEqualTo(ItemCondition.OpenBox);
        assertThat(updated.getUnitSystemCost()).isCloseTo(650.5, within(0.001));
        assertThat(updated.getQty()).isEqualTo(3);
        assertThat(updated.getStatus()).isEqualTo(FulfilmentStatus.Delivered);
    }

    @Test
    void newItemTakesEveryFieldWithTaxInPercent() {
        // given
        WarehouseItem stored = item(FulfilmentStatus.New);
        WarehouseItemEditForm form = WarehouseItemEditForm.of(stored);
        form.setQty("5");
        form.setCost("1 049,90");
        form.setTax("8");
        form.setStatus("Allocation");
        form.setDeliveryId("d-2");

        // when
        WarehouseItem updated = form.toUpdate(stored);

        // then
        assertThat(form.validate(FulfilmentStatus.New)).isEmpty();
        assertThat(updated.getQty()).isEqualTo(5);
        assertThat(updated.getCost()).isCloseTo(1049.90, within(0.001));
        assertThat(updated.getTax()).isCloseTo(1.08, within(0.0001));
        assertThat(updated.getStatus()).isEqualTo(FulfilmentStatus.Allocation);
        assertThat(updated.getDeliveryId()).isEqualTo("d-2");
    }

    @Test
    void newItemRefusesAStatusOutsideNewAndAllocation() {
        // given
        WarehouseItemEditForm form = WarehouseItemEditForm.of(item(FulfilmentStatus.New));
        form.setStatus("Destroyed");

        // when / then
        assertThat(form.validate(FulfilmentStatus.New)).containsEntry("status", "warehouse.item.error.status");
    }

    @Test
    void invalidValuesNameTheirFields() {
        // given
        WarehouseItemEditForm form = WarehouseItemEditForm.of(item(FulfilmentStatus.New));
        form.setName(" ");
        form.setQty("0");
        form.setCost("abc");
        form.setTax("150");
        form.setEan("");
        form.setManufacturerCode("");
        form.setUnitSystemCost("-1");
        form.setComment("x".repeat(2001));
        form.setCondition("Broken");

        // when
        Map<String, String> errors = form.validate(FulfilmentStatus.New);

        // then
        assertThat(errors).containsOnlyKeys("name", "qty", "cost", "tax", "ean", "manufacturerCode", "unitSystemCost", "comment", "condition");
    }

    @Test
    void emptySystemCostClearsIt() {
        // given
        WarehouseItem stored = item(FulfilmentStatus.Delivered);
        stored.setUnitSystemCost(500);
        WarehouseItemEditForm form = WarehouseItemEditForm.of(stored);
        form.setUnitSystemCost(" ");

        // when
        WarehouseItem updated = form.toUpdate(stored);

        // then
        assertThat(updated.hasUnitSystemCost()).isFalse();
    }

    @Test
    void multiplierOfThePageBeforeTheRedesignBecomesPercent() {
        // when / then
        assertThat(WarehouseItemEditForm.percentFromMultiplier("1.23")).isEqualTo("23");
        assertThat(WarehouseItemEditForm.percentFromMultiplier("1.08")).isEqualTo("8");
        assertThat(WarehouseItemEditForm.percentFromMultiplier("1")).isEqualTo("0");
        assertThat(WarehouseItemEditForm.percentFromMultiplier("23")).isEqualTo("23");
        assertThat(WarehouseItemEditForm.percentFromMultiplier("abc")).isEqualTo("abc");
        assertThat(WarehouseItemEditForm.percentFromMultiplier(null)).isNull();
    }

    @Test
    void storedCommentOverTheLimitLeftAsItWasDoesNotBlockTheSave() {
        // given
        WarehouseItem stored = item(FulfilmentStatus.Delivered);
        stored.setComment(("A; B\n").repeat(500));
        WarehouseItemEditForm form = WarehouseItemEditForm.of(stored);
        // a textarea posts its line breaks as CRLF
        form.setComment(stored.getComment().replace("\n", "\r\n"));
        form.setSerialNo("S9");

        // when
        Map<String, String> errors = form.validate(FulfilmentStatus.Delivered, stored.getComment());

        // then
        assertThat(errors).isEmpty();
    }

    @Test
    void changedCommentOverTheLimitIsRefused() {
        // given
        WarehouseItem stored = item(FulfilmentStatus.Delivered);
        WarehouseItemEditForm form = WarehouseItemEditForm.of(stored);
        form.setComment("x".repeat(2001));

        // when / then
        assertThat(form.validate(FulfilmentStatus.Delivered, stored.getComment())).containsOnlyKeys("comment");
    }

    @Test
    void itemWithoutCategoryIsShownAsTheEmptyChoice() {
        // given
        WarehouseItem missing = item(FulfilmentStatus.Delivered);
        missing.setCategory(null);
        WarehouseItem empty = item(FulfilmentStatus.Delivered);
        empty.setCategory("");
        WarehouseItem uncategorized = item(FulfilmentStatus.Delivered);
        uncategorized.setCategory("Uncategorized");

        // when / then
        assertThat(WarehouseItemEditForm.of(missing).getCategory()).isEmpty();
        assertThat(WarehouseItemEditForm.of(empty).getCategory()).isEmpty();
        assertThat(WarehouseItemEditForm.of(uncategorized).getCategory()).isEmpty();
    }

    @Test
    void emptyCategoryChoiceRemovesTheCategory() {
        // given
        WarehouseItem stored = item(FulfilmentStatus.Delivered);
        WarehouseItemEditForm form = WarehouseItemEditForm.of(stored);
        form.setCategory("");

        // when
        WarehouseItem updated = form.toUpdate(stored);

        // then
        assertThat(updated.getCategory()).isEqualTo("Uncategorized");
    }

    @Test
    void itemWithoutCategoryKeepsItsStoredValueWhenTheChoiceStaysEmpty() {
        // given
        WarehouseItem stored = item(FulfilmentStatus.Delivered);
        stored.setCategory("");
        WarehouseItemEditForm form = WarehouseItemEditForm.of(stored);
        form.setComment("only the comment");

        // when
        WarehouseItem updated = form.toUpdate(stored);

        // then
        assertThat(updated.getCategory()).isEmpty();
    }

    @Test
    void destroyedItemNeedsAName() {
        // given
        WarehouseItemEditForm form = WarehouseItemEditForm.of(item(FulfilmentStatus.Destroyed));
        form.setName(" ");

        // when / then
        assertThat(form.validate(FulfilmentStatus.Destroyed)).containsOnlyKeys("name");
    }
}
