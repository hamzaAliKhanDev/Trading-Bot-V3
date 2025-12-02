package com.deltaexchange.trade.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.deltaexchange.trade.config.DeltaConfig;
import com.deltaexchange.trade.util.DeltaSignatureUtil;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class DeadZoneOrderService {

    private static final Logger consoleLogger = LogManager.getLogger("Console");
    private static final Logger errorLogger = LogManager.getLogger("Error");

    @Autowired
    private CancelOrderService cancelOrderService;

    @Autowired
    private WebClientService webClientService;

    @Autowired
    private DeltaConfig config;

    @Autowired
    private DeltaSignatureUtil signRequest;
    
    @Autowired
    private CheckAndOrderService placeOrderService;

    // **************************************************************
    // MAIN SERVICE FLOW
    // **************************************************************
    public Mono<Void> applyDeadZoneOrder() {

        return getOpenOrders(Integer.valueOf(config.getProductId()))
                .flatMap(arr -> {

                    JSONObject twoLotOrder = null;
                    List<Integer> toCancelList = new ArrayList<>();

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);

                        int size = obj.getInt("size");
                        int id = obj.getInt("id");

                        if (size == 2) {
                            twoLotOrder = obj;   // save this one
                        } else {
                            toCancelList.add(id); // cancel all other orders
                        }
                    }

                    if (twoLotOrder == null) {
                        consoleLogger.info("No 2-lot order found. Nothing to reverse.");
                        return Mono.empty();
                    }

                    consoleLogger.info("2-lot order found: {}", twoLotOrder);

                    return cancelOtherOrders(toCancelList)
                            .then(placeOppositeOrder(twoLotOrder));
                })
                .then();
    }

    // **************************************************************
    // CANCEL ALL ORDERS EXCEPT LOT SIZE 2
    // **************************************************************
    private Mono<Void> cancelOtherOrders(List<Integer> ids) {

        if (ids.isEmpty()) {
            consoleLogger.info("No orders to cancel except the 2-lot order.");
            return Mono.empty();
        }

        consoleLogger.info("Cancelling {} orders...", ids.size());

        return Flux.fromIterable(ids)
                .flatMap(id -> cancelOrderService.cancelSingleOrder(id))
                .then();
    }

    // **************************************************************
    // PLACE OPPOSITE ORDER 1250 POINTS AWAY
    // **************************************************************
    private Mono<Void> placeOppositeOrder(JSONObject twoLotOrder) {

        try {
            double limitPrice = twoLotOrder.getDouble("limit_price");
            String side = twoLotOrder.getString("side"); 
            // Or "order_type" depending on your API output

            String oppositeSide = side.equalsIgnoreCase("buy") ? "sell" : "buy";

            double newLimitPrice = side.equalsIgnoreCase("buy")
                    ? (limitPrice + 1250)
                    : (limitPrice - 1250);

            consoleLogger.info(
                    "Placing opposite order | originalSide={} | newSide={} | oldPrice={} | newPrice={}",
                    side, oppositeSide, limitPrice, newLimitPrice
            );

             placeOrderService.changeLevAndexecuteOrder(
            		10,
                    String.valueOf(newLimitPrice),
                    2,
                    oppositeSide
            );
             return Mono.empty();

        } catch (Exception e) {
            errorLogger.error("Error placing opposite order:", e);
            return Mono.empty();
        }
    }

    // **************************************************************
    // GET OPEN ORDERS (copied from your CancelOrderService)
    // **************************************************************
    private Mono<JSONArray> getOpenOrders(int productId) {
        try {
            String endpoint = "/v2/orders";
            String query = "state=open&product_id=" + productId;

            long ts = Instant.now().getEpochSecond();
            String fullEndpoint = endpoint + "?" + query;

            String prehash = "GET" + ts + fullEndpoint;
            String signature = signRequest.hmacSHA256(prehash, config.getApiSecret());

            var client = webClientService.buildClient(config.getBaseUrl());

            return client.get()
                    .uri(fullEndpoint)
                    .header("api-key", config.getApiKey())
                    .header("signature", signature)
                    .header("timestamp", String.valueOf(ts))
                    .header("Accept", "application/json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .map(res -> {
                        consoleLogger.info("Open Orders Response: {}", res);
                        JSONObject json = new JSONObject(res);
                        return json.getJSONArray("result");
                    });

        } catch (Exception e) {
            errorLogger.error("Error fetching open orders:", e);
            return Mono.just(new JSONArray());
        }
    }
}
