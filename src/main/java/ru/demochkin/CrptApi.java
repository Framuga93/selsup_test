package ru.demochkin;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;


import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class CrptApi {

    private final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final int requestLimit;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final AtomicInteger requestCount = new AtomicInteger(0);;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final TimeUnit timeUnit;
    private long interval;

    public CrptApi(TimeUnit timeUnit, int requestLimit, long interval) {
        this.requestLimit = requestLimit;
        this.timeUnit = timeUnit;
        this.interval = interval;
        startRequestCounterResetTask();
    }

    private void startRequestCounterResetTask() {
        scheduler.scheduleAtFixedRate(() -> {
            synchronized (this) {
                requestCount.set(0);
                notifyAll();
                log.info("Request count reset to 0");
            }
        }, 0, interval, timeUnit);
    }

    public void createDocument(Document document, String signature) throws InterruptedException, IOException {
        log.info("Creating document with ID: {}", document.doc_id);
        synchronized (this) {
            while (requestCount.get() >= requestLimit) {
                wait();
            }
            requestCount.incrementAndGet();
            log.debug("Request count incremented: {}", requestCount.get());
        }

        String requestBody = objectMapper.writeValueAsString(document);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-type", "application/json")
                .header("Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        log.debug("Sending HTTP request to URL: {}", API_URL);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        log.info("Received response with status code: {}", response.statusCode());
        log.debug("Response body: {}", response.body());

        synchronized (this) {

//            requestCount.decrementAndGet();  // Если нам нужна очередь(вышел-зашел) то используем эту функцию
            log.debug("Request count decremented: {}", requestCount.get());
            notifyAll();
        }
    }

    public void shutdown(){
        scheduler.shutdown();
    }


    @Data
    public static class Document {
        private Description description;
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private ArrayList<Product> products;
        private String reg_date;
        private String reg_number;


        @Data
        public static class Description {
            private String participantInn;
        }

        @Data
        public static class Product {
            private String certificate_document;
            private String certificate_document_date;
            private String certificate_document_number;
            private String owner_inn;
            private String producer_inn;
            private String production_date;
            private String tnved_code;
            private String uit_code;
            private String uitu_code;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 2, 5L);
        Document document = createTestDocument();
        String signature = "example_signature";

        createTestThreads(api, document, signature);
        api.shutdown();
    }

    private static void createTestThreads(CrptApi api, Document document, String signature) throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(10);

        for (int i = 0; i < 10; i++) {
            executorService.submit(() -> {
                try {
                    api.createDocument(document, signature);
                } catch (InterruptedException | IOException e) {
                    log.debug(e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
    }

    public static Document createTestDocument() {
        Document document = new Document();
        document.doc_id = "example_id";
        document.doc_status = "example_status";
        document.doc_type = "LP_INTRODUCE_GOODS";
        document.importRequest = true;
        document.owner_inn = "1234567890";
        document.participant_inn = "0987654321";
        document.producer_inn = "1122334455";
        document.production_date = "2020-01-23";
        document.production_type = "example_type";
        document.reg_date = "2020-01-23";
        document.reg_number = "example_reg_number";

        Document.Description description = new Document.Description();
        description.participantInn = "1234567890";
        document.description = description;

        Document.Product product = new Document.Product();
        product.certificate_document = "example_document";
        product.certificate_document_date = "2020-01-23";
        product.certificate_document_number = "12345";
        product.owner_inn = "1234567890";
        product.producer_inn = "1122334455";
        product.production_date = "2020-01-23";
        product.tnved_code = "example_code";
        product.uit_code = "example_uit_code";
        product.uitu_code = "example_uitu_code";

        document.products = new ArrayList<>(List.of(product));

        return document;
    }
}
