package com.coinvest.portfolio.event;

import com.coinvest.portfolio.dto.PortfolioValuation;

public record RebalanceAlertEvent(PortfolioValuation valuation) {
}
