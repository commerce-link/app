package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import pl.commercelink.starter.security.CustomSecurityContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

/** A scanned order card opens the order for whoever scans it: the store's own page, or the super admin's store-scoped one. */
class OrderScanControllerTest {

    private static final String STORE_ID = "uma2dqukxr";
    private static final String ORDER_ID = "3e373abc-1111-2222-3333-444455556666";

    private final OrderScanController controller = new OrderScanController();
    private MockedStatic<CustomSecurityContext> securityStub;

    @BeforeEach
    void setUp() {
        securityStub = mockStatic(CustomSecurityContext.class);
    }

    @AfterEach
    void tearDown() {
        securityStub.close();
    }

    @Test
    void aUserOfTheStoreOpensTheOrderPage() {
        // given
        signedIn(STORE_ID, false);

        // when
        String view = controller.openScannedOrder(STORE_ID, ORDER_ID);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + ORDER_ID);
    }

    @Test
    void aSuperAdminOpensTheStoreScopedOrderPage() {
        // given
        signedIn(null, true);

        // when
        String view = controller.openScannedOrder(STORE_ID, ORDER_ID);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/store/" + STORE_ID + "/orders/" + ORDER_ID);
    }

    @Test
    void aUserOfAnotherStoreGetsNotFound() {
        // given
        signedIn("other-store", false);

        // then: the same answer as an order that does not exist, so the code tells nothing about other stores
        assertThatThrownBy(() -> controller.openScannedOrder(STORE_ID, ORDER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsIdentifiersOutsideTheSafeAlphabet() {
        // given
        signedIn(STORE_ID, false);

        // then: nothing but letters, digits, '-' and '_' reaches a redirect
        for (String orderId : new String[]{"{orderId}", "..", "o1\r\nSet-Cookie: x=1", "a".repeat(65), ""}) {
            assertThatThrownBy(() -> controller.openScannedOrder(STORE_ID, orderId))
                    .isInstanceOf(ResponseStatusException.class);
        }
        assertThatThrownBy(() -> controller.openScannedOrder("../x", ORDER_ID)).isInstanceOf(ResponseStatusException.class);
    }

    private void signedIn(String storeId, boolean superAdmin) {
        securityStub.when(CustomSecurityContext::getStoreId).thenReturn(storeId);
        securityStub.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(superAdmin);
    }
}
