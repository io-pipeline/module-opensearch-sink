package io.pipeline.module.opensearchsink.service;

import io.pipeline.module.opensearchsink.opensearch.ReactiveOpenSearchClient;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;

import java.util.List;

@ApplicationScoped
public class OpenSearchRepository {

    private final ReactiveOpenSearchClient reactiveClient;

    @Inject
    public OpenSearchRepository(ReactiveOpenSearchClient reactiveClient) {
        this.reactiveClient = reactiveClient;
    }

    public Uni<BulkResponse> bulk(List<BulkOperation> operations) {
        if (operations == null || operations.isEmpty()) {
            return Uni.createFrom().item(BulkResponse.of(b -> b.items(List.of()).errors(false).took(0)));
        }
        BulkRequest bulkRequest = new BulkRequest.Builder().operations(operations).build();
        // Use the reactive client which handles the blocking call and checked exception
        return reactiveClient.bulk(bulkRequest);
    }
}
