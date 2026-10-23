package pl.commercelink.web.errorpage;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorViewResolver;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.RequestContextUtils;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.publicpage.PublicPage;
import pl.commercelink.web.publicpage.PublicPageFactory;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * An error on a page sent to a store's customers (a missing page, an exception, a bad parameter) is shown on the store's
 * own public frame, never with the dashboard's look or the details Spring keeps in the error model. Every other path goes
 * to Spring's default error views (error/404 for a missing page): Spring Boot creates its own resolver only when the
 * context has none, so this one has to hand those paths on (see {@link ErrorPageConfiguration}).
 */
@Slf4j
public class PublicErrorViewResolver implements ErrorViewResolver, Ordered {

    static final String VIEW = "error/public";

    private final StoresRepository storesRepository;
    private final ErrorViewResolver springDefault;

    PublicErrorViewResolver(StoresRepository storesRepository, ErrorViewResolver springDefault) {
        this.storesRepository = storesRepository;
        this.springDefault = springDefault;
    }

    @Override
    public ModelAndView resolveErrorView(HttpServletRequest request, HttpStatus status, Map<String, Object> model) {
        String path = CustomerPageErrors.originalPath(request);
        Optional<String> storeId = CustomerPageErrors.storeIdOf(path);
        if (storeId.isEmpty()) {
            return springDefault.resolveErrorView(request, status, model);
        }
        Locale locale = RequestContextUtils.getLocale(request);
        Store store = findStore(storeId.get());
        PublicPage page = store != null
                ? PublicPageFactory.of(store, path, locale)
                : PublicPageFactory.unknownStore(path, locale);
        return new ModelAndView(VIEW, Map.of(
                "publicPage", page,
                "errorStatus", status.value(),
                "notFound", status == HttpStatus.NOT_FOUND), status);
    }

    @Override
    public int getOrder() {
        return 0;
    }

    // the error may come from the database itself: the page then goes without the store's brand
    private Store findStore(String storeId) {
        try {
            return storesRepository.findById(storeId);
        } catch (RuntimeException e) {
            log.warn("Store {} could not be read for its error page", storeId, e);
            return null;
        }
    }
}
