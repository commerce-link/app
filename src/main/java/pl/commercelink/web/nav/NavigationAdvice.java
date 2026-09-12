package pl.commercelink.web.nav;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.Arrays;

@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class NavigationAdvice {

    private final StoresRepository storesRepository;

    @ModelAttribute("navigation")
    public NavigationModel navigation(HttpServletRequest request) {
        UserRole role = currentRole();
        return role == null ? null : NavigationModel.forRoleAndPath(role, request.getRequestURI());
    }

    @ModelAttribute("storeContext")
    public StoreContext storeContext(HttpServletRequest request) {
        if (currentRole() != UserRole.SUPER_ADMIN) {
            return null;
        }
        String storeId = StorePath.storeIdIn(request.getRequestURI());
        if (storeId == null) {
            return null;
        }
        Store store = storesRepository.findById(storeId);
        return store == null ? null : new StoreContext(storeId, store.getName());
    }

    @ModelAttribute("userEmail")
    public String userEmail() {
        return CustomSecurityContext.getLoggedInUser()
                .map(user -> (String) user.getAttribute("email"))
                .orElse(null);
    }

    private UserRole currentRole() {
        return CustomSecurityContext.getLoggedInUser()
                .flatMap(user -> user.getCustomAttribute("role"))
                .map(NavigationAdvice::toRole)
                .orElse(null);
    }

    private static UserRole toRole(String role) {
        return Arrays.stream(UserRole.values())
                .filter(known -> known.name().equals(role))
                .findFirst()
                .orElseGet(() -> {
                    log.warn("Unknown user role '{}' — rendering the dashboard without navigation", role);
                    return null;
                });
    }
}
