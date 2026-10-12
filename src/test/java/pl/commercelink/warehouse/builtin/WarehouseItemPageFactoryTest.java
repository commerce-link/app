package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.support.ResourceBundleMessageSource;
import pl.commercelink.inventory.deliveries.DeliveryRedirectResolver;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.products.StoreCategories;
import pl.commercelink.warehouse.api.ItemCondition;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WarehouseItemPageFactoryTest {

    private static final Locale PL = Locale.forLanguageTag("pl");

    @Mock private StoreCategories storeCategories;
    @Mock private SupplierLabels supplierLabels;
    @Mock private SupplierLabelMap labelMap;
    @Mock private SupplierRegistry suppliers;
    @Mock private DeliveryRedirectResolver redirects;

    private WarehouseItemPageFactory factory;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        factory = new WarehouseItemPageFactory(messages, storeCategories, supplierLabels, suppliers, redirects);
        when(supplierLabels.forStoreId("store-1")).thenReturn(labelMap);
        when(storeCategories.groupsFor("store-1")).thenReturn(List.of(new StoreCategories.Group("Komputery", List.of("Dyski SSD"))));
        when(redirects.pointsToDelivery(any())).thenReturn(true);
        when(redirects.resolveFor(any())).thenReturn("/dashboard/deliveries/details?deliveryId=3f2a9c1e-0000");
    }

    private static WarehouseItem item(FulfilmentStatus status) {
        WarehouseItem item = new WarehouseItem("store-1", "3f2a9c1e-0000", "Dyski SSD", "Samsung 990 PRO 2TB", "8806094215038", "MZ-V9P2T0BW", 689.0, 3);
        item.setItemId("7c1e5a20-1111-2222-3333-444444444444");
        item.setStatus(status);
        return item;
    }

    @Test
    void deliveredItemShowsFactsOriginAndNoDelete() {
        // given
        WarehouseItem item = item(FulfilmentStatus.Delivered);
        item.setSerialNo("S1");

        // when
        WarehouseItemPage page = factory.page(item, "/dashboard/warehouse?q=ssd", false, null, Map.of(), null, PL);

        // then
        assertThat(page.mode()).isEqualTo(WarehouseItemPage.Mode.EDIT);
        assertThat(page.shortId()).isEqualTo("7c1e5a20");
        assertThat(page.backHref()).isEqualTo("/dashboard/warehouse?q=ssd");
        assertThat(page.backLabel()).isEqualTo("Magazyn");
        assertThat(page.statusTone()).isEqualTo("is-ok");
        assertThat(page.facts()).extracting(WarehouseItemPage.Fact::value).contains("3 szt.", "689,00 PLN", "847,47 PLN", "23%");
        assertThat(page.totalNet()).isEqualTo("2\u00A0067,00 PLN");
        assertThat(page.origin().deliveryHref()).isEqualTo("/dashboard/deliveries/details?deliveryId=3f2a9c1e-0000");
        assertThat(page.origin().documentsHref()).isEqualTo(
                "/dashboard/warehouse-documents/delivery-mfn-history?deliveryId=3f2a9c1e-0000&mfn=MZ-V9P2T0BW&from=item&itemId=7c1e5a20-1111-2222-3333-444444444444");
        assertThat(page.delete()).isNull();
        assertThat(page.form().getBack()).isEqualTo("/dashboard/warehouse?q=ssd");
    }

    @Test
    void serialLinksSplitTrimAndEncode() {
        // given
        WarehouseItem item = item(FulfilmentStatus.Delivered);
        item.setSerialNo("A1, ,B 2 ");

        // when
        WarehouseItemPage page = factory.page(item, null, false, null, Map.of(), null, PL);

        // then
        assertThat(page.origin().serials()).containsExactly(
                new WarehouseItemPage.Link("A1", "/dashboard/item/history?serialNo=A1"),
                new WarehouseItemPage.Link("B 2", "/dashboard/item/history?serialNo=B+2"));
    }

    @Test
    void newItemIsFullyEditableAndDeletable() {
        // when
        WarehouseItemPage page = factory.page(item(FulfilmentStatus.New), null, false, null, Map.of(), null, PL);

        // then
        assertThat(page.mode()).isEqualTo(WarehouseItemPage.Mode.NEW);
        assertThat(page.facts()).isEmpty();
        assertThat(page.statuses()).extracting(WarehouseItemPage.Choice::value).containsExactly("New", "Allocation");
        assertThat(page.origin().documentsHref()).isNull();
        assertThat(page.delete().href()).isEqualTo("/dashboard/warehouse/items/7c1e5a20-1111-2222-3333-444444444444/delete");
        assertThat(page.delete().title()).contains("Samsung 990 PRO 2TB");
    }

    @Test
    void destroyedItemNamesTheDateAndGoesBackToTheArchive() {
        // given
        WarehouseItem item = item(FulfilmentStatus.Destroyed);
        item.setDestroyedAt(LocalDate.of(2026, 10, 6));
        item.setCondition(ItemCondition.Damaged);

        // when
        WarehouseItemPage page = factory.page(item, null, false, null, Map.of(), null, PL);

        // then
        assertThat(page.mode()).isEqualTo(WarehouseItemPage.Mode.DESTROYED);
        assertThat(page.destroyedTitle()).isEqualTo("Zniszczona 06.10.2026");
        assertThat(page.backHref()).isEqualTo("/dashboard/warehouse/items/destroyed");
        assertThat(page.backLabel()).isEqualTo("Zniszczone pozycje");
        assertThat(page.statusTone()).isEqualTo("is-neutral");
        assertThat(page.readonlyCondition().tone()).isEqualTo("is-bad");
    }

    @Test
    void destroyedItemAfterAFailedSaveStillShowsTheStoredReadOnlyData() {
        // given
        WarehouseItem item = item(FulfilmentStatus.Destroyed);
        item.setSerialNo("S1, S2");
        WarehouseItemEditForm posted = new WarehouseItemEditForm();
        posted.setComment("x".repeat(2001));

        // when
        WarehouseItemPage page = factory.page(item, null, false, posted, Map.of("comment", "warehouse.item.error.comment"), null, PL);

        // then
        assertThat(page.form().getCategory()).isEqualTo("Dyski SSD");
        assertThat(page.form().getSerialNo()).isEqualTo("S1, S2");
        assertThat(page.form().getComment()).hasSize(2001);
    }

    @Test
    void destroyedWithoutDateNamesNoDate() {
        // when
        WarehouseItemPage page = factory.page(item(FulfilmentStatus.Destroyed), null, false, null, Map.of(), null, PL);

        // then
        assertThat(page.destroyedTitle()).isEqualTo("Zniszczona");
    }

    @Test
    void itemAddedByHandHasNoDocumentsLink() {
        // given
        WarehouseItem item = item(FulfilmentStatus.Delivered);
        item.setDeliveryId("Unknown");

        // when
        WarehouseItemPage page = factory.page(item, null, false, null, Map.of(), null, PL);

        // then
        assertThat(page.origin().documentsHref()).isNull();
        assertThat(page.origin().deliveryHref()).isNull();
    }

    @Test
    void categoryOutsideTheCatalogIsKeptAsAnOption() {
        // given
        WarehouseItem item = item(FulfilmentStatus.Delivered);
        item.setCategory("Stara kategoria");

        // when
        WarehouseItemPage page = factory.page(item, null, false, null, Map.of(), null, PL);

        // then
        assertThat(page.categoryOutsideCatalog()).isTrue();
    }
}
