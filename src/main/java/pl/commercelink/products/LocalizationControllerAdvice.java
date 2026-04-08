package pl.commercelink.products;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
class LocalizationControllerAdvice {

    @ModelAttribute("pcl")
    public ProductCategoryLocalization pcl() {
        return ProductCategoryLocalization.INSTANCE;
    }

    @ModelAttribute("pgl")
    public ProductGroupLocalization pgl() {
        return ProductGroupLocalization.INSTANCE;
    }
}
