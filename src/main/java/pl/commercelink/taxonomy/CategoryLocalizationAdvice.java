package pl.commercelink.taxonomy;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
class CategoryLocalizationAdvice {

    private final CategoryLocalizer categoryLocalizer;

    @ModelAttribute("categoryI18n")
    CategoryLocalizer categoryI18n() {
        return categoryLocalizer;
    }
}
