package pl.commercelink.web.nav;

import org.junit.jupiter.api.Test;
import pl.commercelink.starter.security.UserRole;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NavSectionTest {

    private static final NavItem FOR_ADMIN =
            new NavItem("payments", "nav.payments", "/dashboard/payments", "fa-credit-card", Set.of(UserRole.ADMIN));
    private static final NavItem FOR_EVERYONE =
            new NavItem("orders", "nav.orders", "/dashboard/orders", "fa-shopping-cart",
                    Set.of(UserRole.USER, UserRole.ADMIN));

    @Test
    void keepsOnlyTheItemsTheRoleMaySee() {
        // given
        NavSection section = new NavSection("nav.group.sales", List.of(FOR_ADMIN, FOR_EVERYONE));

        // when
        NavSection filtered = section.filteredFor(UserRole.USER);

        // then
        assertThat(filtered.items()).extracting(NavItem::key).containsExactly("orders");
        assertThat(filtered.messageKey()).isEqualTo("nav.group.sales");
    }

    @Test
    void reportsEmptyWhenTheRoleSeesNothingInTheGroup() {
        // given
        NavSection section = new NavSection("nav.group.finance", List.of(FOR_ADMIN));

        // when
        NavSection filtered = section.filteredFor(UserRole.SUPER_ADMIN);

        // then
        assertThat(filtered.isEmpty()).isTrue();
    }
}
