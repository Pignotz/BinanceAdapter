package binance.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Stack;

public class CoinBalance {

	
	private String coin;
	private Stack<CoinBalanceEntry> balanceHistory;
	
	public CoinBalance(String coin) {
		this.coin=coin;
		this.balanceHistory = new Stack<CoinBalance.CoinBalanceEntry>();
	}
	
	
	public addCoinBalanceEntry(String coin, BigDecimal amount, String counterValueCoin, BigDecimal counterValueAmount) {
		
	}
	
	
	
	
	
	public static class CoinBalanceEntry {
		private final String coin;
		private final BigDecimal amount;
		private final String counterValueCoin;
		private final BigDecimal counterValueAmount;
		
		public CoinBalanceEntry(String coin, BigDecimal amount, String counterValueCoin, BigDecimal counterValueAmount) {
			this.coin=coin;
			this.amount = amount;
			this.counterValueCoin=counterValueCoin;
			this.counterValueAmount=counterValueAmount;
		}

		public String getCoin() {
			return coin;
		}

		public BigDecimal getAmount() {
			return amount;
		}

		public String getCounterValueCoin() {
			return counterValueCoin;
		}

		public BigDecimal getCounterValueAmount() {
			return counterValueAmount;
		}
		
		
	}
	
}
