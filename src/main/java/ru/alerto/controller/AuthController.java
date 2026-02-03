package ru.alerto.controller;

import ru.alerto.DAO.AccountsDAO;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthController {

    private final AccountsDAO accountsDAO;

    public AuthController(AccountsDAO accountsDAO) {
        this.accountsDAO = accountsDAO;
    }

    @GetMapping("/deleteCookie")
    public ResponseEntity<String> deleteCookie() {
        ResponseCookie cookie = AccountsDAO.expireCookie();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body("Cookie is deleted");
    }

    @PostMapping("/reg")
    public ResponseEntity<String> registration(@RequestBody String userBody) throws JSONException {
        ResponseCookie cookie = accountsDAO.returnCookie();
        JSONObject userJson = new JSONObject(userBody);

        userJson.put("cookie", cookie.getValue());

        if (accountsDAO.registration(userJson)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(generateResponseJson(userJson, cookie.getValue()));
        } else {
            return ResponseEntity.ok("Пользователь с таким логином уже существует");
        }
    }

    @PostMapping("/log")
    public ResponseEntity<String> login(@RequestBody String userBody) throws JSONException {
        ResponseCookie cookie = accountsDAO.returnCookie();
        JSONObject userJson = new JSONObject(userBody);
        userJson.put("cookie", cookie.getValue());

        if (accountsDAO.login(userJson)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(generateResponseJson(userJson, cookie.getValue()));
        } else {
            return ResponseEntity.ok("Неправильный логин или пароль");
        }
    }

    @PostMapping("/rec")
    public ResponseEntity<String> passwordRecovery(@RequestBody String userBody) throws JSONException {
        ResponseCookie cookie = accountsDAO.returnCookie();
        JSONObject userJson = new JSONObject(userBody);
        userJson.put("cookie", cookie.getValue());

        if (accountsDAO.recovery(userJson)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(generateResponseJson(userJson, cookie.getValue()));
        } else {
            return ResponseEntity.ok("Неправильный логин или пароль");
        }
    }

    @GetMapping(path = "/editFavourite")
    public ResponseEntity<String> editFavourites(@CookieValue(value = "JSESSIONID", required = false) String cookieValue,
                                                 @RequestParam(value = "id", required = false) Integer id) {
        if (cookieValue == null || cookieValue.isEmpty()) {
            return ResponseEntity.ok("local");
        }
        return ResponseEntity.ok(accountsDAO.lists(cookieValue, id, "favorites"));
    }

    @GetMapping(path = "/editComparison")
    public ResponseEntity<String> editComparisons(@CookieValue(value = "JSESSIONID", required = false) String cookieValue,
                                                  @RequestParam(value = "id", required = false) Integer id) {
        if (cookieValue == null || cookieValue.isEmpty()) {
            return ResponseEntity.ok("local");
        }
        return ResponseEntity.ok(accountsDAO.lists(cookieValue, id, "comparisons"));
    }

    @GetMapping("/checkCookie")
    public ResponseEntity<String> checkCookie(@CookieValue(value = "JSESSIONID", required = false) String cookieValue) {
        if (cookieValue != null && !cookieValue.isEmpty()) {
            JSONObject result = accountsDAO.checkCookie(cookieValue);
            return ResponseEntity.ok(result.toString());
        }
        return ResponseEntity.ok("local");
    }

    private String generateResponseJson(JSONObject userJson, String cookieValue) throws JSONException {
        String login = userJson.getString("login");
        String favouritesStr = accountsDAO.lists(cookieValue, null, "favorites");

        return new JSONObject()
                .put("message", login)
                .put("userData", new JSONObject()
                        .put("login", login)
                        .put("favourites", new JSONArray(favouritesStr)))
                .toString();
    }
}