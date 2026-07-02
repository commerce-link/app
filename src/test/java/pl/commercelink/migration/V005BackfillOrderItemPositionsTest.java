package pl.commercelink.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.taxonomy.ProductCategory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class V005BackfillOrderItemPositionsTest {

    private static final String ORDER_ID = "order-1";

    @Mock
    private OrderItemsRepository orderItemsRepository;

    @InjectMocks
    private V005_BackfillOrderItemPositions migration;

    @Test
    @DisplayName("assigns order item positions following the category ordinal display order within each order")
    void assignsOrderItemPositionsFollowingCategoryOrdinalDisplayOrder() {
        // given
        OrderItem laptopItem = orderItem(ORDER_ID, ProductCategory.Laptops.name());
        OrderItem cpuItem = orderItem(ORDER_ID, ProductCategory.CPU.name());
        OrderItem serviceItem = orderItem(ORDER_ID, ProductCategory.Services.name());
        when(orderItemsRepository.findAll()).thenReturn(List.of(laptopItem, cpuItem, serviceItem));

        // when
        migration.backfillPositions();

        // then
        assertThat(cpuItem.getPosition()).isEqualTo(0);
        assertThat(serviceItem.getPosition()).isEqualTo(1);
        assertThat(laptopItem.getPosition()).isEqualTo(2);
        verify(orderItemsRepository).save(cpuItem);
        verify(orderItemsRepository).save(serviceItem);
        verify(orderItemsRepository).save(laptopItem);
    }

    @Test
    @DisplayName("positions items independently per order")
    void positionsItemsIndependentlyPerOrder() {
        // given
        OrderItem firstOrderItem = orderItem("order-1", ProductCategory.Laptops.name());
        OrderItem secondOrderItem = orderItem("order-2", ProductCategory.CPU.name());
        when(orderItemsRepository.findAll()).thenReturn(List.of(firstOrderItem, secondOrderItem));

        // when
        migration.backfillPositions();

        // then
        assertThat(firstOrderItem.getPosition()).isEqualTo(0);
        assertThat(secondOrderItem.getPosition()).isEqualTo(0);
    }

    @Test
    @DisplayName("treats missing and unknown categories as last without failing")
    void treatsMissingAndUnknownCategoriesAsLastWithoutFailing() {
        // given
        OrderItem cpuItem = orderItem(ORDER_ID, ProductCategory.CPU.name());
        OrderItem missingCategoryItem = orderItem(ORDER_ID, null);
        OrderItem unknownCategoryItem = orderItem(ORDER_ID, "NotACategory");
        when(orderItemsRepository.findAll()).thenReturn(List.of(missingCategoryItem, cpuItem, unknownCategoryItem));

        // when
        migration.backfillPositions();

        // then
        assertThat(cpuItem.getPosition()).isEqualTo(0);
        assertThat(missingCategoryItem.getPosition()).isEqualTo(1);
        assertThat(unknownCategoryItem.getPosition()).isEqualTo(2);
    }

    @Test
    @DisplayName("does not overwrite order items that already have a position")
    void doesNotOverwriteOrderItemsThatAlreadyHavePosition() {
        // given
        OrderItem positionedItem = orderItem(ORDER_ID, ProductCategory.CPU.name());
        positionedItem.setPosition(7);
        OrderItem missingItem = orderItem(ORDER_ID, ProductCategory.CPU.name());
        when(orderItemsRepository.findAll()).thenReturn(List.of(positionedItem, missingItem));

        // when
        migration.backfillPositions();

        // then
        assertThat(positionedItem.getPosition()).isEqualTo(7);
        assertThat(missingItem.getPosition()).isEqualTo(1);
        verify(orderItemsRepository, never()).save(positionedItem);
        verify(orderItemsRepository).save(missingItem);
    }

    private OrderItem orderItem(String orderId, String categoryKey) {
        return new OrderItem(orderId, categoryKey, "Product", 1, 100.0, "SKU", false);
    }
}
