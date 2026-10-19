package pl.commercelink.web.deliveries.create;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The create flow spends money, and the controller tests call its handlers directly, so nothing else would notice a
 * handler without a rule: store routes are the store admin's, /dashboard/store/{storeId}/... the super admin's.
 */
class DeliveryCreateAuthorizationTest {

    private static List<Method> handlers() {
        return Arrays.stream(DeliveryCreateController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class) || method.isAnnotationPresent(PostMapping.class))
                .toList();
    }

    private static String pathOf(Method method) {
        GetMapping get = method.getAnnotation(GetMapping.class);
        PostMapping post = method.getAnnotation(PostMapping.class);
        String[] paths = get != null ? (get.value().length > 0 ? get.value() : get.path())
                : (post.value().length > 0 ? post.value() : post.path());
        return paths[0];
    }

    @Test
    void everyCreateRouteStatesItsRoleByTheAddressItServes() {
        // given
        List<Method> handlers = handlers();

        // then
        assertThat(handlers).hasSize(20);
        for (Method handler : handlers) {
            String path = pathOf(handler);
            PreAuthorize rule = handler.getAnnotation(PreAuthorize.class);
            assertThat(rule).as(handler.getName()).isNotNull();
            assertThat(rule.value()).as(path).isEqualTo(path.startsWith("/dashboard/store/{storeId}/")
                    ? "hasRole('SUPER_ADMIN')" : "hasRole('ADMIN')");
        }
    }
}
