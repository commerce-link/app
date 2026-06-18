package pl.commercelink.products;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
class LocalizationControllerAdvice {

    private final ProductCategoryLocalization productCategoryLocalization;
    private final ProductGroupLocalization productGroupLocalization;

    @ModelAttribute("pcl")
    ProductCategoryLocalization pcl() {
        return productCategoryLocalization;
    }

    @ModelAttribute("pgl")
    ProductGroupLocalization pgl() {
        return productGroupLocalization;
    }
}
