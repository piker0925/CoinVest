package com.tradeowl;

import org.springframework.boot.SpringApplication;

public class TestTradeOwlApplication {

	public static void main(String[] args) {
		SpringApplication.from(TradeOwlApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
