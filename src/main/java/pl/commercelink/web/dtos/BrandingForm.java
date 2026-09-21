package pl.commercelink.web.dtos;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import pl.commercelink.stores.BrandColor;
import pl.commercelink.stores.Branding;
import pl.commercelink.stores.LogoImageType;
import pl.commercelink.stores.Store;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Store name, logo and brand colour shown to customers on the offer, order and return pages. */
@Getter
@Setter
public class BrandingForm {

    public static final int LOGO_MAX_BYTES = 1024 * 1024;
    public static final int STORE_NAME_MAX_LENGTH = 100;

    private String storeName;
    private String primaryColor;
    private MultipartFile logoFile;
    private boolean removeLogo;

    // Filled by validate() only; never bound from the request.
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private LogoUpload logoUpload;

    public record LogoUpload(LogoImageType type, byte[] content) {
    }

    public static BrandingForm from(Store store) {
        BrandingForm form = new BrandingForm();
        form.storeName = store.getName();
        if (store.getBranding() != null) {
            form.primaryColor = store.getBranding().getPrimaryColor();
        }
        return form;
    }

    /** Field name to message key, in the order the fields appear on the page. Empty when the branding can be saved. */
    public Map<String, String> validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        String name = StringUtils.trimToEmpty(storeName);
        if (name.isEmpty()) {
            errors.put("storeName", "store.branding.name.required");
        } else if (name.length() > STORE_NAME_MAX_LENGTH) {
            errors.put("storeName", "store.branding.name.too.long");
        }
        validateLogo().ifPresent(error -> errors.put("logoFile", error));
        if (StringUtils.isNotBlank(primaryColor) && BrandColor.normalize(primaryColor).isEmpty()) {
            errors.put("primaryColor", "store.branding.color.invalid");
        }
        return errors;
    }

    /** The chosen logo once {@link #validate()} has accepted it. */
    public Optional<LogoUpload> logoUpload() {
        return Optional.ofNullable(logoUpload);
    }

    /** Name and colour only; the logo file is stored by the caller, because it lives outside the store record. */
    public void applyTo(Store store) {
        store.setName(StringUtils.trim(storeName));
        Branding branding = store.getBranding() != null ? store.getBranding() : new Branding();
        branding.setPrimaryColor(BrandColor.normalize(primaryColor).orElse(null));
        store.setBranding(branding);
    }

    public String getNormalizedColor() {
        return BrandColor.normalize(primaryColor).orElse(null);
    }

    private Optional<String> validateLogo() {
        logoUpload = null;
        if (logoFile == null || logoFile.isEmpty()) {
            return Optional.empty();
        }
        if (logoFile.getSize() > LOGO_MAX_BYTES) {
            return Optional.of("store.branding.logo.too.large");
        }
        try {
            byte[] content = logoFile.getBytes();
            Optional<LogoImageType> type = LogoImageType.detect(content);
            if (type.isEmpty()) {
                return Optional.of("store.branding.logo.invalid.type");
            }
            logoUpload = new LogoUpload(type.get(), content);
            return Optional.empty();
        } catch (IOException e) {
            return Optional.of("store.branding.logo.invalid.type");
        }
    }
}
