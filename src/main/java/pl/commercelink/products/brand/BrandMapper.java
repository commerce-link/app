package pl.commercelink.products.brand;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import pl.commercelink.pim.api.Brand;
import pl.commercelink.pim.api.PimCatalog;

import java.util.List;
import java.util.Optional;

@Component
@DependsOn("pimCatalogRegistry")
@RequiredArgsConstructor
public class BrandMapper {

    private final PimCatalog pimCatalog;
    private volatile List<Brand> brands = List.of();

    @PostConstruct
    public void load() {
        this.brands = List.copyOf(pimCatalog.allBrands());
    }

    public Optional<Brand> findBrand(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        for (Brand brand : brands) {
            if (brand.matches(trimmed)) {
                return Optional.of(brand);
            }
        }
        return Optional.empty();
    }

    public String unifyBrand(String input) {
        if (input == null) {
            return null;
        }
        return findBrand(input).map(Brand::name).orElse(input);
    }
}
