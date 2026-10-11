package io.github.notlhos.research.application.integration.milvus;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.notlhos.research.application.config.ResearchProperties;
import io.github.notlhos.research.application.integration.milvus.model.ScoredPaper;
import io.github.notlhos.research.application.kafka.model.EmbeddingVectorDto;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
class MilvusPaperVectorStore implements PaperVectorStore, InitializingBean, DisposableBean {

    private static final String ID_FIELD = "arxiv_id";

    private static final String VECTOR_FIELD = "embedding";

    private static final int ID_MAX_LENGTH = 64;

    private static final Map<String, Object> HNSW_PARAMS = Map.of("M", 16, "efConstruction", 128);

    private final ResearchProperties.Milvus config;

    private final MilvusClientV2 client;

    MilvusPaperVectorStore(ResearchProperties properties) {
        this.config = properties.milvus();
        this.client = new MilvusClientV2(ConnectConfig.builder().uri(config.uri()).build());
    }

    @Override
    public void afterPropertiesSet() {
        boolean exists = client.hasCollection(HasCollectionReq.builder().collectionName(config.collection()).build());
        if (!exists) {
            createCollection();
        }
        client.loadCollection(LoadCollectionReq.builder().collectionName(config.collection()).build());
        log.info("Milvus collection {} ready", config.collection());
    }

    @Override
    public void upsert(List<EmbeddingVectorDto> vectors) {
        List<JsonObject> rows = vectors.stream().map(MilvusPaperVectorStore::toRow).toList();
        client.upsert(UpsertReq.builder().collectionName(config.collection()).data(rows).build());
    }

    @Override
    public List<ScoredPaper> search(float[] query, Collection<String> candidateIds, int limit) {
        if (candidateIds.isEmpty()) {
            return List.of();
        }
        SearchResp response = client.search(SearchReq.builder()
                .collectionName(config.collection())
                .annsField(VECTOR_FIELD)
                .data(List.of(new FloatVec(query)))
                .filter(ID_FIELD + " in {ids}")
                .filterTemplateValues(Map.of("ids", List.copyOf(candidateIds)))
                .topK(limit)
                .outputFields(List.of(ID_FIELD))
                .consistencyLevel(ConsistencyLevel.STRONG)
                .build());
        return response.getSearchResults().getFirst().stream()
                .map(hit -> new ScoredPaper(String.valueOf(hit.getId()), hit.getScore()))
                .toList();
    }

    @Override
    public void destroy() {
        client.close();
    }

    /* ***** Private Methods ***** */

    private void createCollection() {
        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder().build();
        schema.addField(AddFieldReq.builder()
                .fieldName(ID_FIELD)
                .dataType(DataType.VarChar)
                .maxLength(ID_MAX_LENGTH)
                .isPrimaryKey(true)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName(VECTOR_FIELD)
                .dataType(DataType.FloatVector)
                .dimension(config.dimension())
                .build());
        IndexParam index = IndexParam.builder()
                .fieldName(VECTOR_FIELD)
                .indexType(IndexParam.IndexType.HNSW)
                .metricType(IndexParam.MetricType.COSINE)
                .extraParams(HNSW_PARAMS)
                .build();
        client.createCollection(CreateCollectionReq.builder()
                .collectionName(config.collection())
                .collectionSchema(schema)
                .indexParams(List.of(index))
                .build());
    }

    private static JsonObject toRow(EmbeddingVectorDto vector) {
        JsonArray values = new JsonArray(vector.vector().length);
        for (float value : vector.vector()) {
            values.add(value);
        }
        JsonObject row = new JsonObject();
        row.addProperty(ID_FIELD, vector.id());
        row.add(VECTOR_FIELD, values);
        return row;
    }

}
