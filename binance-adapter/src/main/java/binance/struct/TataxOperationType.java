package binance.struct;

public enum TataxOperationType {

	IGNORE(null),
	DECIDE_BASED_ON_AMOUNT(null),
	DEPOSIT(false),
	CREDIT(false),
	//FUNDING_FEE,
	//STAKING,
	//FUNDING_FEE_CREDIT,
	EARN(false),
	//PROFIT,
	AIRDROP(false),
	//FORK,
	//MINING,
	//CASHBACK,
	//DONATION_RECEIVED,
	//DONATION_SENT,
	DEBIT(true),
	EXCHANGE_FEE(true),
	WITHDRAWAL(true);
	//FUNDING_FEE_DEBIT,
	//BLOCKCHAIN_FEE,
	//LOSS,
	
	
	TataxOperationType(Boolean object) {
		this.doNegateAmount=object;
	}

	private final Boolean doNegateAmount;

	public Boolean getDoNegateAmount() {
		return doNegateAmount;
	}
	
	
	
}
