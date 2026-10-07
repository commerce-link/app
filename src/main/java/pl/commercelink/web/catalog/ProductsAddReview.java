package pl.commercelink.web.catalog;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.products.CatalogPlacement;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.PriceDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductRecommendation;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.products.brand.BrandMapper;
import pl.commercelink.web.dtos.ProductsBulkAddForm;
import pl.commercelink.web.inventory.InventoryBrowseController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * "Uzupełnij dane": the review of products taken from the inventory before they are added to a manual catalog category,
 * and its save. Shared by the two ways in -- "Dodaj produkty z asortymentu" in the catalog (the category comes from the
 * address) and "Dodaj do katalogu" on the inventory page (the category is chosen on the review itself).
 */
@Component
@RequiredArgsConstructor
public class ProductsAddReview {

    public static final String VIEW = "catalog/products-add-review";

    private final ProductRepository productRepository;
    private final Inventory inventory;
    private final PimCatalog pimCatalog;
    private final BrandMapper brandMapper;
    private final MessageSource messageSource;
    private final CatalogPlacement catalogPlacement;

    /**
     * @param resetRows rows whose label or pricing group, kept from the previous category, the new one does not offer and
     *                  which went back to the proposal's
     */
    public record Prepared(ProductsBulkAddForm form, List<String> skipped, List<String> skippedExisting, int resetRows) {
    }

    /**
     * The rows of the review for {@code eans}: what the inventory no longer has and what the category holds already are
     * skipped. {@code edited} is the review as the operator left it before choosing another category: the name and the
     * identifiers typed there stay with their product, the label and the pricing group only when the new category has
     * them, and the review keeps its id.
     */
    public Prepared prepare(String storeId, CategoryDefinition category, List<String> eans, @Nullable ProductsBulkAddForm edited) {
        InventoryView enabled = inventory.withEnabledSuppliersOnly(storeId);
        // Read once: the same selection sent twice (Back, a double click) must not add the product a second time.
        List<InventoryKey> alreadyInCategory = productRepository.findAll(category.getCategoryId()).stream()
                .map(InventoryKey::fromProduct)
                .toList();
        List<Product> products = new ArrayList<>();
        List<String> sources = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> skippedExisting = new ArrayList<>();
        for (String ean : eans) {
            MatchedInventory matched = enabled.findByEan(ean);
            // The proposals were read before the page was shown; a product can leave the inventory in the meantime.
            if (matched.isEmpty()) {
                skipped.add(ean);
                continue;
            }
            InventoryKey key = matched.getInventoryKey();
            if (alreadyInCategory.stream().anyMatch(key::matches)) {
                skippedExisting.add(ean);
                continue;
            }
            Optional<PimEntry> entry = pimCatalog.findByPimIdOrGtinsOrMpns(key.getId(), key.getProductEans(), key.getProductCodes());
            products.add(new ProductRecommendation(category, matched, entry).toProduct());
            sources.add(ean);
        }
        ProductsBulkAddForm form = ProductsBulkAddForm.of(products);
        for (int index = 0; index < sources.size(); index++) {
            form.getProducts().get(index).setSourceEan(sources.get(index));
        }
        int resetRows = edited == null ? 0 : keepEdits(form, edited, category);
        if (edited != null && StringUtils.isNotBlank(edited.getReviewId())) {
            form.setReviewId(edited.getReviewId());
        }
        return new Prepared(form, skipped, skippedExisting, resetRows);
    }

    private static int keepEdits(ProductsBulkAddForm form, ProductsBulkAddForm edited, CategoryDefinition category) {
        Map<String, ProductsBulkAddForm.Row> before = new LinkedHashMap<>();
        edited.getProducts().stream()
                .filter(row -> row != null && row.getSourceEan() != null)
                .forEach(row -> before.putIfAbsent(row.getSourceEan(), row));
        List<String> labels = category.getGroupingOrder();
        List<String> groups = pricingGroups(category);
        int reset = 0;
        for (ProductsBulkAddForm.Row row : form.getProducts()) {
            ProductsBulkAddForm.Row typed = before.get(row.getSourceEan());
            if (typed == null) {
                continue;
            }
            if (typed.getName() != null) {
                row.setName(typed.getName());
            }
            if (typed.getEan() != null) {
                row.setEan(typed.getEan());
            }
            if (typed.getManufacturerCode() != null) {
                row.setManufacturerCode(typed.getManufacturerCode());
            }
            boolean lost = false;
            // A category that groups by nothing takes any label, as its review shows a text field for it.
            if (labels.isEmpty() || labels.contains(typed.getLabel())) {
                row.setLabel(typed.getLabel());
            } else {
                lost = StringUtils.isNotBlank(typed.getLabel()) && !typed.getLabel().equals(row.getLabel());
            }
            Optional<String> group = groups.stream().filter(g -> g.equalsIgnoreCase(typed.getPricingGroup())).findFirst();
            if (group.isPresent()) {
                row.setPricingGroup(group.get());
            } else {
                lost |= StringUtils.isNotBlank(typed.getPricingGroup()) && !typed.getPricingGroup().equalsIgnoreCase(row.getPricingGroup());
            }
            if (lost) {
                reset++;
            }
        }
        return reset;
    }

    /**
     * Saves the reviewed rows into {@code category} and says how many were added. The review skipped what the category
     * had when it was rendered; the same review sent again (Back, a double click) is decided here once more, against the
     * category as it is now.
     */
    public int save(String storeId, CategoryDefinition category, ProductsBulkAddForm form) {
        List<InventoryKey> alreadyInCategory = new ArrayList<>(productRepository.findAll(category.getCategoryId()).stream()
                .map(InventoryKey::fromProduct)
                .toList());
        InventoryView enabled = inventory.withEnabledSuppliersOnly(storeId);
        int added = 0;
        for (int index = 0; index < form.getProducts().size(); index++) {
            // The category and the id are the application's to give, and so is the PIM entry: a pim id taken from the
            // form would bind the product to an arbitrary entry of the catalog.
            Product product = form.toProduct(index, category.getCategoryId());
            InventoryKey key = InventoryKey.fromProduct(product);
            if (alreadyInCategory.stream().anyMatch(key::matches)) {
                continue;
            }
            pimEntryOf(enabled, key, product).ifPresent(entry -> {
                product.setPimId(entry.pimId());
                product.setBrand(brandMapper.unifyBrand(entry.brand()));
            });
            try {
                productRepository.save(product);
            } catch (ConditionalCheckFailedException e) {
                // The same review, saved by a parallel request that read the category at the same moment, wrote this
                // row first under the same id: the category has it, exactly as if the read above had found it -- so
                // its key guards the rows after it, as the key of a row saved here would.
                alreadyInCategory.add(key);
                continue;
            }
            alreadyInCategory.add(key);
            added++;
        }
        if (added > 0) {
            catalogPlacement.evict(storeId);
        }
        return added;
    }

    /** The PIM entry of a product: by the inventory's own key when the inventory knows it, else by its identifiers. */
    public Optional<PimEntry> pimEntryOf(InventoryView inventory, InventoryKey key, Product product) {
        MatchedInventory matched = inventory.findByInventoryKey(key);
        if (!matched.isEmpty()) {
            InventoryKey known = matched.getInventoryKey();
            Optional<PimEntry> byInventoryKey = pimCatalog.findByPimIdOrGtinsOrMpns(
                    known.getId(), known.getProductEans(), known.getProductCodes());
            if (byInventoryKey.isPresent()) {
                return byInventoryKey;
            }
        }
        return pimCatalog.findByGtinOrMpn(product.getEan(), product.getManufacturerCode());
    }

    /**
     * The notice the inventory page shows after a save: what was added where, and how many were skipped -- the ones the
     * review dropped as well as the ones the save found again.
     */
    public void noticeForInventory(RedirectAttributes redirectAttributes, String catalogId, CategoryDefinition category,
                                   int added, int skipped, Locale locale) {
        redirectAttributes.addFlashAttribute(InventoryBrowseController.NOTICE_FLASH, messageSource.getMessage(
                skipped > 0 ? "inventory.browse.added.skipped" : "inventory.browse.added",
                new Object[]{category.getName(), added, skipped}, locale));
        redirectAttributes.addFlashAttribute("inventoryNoticeHref", CatalogPaths.category(catalogId, category.getCategoryId()));
    }

    /** @param errors field id to message key; the page is given the texts, as the summary links to the fields. */
    public String render(ProductCatalog catalog, CategoryDefinition category, ProductsBulkAddForm form,
                         List<String> skipped, List<String> skippedExisting, Map<String, String> errors,
                         Model model, Locale locale) {
        Map<String, String> texts = new LinkedHashMap<>();
        errors.forEach((field, key) -> texts.put(field, messageSource.getMessage(key, null, locale)));
        model.addAttribute("form", form);
        model.addAttribute("errors", texts);
        model.addAttribute("errorSummary", ProductsBulkAddForm.summary(texts, (number, text) ->
                messageSource.getMessage(ProductsBulkAddForm.SUMMARY_LINE, new Object[]{number, text}, locale)));
        model.addAttribute("catalog", catalog);
        model.addAttribute("category", category);
        model.addAttribute("labels", category.getGroupingOrder());
        model.addAttribute("pricingGroups", pricingGroups(category));
        model.addAttribute("skipped", skipped);
        model.addAttribute("skippedExisting", skippedExisting);
        model.addAttribute("saveAction", CatalogPaths.productsAddSave(catalog.getCatalogId(), category.getCategoryId()));
        model.addAttribute("backHref", CatalogPaths.productsAdd(catalog.getCatalogId(), category.getCategoryId()));
        return VIEW;
    }

    public static List<String> pricingGroups(CategoryDefinition category) {
        return category.getPriceDefinitions().stream().map(PriceDefinition::getPricingGroup).distinct().toList();
    }
}
