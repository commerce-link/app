package pl.commercelink.inventory.supplier;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.InventoryRepository;
import pl.commercelink.inventory.supplier.api.ParsedRow;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.XmlItem;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.util.LinkedList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class XmlProductFeedLoader {

    private final InventoryRepository inventoryRepository;
    private final StoreFeedRepository storeFeedRepository;
    private final DataCleanup dataCleanup;
    private final FeedRowProcessor feedRowProcessor;

    public <V extends XmlItem> List<InventoryItem> load(Class<V> itemClass, String itemElementName, SupplierInfo supplierInfo) {
        String supplierName = supplierInfo.name();
        try (Reader reader = inventoryRepository.read(supplierName, "xml")) {
            return parse(itemClass, itemElementName, supplierInfo, reader, 0);
        } catch (JAXBException | XMLStreamException e) {
            log.error("Skipping feed for supplier {}: content cannot be deserialized (malformed or rejected); previous inventory is kept", supplierName, e);
            return new LinkedList<>();
        } catch (NoSuchBucketException | NoSuchKeyException | FileNotFoundException e) {
            log.warn("Skipping feed for supplier {}: feed file not found or unreadable; previous inventory is kept", supplierName);
            return new LinkedList<>();
        } catch (IOException e) {
            log.error("Skipping feed for supplier {}: feed file could not be read; previous inventory is kept", supplierName, e);
            return new LinkedList<>();
        }
    }

    public <V extends XmlItem> List<InventoryItem> load(Class<V> itemClass, String itemElementName, SupplierInfo supplierInfo, String storeId, int taxonomyPenalty) {
        String supplierName = supplierInfo.name();
        try (Reader reader = storeFeedRepository.read(storeId, supplierName, "xml")) {
            return parse(itemClass, itemElementName, supplierInfo, reader, taxonomyPenalty);
        } catch (JAXBException | XMLStreamException e) {
            log.error("Skipping store feed for {}/{}: content cannot be deserialized (malformed or rejected)", storeId, supplierName, e);
            return new LinkedList<>();
        } catch (NoSuchBucketException | NoSuchKeyException | FileNotFoundException e) {
            log.warn("Skipping store feed for {}/{}: feed file not found or unreadable", storeId, supplierName);
            return new LinkedList<>();
        } catch (IOException e) {
            log.error("Skipping store feed for {}/{}: feed file could not be read", storeId, supplierName, e);
            return new LinkedList<>();
        }
    }

    private <V extends XmlItem> List<InventoryItem> parse(Class<V> itemClass, String itemElementName, SupplierInfo supplierInfo, Reader reader, int taxonomyPenalty)
            throws JAXBException, XMLStreamException {
        XMLInputFactory xif = XMLInputFactory.newFactory();
        // Feeds are attacker-influenceable, so reject DTDs (and thus all internal/external entities)
        // and external entity resolution — this closes XXE, SSRF via external entities, and
        // entity-expansion ("billion laughs") DoS.
        xif.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        xif.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        XMLStreamReader xsr = xif.createXMLStreamReader(reader);

        JAXBContext jaxbContext = JAXBContext.newInstance(itemClass);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

        List<InventoryItem> res = new LinkedList<>();
        FeedParseStats stats = new FeedParseStats(supplierInfo.name());

        try {
            while (xsr.hasNext()) {
                if (xsr.isStartElement() && xsr.getLocalName().equals(itemElementName)) {
                    V xmlItem = unmarshaller.unmarshal(xsr, itemClass).getValue();

                    ParsedRow parsed = xmlItem.toParsedRow(supplierInfo);
                    feedRowProcessor.process(parsed, taxonomyPenalty, stats)
                            .ifPresent(res::add);
                } else {
                    xsr.next();
                }
            }
        } finally {
            xsr.close();
        }

        stats.log();
        return dataCleanup.run(res);
    }

}
