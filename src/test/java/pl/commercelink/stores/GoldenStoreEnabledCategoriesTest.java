package pl.commercelink.stores;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.taxonomy.ProductGroup;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class GoldenStoreEnabledCategoriesTest {

    @Test
    void returnsCategoriesOfEnabledGroupsInEnumDeclarationOrder() {
        // given
        FulfilmentConfiguration configuration = new FulfilmentConfiguration();
        configuration.setEnabledProductGroups(List.of(ProductGroup.Peripherals, ProductGroup.Furniture));
        Store store = new Store();
        store.setFulfilmentConfiguration(configuration);

        // when
        List<ProductCategory> categories = store.getEnabledProductCategories();

        // then
        assertThat(categories).containsExactly(
                ProductCategory.Displays,
                ProductCategory.Keyboards,
                ProductCategory.Mice,
                ProductCategory.KeyboardsAndMice,
                ProductCategory.Headphones,
                ProductCategory.Microphones,
                ProductCategory.Webcams,
                ProductCategory.Speakers,
                ProductCategory.MousePads,
                ProductCategory.GamingChairs,
                ProductCategory.OfficeChairs,
                ProductCategory.GamingDesks,
                ProductCategory.OfficeDesks,
                ProductCategory.StandingDesks,
                ProductCategory.MonitorMounts,
                ProductCategory.Footrests);
    }

    @Test
    void returnsEmptyListWhenEnabledProductGroupsIsEmpty() {
        // given
        FulfilmentConfiguration configuration = new FulfilmentConfiguration();
        configuration.setEnabledProductGroups(Collections.emptyList());
        Store store = new Store();
        store.setFulfilmentConfiguration(configuration);

        // when
        List<ProductCategory> categories = store.getEnabledProductCategories();

        // then
        assertThat(categories).isEmpty();
    }

    @Test
    void returnsEmptyListWhenFulfilmentConfigurationIsNull() {
        // given
        Store store = new Store();

        // when
        List<ProductCategory> categories = store.getEnabledProductCategories();

        // then
        assertThat(categories).isEmpty();
    }
}
