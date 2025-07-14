package binance.model.account.margin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import binance.model.account.AccountType;
import binance.prices.PriceTable;

@Component
public class IsolatedMarginAccount extends MarginAccount {

	
	private static Logger logger = LogManager.getLogger(IsolatedMarginAccount.class);
	@Autowired private PriceTable priceTable;

	
	public IsolatedMarginAccount() {
		super(AccountType.ISOLATED_MARGIN, logger);
	}


	@Override
	public PriceTable getPriceTable() {
		return priceTable;
	}

	
		
}
