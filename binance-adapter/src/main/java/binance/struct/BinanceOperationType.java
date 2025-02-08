package binance.struct;

public enum BinanceOperationType {

    FIAT_DEPOSIT("Fiat Deposit",TataxOperationType.DEPOSIT),
    TRANSACTION_FEE("Transaction Fee",TataxOperationType.EXCHANGE_FEE),
    TRANSACTION_BUY("Transaction Buy",TataxOperationType.DECIDE_BASED_ON_AMOUNT),
    TRANSACTION_SPEND("Transaction Spend",TataxOperationType.DECIDE_BASED_ON_AMOUNT),
    CASHBACK_VOUCHER("Cashback Voucher",TataxOperationType.EARN),
    AIRDROP_ASSETS("Airdrop Assets",TataxOperationType.AIRDROP),
    SIMPLE_EARN_FLEXIBLE_INTEREST("Simple Earn Flexible Interest",TataxOperationType.EARN),
    SIMPLE_EARN_FLEXIBLE_RT_APR_INTEREST("Simple Earn Flexible realTimeApr Interest",TataxOperationType.EARN),
    SIMPLE_EARN_FLEXIBLE_SUBSCRIPTION("Simple Earn Flexible Subscription",TataxOperationType.IGNORE),
    SIMPLE_EARN_FLEXIBLE_REDEMPTION("Simple Earn Flexible Redemption",TataxOperationType.IGNORE),
    TRANSFER_ACCOUNT("Transfer Between Main Account/Futures and Margin Account",TataxOperationType.IGNORE),
    CROSS_MARGIN_LIQUIDATION_SMALL_ASSET_TAKEOVER("Cross Margin Liquidation - Small Assets Takeover",TataxOperationType.DECIDE_BASED_ON_AMOUNT),
    FUND_RECOVERY("Fund Recovery",TataxOperationType.DEBIT),
    WITHDRAW("Withdraw",TataxOperationType.WITHDRAWAL),
    ISOLATED_MARGIN_LIQUIDATION_FEE("Isolated Margin Liquidation - Fee", TataxOperationType.EXCHANGE_FEE),
    SIMPLE_EARN_LOCKED_SUBSCRIPTION("Simple Earn Locked Subscription",TataxOperationType.IGNORE),
    SIMPLE_EARN_LOCKED_REWARDS("Simple Earn Locked Rewards",TataxOperationType.EARN),
    CASH_VOUCHER("Cash Voucher",TataxOperationType.AIRDROP),
    TRANSACTION_REVENUE("Transaction Revenue",TataxOperationType.CREDIT),
    TRANSACTION_SOLD("Transaction Sold",TataxOperationType.DEBIT),
    ETH_2_0_STAKING("ETH 2.0 Staking",TataxOperationType.DECIDE_BASED_ON_AMOUNT),
    ETH_2_0_STAKING_REWARDS("ETH 2.0 Staking Rewards",TataxOperationType.EARN),
    BINANCE_CONVERT("Binance Convert",TataxOperationType.DECIDE_BASED_ON_AMOUNT),
    DEPOSIT("Deposit",TataxOperationType.DEPOSIT),
    SMALL_ASSETS_EXCHANGE_BNB("Small Assets Exchange BNB",TataxOperationType.DECIDE_BASED_ON_AMOUNT),
    ETH_2_0_STAKING_WITHDRAWALS("ETH 2.0 Staking Withdrawals",TataxOperationType.DECIDE_BASED_ON_AMOUNT),
    BETH_TO_WBETH_WRAPPING("BETH to WBETH Wrapping",TataxOperationType.DECIDE_BASED_ON_AMOUNT),
    MARGIN_LOAN("Margin Loan",TataxOperationType.CREDIT),
    MARGIN_REPAYMENT("Margin Repayment",TataxOperationType.DEBIT),
    ISOLATED_MARGIN_LOAN("Isolated Margin Loan",TataxOperationType.CREDIT),
    ISOLATED_MARGIN_REPAYMENT("Isolated Margin Repayment",TataxOperationType.DEBIT),
    BNB_FEE_DEDUCTION("BNB Fee Deduction",TataxOperationType.DECIDE_BASED_ON_AMOUNT),
    BUY("Buy",TataxOperationType.DECIDE_BASED_ON_AMOUNT),
    FEE("Fee",TataxOperationType.EXCHANGE_FEE),
    DISTRIBUTION("Distribution",TataxOperationType.AIRDROP);

    private final String displayName;
    private final TataxOperationType tataxMapping;

    
    
    // Constructor to assign display name to each enum constant
    BinanceOperationType(String displayName, TataxOperationType tataxOperationType) {
        this.displayName = displayName;
        this.tataxMapping=tataxOperationType;
    }
   

    // Getter method to retrieve the display name
    public String getDisplayName() {
        return displayName;
    }
    
    

    public TataxOperationType getTataxMapping() {
		return tataxMapping;
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
}
