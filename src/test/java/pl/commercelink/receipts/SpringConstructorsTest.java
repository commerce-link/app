package pl.commercelink.receipts;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards against the D1 regression: a Spring-managed class with more than one constructor and no
 * {@code @Autowired} constructor fails to start the application context (Spring falls back to looking for a
 * default constructor, which does not exist). No test in this module raises a Spring context, so nothing else
 * catches this before the app fails to boot.
 */
class SpringConstructorsTest {

    private static final List<String> EXTRA_BEAN_NAMES = List.of(
            "pl.commercelink.provider.ProviderCallLimiter",
            "pl.commercelink.web.ReceiptSystems",
            "pl.commercelink.web.StoreReceiptsSettingsController",
            "pl.commercelink.web.StoreReceiptSystemController",
            "pl.commercelink.web.OrderReceiptsController"
    );

    @Test
    void everyMultiConstructorBeanHasExactlyOneAutowiredConstructor() throws ClassNotFoundException {
        Set<Class<?>> beans = new LinkedHashSet<>(scanReceiptsPackage());
        for (String name : EXTRA_BEAN_NAMES) {
            beans.add(Class.forName(name));
        }

        assertThat(beans).isNotEmpty();

        List<String> violations = new ArrayList<>();
        for (Class<?> bean : beans) {
            Constructor<?>[] constructors = bean.getDeclaredConstructors();
            if (constructors.length <= 1) {
                continue;
            }
            long autowired = 0;
            for (Constructor<?> constructor : constructors) {
                if (constructor.isAnnotationPresent(Autowired.class)) {
                    autowired++;
                }
            }
            if (autowired != 1) {
                violations.add(bean.getName() + " has " + constructors.length + " constructors and "
                        + autowired + " annotated @Autowired (expected exactly 1)");
            }
        }

        assertThat(violations).isEmpty();
    }

    private static List<Class<?>> scanReceiptsPackage() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Service.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Configuration.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));

        List<Class<?>> classes = new ArrayList<>();
        scanner.findCandidateComponents("pl.commercelink.receipts").forEach(candidate -> {
            try {
                classes.add(Class.forName(candidate.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        });
        return classes;
    }
}
