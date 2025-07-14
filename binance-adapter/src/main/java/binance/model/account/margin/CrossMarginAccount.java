package binance.model.account.margin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import binance.job.BinanceHistoryJobConfig;
import binance.model.account.AccountType;
import binance.prices.PriceTable;

@Component
public class CrossMarginAccount extends MarginAccount {

	
	private static Logger logger = LogManager.getLogger(CrossMarginAccount.class);

	@Autowired private PriceTable priceTable;
	
	public CrossMarginAccount() {
		super(AccountType.CROSS_MARGIN,logger);
	}

	@Override
	public PriceTable getPriceTable() {
		return priceTable;
	}

	
		
}
