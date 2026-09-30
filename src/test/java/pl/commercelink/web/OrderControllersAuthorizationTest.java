package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A super admin views a store's order read-only; every write of the orders screen must refuse the role, and the
 * store-scoped read addresses must be the super admin's only. standaloneSetup MockMvc does not evaluate PreAuthorize,
 * so the annotations of the orders controller are the contract pinned here.
 */
class OrderControllersAuthorizationTest {

    private static final Set<String> STORE_ROLES_ONLY = Set.of("!hasRole('SUPER_ADMIN')", "hasRole('ADMIN')");

    private static List<Method> handlers(Class<?> controller) {
        List<Method> handlers = new ArrayList<>();
        for (Method method : controller.getDeclaredMethods()) {
            if (method.isAnnotationPresent(GetMapping.class) || method.isAnnotationPresent(PostMapping.class)) {
                handlers.add(method);
            }
        }
        return handlers;
    }

    private static String pathOf(Method method) {
        GetMapping get = method.getAnnotation(GetMapping.class);
        PostMapping post = method.getAnnotation(PostMapping.class);
        String[] paths = get != null ? (get.value().length > 0 ? get.value() : get.path())
                : (post.value().length > 0 ? post.value() : post.path());
        return paths.length == 0 ? "" : paths[0];
    }

    private static Method handler(String name) {
        return Arrays.stream(OrdersController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(name) && method.isAnnotationPresent(PostMapping.class))
                .findFirst().orElseThrow();
    }

    @Test
    void everyOrdersEndpointStatesWhoMayCallIt() {
        for (Method handler : handlers(OrdersController.class)) {
            // given
            PreAuthorize rule = handler.getAnnotation(PreAuthorize.class);
            String path = pathOf(handler);

            // then
            assertThat(rule).as("OrdersController#" + handler.getName()).isNotNull();
            if (path.startsWith("/dashboard/store/{storeId}")) {
                assertThat(handler.isAnnotationPresent(PostMapping.class)).as(path + " is read-only").isFalse();
                assertThat(rule.value()).isEqualTo("hasRole('SUPER_ADMIN')");
            } else {
                assertThat(rule.value()).as(path).isIn(STORE_ROLES_ONLY);
            }
        }
    }

    @Test
    void everyPostOnAnOrderRefusesTheSuperAdmin() {
        for (Method handler : handlers(OrdersController.class)) {
            // given
            String path = pathOf(handler);
            if (!handler.isAnnotationPresent(PostMapping.class) || !path.startsWith("/dashboard/orders/{orderId}/")) {
                continue;
            }

            // then
            String expected = handler.getName().equals("removeDocument") ? "hasRole('ADMIN')" : "!hasRole('SUPER_ADMIN')";
            assertThat(handler.getAnnotation(PreAuthorize.class).value()).as(path).isEqualTo(expected);
        }
    }

    @Test
    void theRmaWarehouseMoveHasTheSameRuleAsItsNeighbours() {
        // when
        Method rma = handler("moveSelectedItemsToTheWarehouseForRMA");

        // then
        assertThat(rma.getAnnotation(PreAuthorize.class).value()).isEqualTo("!hasRole('SUPER_ADMIN')");
    }

    @Test
    void theInvoiceConfirmationPageIsForTheStoreUsersLikeTheIssuingItself() {
        // when
        Method page = Arrays.stream(OrdersController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("confirmInvoice")).findFirst().orElseThrow();

        // then
        assertThat(pathOf(page)).isEqualTo("/dashboard/orders/{orderId}/invoicing");
        assertThat(page.isAnnotationPresent(GetMapping.class)).isTrue();
        assertThat(page.getAnnotation(PreAuthorize.class).value()).isEqualTo("!hasRole('SUPER_ADMIN')");
        assertThat(handler("createInvoice").getAnnotation(PreAuthorize.class).value()).isEqualTo("!hasRole('SUPER_ADMIN')");
    }

    @Test
    void unpinningADocumentIsForTheAdministrator() {
        // when
        Method remove = handler("removeDocument");

        // then
        assertThat(remove.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");
    }

    @Test
    void theSuperAdminIsDeniedTheAsyncAddressSaveWhichAStoreAdminMayCall() throws Exception {
        // given: the handler's own @PreAuthorize evaluated as method security does; a denial becomes a 403
        Method save = handler("updateAddressDetails");
        Object[] asyncPost = {"order-1", "billing", new pl.commercelink.orders.Order("store-1"), "fetch", null, null, null,
                null, java.util.Locale.ENGLISH};
        org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager manager =
                new org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager();
        org.springframework.security.util.SimpleMethodInvocation invocation =
                new org.springframework.security.util.SimpleMethodInvocation(org.mockito.Mockito.mock(OrdersController.class), save, asyncPost);
        org.springframework.security.core.Authentication superAdmin = new org.springframework.security.authentication.TestingAuthenticationToken(
                "super", "x", "ROLE_SUPER_ADMIN");
        org.springframework.security.core.Authentication admin = new org.springframework.security.authentication.TestingAuthenticationToken(
                "admin", "x", "ROLE_ADMIN");

        // when
        boolean superAdminGranted = manager.authorize(() -> superAdmin, invocation).isGranted();
        boolean adminGranted = manager.authorize(() -> admin, invocation).isGranted();

        // then
        assertThat(superAdminGranted).isFalse();
        assertThat(adminGranted).isTrue();
    }
}
