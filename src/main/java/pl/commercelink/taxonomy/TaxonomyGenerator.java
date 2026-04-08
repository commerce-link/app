package pl.commercelink.taxonomy;

import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
public class TaxonomyGenerator {

    @Autowired
    private TaxonomyCache taxonomyCache;

    @Autowired
    private TaxonomyRepository taxonomyRepository;

    @SqsListener(
            value = "supplier-taxonomy-queue",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(String message) {
        taxonomyRepository.save(taxonomyCache.getTaxonomies());
    }
}
