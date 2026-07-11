package com.coinvest;

import org.springframework.boot.SpringApplication;

public class TestCoinvestApplication {

	public static void main(String[] args) {
		SpringApplication.from(CoinvestApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
