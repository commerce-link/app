package pl.commercelink.web.activity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import pl.commercelink.starter.security.tenant.TenantResolver;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoresRepository;

import java.io.IOException;

/**
 * Closes what the public reaches of an inactive store: the shop API answers 403 {@code store-inactive}, the client
 * pages show that the store is inactive. An unknown store is left to the endpoint, which answers as it always has.
 */
@Component
@RequiredArgsConstructor
public class PublicStoreActivityInterceptor implements HandlerInterceptor {

    static final String INACTIVE_STORE_BODY = "{\"error\":\"store-inactive\"}";

    private static final String CLIENT_PAGES_ROOT = "store";

    private final TenantResolver tenantResolver;
    private final StoresRepository storesRepository;
    private final StoreActivity storeActivity;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String storeId = tenantResolver.resolveTenantId(request);
        if (storeId == null) {
            return true;
        }
        Store store = storesRepository.findById(storeId);
        if (store == null || storeActivity.isActive(store)) {
            return true;
        }
        if (isClientPage(request)) {
            throw new StoreInactiveException(storeId);
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(INACTIVE_STORE_BODY);
        return false;
    }

    // the shop API lives under /Store/{storeId}, the pages sent to the store's clients under /store/{storeId}
    private static boolean isClientPage(HttpServletRequest request) {
        String[] segments = request.getRequestURI().split("/");
        return segments.length > 1 && CLIENT_PAGES_ROOT.equals(segments[1]);
    }
}
