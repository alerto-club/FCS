package ru.alerto.controller;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
public class SearchDictionaryController {

    @GetMapping("/API/getForSearch")
    public ResponseEntity<String> getForSearch(@RequestParam(value = "title", required = false) String title) throws JSONException, IOException {

        ClassPathResource file =  new ClassPathResource("API.json");
        String jsonString = StreamUtils.copyToString(file.getInputStream(), StandardCharsets.UTF_8);
        JSONArray jsonArray = new JSONArray(jsonString);

        String[] searchWords = title.split("\\s+");
        StringBuilder result = new StringBuilder();

        for (String searchWord : searchWords) {
            boolean found = false;
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                for (String key : jsonObject.keySet()) {
                    JSONArray values = jsonObject.getJSONArray(key);
                    for (int j = 0; j < values.length(); j++) {
                        String value = values.getString(j);
                        if (value.equalsIgnoreCase(searchWord)) {
                            result.append(key).append(" ");
                            found = true;
                        }
                    }
                }
            }
            if (!found) {
                result.append(searchWord).append(" ");
            }
        }
        return new ResponseEntity<>(result.toString().trim(), HttpStatus.OK);
    }
}
