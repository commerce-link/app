package pl.commercelink.orders;

import org.junit.jupiter.api.Test;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.taxonomy.ProductGroup;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryKeyHelpersTest {

    @Test
    void hasCategoryKeyMatchesEnumCategoryCheck() {
        // given
        OrderItem service = orderItemWithCategory(ProductCategory.Services);
        OrderItem laptop = orderItemWithCategory(ProductCategory.Laptops);

        // when / then
        assertThat(service.hasCategoryKey("Services"))
                .isEqualTo(service.getCategory() == ProductCategory.Services)
                .isTrue();
        assertThat(laptop.hasCategoryKey("Services"))
                .isEqualTo(laptop.getCategory() == ProductCategory.Services)
                .isFalse();
    }

    @Test
    void hasGroupKeyMatchesEnumGroupCheck() {
        // given
        OrderItem service = orderItemWithCategory(ProductCategory.Services);
        OrderItem laptop = orderItemWithCategory(ProductCategory.Laptops);

        // when / then
        assertThat(service.hasGroupKey("Services"))
                .isEqualTo(service.getCategory().getProductGroup() == ProductGroup.Services)
                .isTrue();
        assertThat(laptop.hasGroupKey("Services"))
                .isEqualTo(laptop.getCategory().getProductGroup() == ProductGroup.Services)
                .isFalse();
    }

    @Test
    void sequenceNumberMatchesEnumOrdinal() {
        // given
        OrderItem laptop = orderItemWithCategory(ProductCategory.Laptops);

        // when
        int sequence = laptop.getSequenceNumber();

        // then
        assertThat(sequence).isEqualTo(ProductCategory.Laptops.ordinal());
    }

    @Test
    void categoryKeyAndGroupKeyAreNotConflated() {
        // given
        OrderItem laptop = orderItemWithCategory(ProductCategory.Laptops);
        String groupKey = ProductCategory.Laptops.getProductGroup().name();

        // when / then
        assertThat(laptop.hasGroupKey(groupKey)).isTrue();
        assertThat(laptop.hasCategoryKey(groupKey)).isFalse();
        assertThat(laptop.hasCategoryKey("Laptops")).isTrue();
        assertThat(laptop.hasGroupKey("Laptops")).isFalse();
    }

    @Test
    void basketItemHelpersMatchEnumChecks() {
        // given
        BasketItem service = basketItemWithCategory(ProductCategory.Services);
        BasketItem laptop = basketItemWithCategory(ProductCategory.Laptops);

        // when / then
        assertThat(service.hasCategoryKey("Services")).isTrue();
        assertThat(service.isService()).isTrue();
        assertThat(service.isProduct()).isFalse();
        assertThat(laptop.isProduct()).isTrue();
        assertThat(laptop.getSequenceNumber()).isEqualTo(ProductCategory.Laptops.ordinal());
    }

    @Test
    void basketItemWithNullCategoryIsProductWithoutException() {
        // given
        BasketItem item = new BasketItem();

        // when / then
        assertThat(item.getCategoryKey()).isNull();
        assertThat(item.isProduct()).isTrue();
        assertThat(item.isService()).isFalse();
        assertThat(item.hasCategoryKey("Services")).isFalse();
    }

    @Test
    void isServiceMatchesEnumCategoryCheck() {
        // given
        OrderItem service = orderItemWithCategory(ProductCategory.Services);
        OrderItem laptop = orderItemWithCategory(ProductCategory.Laptops);

        // when / then
        assertThat(service.isService())
                .isEqualTo(service.getCategory() == ProductCategory.Services)
                .isTrue();
        assertThat(laptop.isService())
                .isEqualTo(laptop.getCategory() == ProductCategory.Services)
                .isFalse();
    }

    @Test
    void isProductIsExactInverseOfIsServiceCategory() {
        // given
        OrderItem service = orderItemWithCategory(ProductCategory.Services);
        OrderItem laptop = orderItemWithCategory(ProductCategory.Laptops);

        // when / then
        assertThat(service.isProduct())
                .isEqualTo(service.getCategory() != ProductCategory.Services)
                .isFalse();
        assertThat(laptop.isProduct())
                .isEqualTo(laptop.getCategory() != ProductCategory.Services)
                .isTrue();
    }

    @Test
    void isServiceGroupMatchesEnumGroupCheck() {
        // given
        OrderItem service = orderItemWithCategory(ProductCategory.Services);
        OrderItem laptop = orderItemWithCategory(ProductCategory.Laptops);

        // when / then
        assertThat(service.isServiceGroup())
                .isEqualTo(service.getCategory().getProductGroup() == ProductGroup.Services)
                .isTrue();
        assertThat(laptop.isServiceGroup())
                .isEqualTo(laptop.getCategory().getProductGroup() == ProductGroup.Services)
                .isFalse();
    }

    @Test
    void serviceCategoryAndServiceGroupStayDistinctPredicates() {
        // given
        OrderItem laptop = orderItemWithCategory(ProductCategory.Laptops);
        OrderItem service = orderItemWithCategory(ProductCategory.Services);

        // when / then
        assertThat(laptop.isService()).isFalse();
        assertThat(laptop.isServiceGroup()).isFalse();
        assertThat(service.isService()).isTrue();
        assertThat(service.isServiceGroup()).isTrue();
    }

    @Test
    void basketItemServicePredicatesMatchEnumChecks() {
        // given
        BasketItem service = basketItemWithCategory(ProductCategory.Services);
        BasketItem laptop = basketItemWithCategory(ProductCategory.Laptops);

        // when / then
        assertThat(service.isService())
                .isEqualTo(service.getCategory() == ProductCategory.Services)
                .isTrue();
        assertThat(service.isProduct()).isFalse();
        assertThat(laptop.isService()).isFalse();
        assertThat(laptop.isProduct())
                .isEqualTo(laptop.getCategory() != ProductCategory.Services)
                .isTrue();
    }

    private OrderItem orderItemWithCategory(ProductCategory category) {
        OrderItem item = new OrderItem();
        item.setCategoryKey(category.name());
        return item;
    }

    private BasketItem basketItemWithCategory(ProductCategory category) {
        BasketItem item = new BasketItem();
        item.setCategoryKey(category.name());
        return item;
    }
}
