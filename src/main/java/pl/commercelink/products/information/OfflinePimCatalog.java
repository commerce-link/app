package pl.commercelink.products.information;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import pl.commercelink.pim.api.PIMEntryAddedEvent;
import pl.commercelink.pim.api.PIMEntryDeletedEvent;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimCategory;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimEntryRequest;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

class OfflinePimCatalog implements PimCatalog {

    private static final String CATEGORIES_RESOURCE = "/icecat/categories.json";
    private static final String LANG = "pl";

    private final List<PimCategory> categories;

    OfflinePimCatalog() {
        this.categories = loadBundledCategories();
    }

    private static List<PimCategory> loadBundledCategories() {
        try (InputStream stream = OfflinePimCatalog.class.getResourceAsStream(CATEGORIES_RESOURCE)) {
            if (stream == null) {
                System.err.println("OfflinePimCatalog: missing classpath resource " + CATEGORIES_RESOURCE + " — serving no categories");
                return List.of();
            }
            List<BundledCategoryNode> nodes = new ObjectMapper()
                    .readValue(stream, new TypeReference<List<BundledCategoryNode>>() {});
            List<PimCategory> categories = nodes.stream()
                    .map(node -> new PimCategory(node.id(), node.parentId(), node.namePl(), LANG))
                    .toList();
            System.out.println("OfflinePimCatalog: loaded " + categories.size() + " bundled categories from " + CATEGORIES_RESOURCE);
            return categories;
        } catch (Exception e) {
            System.err.println("OfflinePimCatalog: failed to load " + CATEGORIES_RESOURCE + " — serving no categories: " + e.getMessage());
            return List.of();
        }
    }

    private record BundledCategoryNode(String id, String parentId, String namePl, String nameEn) {
    }

    @Override
    public List<PimCategory> allCategories() {
        return categories;
    }

    @Override
    public List<PimEntry> findAll() {
        return List.of();
    }

    @Override
    public Optional<PimEntry> findByPimId(String pimId) {
        return Optional.empty();
    }

    @Override
    public Optional<PimEntry> findByGtin(String gtin) {
        return Optional.empty();
    }

    @Override
    public Optional<PimEntry> findByMpn(String mpn) {
        return Optional.empty();
    }

    @Override
    public Optional<PimEntry> findByGtinOrMpn(String gtin, String mpn) {
        return Optional.empty();
    }

    @Override
    public void submit(PimEntryRequest request) {
    }

    @Override
    public void refresh() {
    }

    @Override
    public void onEntryAdded(Consumer<PIMEntryAddedEvent> listener) {
    }

    @Override
    public void onEntryDeleted(Consumer<PIMEntryDeletedEvent> listener) {
    }
}
