package ru.alerto.services;

import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
@Slf4j
public class ElasticsearchService {

    private final RestHighLevelClient elasticsearchClient;

    @Autowired
    public ElasticsearchService(RestHighLevelClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    public SearchResponse searchAllDocuments(String indexName) throws IOException {
        SearchRequest searchRequest = new SearchRequest(indexName);

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.matchAllQuery());

        searchRequest.source(sourceBuilder);

        return elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);
    }

    public JSONObject searchProductsById(Integer id, List<String> indexNamesList) {
        JSONObject result = new JSONObject();

        for (String indexName : indexNamesList) {
            SearchRequest searchRequest = new SearchRequest(indexName.toLowerCase(Locale.ROOT));
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(QueryBuilders.matchQuery("id", id));
            searchRequest.source(searchSourceBuilder);

            try {
                SearchResponse searchResponse = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);

                for (SearchHit hit : searchResponse.getHits().getHits()) {
                    result = new JSONObject("{" + new JSONObject(hit.getSourceAsString()).getString("log_entry").substring(52).replaceAll("=>", ":")).put("type", indexName);
                }
            } catch (IOException e) {
                log.error("Ошибка при поиске всех документов в индексе {}: {}", indexName, e.getMessage(), e);
            }
        }

        return result;
    }

    public List<String> getProductsForSearch(List<String> indexNamesList) throws IOException {
        List<String> titles = new ArrayList<>();

        for (String indexName : indexNamesList) {
            SearchRequest searchRequest = new SearchRequest(indexName.toLowerCase(Locale.ROOT));
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.fetchSource("title", null);
            searchRequest.source(searchSourceBuilder);
            searchSourceBuilder.size(1000);
            SearchResponse searchResponse = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);

            for (SearchHit hit : searchResponse.getHits().getHits()) {
                Map<String, Object> sourceAsMap = hit.getSourceAsMap();
                String title = (String) sourceAsMap.get("title");
                if (title != null) {
                    titles.add(title);
                }
            }
        }

        return titles;
    }

    public JSONObject getSearchObjectsWithFilter(String stringRequest) throws JSONException, IOException {
        JSONArray result = new JSONArray();
        JSONObject jsonRequest = new JSONObject(stringRequest);
        List<String> indexNamesList;

        if(jsonRequest.getString("type").equals("none")){
            indexNamesList = new ArrayList<>(Arrays.asList("Phones", "Laptops"));
        }else{indexNamesList = new ArrayList<>(List.of(jsonRequest.getString("type")));}

        for (String indexName : indexNamesList) {
            SearchRequest searchRequest = new SearchRequest(indexName.toLowerCase(Locale.ROOT));
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

            QueryBuilder fuzzyQuery = QueryBuilders.multiMatchQuery(jsonRequest.getString("search"), "title")
                    .fuzziness(Fuzziness.AUTO)
                    .maxExpansions(10);

            searchSourceBuilder.query(fuzzyQuery);
            searchRequest.source(searchSourceBuilder);

            SearchResponse searchResponse = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);

            for (SearchHit hit : searchResponse.getHits().getHits()) {
                result.put(new JSONObject("{" + new JSONObject(hit.getSourceAsString()).getString("log_entry").substring(52).replaceAll("=>", ":")).put("type", jsonRequest.getString("type")));
            }
        }
        JSONArray filteredArray = new JSONArray();
        result.forEach(type -> {
            if (type instanceof JSONObject typeObject) {
                if (jsonRequest.getString("type").equals(typeObject.getString("type")) || jsonRequest.getString("type").equals("none")) {
                    if (matchesFilters(typeObject.getJSONObject("property"), jsonRequest.getJSONArray("filters"))) {
                        filteredArray.put(typeObject);
                    }
                }
            }
        });
        JSONObject js = new JSONObject();
        JSONObject filteredObj = new JSONObject();
        Map<String, Map<String, Integer>> filterCounts = new HashMap<>();

        filteredArray.forEach(property -> {
            if (property instanceof JSONObject propertyObject) {
                JSONObject propertyDetails = propertyObject.getJSONObject("property");
                try {
                    String[] keys = JSONObject.getNames(propertyDetails);
                    if (keys != null) {
                        for (String key : keys) {
                            js.append(key, propertyDetails.getString(key));

                            String value = propertyDetails.getString(key);

                            filterCounts.computeIfAbsent(key, k -> new HashMap<>())
                                    .compute(value, (k, v) -> v == null ? 1 : v + 1);
                        }
                    }

                } catch (JSONException ignored) {
                }
            }
        });

        JSONObject finalFilters = new JSONObject();
        for (Map.Entry<String, Map<String, Integer>> entry : filterCounts.entrySet()) {
            JSONObject valueObject = new JSONObject();
            for (Map.Entry<String, Integer> valueEntry : entry.getValue().entrySet()) {
                valueObject.put(valueEntry.getKey(), valueEntry.getValue());
            }
            finalFilters.put(entry.getKey(), valueObject);
        }

        filteredObj.put("filters", finalFilters);
        filteredObj.put("products", filteredArray);
        return filteredObj;
    }


    private static boolean matchesFilters(JSONObject properties, JSONArray filters) {
        for (int i = 0; i < filters.length(); i++) {
            JSONArray filter = filters.getJSONArray(i);
            String propertyName = filter.getString(0);
            String propertyValue = filter.getString(1);

            if (!properties.has(propertyName) || !properties.getString(propertyName).contains(propertyValue)) {
                return false;
            }
        }
        return true;
    }
}