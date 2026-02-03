package ru.alerto.DAO;

import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

@Repository
@Slf4j
public class ProductsDAO {

    private final MongoTemplate mongoTemplate;
    private static final List<String> COLLECTION_NAMES = Arrays.asList("Phones", "Laptops");

    @Autowired
    public ProductsDAO(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public long numberOfProducts() {
        long count = 0;
        for (String collection : COLLECTION_NAMES) {
            count += mongoTemplate.getCollection(collection).countDocuments();
        }
        return count;
    }

    public List<String> getProductsForSearch() {
        List<String> titles = new LinkedList<>();
        Query query = new Query();
        query.fields().include("title");

        for (String collection : COLLECTION_NAMES) {
            List<Document> docs = mongoTemplate.find(query, Document.class, collection);
            for (Document doc : docs) {
                titles.add(doc.getString("title"));
            }
        }
        return titles;
    }

    public JSONObject getById(int id) {
        Query query = new Query(Criteria.where("_id").is(String.valueOf(id)));

        for (String collection : COLLECTION_NAMES) {
            Document doc = mongoTemplate.findOne(query, Document.class, collection);
            if (doc != null) {
                return new JSONObject(doc.toJson()).put("type", collection);
            }
        }
        log.warn("Product with id {} not found", id);
        return new JSONObject();
    }

    public JSONArray getFavourites(List<Object> idList) {
        List<String> stringIds = idList.stream().map(Object::toString).collect(Collectors.toList());
        Query query = new Query(Criteria.where("_id").in(stringIds));

        return getJSONArrayFromQuery(query);
    }

    public JSONArray getAllObjects(int limit, int page) {
        List<Document> allDocs = new ArrayList<>();
        Query query = new Query().skip((long) limit * (page - 1)).limit(limit);

        for (String collection : COLLECTION_NAMES) {
            List<Document> docs = mongoTemplate.find(query, Document.class, collection);
            for (Document doc : docs) {
                doc.append("type", collection);
                allDocs.add(doc);
            }
        }
        Collections.shuffle(allDocs);

        JSONArray jsonArray = new JSONArray();
        for (Document doc : allDocs) {
            jsonArray.put(new JSONObject(doc.toJson()));
        }
        return jsonArray;
    }

    public JSONArray getSearchObjects(String title) {
        JSONArray jsonArray = new JSONArray();
        String[] keywords = title.split("\\s+");

        for (int i = keywords.length; i > 0; i--) {
            String keyword = String.join(" ", Arrays.copyOfRange(keywords, 0, i));
            String regex = keyword + "\\b"; // Граница слова

            Query queryPhones = new Query(Criteria.where("description").regex(regex, "i"));
            List<Document> phones = mongoTemplate.find(queryPhones, Document.class, "Phones");
            mergeJSONArrayWithoutDuplicates(jsonArray, docsToJSONArray(phones), "Phones");

            Query queryLaptops = new Query(Criteria.where("title").regex(regex, "i"));
            List<Document> laptops = mongoTemplate.find(queryLaptops, Document.class, "Laptops");
            mergeJSONArrayWithoutDuplicates(jsonArray, docsToJSONArray(laptops), "Laptops");
        }
        return jsonArray;
    }

    private JSONArray getJSONArrayFromQuery(Query query) {
        JSONArray jsonArray = new JSONArray();
        for (String collection : COLLECTION_NAMES) {
            List<Document> docs = mongoTemplate.find(query, Document.class, collection);
            mergeJSONArray(jsonArray, docsToJSONArray(docs), collection);
        }
        return jsonArray;
    }

    private JSONArray docsToJSONArray(List<Document> docs) {
        JSONArray jsonArray = new JSONArray();
        for (Document doc : docs) {
            jsonArray.put(new JSONObject(doc.toJson()));
        }
        return jsonArray;
    }

    private void mergeJSONArray(JSONArray target, JSONArray source, String type) {
        for (int i = 0; i < source.length(); i++) {
            target.put(source.getJSONObject(i).put("type", type));
        }
    }

    private void mergeJSONArrayWithoutDuplicates(JSONArray target, JSONArray source, String type) {
        for (int i = 0; i < source.length(); i++) {
            JSONObject newObj = source.getJSONObject(i);
            if (!containsObject(target, newObj)) {
                target.put(newObj.put("type", type));
            }
        }
    }

    private boolean containsObject(JSONArray array, JSONObject obj) {
        String objId = obj.getString("_id");
        for (int i = 0; i < array.length(); i++) {
            if (array.getJSONObject(i).getString("_id").equals(objId)) {
                return true;
            }
        }
        return false;
    }

    public JSONObject getSearchObjectsWithFilter(String stringRequest) {
        JSONObject jsonRequest = new JSONObject(stringRequest);
        String searchTitle = jsonRequest.getString("search");

        JSONArray products = getSearchObjects(searchTitle);

        JSONArray filteredArray = new JSONArray();

        products.forEach(item -> {
            if (item instanceof JSONObject typeObject) {
                String reqType = jsonRequest.getString("type");
                String objType = typeObject.getString("type");

                if (reqType.equals(objType) || reqType.equals("none")) {
                    if (matchesFilters(typeObject.getJSONObject("property"), jsonRequest.getJSONArray("filters"))) {
                        filteredArray.put(typeObject);
                    }
                }
            }
        });

        return buildResponseWithFilters(filteredArray);
    }

    private boolean matchesFilters(JSONObject properties, JSONArray filters) {
        for (int i = 0; i < filters.length(); i++) {
            JSONArray filter = filters.getJSONArray(i);
            String key = filter.getString(0);
            String val = filter.getString(1);
            if (!properties.has(key) || !properties.getString(key).contains(val)) {
                return false;
            }
        }
        return true;
    }

    private JSONObject buildResponseWithFilters(JSONArray filteredArray) {
        JSONObject response = new JSONObject();
        Map<String, Map<String, Integer>> filterCounts = new HashMap<>();

        filteredArray.forEach(item -> {
            if (item instanceof JSONObject prod) {
                JSONObject props = prod.getJSONObject("property");
                Iterator<String> keys = props.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    String value = props.getString(key);
                    filterCounts.computeIfAbsent(key, k -> new HashMap<>())
                            .merge(value, 1, Integer::sum);
                }
            }
        });

        JSONObject finalFilters = new JSONObject();
        filterCounts.forEach((key, valMap) -> finalFilters.put(key, new JSONObject(valMap)));

        response.put("filters", finalFilters);
        response.put("products", filteredArray);
        return response;
    }
}