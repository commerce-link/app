package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.invoicing.InvoicePositionName;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.OrderItem;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SWEEP surface B — Services-category special-casing (the dominant category branch in the domain).
 *
 * {@code ProductCategory.Services} is the single category that toggles "is this a physical product
 * to fulfil from inventory" vs "an internal/intangible position". This surface freezes the predicates
 * and partitioning that pivot on it, none of which is covered by surfaces 1-13.
 *
 * Sites:
 *  - BasketItem.isProduct()  == (category != Services)
 *  - BasketItem.isService()  == (category == Services)
 *  - BasketItem.hasCategory(c)/Item.hasCategory(c) == (this.category == c)  [reference equality]
 *  - Item.hasGroup(ProductGroup) == (category.getProductGroup() == group)
 *  - Item.isAllocated()/isOrdered(): Services-group + Delivered short-circuits to true WITHOUT
 *    requiring allocation details (the non-services path requires ean+mfn+deliveryId).
 *  - OrderItem.canBeFulfilledInternally() == (Services && isWarehouseFulfilled()).
 *  - Basket.getBasketItemsForProducts()/ForServices(): partition by hasCategory(Services).
 *  - InvoicePositionName.fromOrderItems(): drops Services positions from the invoice text.
 */
@ExtendWith(MockitoExtension.class)
class GoldenSweepServicesBranchingTest {

    private static BasketItem basketItem(ProductCategory category, String mfn) {
        return new BasketItem("id", "name", mfn, category, 10.0, 0, 1, null, 1, false);
    }

    private static OrderItem orderItem(ProductCategory category) {
        return new OrderItem(null, category, "n", 1, 0, null, false);
    }

    @Test
    void basketItemIsProductForEveryCategoryExceptServices() {
        // given / then
        for (ProductCategory category : ProductCategory.values()) {
            BasketItem item = basketItem(category, "M");
            boolean isServices = category == ProductCategory.Services;
            assertThat(item.isService()).isEqualTo(isServices);
            // isProduct is the strict complement of isService (no third state).
            assertThat(item.isProduct()).isEqualTo(!isServices);
        }
        // Other is a PRODUCT, not a service (Other lives in PcComponents group, not Services).
        assertThat(basketItem(ProductCategory.Other, "M").isProduct()).isTrue();
        assertThat(basketItem(ProductCategory.Services, "M").isService()).isTrue();
    }

    @Test
    void basketCategoryKeyHoldsTheSelectedCategoryName() {
        // given
        BasketItem cpu = basketItem(ProductCategory.CPU, "M");

        // then
        // the basket carries the decoupled string key (no enum); a null category yields a null key.
        assertThat(cpu.getCategoryKey()).isEqualTo("CPU");
        assertThat(cpu.getCategoryKey()).isNotEqualTo("Services");
        assertThat(basketItem(null, "M").getCategoryKey()).isNull();
    }

    @Test
    void orderItemDistinguishesOnlyServiceFromProduct() {
        // given
        OrderItem services = orderItem(ProductCategory.Services);
        OrderItem cpu = orderItem(ProductCategory.CPU);

        // then
        // order items no longer carry the fine-grained group, only the product/service flag.
        assertThat(services.isService()).isTrue();
        assertThat(cpu.isService()).isFalse();
        assertThat(cpu.isProduct()).isTrue();
        // Other lives in a product group, so it is a product (not a service).
        assertThat(orderItem(ProductCategory.Other).isProduct()).isTrue();
    }

    @Test
    void servicesDeliveredItemIsAllocatedAndOrderedWithoutAllocationDetails() {
        // given
        // a Services item with status Delivered and NO ean/mfn/deliveryId.
        OrderItem service = orderItem(ProductCategory.Services);
        service.setStatus(FulfilmentStatus.Delivered);

        // then
        // Services-group + Delivered short-circuits both predicates to true despite missing allocation details.
        assertThat(service.isAllocated()).isTrue();
        assertThat(service.isOrdered()).isTrue();
        assertThat(service.hasAllocationDetails()).isFalse();
    }

    @Test
    void nonServicesDeliveredItemNeedsAllocationDetailsToBeAllocated() {
        // given
        // a physical (CPU) item that is Delivered but has NO ean/mfn/deliveryId.
        OrderItem product = orderItem(ProductCategory.CPU);
        product.setStatus(FulfilmentStatus.Delivered);

        // then
        // no Services short-circuit => requires allocation details, which are absent => false.
        assertThat(product.hasAllocationDetails()).isFalse();
        assertThat(product.isAllocated()).isFalse();
        assertThat(product.isOrdered()).isFalse();
    }

    @Test
    void canBeFulfilledInternallyRequiresServicesAndWarehouseDelivery() {
        // given
        OrderItem warehouseService = orderItem(ProductCategory.Services);
        warehouseService.setDeliveryId(OrderItem.GENERIC_WAREHOUSE_ORDER_NO);
        OrderItem nonWarehouseService = orderItem(ProductCategory.Services);
        nonWarehouseService.setDeliveryId("Action");
        OrderItem warehouseProduct = orderItem(ProductCategory.CPU);
        warehouseProduct.setDeliveryId(OrderItem.GENERIC_WAREHOUSE_ORDER_NO);

        // then
        assertThat(warehouseService.canBeFulfilledInternally()).isTrue();
        // both conjuncts are required: Services but not warehouse-fulfilled -> false.
        assertThat(nonWarehouseService.canBeFulfilledInternally()).isFalse();
        // warehouse-fulfilled but not Services -> false.
        assertThat(warehouseProduct.canBeFulfilledInternally()).isFalse();
    }

    @Test
    void basketPartitionsItemsByServicesCategory() {
        // given
        Basket basket = new Basket();
        basket.setBasketItems(List.of(
                basketItem(ProductCategory.CPU, "CPU1"),
                basketItem(ProductCategory.Services, "SVC1"),
                basketItem(ProductCategory.Other, "OTH1"),
                basketItem(ProductCategory.Services, "SVC2")
        ));

        // when
        List<String> products = basket.getBasketItemsForProducts().stream()
                .map(BasketItem::getCategoryKey).collect(Collectors.toList());
        List<String> services = basket.getBasketItemsForServices().stream()
                .map(BasketItem::getCategoryKey).collect(Collectors.toList());

        // then
        // products = everything that is NOT Services (Other counts as a product); services = only Services.
        assertThat(products).containsExactly("CPU", "Other");
        assertThat(services).containsExactly("Services", "Services");
    }

    @Test
    void invoicePositionNameDropsServicesPositions() {
        // given
        List<OrderItem> orderItems = List.of(
                new OrderItem(null, ProductCategory.CPU, "Procesor", 2, 0, null, false),
                new OrderItem(null, ProductCategory.Services, "Dostawa", 1, 0, null, false),
                new OrderItem(null, ProductCategory.Laptops, "Laptop", 1, 0, null, false)
        );

        // when
        String position = InvoicePositionName.fromOrderItems("Zamowienie", orderItems);

        // then
        // Services ("Dostawa") is filtered out; qty>1 gets the "2x " prefix, qty==1 stays bare.
        assertThat(position).isEqualTo("Zamowienie 2x Procesor, Laptop");
        assertThat(position).doesNotContain("Dostawa");
    }
}
