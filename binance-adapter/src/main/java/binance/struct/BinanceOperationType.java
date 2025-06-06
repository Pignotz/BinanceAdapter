package binance.struct;

public enum BinanceOperationType {

    FIAT_DEPOSIT("Fiat Deposit",TataxOperationType.DEPOSIT, false),
   
    TRANSFER_ACCOUNT("Transfer Between Main Account/Futures and Margin Account",TataxOperationType.DECIDE_BASED_ON_AMOUNT, true),
    
    MARGIN_LOAN("Margin Loan",TataxOperationType.CREDIT,true),
    ISOLATED_MARGIN_LOAN("Isolated Margin Loan",TataxOperationType.CREDIT,true),

    TRANSACTION_SPEND("Transaction Spend",TataxOperationType.DECIDE_BASED_ON_AMOUNT,true),
    TRANSACTION_SOLD("Transaction Sold",TataxOperationType.DEBIT,true),
    TRANSACTION_FEE("Transaction Fee",TataxOperationType.EXCHANGE_FEE,true),
    TRANSACTION_BUY("Transaction Buy",TataxOperationType.DECIDE_BASED_ON_AMOUNT,true),
    TRANSACTION_REVENUE("Transaction Revenue",TataxOperationType.CREDIT,true),   

    CROSS_MARGIN_LIQUIDATION_SMALL_ASSET_TAKEOVER("Cross Margin Liquidation - Small Assets Takeover",TataxOperationType.DECIDE_BASED_ON_AMOUNT,true),
    ISOLATED_MARGIN_LIQUIDATION_FEE("Isolated Margin Liquidation - Fee", TataxOperationType.EXCHANGE_FEE,true),

    MARGIN_REPAYMENT("Margin Repayment",TataxOperationType.DEBIT,true),
    ISOLATED_MARGIN_REPAYMENT("Isolated Margin Repayment",TataxOperationType.DEBIT,true),


    
    
    CASHBACK_VOUCHER("Cashback Voucher",TataxOperationType.EARN,false),
    AIRDROP_ASSETS("Airdrop Assets",TataxOperationType.AIRDROP,false),
    SIMPLE_EARN_FLEXIBLE_INTEREST("Simple Earn Flexible Interest",TataxOperationType.EARN,false),
    SIMPLE_EARN_FLEXIBLE_SUBSCRIPTION("Simple Earn Flexible Subscription",TataxOperationType.IGNORE,false),
    SIMPLE_EARN_FLEXIBLE_REDEMPTION("Simple Earn Flexible Redemption",TataxOperationType.IGNORE,false),

    FUND_RECOVERY("Fund Recovery",TataxOperationType.DEBIT,false),
    WITHDRAW("Withdraw",TataxOperationType.WITHDRAWAL,false),
    SIMPLE_EARN_LOCKED_SUBSCRIPTION("Simple Earn Locked Subscription",TataxOperationType.IGNORE,false),
    SIMPLE_EARN_LOCKED_REWARDS("Simple Earn Locked Rewards",TataxOperationType.EARN,false),
    CASH_VOUCHER("Cash Voucher",TataxOperationType.AIRDROP,false),
    ETH_2_0_STAKING("ETH 2.0 Staking",TataxOperationType.DECIDE_BASED_ON_AMOUNT,false),
    ETH_2_0_STAKING_REWARDS("ETH 2.0 Staking Rewards",TataxOperationType.EARN,false),
    BINANCE_CONVERT("Binance Convert",TataxOperationType.DECIDE_BASED_ON_AMOUNT,false),
    DEPOSIT("Deposit",TataxOperationType.DEPOSIT,false),
    SMALL_ASSETS_EXCHANGE_BNB("Small Assets Exchange BNB",TataxOperationType.DECIDE_BASED_ON_AMOUNT,false),
    ETH_2_0_STAKING_WITHDRAWALS("ETH 2.0 Staking Withdrawals",TataxOperationType.DECIDE_BASED_ON_AMOUNT,false),
    BETH_TO_WBETH_WRAPPING("BETH to WBETH Wrapping",TataxOperationType.DECIDE_BASED_ON_AMOUNT,false),
    BNB_FEE_DEDUCTION("BNB Fee Deduction",TataxOperationType.DECIDE_BASED_ON_AMOUNT,false),
   // BUY("Buy",TataxOperationType.DECIDE_BASED_ON_AMOUNT,false),
    FEE("Fee",TataxOperationType.EXCHANGE_FEE,false),
    DISTRIBUTION("Distribution",TataxOperationType.AIRDROP,false);

    private final String displayName;
    private final TataxOperationType tataxMapping;
    private final Boolean neededForMargin;
    
    
    // Constructor to assign display name to each enum constant
    BinanceOperationType(String displayName, TataxOperationType tataxOperationType, Boolean neededForMargin) {
        this.displayName = displayName;
        this.tataxMapping=tataxOperationType;
        this.neededForMargin = neededForMargin;
    }
   

    // Getter method to retrieve the display name
    public String getDisplayName() {
        return displayName;
    }
    
    

    public TataxOperationType getTataxMapping() {
		return tataxMapping;
	}

    

	public Boolean getNeededForMargin() {
		return neededForMargin;
	}


	// Static method to get an enum by display name (if needed for CSV mapping or other operations)
    public static BinanceOperationType fromDisplayName(String displayName) {
        for (BinanceOperationType type : BinanceOperationType.values()) {
            if (type.getDisplayName().equalsIgnoreCase(displayName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown display name: " + displayName);
    }


	public boolean isRepayment() {
		return this.equals(MARGIN_REPAYMENT) || this.equals(ISOLATED_MARGIN_REPAYMENT);
	}


	boolean isLoan() {
		return this.equals(MARGIN_LOAN) || this.equals(ISOLATED_MARGIN_LOAN);

	}
}
