package pl.commercelink.web.settings;

import java.util.List;

public record StoreSettingsOverview(List<SettingsSectionView> sections, List<StoreAlert> alerts) {
}
