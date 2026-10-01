package pl.commercelink.inventory.supplier;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.InventoryRepository;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.ParsedRow;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.SupplierType;
import pl.commercelink.inventory.supplier.api.XmlItem;

import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XmlProductFeedLoaderTest {

    private static final SupplierInfo SUPPLIER = new SupplierInfo("acme", SupplierType.Distributor, 0, "PL", null);

    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private StoreFeedRepository storeFeedRepository;
    @Mock
    private DataCleanup dataCleanup;
    @Mock
    private FeedRowProcessor feedRowProcessor;
    @InjectMocks
    private XmlProductFeedLoader loader;

    @Test
    void parsesWellFormedFeedWithoutADtd() throws Exception {
        // given
        when(inventoryRepository.read("acme", "xml")).thenReturn(reader(
                "<products><product><ean>111</ean></product><product><ean>222</ean></product></products>"));
        when(feedRowProcessor.process(any(), anyInt(), any())).thenReturn(Optional.empty());
        when(dataCleanup.run(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        List<InventoryItem> result = loader.load(TestProduct.class, "product", SUPPLIER);

        // then
        assertThat(result).isEmpty();
        verify(feedRowProcessor, times(2)).process(any(), anyInt(), any());
    }

    @Test
    void skipsFeedWithExternalEntityAndNeverResolvesIt() throws Exception {
        // given
        String feed = "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE products [ <!ENTITY xxe SYSTEM \"file:///etc/passwd\"> ]>"
                + "<products><product><ean>&xxe;</ean></product></products>";
        when(inventoryRepository.read("acme", "xml")).thenReturn(reader(feed));

        // when
        List<InventoryItem> result = loader.load(TestProduct.class, "product", SUPPLIER);

        // then
        assertThat(result).isEmpty();
        verify(feedRowProcessor, never()).process(any(), anyInt(), any());
        verifyNoInteractions(dataCleanup);
    }

    @Test
    void skipsFeedWithEntityExpansionBomb() throws Exception {
        // given
        String feed = "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE lolz [ <!ENTITY lol \"lol\">"
                + " <!ENTITY lol2 \"&lol;&lol;&lol;&lol;&lol;\">"
                + " <!ENTITY lol3 \"&lol2;&lol2;&lol2;&lol2;&lol2;\"> ]>"
                + "<products><product><ean>&lol3;</ean></product></products>";
        when(inventoryRepository.read("acme", "xml")).thenReturn(reader(feed));

        // when
        List<InventoryItem> result = loader.load(TestProduct.class, "product", SUPPLIER);

        // then
        assertThat(result).isEmpty();
        verify(feedRowProcessor, never()).process(any(), anyInt(), any());
    }

    private static Reader reader(String xml) {
        return new StringReader(xml);
    }

    @XmlRootElement(name = "product")
    @XmlAccessorType(XmlAccessType.FIELD)
    static class TestProduct implements XmlItem {

        private String ean;

        @Override
        public String getEan() {
            return ean;
        }

        @Override
        public String getMfn() {
            return null;
        }

        @Override
        public String getBrand() {
            return null;
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public String getCategory() {
            return null;
        }

        @Override
        public double getNetPrice() {
            return 0;
        }

        @Override
        public int getQty() {
            return 0;
        }

        @Override
        public String getCurrency() {
            return null;
        }

        @Override
        public ParsedRow toParsedRow(SupplierInfo supplierInfo) {
            return new ParsedRow(null, null);
        }
    }
}
