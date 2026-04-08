package pl.commercelink.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.commercelink.financials.GoogleOfflineConversionsExport;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDate;

@RestController
@RequestMapping("/Store/{storeId}/Reporting")
public class GoogleAdsUploadController {

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private GoogleOfflineConversionsExport googleOfflineConversionsExport;

    @GetMapping(value = "/Google/Conversions/{token}", produces = "text/csv")
    public ResponseEntity<String> export(@PathVariable String storeId, @PathVariable String token) {
        Store store = storesRepository.findById(storeId);
        if (!store.isGoogleAdsConversionsEnabled(token))  {
            return ResponseEntity.status(403).build();
        }

        LocalDate yesterday = LocalDate.now().minusDays(1);
        String csv = googleOfflineConversionsExport.run(storeId, yesterday, yesterday);
        String filename = "offline-conversions-" + yesterday + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(csv);
    }

}
