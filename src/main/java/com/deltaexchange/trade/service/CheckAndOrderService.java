package com.deltaexchange.trade.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.deltaexchange.trade.config.DeltaDto;

@Service
public class CheckAndOrderService {

	@Autowired
	private CancelOrderService cancelAllOrders;
	@Autowired
	private SetLeverageService setOrderLeverage;
	@Autowired
	private PlaceOrderService placeOrder;

	@Autowired
	private DeltaDto deltaDto;

	@Autowired
	private EditOrdersService editOrders;

	private static final Logger consoleLogger = LogManager.getLogger("Console");
	private static final Logger errorLogger = LogManager.getLogger("Error");
	private static final Logger transactionLogger = LogManager.getLogger("Transaction");

	public JSONObject executionMain(String entryPrice, int size) {
		try {
			// Cancels All Orders
			if (Math.abs(size) == 2) {
				cancelAllOrders.cancelOrdersForProductAsJson().subscribe(cancelOrdersNode -> {
					consoleLogger.info("cancelOrdersNode:::::{}", cancelOrdersNode);

					JSONObject cancelOrdersResponse = new JSONObject(cancelOrdersNode.toString());
					boolean apiSuccess = cancelOrdersResponse.getBoolean("success");

					if (!apiSuccess) {
						consoleLogger.info(":::::::::Cancel Order service returned success false::::::::::::");
					} else {
						transactionLogger.info("Cancelled All Previous Orders for EntryPrice->{}, Size->{}:::::",
								entryPrice, size);
					}
				});
			}
			// Set Leverage of Orders
			int leverage = returnLeverage(size);
			setOrderLeverage.setOrderLeverage(leverage).subscribe(setLeverageNode -> {
				JSONObject setLeverageResponse = new JSONObject(setLeverageNode.toString());
				boolean apiSuccesslev = setLeverageResponse.getBoolean("success");

				if (!apiSuccesslev) {
					consoleLogger.info(":::::::::Set Leverage Service returned success false::::::::::::");
				} else {
					transactionLogger.info(
							"Leverage Set Successfully Orders for EntryPrice->{}, Size->{}, Leverage->{}:::::",
							entryPrice, size, leverage);

					// Place Orders
					placeOrder(entryPrice, size);

					// Added to edit orders
					int abs = Math.abs(size);
					if (abs >= 6) {
						editOrders.editOrdersForLotSize(size).subscribe();
						deltaDto.setLastOrderSize(size);
					}

				}
			});

		} catch (Exception e) {
			errorLogger.error("Error occured in Check and Order Service:::::", e);
		}

		return null;
	}

	public int returnLeverage(int size) {

		int abs = Math.abs(size);

		switch (abs) {
		case 2:
		case 6:
			return 10;

		case 18:
			return 25;

		default:
			return 10;
		}
	}

	public void placeOrder(String entryPrice, int size) {

		double entryPriceRaw = Double.parseDouble(entryPrice);
		long entryPriceDouble = (long) entryPriceRaw;

		switch (size) {

		case 2:
			executeOrder(String.valueOf(entryPriceDouble + 500), 4, "sell");
			executeOrder(String.valueOf(entryPriceDouble - 750), 4, "buy");
			executeOrder(String.valueOf(entryPriceDouble - 1250), 12, "buy");
			break;

		case -2:
			executeOrder(String.valueOf(entryPriceDouble - 500), 4, "buy");
			executeOrder(String.valueOf(entryPriceDouble + 750), 4, "sell");
			executeOrder(String.valueOf(entryPriceDouble + 1250), 12, "sell");
			break;

		case 18:
			executeOrder(String.valueOf(entryPriceDouble + 200), 18, "sell");
			executeOrder(String.valueOf(entryPriceDouble - 750), 36, "buy");
			changeLevAndexecuteOrder(35,String.valueOf(entryPriceDouble - 1250), 108, "buy");
			changeLevAndexecuteOrder(45,String.valueOf(entryPriceDouble - 1750), 324, "buy");
			changeLevAndexecuteOrder(60,String.valueOf(entryPriceDouble - 2250), 972, "buy");
			changeLevAndexecuteOrder(75,String.valueOf(entryPriceDouble - 2750), 2916, "buy");
			break;

		case -18:
			executeOrder(String.valueOf(entryPriceDouble - 200), 18, "buy");
			executeOrder(String.valueOf(entryPriceDouble + 750), 36, "sell");
			changeLevAndexecuteOrder(35,String.valueOf(entryPriceDouble + 1250), 108, "sell");
			changeLevAndexecuteOrder(45,String.valueOf(entryPriceDouble + 1750), 324, "sell");
			changeLevAndexecuteOrder(60,String.valueOf(entryPriceDouble + 2250), 972, "sell");
			changeLevAndexecuteOrder(75,String.valueOf(entryPriceDouble + 2750), 2916, "sell");
			break;
		}
	}

	public void executeOrder(String limitPrice, int size, String side) {

		placeOrder.placeOrder(limitPrice, size, side).subscribe(placeOrderNode -> {

			JSONObject placeOrderResponse = new JSONObject(placeOrderNode.toString());
			boolean apiSuccess = placeOrderResponse.getBoolean("success");

			if (!apiSuccess) {
				consoleLogger.info(
						":::::::::Place Order service returned false for LimitPrice->{}, Size->{}, Side->{}:::::::",
						limitPrice, size, side);
			} else {
				deltaDto.setLastOrderSize(size);
				transactionLogger.info(
						"Order Placed Successfully with Details:- \n Side->{}, \n LimitPrice->{}, \n Size->{}:::::",
						side, limitPrice, size);
				transactionLogger.info(
						"::::::::::::::::::::::::::::::::::New Order execution Ended:::::::::::::::::::::::::::::::::::");
			}

		}, error -> {
			errorLogger.error("Error placing order:", error);
		});
	}

	public void changeLevAndexecuteOrder(int leverage,String limitPrice, int size, String side) {

		setOrderLeverage.setOrderLeverage(leverage).subscribe(setLeverageNode -> {
			JSONObject setLeverageResponse = new JSONObject(setLeverageNode.toString());
			boolean apiSuccesslev = setLeverageResponse.getBoolean("success");

			if (!apiSuccesslev) {
				consoleLogger.info(":::::::::Set Leverage Service returned success false::::::::::::");
			} else {
				transactionLogger.info(
						"Leverage Set Successfully Orders for limitPrice->{}, Size->{}, Leverage->{}:::::", limitPrice,
						size, leverage);

				placeOrder.placeOrder(limitPrice, size, side).subscribe(placeOrderNode -> {

					JSONObject placeOrderResponse = new JSONObject(placeOrderNode.toString());
					boolean apiSuccess = placeOrderResponse.getBoolean("success");

					if (!apiSuccess) {
						consoleLogger.info(
								":::::::::Place Order service returned false for LimitPrice->{}, Size->{}, Side->{}:::::::",
								limitPrice, size, side);
					} else {
						deltaDto.setLastOrderSize(size);
						transactionLogger.info(
								"Order Placed Successfully with Details:- \n Side->{}, \n LimitPrice->{}, \n Size->{}:::::",
								side, limitPrice, size);
						transactionLogger.info(
								"::::::::::::::::::::::::::::::::::New Order execution Ended:::::::::::::::::::::::::::::::::::");
					}

				}, error -> {
					errorLogger.error("Error placing order:", error);
				});
			}
		});
	}
}
