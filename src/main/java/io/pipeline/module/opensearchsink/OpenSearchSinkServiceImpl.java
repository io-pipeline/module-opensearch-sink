package io.pipeline.module.opensearchsink;

import io.pipeline.api.annotation.PipelineAutoRegister;
import io.pipeline.common.service.SchemaExtractorService;
import io.pipeline.data.module.*;
import io.pipeline.module.opensearchsink.config.opensearch.BatchOptions;
import io.pipeline.opensearch.v1.*;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@GrpcService
@PipelineAutoRegister(moduleType = "sink", useHttpPort = true)
public class OpenSearchSinkServiceImpl implements PipeStepProcessor {

    private static final Logger LOG = Logger.getLogger(OpenSearchSinkServiceImpl.class);

    @Inject
    SchemaExtractorService schemaExtractorService;

    @Inject
    MutinyOpenSearchManagerServiceGrpc.MutinyOpenSearchManagerServiceStub openSearchManager;

    @Override
    public Uni<ModuleProcessResponse> processData(ModuleProcessRequest request) {
        if (!request.hasDocument()) {
            return Uni.createFrom().item(ModuleProcessResponse.newBuilder()
                .setSuccess(true)
                .addProcessorLogs("No document to process.")
                .build());
        }

        OpenSearchDocument osDoc = convertToOpenSearchDocument(request.getDocument());
        String indexName = "pipeline-" + request.getDocument().getDocumentType().toLowerCase();

        return openSearchManager.indexDocument(IndexDocumentRequest.newBuilder()
                .setIndexName(indexName)
                .setDocument(osDoc)
                .build())
            .map(response -> ModuleProcessResponse.newBuilder()
                .setOutputDoc(request.getDocument())
                .setSuccess(response.getSuccess())
                .addProcessorLogs(response.getMessage())
                .build());
    }

    private OpenSearchDocument convertToOpenSearchDocument(io.pipeline.data.model.PipeDoc pipeDoc) {
        OpenSearchDocument.Builder builder = OpenSearchDocument.newBuilder()
            .setOriginalDocId(pipeDoc.getId())
            .setDocType(pipeDoc.getDocumentType())
            //.setCreatedBy(pipeDoc.getCreatedBy())
            //.setCreatedAt(pipeDoc.getCreatedDate())
            .setLastModifiedAt(pipeDoc.getLastModifiedDate());

        if (pipeDoc.hasTitle()) builder.setTitle(pipeDoc.getTitle());
        if (pipeDoc.hasBody()) builder.setBody(pipeDoc.getBody());
        if (pipeDoc.getKeywordsCount() > 0) builder.addAllTags(pipeDoc.getKeywordsList());

        // Convert embeddings
        for (var result : pipeDoc.getSemanticResultsList()) {
            for (var chunk : result.getChunksList()) {
                if (chunk.hasEmbeddingInfo() && chunk.getEmbeddingInfo().getVectorCount() > 0) {
                    builder.addEmbeddings(Embedding.newBuilder()
                        .addAllVector(chunk.getEmbeddingInfo().getVectorList())
                        .setSourceText(chunk.getEmbeddingInfo().getTextContent())
                        .setChunkConfigId(result.getChunkConfigId())
                        .setEmbeddingId(result.getEmbeddingConfigId())
                        .setIsPrimary(result.getChunkConfigId().contains("title"))
                        .build());
                }
            }
        }

        return builder.build();
    }

    @Override
    public Uni<ServiceRegistrationResponse> getServiceRegistration(RegistrationRequest request) {
        ServiceRegistrationResponse.Builder responseBuilder = ServiceRegistrationResponse.newBuilder()
            .setModuleName("opensearch-sink")
            .setCapabilities(Capabilities.newBuilder().addTypes(CapabilityType.SINK).build())
            .setHealthCheckPassed(true)
            .setHealthCheckMessage("Service is running.");

        schemaExtractorService.extractSchemaForClass(BatchOptions.class)
            .ifPresent(responseBuilder::setJsonConfigSchema);

        return Uni.createFrom().item(responseBuilder.build());
    }

    @Override
    public Uni<ModuleProcessResponse> testProcessData(ModuleProcessRequest request) {
        return processData(request);
    }
}