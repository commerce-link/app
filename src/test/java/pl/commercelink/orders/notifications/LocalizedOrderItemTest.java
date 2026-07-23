package pl.commercelink.orders.notifications;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.taxonomy.CategoryLocalizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalizedOrderItemTest {

    @Mock
    private CategoryLocalizer categoryLocalizer;

    @Test
    void keepsTheCategoryDefinitionNameVerbatimEvenWhenItCollidesWithAPimCategoryLeaf() {
        // given
        OrderItem orderItem = new OrderItem("order-1", "Karty graficzne", "RTX 5090", 1, 9000.0, "SKU-1", false);
        when(categoryLocalizer.localize("Karty graficzne", "singular")).thenReturn("Karty graficzne");

        // when
        LocalizedOrderItem localized = LocalizedOrderItem.fromOrderItem(orderItem, categoryLocalizer);

        // then
        assertThat(localized.getCategory()).isEqualTo("Karty graficzne");
    }

    @Test
    void localizesCategoryByKeyEvenWhenOutsideEnum() {
        // given
        OrderItem orderItem = new OrderItem("order-1", "Smartwatches", "Watch 5", 2, 900.0, "SKU-1", false);
        when(categoryLocalizer.localize("Smartwatches", "singular")).thenReturn("Smartwatche");

        // when
        LocalizedOrderItem localized = LocalizedOrderItem.fromOrderItem(orderItem, categoryLocalizer);

        // then
        assertThat(localized.getCategory()).isEqualTo("Smartwatche");
        assertThat(localized.getName()).isEqualTo("Watch 5");
        assertThat(localized.getQuantity()).isEqualTo(2);
        assertThat(localized.getPrice()).isEqualTo(900.0);
    }
}
