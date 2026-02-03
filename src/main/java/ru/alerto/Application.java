package ru.alerto;

import ru.alerto.configs.ElasticsearchClientConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootApplication(exclude = {ElasticsearchClientAutoConfiguration.class})
@Import(ElasticsearchClientConfig.class)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}