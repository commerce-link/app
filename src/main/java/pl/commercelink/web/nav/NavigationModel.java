package pl.commercelink.web.nav;

import pl.commercelink.starter.security.UserRole;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class NavigationModel {

    private final List<NavSection> sections;
    private final List<NavItem> footer;
    private final NavItem active;

    private NavigationModel(List<NavSection> sections, List<NavItem> footer, NavItem active) {
        this.sections = sections;
        this.footer = footer;
        this.active = active;
    }

    public static NavigationModel forRoleAndPath(UserRole role, String path) {
        List<NavSection> sections = NavigationCatalog.sections().stream()
                .map(section -> section.filteredFor(role))
                .filter(section -> !section.isEmpty())
                .toList();
        List<NavItem> footer = NavigationCatalog.footer().stream()
                .filter(item -> item.visibleFor(role))
                .toList();
        return new NavigationModel(sections, footer, findActive(sections, footer, path));
    }

    private static NavItem findActive(List<NavSection> sections, List<NavItem> footer, String path) {
        String normalized = StorePath.stripStorePrefix(path);
        return Stream.concat(sections.stream().flatMap(section -> section.items().stream()), footer.stream())
                .filter(item -> matches(item.path(), normalized))
                .max(Comparator.comparingInt(item -> item.path().length()))
                .orElse(null);
    }

    private static boolean matches(String itemPath, String path) {
        return path.equals(itemPath) || path.startsWith(itemPath + "/");
    }

    public List<NavSection> sections() {
        return sections;
    }

    public List<NavItem> footer() {
        return footer;
    }

    public NavItem active() {
        return active;
    }

    public boolean isActive(NavItem item) {
        return active != null && active.key().equals(item.key());
    }

    public String activeMessageKey() {
        return active == null ? null : active.messageKey();
    }

    public String activeSectionMessageKey() {
        if (active == null) {
            return null;
        }
        return sections.stream()
                .filter(section -> section.items().contains(active))
                .map(NavSection::messageKey)
                .findFirst()
                .orElse(null);
    }
}
