package pl.commercelink.web.nav;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

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

    private UserRole currentRole() {
        return CustomSecurityContext.getLoggedInUser()
                .flatMap(user -> user.getCustomAttribute("role"))
                .map(UserRole::valueOf)
                .orElse(null);
    }
}
