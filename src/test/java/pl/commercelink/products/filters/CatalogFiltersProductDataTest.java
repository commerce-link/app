package pl.commercelink.products.filters;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.products.InventoryDefinition;
import pl.commercelink.products.ProductData;
import pl.commercelink.starter.dynamodb.Metadata;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The catalog filters work on the product data of the resolver: the name and brand of the PIM entry when there is a
 * trusted one, of the taxonomy otherwise. The taxonomy record alone is not read.
 */
@ExtendWith(MockitoExtension.class)
class CatalogFiltersProductDataTest {

    private static final String EAN = "5900000000001";

    @Mock
    private MatchedInventory matchedInventory;

    @BeforeEach
    void productIsDescribedByThePim() {
        when(matchedInventory.getProductData()).thenReturn(new ProductData(
                "Gigabyte GeForce RTX 5070 Windforce OC (GV-N5070WF3OC-12GD)", "GIGABYTE", "Karty graficzne", "170",
                EAN, "GV-N5070WF3OC-12GD", null, null));
        lenient().when(matchedInventory.getEans()).thenReturn(List.of(EAN));
    }

    @Test
    void brandFilterTakesTheBrandOfTheProductData() {
        // when
        boolean listed = accepts(InventoryFilterType.BRAND_NAME, new Metadata("Brands", "Asus, Gigabyte"));
        boolean otherBrands = accepts(InventoryFilterType.BRAND_NAME, new Metadata("Brands", "Asus, MSI"));

        // then
        assertThat(listed).isTrue();
        assertThat(otherBrands).isFalse();
    }

    @Test
    void titleContainsFilterReadsTheNameOfTheProductData() {
        // when
        boolean manufacturerCode = accepts(InventoryFilterType.PRODUCT_TITLE_CONTAINS, new Metadata("Keywords", "gv-n5070wf3oc"));
        boolean absentWord = accepts(InventoryFilterType.PRODUCT_TITLE_CONTAINS, new Metadata("Keywords", "Radeon"));

        // then
        assertThat(manufacturerCode).isTrue();
        assertThat(absentWord).isFalse();
    }

    @Test
    void titleDoesNotContainFilterReadsTheNameOfTheProductData() {
        // when
        boolean presentWord = accepts(InventoryFilterType.PRODUCT_TITLE_DOES_NOT_CONTAIN, new Metadata("Keywords", "Windforce"));
        boolean absentWord = accepts(InventoryFilterType.PRODUCT_TITLE_DOES_NOT_CONTAIN, new Metadata("Keywords", "Aorus"));

        // then
        assertThat(presentWord).isFalse();
        assertThat(absentWord).isTrue();
    }

    @Test
    void productLineFilterFindsTheBrandAndTheLineInTheProductData() {
        // when
        boolean line = accepts(InventoryFilterType.PRODUCT_LINE_BY_BRAND, new Metadata("gigabyte", "Windforce, Eagle"));
        boolean otherLine = accepts(InventoryFilterType.PRODUCT_LINE_BY_BRAND, new Metadata("gigabyte", "Aorus"));
        boolean otherBrand = accepts(InventoryFilterType.PRODUCT_LINE_BY_BRAND, new Metadata("msi", "Windforce"));

        // then
        assertThat(line).isTrue();
        assertThat(otherLine).isFalse();
        assertThat(otherBrand).isFalse();
    }

    @Test
    void productLineExclusionFindsTheBrandAndTheLineInTheProductData() {
        // when
        boolean excludedLine = accepts(InventoryFilterType.PRODUCT_LINE_BY_BRAND_NOT_CONTAIN, new Metadata("gigabyte", "Windforce"));
        boolean otherLine = accepts(InventoryFilterType.PRODUCT_LINE_BY_BRAND_NOT_CONTAIN, new Metadata("gigabyte", "Aorus"));
        boolean otherBrand = accepts(InventoryFilterType.PRODUCT_LINE_BY_BRAND_NOT_CONTAIN, new Metadata("msi", "Windforce"));

        // then
        assertThat(excludedLine).isFalse();
        assertThat(otherLine).isTrue();
        assertThat(otherBrand).isTrue();
    }

    @Test
    void eanExclusionByBrandFindsTheBrandInTheProductData() {
        // when
        boolean excludedEan = accepts(InventoryFilterType.PRODUCT_EAN_BY_BRAND_NOT_EQ, new Metadata("gigabyte", EAN));
        boolean otherEan = accepts(InventoryFilterType.PRODUCT_EAN_BY_BRAND_NOT_EQ, new Metadata("gigabyte", "5900000000999"));
        boolean otherBrand = accepts(InventoryFilterType.PRODUCT_EAN_BY_BRAND_NOT_EQ, new Metadata("msi", EAN));

        // then
        assertThat(excludedEan).isFalse();
        assertThat(otherEan).isTrue();
        assertThat(otherBrand).isTrue();
    }

    private boolean accepts(InventoryFilterType type, Metadata metadata) {
        boolean accepted = new InventoryDefinition(type, List.of(metadata)).test(matchedInventory);
        verify(matchedInventory, never()).getTaxonomy();
        return accepted;
    }
}
