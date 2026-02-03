package ru.alerto.controller;

import ru.alerto.services.ElasticsearchService;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RestController
@Slf4j
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class ProductController {

    private final ElasticsearchService elasticsearchService;
    static List<String> indexNamesList = new ArrayList<>(Arrays.asList("Laptops", "Phones"));

    @Autowired
    public ProductController(ElasticsearchService elasticsearchService) {
        this.elasticsearchService = elasticsearchService;
    }

    @GetMapping("/search-all")
    public SearchResponse searchAll() {
        try {
            return elasticsearchService.searchAllDocuments("laptops");
        } catch (IOException e) {
            log.error("Error during search-all: {}", e.getMessage(), e);
            return null;
        }
    }

    @GetMapping("/products")
    public ResponseEntity<String> searchProductsByTitle(@RequestParam(value = "id", required = false) Integer id) {
        // Просто возвращаем тело, заголовки Spring добавит сам
        return ResponseEntity.ok(String.valueOf(elasticsearchService.searchProductsById(id, indexNamesList)));
    }

    @GetMapping("/products/getForSearch")
    public ResponseEntity<String> sendProductsForSearch() throws IOException {
        return ResponseEntity.ok(String.valueOf(elasticsearchService.getProductsForSearch(indexNamesList)));
    }

    @PostMapping("/favourites/get")
    public ResponseEntity<String> sendFavourites(@RequestBody String ids) {
        JSONArray transformedResult = new JSONArray();
        JSONObject requestJson = new JSONObject(ids); // Лучше парсить один раз

        if (requestJson.has("favourites")) {
            for (Object id : requestJson.getJSONArray("favourites")) {
                try {
                    transformedResult.put(elasticsearchService.searchProductsById(Integer.parseInt(id.toString()), indexNamesList));
                } catch (Exception ignored) {}
            }
        }
        return ResponseEntity.ok(transformedResult.toString());
    }

    @GetMapping("/products/ForComparison/{type}/{property}")
    public ResponseEntity<Resource> getForComparison(@PathVariable String property, @PathVariable String type) {
        // Тут оставляем как есть, Resource требует особого подхода, но без кастомных заголовков CORS
        return ResponseEntity.ok().body(new ClassPathResource(type + "/" + property + ".json"));
    }

    @GetMapping("/products/tops")
    public ResponseEntity<String> getTop() {
        JSONArray req = new JSONArray();
        List<String> typeTopList = List.of("Топ телефонов до 20 000", "Топ телефонов-флагманов", "Топ ноутбуков до 50 000");

        for (String title : typeTopList) {
            List<Integer> idList = new ArrayList<>();
            // Ваша логика switch...
            switch (title) {
                case "Топ телефонов до 20 000" -> idList = Arrays.asList(133, 70, 150, 262, 108, 244, 242, 24, 166);
                case "Топ телефонов-флагманов" -> idList = Arrays.asList(4, 143, 9, 43, 170, 74, 265, 150);
                case "Топ ноутбуков до 50 000" -> idList = Arrays.asList(10003, 10007, 10008, 10016, 10017, 10020);
            }

            JSONObject oneObject = new JSONObject();
            oneObject.put("title", title);
            for (Integer id : idList) {
                try {
                    oneObject.append("products", elasticsearchService.searchProductsById(id, indexNamesList));
                } catch (Exception ignored) {}
            }
            req.put(oneObject);
        }
        return ResponseEntity.ok(req.toString());
    }

    @PostMapping("products/search/withFilter")
    public ResponseEntity<String> sendSearchWithFilter2(@RequestBody String stringRequest) throws IOException {
        JSONObject js = elasticsearchService.getSearchObjectsWithFilter(stringRequest);
        return ResponseEntity.ok(js.toString());
    }
}