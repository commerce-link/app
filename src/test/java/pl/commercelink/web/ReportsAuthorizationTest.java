package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reports, their files and the stock ledger closing are a store admin's: the class-level rule covers every address
 * and no handler loosens it, and none takes a store from the request (the store is always the session's).
 * standaloneSetup MockMvc does not evaluate PreAuthorize, so the annotations are the contract pinned here.
 */
class ReportsAuthorizationTest {

    private static List<Method> handlers() {
        return Arrays.stream(FinancialReportsController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class) || method.isAnnotationPresent(PostMapping.class))
                .toList();
    }

    @Test
    void everyReportsAddressIsForTheStoreAdminOnly() {
        // when
        PreAuthorize rule = FinancialReportsController.class.getAnnotation(PreAuthorize.class);

        // then
        assertThat(rule.value()).isEqualTo("hasRole('ADMIN')");
        assertThat(handlers()).hasSizeGreaterThanOrEqualTo(10)
                .allSatisfy(handler -> assertThat(handler.isAnnotationPresent(PreAuthorize.class)).as(handler.getName()).isFalse());
    }

    @Test
    void noReportsAddressTakesTheStoreFromTheRequest() {
        // then
        assertThat(handlers()).allSatisfy(handler -> {
            GetMapping get = handler.getAnnotation(GetMapping.class);
            PostMapping post = handler.getAnnotation(PostMapping.class);
            String[] paths = get != null ? get.value() : post.value();
            assertThat(paths).as(handler.getName()).noneMatch(path -> path.contains("{storeId}"));
            assertThat(Arrays.stream(handler.getParameters()).map(parameter -> parameter.getName().toLowerCase()))
                    .as(handler.getName()).noneMatch(name -> name.contains("store"));
        });
    }
}
