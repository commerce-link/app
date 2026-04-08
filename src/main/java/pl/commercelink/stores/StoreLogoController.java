package pl.commercelink.stores;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class StoreLogoController {

    @Autowired
    StoresRepository storesRepository;

    @GetMapping("/StoreLogo/{storeId}")
    public ResponseEntity<?> getStoreLogo(@PathVariable String storeId) {
        String location = storesRepository.findLogoLocationWithExtension(storeId);
        try {
            byte[] data = storesRepository.getLogoResponse(storeId);
            if (data == null) {
                return ResponseEntity.notFound().build();
            }
            ByteArrayResource resource = new ByteArrayResource(data);
            String fileName = location.substring(location.lastIndexOf('/') + 1);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                    .contentType(storesRepository.getMediaType(fileName))
                    .contentLength(data.length)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
