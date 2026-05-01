package pl.commercelink.inventory.supplier;

import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.InventoryRepository;
import pl.commercelink.inventory.supplier.api.ParsedRow;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.XmlItem;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;

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

@Component
public class XmlProductFeedLoader {

    private final InventoryRepository inventoryRepository;
    private final DataCorrection dataCorrection;
    private final DataCleanup dataCleanup;
    private final TaxonomyCache taxonomyCache;

    XmlProductFeedLoader(InventoryRepository inventoryRepository, DataCorrection dataCorrection, DataCleanup dataCleanup, TaxonomyCache taxonomyCache) {
        this.inventoryRepository = inventoryRepository;
        this.dataCorrection = dataCorrection;
        this.dataCleanup = dataCleanup;
        this.taxonomyCache = taxonomyCache;
    }

    public <V extends XmlItem> List<InventoryItem> load(Class<V> itemClass, String itemElementName, SupplierInfo supplierInfo) {
        String supplierName = supplierInfo.name();
        try (Reader reader = inventoryRepository.read(supplierName, "xml")) {
            XMLInputFactory xif = XMLInputFactory.newFactory();
            XMLStreamReader xsr = xif.createXMLStreamReader(reader);

            JAXBContext jaxbContext = JAXBContext.newInstance(itemClass);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

            List<InventoryItem> res = new LinkedList<>();

            try {
                while (xsr.hasNext()) {
                    if (xsr.isStartElement() && xsr.getLocalName().equals(itemElementName)) {
                        V xmlItem = unmarshaller.unmarshal(xsr, itemClass).getValue();

                        ParsedRow parsed = xmlItem.toParsedRow(supplierInfo);
                        InventoryItem inventoryItem = dataCorrection.run(parsed.item());
                        Taxonomy taxonomy = dataCorrection.run(parsed.taxonomy());

                        if (taxonomy.isProcessable() && inventoryItem.isSellable()) {
                            taxonomyCache.add(taxonomy);
                            res.add(inventoryItem);
                        }
                    } else {
                        xsr.next();
                    }
                }
            } finally {
                xsr.close();
            }

            return dataCleanup.run(res);

        } catch (JAXBException | XMLStreamException e) {
            System.out.println("Skipping feed file: " + supplierName + " as it can't be deserialized.");
            return new LinkedList<>();
        } catch (NoSuchBucketException | NoSuchKeyException | FileNotFoundException e) {
            System.out.println("Skipping feed file: " + supplierName + " as it does not exists or is unreadable.");
            return new LinkedList<>();
        } catch (IOException e) {
            System.out.println("Skipping feed file: " + supplierName + " as it can't be read.");
            return new LinkedList<>();
        }
    }

}
