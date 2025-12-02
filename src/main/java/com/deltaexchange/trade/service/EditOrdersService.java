package com.deltaexchange.trade.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.deltaexchange.trade.config.DeltaConfig;
import com.deltaexchange.trade.util.DeltaSignatureUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class EditOrdersService {

    private static final Logger consoleLogger = LogManager.getLogger("Console");
    private static final Logger errorLogger = LogManager.getLogger("Error");
    private static final Logger transactionLogger = LogManager.getLogger("Transaction");

    @Autowired
    private WebClientService webClientService;

    @Autowired
    private DeltaConfig config;

    @Autowired
    private DeltaSignatureUtil signRequest;
    
    private final ObjectMapper mapper = new ObjectMapper();

    // **************************************************************
    // PUBLIC METHOD → GET ALL ORDERS → FILTER LOT_SIZE=2 → EDIT THEM
    // **************************************************************
    public Mono<Void> editOrdersForLotSize(int size) {

        return getOpenOrders(Integer.valueOf(config.getProductId()))
                .flatMapMany(arr -> {

                    List<JSONObject> orderList = new ArrayList<>();
                    List<Integer> ordersToEdit = new ArrayList<>();
                    ordersToEdit = getlotSize(Math.abs(size));
                    if(ordersToEdit==null) {
                    	consoleLogger.info("No orders found with lot_size={} for product_id={}",size, config.getProductId());
                        return Flux.empty();
                    }
                    for(int j:ordersToEdit) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);

                        int lotSize = Math.abs(obj.getInt("size"));
                        String side = obj.getString("order_type");
                        if (lotSize == j) {
                        	if(j==4) {
                        		if(size>0) {
                        			if("sell".equalsIgnoreCase(side)) {
                        				orderList.add(obj);
                        			}
                        		}else {
                        			if("buy".equalsIgnoreCase(side)) {
                        				orderList.add(obj);
                        			}
                        		}
                        	}else {
                        		orderList.add(obj);
                        	}	
                        	
                        }
                    }
                    }
                    if (orderList.isEmpty()) {
                        consoleLogger.info("No orders found with lot_size={} for product_id={}",size, config.getProductId());
                        return Flux.empty();
                    }

                    consoleLogger.info("Found {} orders with lot_size={}. Editing...",size, orderList.size());
                    return Flux.fromIterable(orderList);
                })
                .flatMap(this::editSingleOrder)
                .then();
    }
    
    private List<Integer> getlotSize(int size){
    	List<Integer> list  = new ArrayList<>();
    	if(size==6) {
    		list.add(4);
    	}else if(size==18) {
    		list.add(8);
    	}else if(size==54) {
    		list.add(2);
    		list.add(18);
    	}else if(size==162) {
    		list.add(2);
    		list.add(54);
    	}else if(size==486) {
    		list.add(2);
    		list.add(162);
    	}else if(size==1458) {
    		list.add(2);
    		list.add(486);
    	}else if(size==4374) {
    		list.add(2);
    		list.add(1458);
    	}
    	return list;
    }
    
    // **************************************************************
    // GET OPEN ORDERS FOR PRODUCT ID (same as your existing code)
    // **************************************************************
    private Mono<JSONArray> getOpenOrders(int productId) {
        try {
            String endpoint = "/v2/orders";
            String query = "state=open&product_id=" + productId;

            long ts = Instant.now().getEpochSecond();
            String fullEndpoint = endpoint + "?" + query;

            String prehash = "GET" + ts + fullEndpoint;
            String signature = signRequest.hmacSHA256(prehash, config.getApiSecret());

            WebClient client = webClientService.buildClient(config.getBaseUrl());

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

    // **************************************************************
    // EDIT SINGLE ORDER (NEW METHOD)
    // **************************************************************
    private Mono<JsonNode> editSingleOrder(JSONObject orderObj) {

        try {
            int orderId = orderObj.getInt("id");
            String side = orderObj.getString("order_type"); 
            int size = orderObj.getInt("size"); 
            // Based on Delta Exchange API → "buy" or "sell"

            double limitPrice = orderObj.getDouble("limit_price");
            
            double points = getPoints(Math.abs(size));
            double newPrice = side.equalsIgnoreCase("buy")
                    ? limitPrice + points
                    : limitPrice - points;

            transactionLogger.info("Editing Order ID: {} | side={} | oldPrice={} → newPrice={}",
                    orderId, side, limitPrice, newPrice);

            JSONObject body = new JSONObject();
            body.put("id", orderId);
            body.put("product_id", config.getProductId());
            body.put("limit_price", newPrice);
            if(Math.abs(size)==4) {
            	body.put("size", 8);
            }else if(Math.abs(size)==8) {
            	body.put("size", 2);
            }else if(Math.abs(size)>18){
            	body.put("size", Math.abs(size)*3);
            }

            String endpoint = "/v2/orders";

            long ts = Instant.now().getEpochSecond();
            String prehash = "PUT" + ts + endpoint + body;
            String signature = signRequest.hmacSHA256(prehash, config.getApiSecret());

            WebClient client = webClientService.buildClient(config.getBaseUrl());

            return client.method(HttpMethod.PUT)
                    .uri(endpoint)
                    .header("api-key", config.getApiKey())
                    .header("signature", signature)
                    .header("timestamp", String.valueOf(ts))
                    .header("Content-Type", "application/json")
                    .bodyValue(body.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .map(res -> {
                        consoleLogger.info("Edit Response for {} → {}", orderId, res);
                        try {
                            JsonNode json = mapper.readTree(res);
                            return json;
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to parse cancel orders response", e);
                        }
                    });

        } catch (Exception e) {
            errorLogger.error("Error editing order:", e);
            return Mono.empty();
        }
    }
    
    private double getPoints(int size) {
    	if(size==4 || size==8 || size==2 || size ==18) {
    		return 500;
    	}else if(size==54 || size==162 ) {
    		return 575;
    	}else if(size==486 || size==1458) {
    		return 525;
    	}
    	return 0;
    }
}
