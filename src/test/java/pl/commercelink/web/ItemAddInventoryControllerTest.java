package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.web.dtos.ItemAddInventoryRow;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemAddInventoryControllerTest {

    private static final String STORE_ID = "store-1";
    private static final String EAN = "194499027057";

    @Mock
    private Inventory inventory;
    @Mock
    private InventoryView inventoryView;
    @Mock
    private MatchedInventory matched;

    @InjectMocks
    private ItemAddInventoryController controller;

    private MockedStatic<CustomSecurityContext> securityStub;

    @BeforeEach
    void storeOfTheSession() {
        securityStub = mockStatic(CustomSecurityContext.class);
        securityStub.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
    }

    @AfterEach
    void tearDown() {
        securityStub.close();
    }

    @Test
    void listsTheProductOfTheEanWithWhatTheItemWillGet() {
        // given
        when(matched.hasAnyOffers()).thenReturn(true);
        when(matched.getTaxonomy()).thenReturn(new Taxonomy(EAN, "CW2288-111", "Nike", "Air Force 1, rozm. 41", "Buty",
                1, null, null));
        when(matched.getMedianPrice()).thenReturn(Price.fromNet(200));
        when(matched.getTotalAvailableQty()).thenReturn(3L);
        when(matched.getEstimatedDeliveryDays()).thenReturn(2);
        when(inventory.withEnabledSuppliersOnly(STORE_ID)).thenReturn(inventoryView);
        when(inventoryView.findByEan(EAN)).thenReturn(matched);

        // when
        List<ItemAddInventoryRow> rows = controller.find(" " + EAN + " ");

        // then
        assertThat(rows).containsExactly(new ItemAddInventoryRow(EAN, "CW2288-111", "Nike",
                "Air Force 1, rozm. 41", "Buty", 246.0, 3L, 2));
    }

    @Test
    void anEanWithoutOffersListsNothing() {
        // given
        when(matched.hasAnyOffers()).thenReturn(false);
        when(inventory.withEnabledSuppliersOnly(STORE_ID)).thenReturn(inventoryView);
        when(inventoryView.findByEan(EAN)).thenReturn(matched);

        // when
        List<ItemAddInventoryRow> rows = controller.find(EAN);

        // then
        assertThat(rows).isEmpty();
    }

    @Test
    void aBlankEanFindsNothing() {
        // when
        List<ItemAddInventoryRow> rows = controller.find("  ");

        // then
        assertThat(rows).isEmpty();
        verifyNoInteractions(inventory);
    }
}
