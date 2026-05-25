package pl.commercelink.products.information;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.products.CategoryDefinitionType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PimQueuePriorityCalculatorTest {

    @BeforeEach
    void setUp() {
        PimCatalog stub = mock(PimCatalog.class);
        when(stub.brandStrength("Apple")).thenReturn(2);
        when(stub.brandStrength("Phanteks")).thenReturn(1);
        when(stub.brandStrength("RandomBrand")).thenReturn(1);
        BrandFacade.initialize(stub);
    }

    @AfterEach
    void tearDown() {
        BrandFacade.initialize(null);
    }

    @Test
    void priorityMultipliedByPremiumBrandStrength() {
        MatchedInventory inventory = mock(MatchedInventory.class);
        when(inventory.isEmpty()).thenReturn(false);
        when(inventory.getSuppliers()).thenReturn(List.of("S1", "S2", "S3", "S4", "S5"));
        when(inventory.getTotalAvailableQty()).thenReturn(10L);

        int priority = PimQueuePriorityCalculator.calculatePriority(
                "Apple", CategoryDefinitionType.Dynamic, inventory);

        assertThat(priority).isEqualTo(70);
    }

    @Test
    void priorityUnchangedForStandardBrand() {
        MatchedInventory inventory = mock(MatchedInventory.class);
        when(inventory.isEmpty()).thenReturn(false);
        when(inventory.getSuppliers()).thenReturn(List.of("S1", "S2", "S3", "S4", "S5"));
        when(inventory.getTotalAvailableQty()).thenReturn(10L);

        int priority = PimQueuePriorityCalculator.calculatePriority(
                "Phanteks", CategoryDefinitionType.Dynamic, inventory);

        assertThat(priority).isEqualTo(35);
    }

    @Test
    void priorityCappedAtMaxValue() {
        MatchedInventory inventory = mock(MatchedInventory.class);
        when(inventory.isEmpty()).thenReturn(false);
        when(inventory.getSuppliers()).thenReturn(
                List.of("S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8", "S9", "S10"));
        when(inventory.getTotalAvailableQty()).thenReturn(20L);

        int priority = PimQueuePriorityCalculator.calculatePriority(
                "Apple", CategoryDefinitionType.Dynamic, inventory);

        assertThat(priority).isEqualTo(100);
    }

    @Test
    void unknownBrandUsesDefaultStrength() {
        MatchedInventory inventory = mock(MatchedInventory.class);
        when(inventory.isEmpty()).thenReturn(false);
        when(inventory.getSuppliers()).thenReturn(List.of("S1", "S2", "S3"));
        when(inventory.getTotalAvailableQty()).thenReturn(5L);

        int priority = PimQueuePriorityCalculator.calculatePriority(
                "RandomBrand", CategoryDefinitionType.Dynamic, inventory);

        assertThat(priority).isEqualTo(20);
    }
}
