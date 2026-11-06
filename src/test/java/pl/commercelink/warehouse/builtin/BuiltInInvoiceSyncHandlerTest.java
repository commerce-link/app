package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.warehouse.api.InvoiceSyncRequest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuiltInInvoiceSyncHandlerTest {

    private static final String EAN_A = "5901234123457";
    private static final String EAN_B = "5901234123464";
    private static final String SHARED_MFN = "SHOE-1";

    @Mock
    private WarehouseDocumentRepository warehouseDocumentRepository;
    @Mock
    private WarehouseDocumentItemRepository warehouseDocumentItemRepository;

    private BuiltInInvoiceSyncHandler handler;

    @BeforeEach
    void setUp() {
        handler = new BuiltInInvoiceSyncHandler("store-1", warehouseDocumentRepository, warehouseDocumentItemRepository);
    }

    @Test
    void twoProductsUnderOneCodeGetTheirOwnInvoicePrices() {
        // given
        WarehouseDocumentItem lineA = documentLine(EAN_A, 90.0);
        WarehouseDocumentItem lineB = documentLine(EAN_B, 90.0);
        when(warehouseDocumentItemRepository.findByDeliveryId("delivery-1")).thenReturn(List.of(lineA, lineB));

        // when
        handler.sync(new InvoiceSyncRequest("delivery-1", Map.of(EAN_A, 100.0, EAN_B, 120.0), null));

        // then
        assertThat(lineA.getUnitPrice()).isEqualTo(100.0);
        assertThat(lineB.getUnitPrice()).isEqualTo(120.0);
        verify(warehouseDocumentItemRepository).save(lineA);
        verify(warehouseDocumentItemRepository).save(lineB);
    }

    private static WarehouseDocumentItem documentLine(String ean, double unitPrice) {
        return new WarehouseDocumentItem("document-1", DocumentType.GoodsReceipt, null, "delivery-1", ean, SHARED_MFN,
                "Shoe", 1, unitPrice);
    }
}
