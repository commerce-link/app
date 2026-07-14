package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.InventoryRepository;
import pl.commercelink.inventory.supplier.api.CsvRowParser;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.ParsedRow;
import pl.commercelink.inventory.supplier.api.ShippingCostPolicy;
import pl.commercelink.inventory.supplier.api.ShippingPolicy;
import pl.commercelink.inventory.supplier.api.ShippingTerms;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.SupplierType;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.inventory.supplier.api.XmlItem;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.io.FileNotFoundException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreScopedFeedLoaderTest {

    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private StoreFeedRepository storeFeedRepository;
    @Mock
    private DataCorrection dataCorrection;
    @Mock
    private DataCleanup dataCleanup;
    @Mock
    private pl.commercelink.taxonomy.TaxonomyCache taxonomyCache;

    @InjectMocks
    private CsvProductFeedLoader loader;
    @InjectMocks
    private XmlProductFeedLoader xmlLoader;

    @XmlRootElement(name = "Item")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class TestXmlItem implements XmlItem {
        private String ean;
        private String mfn;
        private String brand;
        private String name;
        private String category;
        private double netPrice;
        private int qty;
        private String currency;

        public String getEan() { return ean; }
        public String getMfn() { return mfn; }
        public String getBrand() { return brand; }
        public String getName() { return name; }
        public String getCategory() { return category; }
        public double getNetPrice() { return netPrice; }
        public int getQty() { return qty; }
        public String getCurrency() { return currency; }
    }

    private static final String XML_FEED =
            "<feed><Item><ean>5900000000001</ean><mfn>MFN1</mfn><brand>Brand</brand>"
                    + "<name>Name</name><category>CPU</category><netPrice>10.0</netPrice>"
                    + "<qty>1</qty><currency>PLN</currency></Item></feed>";

    private SupplierInfo supplierInfo(String name) {
        return new SupplierInfo(name, SupplierType.Distributor, 5, "PL",
                new ShippingPolicy(new ShippingTerms(1, new ShippingCostPolicy.Free())));
    }

    @Test
    void csvStoreOverloadReturnsEmptyWhenStoreFeedNotReadable() {
        // given
        when(storeFeedRepository.canRead("store-1", "Action", "csv")).thenReturn(false);

        // when
        List<?> result = loader.fetch(mock(CsvRowParser.class), ';', "store-1", "Action", 0);

        // then
        assertTrue(result.isEmpty());
        verify(storeFeedRepository).canRead("store-1", "Action", "csv");
    }

    @Test
    void csvStoreOverloadReadsStoreScopedKey() throws Exception {
        // given
        when(storeFeedRepository.canRead("store-1", "Action", "csv")).thenReturn(true);
        Reader reader = new StringReader("");
        when(storeFeedRepository.read("store-1", "Action", "csv")).thenReturn(reader);
        when(dataCleanup.run(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        loader.fetch(mock(CsvRowParser.class), ';', "store-1", "Action", 0);

        // then
        verify(storeFeedRepository).read("store-1", "Action", "csv");
    }

    @Test
    void csvStoreOverloadBumpsTaxonomyScoreByPenalty() throws Exception {
        // given
        when(storeFeedRepository.canRead("store-1", "Action", "csv")).thenReturn(true);
        when(storeFeedRepository.read("store-1", "Action", "csv")).thenReturn(new StringReader("header\nrow"));

        CsvRowParser parser = mock(CsvRowParser.class);
        InventoryItem item = new InventoryItem("5900000000001", "MFN1", 10.0, "PLN", 1, 1, "Action", true, true, false);
        Taxonomy taxonomy = new Taxonomy("5900000000001", "MFN1", "Brand", "Name", "CPU", 5, null, null);
        when(parser.tryParse(any())).thenReturn(Optional.of(new ParsedRow(item, taxonomy)));
        when(dataCorrection.run(item)).thenReturn(item);
        when(dataCorrection.run(taxonomy)).thenReturn(taxonomy);
        when(dataCleanup.run(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        loader.fetch(parser, ';', "store-1", "Action", 1000);

        // then
        ArgumentCaptor<Taxonomy> captor = ArgumentCaptor.forClass(Taxonomy.class);
        verify(taxonomyCache).add(captor.capture());
        assertEquals(1005, captor.getValue().dataAccuracyScore());
    }

    @Test
    void xmlStoreOverloadReturnsEmptyWhenStoreFeedNotReadable() throws Exception {
        // given
        when(storeFeedRepository.read("store-1", "Action", "xml")).thenThrow(new FileNotFoundException());

        // when
        List<?> result = xmlLoader.load(TestXmlItem.class, "Item", supplierInfo("Action"), "store-1", 0);

        // then
        assertTrue(result.isEmpty());
        verify(storeFeedRepository).read("store-1", "Action", "xml");
    }

    @Test
    void xmlStoreOverloadReadsStoreScopedKey() throws Exception {
        // given
        when(storeFeedRepository.read("store-1", "Action", "xml")).thenReturn(new StringReader("<feed></feed>"));
        when(dataCleanup.run(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        xmlLoader.load(TestXmlItem.class, "Item", supplierInfo("Action"), "store-1", 0);

        // then
        verify(storeFeedRepository).read("store-1", "Action", "xml");
    }

    @Test
    void xmlStoreOverloadBumpsTaxonomyScoreByPenalty() throws Exception {
        // given
        when(storeFeedRepository.read("store-1", "Action", "xml")).thenReturn(new StringReader(XML_FEED));
        when(dataCorrection.run(any(InventoryItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dataCorrection.run(any(Taxonomy.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dataCleanup.run(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        xmlLoader.load(TestXmlItem.class, "Item", supplierInfo("Action"), "store-1", 1000);

        // then
        ArgumentCaptor<Taxonomy> captor = ArgumentCaptor.forClass(Taxonomy.class);
        verify(taxonomyCache).add(captor.capture());
        assertEquals(1005, captor.getValue().dataAccuracyScore());
    }

    @Test
    void csvGlobalOverloadDoesNotPenaliseTaxonomy() throws Exception {
        // given
        when(inventoryRepository.canRead("Action")).thenReturn(true);
        when(inventoryRepository.read("Action")).thenReturn(new StringReader("header\nrow"));

        CsvRowParser parser = mock(CsvRowParser.class);
        InventoryItem item = new InventoryItem("5900000000001", "MFN1", 10.0, "PLN", 1, 1, "Action", true, true, false);
        Taxonomy taxonomy = new Taxonomy("5900000000001", "MFN1", "Brand", "Name", "CPU", 5, null, null);
        when(parser.tryParse(any())).thenReturn(Optional.of(new ParsedRow(item, taxonomy)));
        when(dataCorrection.run(item)).thenReturn(item);
        when(dataCorrection.run(taxonomy)).thenReturn(taxonomy);
        when(dataCleanup.run(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        loader.fetch(parser, ';', "Action");

        // then
        ArgumentCaptor<Taxonomy> captor = ArgumentCaptor.forClass(Taxonomy.class);
        verify(taxonomyCache).add(captor.capture());
        assertEquals(5, captor.getValue().dataAccuracyScore());
    }

    @Test
    void xmlGlobalOverloadDoesNotPenaliseTaxonomy() throws Exception {
        // given
        when(inventoryRepository.read("Action", "xml")).thenReturn(new StringReader(XML_FEED));
        when(dataCorrection.run(any(InventoryItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dataCorrection.run(any(Taxonomy.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dataCleanup.run(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        xmlLoader.load(TestXmlItem.class, "Item", supplierInfo("Action"));

        // then
        ArgumentCaptor<Taxonomy> captor = ArgumentCaptor.forClass(Taxonomy.class);
        verify(taxonomyCache).add(captor.capture());
        assertEquals(5, captor.getValue().dataAccuracyScore());
    }
}
