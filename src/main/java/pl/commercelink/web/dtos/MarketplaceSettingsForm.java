package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.scheduling.InvalidScheduleException;
import pl.commercelink.scheduling.PollingSchedule;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A marketplace connection: the adapter settings of {@link IntegrationSettingsForm} plus the orders and returns import
 * schedules. Schedules are posted per marketplace as schedules[marketplace.orders] and schedules[marketplace.returns], so a
 * page showing every marketplace (no JavaScript) still saves only those of the chosen one. An empty schedule means the
 * default one.
 */
@Getter
@Setter
public class MarketplaceSettingsForm extends IntegrationSettingsForm {

    public static final String ORDERS = "orders";
    public static final String RETURNS = "returns";

    private Map<String, String> schedules = new HashMap<>();

    public static MarketplaceSettingsForm of(IntegrationSettingsForm settings, String ordersSchedule, String returnsSchedule) {
        MarketplaceSettingsForm form = new MarketplaceSettingsForm();
        form.setProviderName(settings.getProviderName());
        form.setSettings(settings.getSettings());
        form.schedules.put(scheduleKey(settings.getProviderName(), ORDERS), ordersSchedule);
        form.schedules.put(scheduleKey(settings.getProviderName(), RETURNS), returnsSchedule);
        return form;
    }

    /** Name the hidden input of a schedule is posted under. */
    public static String scheduleName(String marketplace, String kind) {
        return "schedules[" + scheduleKey(marketplace, kind) + "]";
    }

    /** Id of a schedule field, also the key of its error. */
    public static String scheduleId(String marketplace, String kind) {
        return "schedule-" + marketplace + "-" + kind;
    }

    public String schedule(String forMarketplace, String kind) {
        return StringUtils.defaultString(schedules.get(scheduleKey(forMarketplace, kind)));
    }

    public String ordersSchedule() {
        return StringUtils.trimToNull(schedule(getProviderName(), ORDERS));
    }

    public String returnsSchedule() {
        return StringUtils.trimToNull(schedule(getProviderName(), RETURNS));
    }

    /**
     * The schedule builder only offers valid schedules at least minIntervalMinutes apart, so an error here means a
     * hand-edited request or a stored expression from before the builder.
     */
    public Map<String, String> validateSchedules(boolean supportsReturns, int minIntervalMinutes) {
        Map<String, String> errors = new LinkedHashMap<>();
        validate(ORDERS, ordersSchedule(), minIntervalMinutes, errors);
        if (supportsReturns) {
            validate(RETURNS, returnsSchedule(), minIntervalMinutes, errors);
        }
        return errors;
    }

    private void validate(String kind, String expression, int minIntervalMinutes, Map<String, String> errors) {
        String normalized = PollingSchedule.normalizeOrNull(expression);
        if (normalized == null) {
            return;
        }
        try {
            PollingSchedule.parse(normalized, minIntervalMinutes);
        } catch (InvalidScheduleException e) {
            errors.put(scheduleId(getProviderName(), kind), e.getReason() == InvalidScheduleException.Reason.TOO_FREQUENT
                    ? "store.marketplace.schedule.tooFrequent" : "store.marketplace.schedule.invalid");
        }
    }

    private static String scheduleKey(String marketplace, String kind) {
        return marketplace + "." + kind;
    }
}
