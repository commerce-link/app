package pl.commercelink.web.settings;

import java.util.List;

public record SettingsSectionView(String messageKey, List<SettingsTileView> tiles) {
}
