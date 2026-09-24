package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.scheduling.InvalidScheduleException;
import pl.commercelink.scheduling.PollingSchedule;
import pl.commercelink.starter.util.UniqueIdentifierGenerator;

import java.util.LinkedHashMap;
import java.util.Map;

/** Name, price-list schedule and deletion protection of a catalog — the whole "Catalog settings" page. */
@Getter
@Setter
public class CatalogSettingsForm {

    private static final int NAME_MAX_LENGTH = 120;

    private String name;
    private String pricelistSchedule;
    /** False by default: an unticked checkbox sends nothing, so anything else could never be switched off. */
    private boolean deletionProtection;
    /**
     * The id a new catalog will get, given when the form is shown and posted back in a hidden field: the same form
     * sent twice names the same catalog, which the second POST finds instead of creating another (RF-5). Unused for
     * an existing catalog, whose id is in the address.
     */
    private String newCatalogId;

    /** A catalog starts protected, the way ProductCatalog does; the operator unticks the box to be able to delete it. */
    public static CatalogSettingsForm forNewCatalog() {
        CatalogSettingsForm form = new CatalogSettingsForm();
        form.deletionProtection = true;
        form.newCatalogId = UniqueIdentifierGenerator.generate();
        return form;
    }

    public static CatalogSettingsForm from(ProductCatalog catalog) {
        CatalogSettingsForm form = new CatalogSettingsForm();
        form.name = catalog.getName();
        form.pricelistSchedule = catalog.getPricelistSchedule();
        form.deletionProtection = catalog.isDeletionProtection();
        return form;
    }

    public Map<String, String> validate(int minIntervalMinutes) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (FormRules.requireText(errors, "name", name, "catalog.name.required") && name.trim().length() > NAME_MAX_LENGTH) {
            errors.put("name", "catalog.name.tooLong");
        }
        String schedule = PollingSchedule.normalizeOrNull(pricelistSchedule);
        if (schedule != null) {
            try {
                PollingSchedule.parse(schedule, minIntervalMinutes);
            } catch (InvalidScheduleException e) {
                errors.put("pricelistSchedule", scheduleErrorKey(e.getReason()));
            }
        }
        return errors;
    }

    /** The ".field" variants carry no placeholders: the error summary renders a key without arguments. */
    public static String scheduleErrorKey(InvalidScheduleException.Reason reason) {
        return reason == InvalidScheduleException.Reason.TOO_FREQUENT
                ? "catalog.pricelist.schedule.error.too.frequent.field"
                : "catalog.pricelist.schedule.error.invalid.field";
    }

    /** What ProductCatalogDetailsService.save expects: a catalog carrying the submitted values. */
    public ProductCatalog toCatalog(String storeId, String catalogId) {
        ProductCatalog catalog = new ProductCatalog();
        catalog.setStoreId(storeId);
        catalog.setCatalogId(catalogId);
        catalog.setName(StringUtils.trimToNull(name));
        catalog.setDeletionProtection(deletionProtection);
        catalog.setPricelistSchedule(PollingSchedule.normalizeOrNull(pricelistSchedule));
        return catalog;
    }
}
