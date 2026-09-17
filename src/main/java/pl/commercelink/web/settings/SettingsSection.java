package pl.commercelink.web.settings;

import java.util.List;

public record SettingsSection(String messageKey, List<SettingsTile> tiles) {
}
