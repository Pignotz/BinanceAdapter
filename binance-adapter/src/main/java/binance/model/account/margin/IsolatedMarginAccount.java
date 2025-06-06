package binance.model.account.margin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import binance.model.account.AccountType;

@Component
public class IsolatedMarginAccount extends MarginAccount {

	
	private static Logger logger = LogManager.getLogger(IsolatedMarginAccount.class);

	
	public IsolatedMarginAccount() {
		super(AccountType.ISOLATED_MARGIN, logger);
	}

	
		
}
