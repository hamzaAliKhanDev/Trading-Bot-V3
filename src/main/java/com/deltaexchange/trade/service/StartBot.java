package com.deltaexchange.trade.service;

import java.time.Duration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.deltaexchange.trade.config.DeltaConfig;
import com.deltaexchange.trade.config.DeltaDto;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class StartBot {

	@Autowired
	private PositionService positionService;

	@Autowired
	private DeltaConfig config;
	
	@Autowired
	private CheckAndOrderService placeOrder;
	
	@Autowired
	private DeltaDto deltaDto;
	
	@Autowired
	private DeadZoneOrderService deadZoneOrder;

	private static final Logger consoleLogger = LogManager.getLogger("Console");
	private static final Logger errorLogger = LogManager.getLogger("Error");

	public void startBotMain() {

		consoleLogger.info("::::::::::::::::Bot Started:::::::::::::");

		Flux.interval(Duration.ofSeconds(config.getLoopInterval()))
				.flatMap(tick -> positionService.getBTCPositionDetails().doOnNext(position -> {

					consoleLogger.info("[BOT] Position data received for tick {}: {}", tick, position);

					if (position != null) {

						JSONObject positionServiceResponse = new JSONObject(position.toString());

						boolean apiSuccess = positionServiceResponse.getBoolean("success");

						if (apiSuccess) {

							JSONObject result = positionServiceResponse.getJSONObject("result");

							if (result != null && !result.isEmpty()) {

								// ENTRY PRICE — must be final or effectively final
								final String[] entryPriceStr = { result.optString("entry_price", "") };

								// Placing order for Dead zone
								if (entryPriceStr[0] == null || entryPriceStr[0].isEmpty()) {
									deltaDto.setLastOrderSize(0);
									consoleLogger
											.info("[BOT] No EntryPrice found. Placing orders for dead zone::::::::");
									entryPriceStr[0] = "";

									deadZoneOrder.applyDeadZoneOrder().subscribe();
									deltaDto.setDeadZoneOrderPlaced(true);
								} else {
									deltaDto.setDeadZoneOrderPlaced(false);
									final long[] entryPrice = { (long) Double.parseDouble(entryPriceStr[0]) };
									final int[] size = { result.getInt("size") };
									
									if(size[0]!=deltaDto.getLastOrderSize()) {
										placeOrder.executionMain(String.valueOf(entryPrice[0]), size[0]);
									}
								}

							} else {
								consoleLogger.info(
										"[BOT] Result JSON found empty or null in position service response. Going for another tick:::::::::::");
							}

						} else {
							consoleLogger.info(
									":::::::::::Position Service API Failed with success flag as False::::::::::::");
						}

					} else {
						consoleLogger.info(":::::::::::::No response returned from position service:::::::::");
					}
				})).doOnError(e -> errorLogger.error("[ERROR]:::::", e)).subscribe();
	}
}
