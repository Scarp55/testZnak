package org.example;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import javax.print.Doc;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class CrptApi {

    private final RestTemplate restTemplate = new RestTemplate();

    private final URI uri = URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create");
    private static Bucket bucket;
    private static BlockingQueue<Document> queue;

    public CrptApi(Long time, int requestLimit) {
        queue = new ArrayBlockingQueue<>(5);
        Bandwidth limit = Bandwidth.classic(requestLimit, Refill.intervally(requestLimit, Duration.ofSeconds(time)));
        bucket = Bucket4j.builder()
                .addLimit(limit)
                .build();
    }

    public void createDocument(Document document) throws InterruptedException {
        queue.put(document);
        Document documentFromQueue = queueHandler();
        ResponseEntity<String> respEntity = sendRequest(documentFromQueue);
        //Дальше можно обработать ответ
    }

    private synchronized Document queueHandler() throws InterruptedException {
        while (!queue.isEmpty()) {
            if (bucket.tryConsume(1)) {
                return queue.take();
            }
        }
        return null;
    }

    private ResponseEntity<String> sendRequest(Document document) {
        String documentJson = convertToJson(document);
        HttpEntity<String> request = new HttpEntity<>(documentJson, getHeaders());
        return restTemplate.postForEntity(uri, request, String.class);
    }

    public String convertToJson(Document document) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(document);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String signature = "digital signature";
        headers.add("Authorization", signature);
        return headers;
    }

    @Setter
    @Getter
    public class Document {

        @JsonProperty("description")
        private Description description;
        @JsonProperty("doc_id")
        private String docId;
        @JsonProperty("doc_status")
        private String docStatus;
        @JsonProperty("doc_type")
        private String docType;
        @JsonProperty("import_request")
        private boolean importRequest;
        @JsonProperty("owner_inn")
        private String ownerInn;
        @JsonProperty("participant_inn")
        private String participantInn;
        @JsonProperty("producer_inn")
        private String producerInn;
        @JsonProperty("production_date")
        private String productionDate;
        @JsonProperty("production_type")
        private String productionType;
        @JsonProperty("products")
        private List<Product> products;
        @JsonProperty("reg_date")
        private String regDate;
        @JsonProperty("reg_number")
        private String regNumber;

        @Setter
        @Getter
        private class Description {
            @JsonProperty("participant_inn")
            private String participantInn;
        }

        @Setter
        @Getter
        private class Product {
            @JsonProperty("certificate_document")
            private String certificateDocument;
            @JsonProperty("certificate_document_date")
            private String certificateDocumentDate;
            @JsonProperty("certificate_document_number")
            private String certificateDocumentNumber;
            @JsonProperty("owner_inn")
            private String ownerInn;
            @JsonProperty("producer_inn")
            private String producerInn;
            @JsonProperty("production_date")
            private String productionDate;
            @JsonProperty("tnved_code")
            private String tnvedCode;
            @JsonProperty("uit_code")
            private String uitCode;
            @JsonProperty("uitu_code")
            private String uituCode;
        }
    }
}
