package pl.commercelink.products.brand;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.pim.api.Brand;
import pl.commercelink.pim.api.PimCatalog;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Component
@RequiredArgsConstructor
public class BrandMapper {

    private final PimCatalog pimCatalog;
    private volatile BrandIndex brandIndex = BrandIndex.of(List.of());

    public String unifyBrand(String input) {
        if (input == null) {
            return null;
        }
        return findBrand(input).map(Brand::name).orElse(input);
    }

    public boolean compatible(String brandA, String brandB) {
        if (brandA == null || brandA.isBlank() || brandB == null || brandB.isBlank()) {
            return false;
        }
        return canonicalName(brandA).equalsIgnoreCase(canonicalName(brandB));
    }

    private String canonicalName(String brand) {
        return findBrand(brand).map(Brand::name).orElse(brand.trim());
    }

    private Optional<Brand> findBrand(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(currentIndex().byName().get(input.trim()));
    }

    private BrandIndex currentIndex() {
        List<Brand> brands = pimCatalog.allBrands();
        if (brands == null) {
            brands = List.of();
        }
        BrandIndex current = brandIndex;
        if (current.source() != brands) {
            current = BrandIndex.of(brands);
            brandIndex = current;
        }
        return current;
    }

    private record BrandIndex(List<Brand> source, Map<String, Brand> byName) {

        static BrandIndex of(List<Brand> brands) {
            Map<String, Brand> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (Brand brand : brands) {
                if (brand.name() != null) {
                    byName.putIfAbsent(brand.name(), brand);
                }
                for (String alias : brand.aliases()) {
                    byName.putIfAbsent(alias, brand);
                }
            }
            return new BrandIndex(brands, byName);
        }
    }
}
