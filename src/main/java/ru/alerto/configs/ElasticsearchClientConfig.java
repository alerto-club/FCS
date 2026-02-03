package ru.alerto.configs;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchClientConfig {

    @Bean
    public RestHighLevelClient elasticsearchClient() {
        RestClientBuilder builder = RestClient.builder(new HttpHost("localhost", 9200, "http"));

        builder.setHttpClientConfigCallback(httpAsyncClientBuilder ->
                httpAsyncClientBuilder.setSSLHostnameVerifier(org.apache.http.conn.ssl.NoopHostnameVerifier.INSTANCE)
        );

        return new RestHighLevelClient(builder);
    }
}