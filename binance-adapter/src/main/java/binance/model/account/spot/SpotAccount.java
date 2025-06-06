package binance.model.account.spot;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import binance.model.account.Account;
import binance.model.account.AccountType;
import binance.model.account.margin.IsolatedMarginAccount;

@Component
public class SpotAccount extends Account {

	private static Logger logger = LogManager.getLogger(IsolatedMarginAccount.class);

	
	public SpotAccount() {
		super(AccountType.SPOT,logger);
	}

}
