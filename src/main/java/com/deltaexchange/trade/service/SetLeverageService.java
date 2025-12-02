package com.deltaexchange.trade.service;

import java.time.Instant;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.deltaexchange.trade.config.DeltaConfig;
import com.deltaexchange.trade.util.DeltaSignatureUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

@Service
public class SetLeverageService {
    private static final Logger consoleLogger = LogManager.getLogger("Console");
    private static final Logger errorLogger = LogManager.getLogger("Error");

    @Autowired
    private WebClientService webClientService;
    @Autowired
    private DeltaConfig config;
    @Autowired
    private DeltaSignatureUtil signRequest;

    private final ObjectMapper mapper = new ObjectMapper();

    public Mono<JsonNode> setOrderLeverage(int leverage) {
        try {

            // EXACT SAME ENDPOINT AS WORKING CODE
            String endpoint = "/v2/products/" + config.getProductId() + "/orders/leverage";

            // BODY EXACT SAME AS WORKING CODE
            String bodyJson = "{\"leverage\":" + leverage + "}";

            long timestamp = Instant.now().getEpochSecond();

            consoleLogger.info("BodyJson String for Signature::: {}", bodyJson);

            // EXACT SAME PREHASH FORMAT: POST + timestamp + endpoint + body
            String prehash = "POST" + timestamp + endpoint + bodyJson;

            consoleLogger.info("Prehash::: {}", prehash);

            // SIGNATURE EXACTLY SAME
            String signature = signRequest.hmacSHA256(prehash, config.getApiSecret());

            WebClient client = webClientService.buildClient(config.getBaseUrl());

            consoleLogger.info("Final Endpoint:::: {}", endpoint);

            return client.post()
                    .uri(endpoint)
                    .header("api-key", config.getApiKey())
                    .header("signature", signature)
                    .header("timestamp", String.valueOf(timestamp))
                    .header("Content-Type", "application/json")  // SAME AS WORKING CODE
                    .bodyValue(bodyJson)                         // SEND RAW STRING BODY — REQUIRED
                    .retrieve()
                    .bodyToMono(String.class)
                    .map(response -> {
                        consoleLogger.info("Response of setOrderLeverage:::: {}", response);
                        try {
                            return mapper.readTree(response);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to parse setOrderLeverage response", e);
                        }
                    });

        } catch (Exception e) {
            errorLogger.error("Error occurred in setOrderLeverage:::", e);
        }
        return null;
    }
}
