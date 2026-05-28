package pl.commercelink.products.brand;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.pim.api.Brand;
import pl.commercelink.pim.api.PimCatalog;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class BrandMapper {

    private final PimCatalog pimCatalog;

    public Optional<Brand> findBrand(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String trimmed = input.trim();
        return pimCatalog.allBrands().stream()
                .filter(brand -> brand.matches(trimmed))
                .findFirst();
    }

    public String unifyBrand(String input) {
        if (input == null) {
            return null;
        }
        return findBrand(input).map(Brand::name).orElse(input);
    }
}
