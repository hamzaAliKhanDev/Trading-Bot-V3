package com.deltaexchange.trade.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CheckAndOrderService {

	@Autowired
	private CancelOrderService cancelAllOrders;
	@Autowired
	private SetLeverageService setOrderLeverage;
	@Autowired
	private PlaceOrderService placeOrder;

	private static final Logger consoleLogger = LogManager.getLogger("Console");
	private static final Logger errorLogger = LogManager.getLogger("Error");
	private static final Logger transactionLogger = LogManager.getLogger("Transaction");

	public JSONObject executionMain(String entryPrice, int size) {
		try {
			// Cancels All Orders
			cancelAllOrders.cancelOrdersForProductAsJson().subscribe(cancelOrdersNode -> {
				consoleLogger.info("cancelOrdersNode:::::{}", cancelOrdersNode);

				JSONObject cancelOrdersResponse = new JSONObject(cancelOrdersNode.toString());
				boolean apiSuccess = cancelOrdersResponse.getBoolean("success");

				if (!apiSuccess) {
					consoleLogger.info(":::::::::Cancel Order service returned success false::::::::::::");
				} else {
					transactionLogger.info("Cancelled All Previous Orders for EntryPrice->{}, Size->{}:::::",
							entryPrice, size);
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

							// Added to place third order for dead zone
							int abs = Math.abs(size);
							if (abs >= 18) {

								int thirdOrderSize = 2;
								int leverage2 = returnLeverage(thirdOrderSize);
								setOrderLeverage.setOrderLeverage(leverage2).subscribe(setLeverageNode2 -> {
									JSONObject setLeverageResponse2 = new JSONObject(setLeverageNode2.toString());
									boolean apiSuccesslev2 = setLeverageResponse2.getBoolean("success");

									if (!apiSuccesslev2) {
										consoleLogger.info(
												":::::::::Set Leverage Service returned success false::::::::::::");
									} else {
										transactionLogger.info(
												"Leverage Set Successfully Orders for EntryPrice->{}, Size->{}, Leverage->{}:::::",
												entryPrice, size, leverage2);

										// Place Orders
										placeThirdOrder(entryPrice, thirdOrderSize, size);
									}
								});
							}

						}
					});
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

		case 54:
			return 35;

		case 162:
			return 45;

		case 486:
			return 60;

		case 1458:
		case 4374:
			return 75;

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
			break;

		case -2:
			executeOrder(String.valueOf(entryPriceDouble - 500), 4, "buy");
			executeOrder(String.valueOf(entryPriceDouble + 750), 4, "sell");
			break;

		case 6:
			executeOrder(String.valueOf(entryPriceDouble + 500), 8, "sell");
			executeOrder(String.valueOf(entryPriceDouble - 750), 12, "buy");
			break;

		case -6:
			executeOrder(String.valueOf(entryPriceDouble - 500), 8, "buy");
			executeOrder(String.valueOf(entryPriceDouble + 750), 12, "sell");
			break;

		case 18:
			executeOrder(String.valueOf(entryPriceDouble + 200), 18, "sell");
			executeOrder(String.valueOf(entryPriceDouble - 750), 36, "buy");
			break;

		case -18:
			executeOrder(String.valueOf(entryPriceDouble - 200), 18, "buy");
			executeOrder(String.valueOf(entryPriceDouble + 750), 36, "sell");
			break;

		case 54:
			executeOrder(String.valueOf(entryPriceDouble + 200), 54, "sell");
			executeOrder(String.valueOf(entryPriceDouble - 750), 108, "buy");
			break;

		case -54:
			executeOrder(String.valueOf(entryPriceDouble - 200), 54, "buy");
			executeOrder(String.valueOf(entryPriceDouble + 750), 108, "sell");
			break;

		case 162:
			executeOrder(String.valueOf(entryPriceDouble + 125), 162, "sell");
			executeOrder(String.valueOf(entryPriceDouble - 750), 324, "buy");
			break;

		case -162:
			executeOrder(String.valueOf(entryPriceDouble - 125), 162, "buy");
			executeOrder(String.valueOf(entryPriceDouble + 750), 324, "sell");
			break;

		case 486:
			executeOrder(String.valueOf(entryPriceDouble + 125), 486, "sell");
			executeOrder(String.valueOf(entryPriceDouble - 750), 972, "buy");
			break;

		case -486:
			executeOrder(String.valueOf(entryPriceDouble - 125), 486, "buy");
			executeOrder(String.valueOf(entryPriceDouble + 750), 972, "sell");
			break;

		case 1458:
			executeOrder(String.valueOf(entryPriceDouble + 100), 1458, "sell");
			executeOrder(String.valueOf(entryPriceDouble - 750), 2916, "buy");
			break;

		case -1458:
			executeOrder(String.valueOf(entryPriceDouble - 100), 1458, "buy");
			executeOrder(String.valueOf(entryPriceDouble + 750), 2916, "sell");
			break;

		case 4374:
			executeOrder(String.valueOf(entryPriceDouble + 100), 4374, "sell");
			break;

		case -4374:
			executeOrder(String.valueOf(entryPriceDouble - 100), 4374, "buy");
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

	public void placeThirdOrder(String entryPrice, int thirdOrdersize, int originalOrderSize) {

		double entryPriceRaw = Double.parseDouble(entryPrice);
		long entryPriceDouble = (long) entryPriceRaw;

		if (originalOrderSize > 0) {
			executeOrder(String.valueOf(entryPriceDouble + 500), thirdOrdersize, "sell");
		} else {
			executeOrder(String.valueOf(entryPriceDouble - 500), thirdOrdersize, "buy");
		}

	}

}
