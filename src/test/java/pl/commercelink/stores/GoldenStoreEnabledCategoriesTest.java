package pl.commercelink.stores;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GOLDEN characterization of {@link Store#getEnabledProductCategories()}. Phase B retyped both the
 * enabled-groups storage and this method from the {@code ProductGroup}/{@code ProductCategory} enums
 * to string keys; this freezes that the SAME category keys are returned, in the SAME enum-declaration
 * order, for the enabled group keys.
 */
@ExtendWith(MockitoExtension.class)
class GoldenStoreEnabledCategoriesTest {

    @Test
    void returnsCategoryKeysOfEnabledGroupsInEnumDeclarationOrder() {
        // given
        FulfilmentConfiguration configuration = new FulfilmentConfiguration();
        configuration.setEnabledProductGroups(List.of("Peripherals", "Furniture"));
        Store store = new Store();
        store.setFulfilmentConfiguration(configuration);

        // when
        List<String> categories = store.getEnabledProductCategories();

        // then
        assertThat(categories).containsExactly(
                "Displays",
                "Keyboards",
                "Mice",
                "KeyboardsAndMice",
                "Headphones",
                "Microphones",
                "Webcams",
                "Speakers",
                "MousePads",
                "GamingChairs",
                "OfficeChairs",
                "GamingDesks",
                "OfficeDesks",
                "StandingDesks",
                "MonitorMounts",
                "Footrests");
    }

    @Test
    void returnsEmptyListWhenEnabledProductGroupsIsEmpty() {
        // given
        FulfilmentConfiguration configuration = new FulfilmentConfiguration();
        configuration.setEnabledProductGroups(Collections.emptyList());
        Store store = new Store();
        store.setFulfilmentConfiguration(configuration);

        // when
        List<String> categories = store.getEnabledProductCategories();

        // then
        assertThat(categories).isEmpty();
    }

    @Test
    void returnsEmptyListWhenFulfilmentConfigurationIsNull() {
        // given
        Store store = new Store();

        // when
        List<String> categories = store.getEnabledProductCategories();

        // then
        assertThat(categories).isEmpty();
    }
}
