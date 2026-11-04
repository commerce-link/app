package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.context.annotation.Profile;
import pl.commercelink.carriers.Carrier;
import pl.commercelink.carriers.CarrierDictionary;
import pl.commercelink.carriers.CarrierMapping;

import java.util.Arrays;
import java.util.List;

/**
 * Local carriers with public codes only (Allegro GET /order/carriers ids, Furgonetka service codes, words of Allegro
 * delivery method names). Production carriers and dictionaries are loaded from outside this repository.
 */
@Profile("localdev")
@ChangeUnit(id = "V025-local-development-carriers-seed", order = "025", author = "commercelink")
public class V025_LocalDevelopmentCarriersSeed {

    private static final List<String> CARRIERS = List.of("InPost", "InPost Kurier", "DPD", "DHL", "Poczta Polska",
            "Orlen", "UPS", "GLS", "FedEx", "DB Schenker", "Raben", "Allegro One", "Meest", "Ambro Express", "X-press");

    private final AmazonDynamoDB dynamoDB;

    public V025_LocalDevelopmentCarriersSeed(AmazonDynamoDB dynamoDB) {
        this.dynamoDB = dynamoDB;
    }

    @Execution
    public void seed() {
        DynamoDBMapper mapper = new DynamoDBMapper(dynamoDB);
        CARRIERS.forEach(name -> mapper.save(new Carrier(name, null)));
        mapper.save(dictionary(CarrierDictionary.marketplaceOrderCarrierTexts("allegro"),
                "Paczkomat=InPost", "InPost=InPost", "Pocztex=Poczta Polska", "Poczta=Poczta Polska", "Orlen=Orlen",
                "DHL=DHL", "DPD=DPD", "UPS=UPS", "GLS=GLS", "FedEx=FedEx", "Schenker=DB Schenker", "Allegro One=Allegro One"));
        mapper.save(dictionary(CarrierDictionary.marketplaceStatusCarrierCodes("allegro"),
                "INPOST=InPost", "INPOST=InPost Kurier", "DHL=DHL", "DPD=DPD", "POCZTA_POLSKA=Poczta Polska",
                "ORLEN=Orlen", "UPS=UPS", "GLS=GLS", "FEDEX=FedEx", "DB_SCHENKER=DB Schenker", "ALLEGRO=Allegro One",
                "RABEN=Raben", "MEEST=Meest", "AMBRO_EXPRESS=Ambro Express"));
        mapper.save(dictionary(CarrierDictionary.shippingLabelCarrierCodes("furgonetka"),
                "inpost=InPost", "dpd=DPD", "dhl=DHL", "poczta=Poczta Polska", "orlen=Orlen", "ups=UPS", "gls=GLS",
                "fedex=FedEx", "meest=Meest", "ambroexpress=Ambro Express", "xpress=X-press"));
        mapper.save(dictionary(CarrierDictionary.shippingLabelCarrierCodes("allegro"),
                "INPOST=InPost", "DHL=DHL", "DPD=DPD", "POCZTA_POLSKA=Poczta Polska", "ORLEN=Orlen", "UPS=UPS",
                "GLS=GLS", "FEDEX=FedEx", "DB_SCHENKER=DB Schenker", "ALLEGRO=Allegro One", "RABEN=Raben",
                "MEEST=Meest", "AMBRO_EXPRESS=Ambro Express"));
    }

    @RollbackExecution
    public void rollback() {
    }

    private static CarrierDictionary dictionary(String key, String... pairs) {
        return new CarrierDictionary(key, Arrays.stream(pairs)
                .map(pair -> pair.split("=", 2))
                .map(parts -> new CarrierMapping(parts[0], parts[1]))
                .toList());
    }
}
