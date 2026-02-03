package ru.alerto.DAO;

import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Repository;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;

@Repository
@Slf4j
public class AccountsDAO {

    private static final String COLLECTION_NAME = "Users";
    private final MongoTemplate mongoTemplate;
    private final MessageDigest sha1;

    @Autowired
    public AccountsDAO(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
        try {
            this.sha1 = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 algorithm not found", e);
        }
    }

    private String hashPassword(String password) {
        return String.format("%032x", new BigInteger(1, sha1.digest(password.getBytes())));
    }

    public boolean registration(JSONObject user) {
        String login = user.getString("login");
        Query query = new Query(Criteria.where("login").is(login));

        if (mongoTemplate.exists(query, COLLECTION_NAME)) {
            log.warn("Registration failed: User '{}' already exists", login);
            return false;
        }

        Document newDocument = new Document("login", login)
                .append("pass", hashPassword(user.getString("pass")))
                .append("cookie", user.getString("cookie"))
                .append("favorites", new ArrayList<>())
                .append("comparisons", new ArrayList<>());

        mongoTemplate.save(newDocument, COLLECTION_NAME);
        log.info("User '{}' registered successfully", login);
        return true;
    }

    public boolean login(JSONObject user) {
        String login = user.getString("login");
        String passHash = hashPassword(user.getString("pass"));

        Query query = new Query(Criteria.where("login").is(login));
        Document userDoc = mongoTemplate.findOne(query, Document.class, COLLECTION_NAME);

        if (userDoc != null && passHash.equals(userDoc.getString("pass"))) {
            Update update = new Update().set("cookie", user.getString("cookie"));
            mongoTemplate.updateFirst(query, update, COLLECTION_NAME);
            log.info("User '{}' logged in successfully", login);
            return true;
        }

        log.warn("Login failed for user '{}': Invalid password or user not found", login);
        return false;
    }

    public boolean recovery(JSONObject user) {
        String login = user.getString("login");
        Query query = new Query(Criteria.where("login").is(login));
        Document userDoc = mongoTemplate.findOne(query, Document.class, COLLECTION_NAME);

        if (userDoc != null) {
            String oldPassHash = hashPassword(user.getString("oldPass"));
            if (oldPassHash.equals(userDoc.getString("pass"))) {
                Update update = new Update()
                        .set("pass", hashPassword(user.getString("pass")))
                        .set("cookie", user.getString("cookie"));

                mongoTemplate.updateFirst(query, update, COLLECTION_NAME);
                log.info("Password recovered for user '{}'", login);
                return true;
            }
        }
        log.warn("Recovery failed for user '{}': Invalid old password", login);
        return false;
    }

    public ResponseCookie returnCookie() {
        return ResponseCookie.from("JSESSIONID", UUID.randomUUID().toString())
                .httpOnly(true)
                .secure(true)
                .maxAge(Duration.ofDays(1))
                .sameSite("None")
                .build();
    }

    public static ResponseCookie expireCookie() {
        return ResponseCookie.from("JSESSIONID", "")
                .httpOnly(true)
                .secure(true)
                .maxAge(0)
                .sameSite("None")
                .build();
    }

    public JSONObject checkCookie(String cookie) {
        Query query = new Query(Criteria.where("cookie").is(cookie));
        Document userDoc = mongoTemplate.findOne(query, Document.class, COLLECTION_NAME);

        JSONObject jsonObject = new JSONObject();
        if (userDoc != null) {
            JSONArray favs = new JSONArray(lists(cookie, null, "favorites"));
            jsonObject.put("message", "asd")
                    .put("userData", new JSONObject()
                            .put("favourites", favs)
                            .put("login", userDoc.getString("login")));
        } else {
            jsonObject.put("message", "вашего кукки нет");
        }
        return jsonObject;
    }

    public String lists(String cookie, Integer id, String listName) {
        Query query = new Query(Criteria.where("cookie").is(cookie));
        Document userDoc = mongoTemplate.findOne(query, Document.class, COLLECTION_NAME);

        if (userDoc != null) {
            List<Integer> currentList = userDoc.getList(listName, Integer.class);
            Set<Integer> uniqueSet = new HashSet<>(currentList != null ? currentList : new ArrayList<>());

            if (id == null) {
                return uniqueSet.toString();
            }

            if (uniqueSet.contains(id)) {
                uniqueSet.remove(id);
                log.debug("Removed id {} from {} for cookie {}", id, listName, cookie);
            } else {
                uniqueSet.add(id);
                log.debug("Added id {} to {} for cookie {}", id, listName, cookie);
            }

            Update update = new Update().set(listName, uniqueSet);
            mongoTemplate.updateFirst(query, update, COLLECTION_NAME);
            return uniqueSet.toString();
        }
        return "Сессия устарела";
    }
}