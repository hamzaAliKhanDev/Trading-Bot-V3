package com.deltaexchange.trade.service;

import lombok.RequiredArgsConstructor;
import reactor.netty.http.client.HttpClient;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.deltaexchange.trade.util.SslContextUtil;

import io.netty.handler.ssl.SslContext;
import jakarta.annotation.PostConstruct;

@Service
@RequiredArgsConstructor
public class WebClientService {
    private static final Logger consoleLogger = LogManager.getLogger("Console");

    private final WebClient.Builder webClientBuilder;
    private WebClient webClient; // no longer final, we'll set it in @PostConstruct

    @PostConstruct
    public void init() {
        consoleLogger.info(":::::::::::::::::::SSL is Loaded:::::::::::::");

        // Load custom SSL context
        SslContext sslContext = SslContextUtil.createSslContext();

        // Create Netty HttpClient with SSL
        HttpClient httpClient = HttpClient.create()
                .secure(sslSpec -> sslSpec.sslContext(sslContext));

        // Configure WebClient with custom HttpClient
        this.webClient = webClientBuilder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    public WebClient buildClient(String baseUrl) {
        return webClientBuilder
                .baseUrl(baseUrl)
                .build();
    }
}

