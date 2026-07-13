package pl.commercelink.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

@ControllerAdvice
public class DemoEnvironmentBannerAdvice {

    private final StoresRepository storesRepository;
    private final boolean demoEnvironment;

    public DemoEnvironmentBannerAdvice(StoresRepository storesRepository,
                                        @Value("${app.registration.demo}") boolean demoEnvironment) {
        this.storesRepository = storesRepository;
        this.demoEnvironment = demoEnvironment;
    }

    @ModelAttribute("demoEnvironment")
    public boolean demoEnvironment() {
        return demoEnvironment;
    }

    @ModelAttribute("demoExpiresAt")
    public String demoExpiresAt() {
        Instant expiresAt = expiresAt();
        return expiresAt == null ? null : expiresAt.atZone(ZoneId.systemDefault()).toLocalDate().toString();
    }

    @ModelAttribute("demoDaysLeft")
    public Long demoDaysLeft() {
        Instant expiresAt = expiresAt();
        if (expiresAt == null) {
            return null;
        }
        long millisLeft = Duration.between(Instant.now(), expiresAt).toMillis();
        long days = (long) Math.ceil(millisLeft / 86_400_000.0);
        return Math.max(0, days);
    }

    private Instant expiresAt() {
        if (!demoEnvironment) {
            return null;
        }
        String storeId = currentStoreId();
        if (storeId == null) {
            return null;
        }
        Store store = storesRepository.findById(storeId);
        if (store == null || store.getDemo() == null || store.getDemo().getExpiresAt() == null) {
            return null;
        }
        try {
            return Instant.parse(store.getDemo().getExpiresAt());
        } catch (RuntimeException e) {
            return null;
        }
    }

    String currentStoreId() {
        return CustomSecurityContext.getStoreId();
    }
}
