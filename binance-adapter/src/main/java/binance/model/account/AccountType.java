package binance.model.account;

public enum AccountType {
	SPOT("Spot"),
	CROSS_MARGIN("Cross Margin"),
	ISOLATED_MARGIN("Isolated Margin");
	
	
	
	private final String transactionCrossReference;

	private AccountType(String transactionCrossReference) {
		this.transactionCrossReference = transactionCrossReference;
	}

	public String getTransactionCrossReference() {
		return transactionCrossReference;
	}

	public static AccountType getAccountType(String account) {
		for (AccountType at : values()) {
			if(at.getTransactionCrossReference().equals(account)) {
				return at;
			}
		}
		return null;
	}
	
	
}