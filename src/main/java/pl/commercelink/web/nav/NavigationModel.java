package pl.commercelink.web.nav;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import pl.commercelink.starter.security.UserRole;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class NavigationModel {

    // The supplier selection page posts under /dashboard/orders but is the queue's second step (spec B14).
    private static final Map<String, String> ALIASES = Map.of("/dashboard/orders/fulfilment", "/dashboard/fulfilment/queue");

    private final List<NavSection> sections;
    private final List<NavItem> footer;
    private final NavItem active;

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
        String normalized = alias(StorePath.stripStorePrefix(path));
        return Stream.concat(sections.stream().flatMap(section -> section.items().stream()), footer.stream())
                .filter(item -> matches(item.path(), normalized))
                .max(Comparator.comparingInt(item -> item.path().length()))
                .orElse(null);
    }

    private static String alias(String path) {
        return ALIASES.entrySet().stream()
                .filter(alias -> matches(alias.getKey(), path))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(path);
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
