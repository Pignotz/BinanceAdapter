package binance.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import binance.CommonDef;

public class CoinBalance {

	
	private String coin;
	private List<CoinBalanceEntry> balanceHistory;
	
	public CoinBalance(String coin) {
		this.coin=coin;
		this.balanceHistory = new ArrayList<CoinBalance.CoinBalanceEntry>();
	}
	
	
	public void addCoinBalanceEntry(String coin, BigDecimal amount, String counterValueCoin, BigDecimal counterValueAmount) {

		balanceHistory.add(0,new CoinBalanceEntry(coin, amount, counterValueCoin, counterValueAmount));
		
	}	
	
	public static class CoinBalanceEntry {
		private final String coin;
		private BigDecimal amount;
		private final String counterValueCoin;
		private BigDecimal counterValueAmount;
		
		private BigDecimal priceOfCoin;
		public CoinBalanceEntry(String coin, BigDecimal amount, String counterValueCoin, BigDecimal counterValueAmount) {
			this.coin=coin;
			this.amount = amount;
			this.counterValueCoin=counterValueCoin;
			this.counterValueAmount=counterValueAmount;
			if(this.counterValueAmount.compareTo(BigDecimal.ZERO)<0 || this.amount.compareTo(BigDecimal.ZERO)< 0) {
				throw new RuntimeException("Error here we always want positive values");
			}
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

		public BigDecimal getPriceOfIncomeCoin() {
			BigDecimal price = counterValueAmount.divide(amount,CommonDef.BIG_DECIMAL_DIVISION_SCALE, RoundingMode.HALF_UP);
			if(priceOfCoin==null) {
				priceOfCoin = price;
			}else {
				if(priceOfCoin.compareTo(price)!=0) {
					throw new RuntimeException();
				}
			}
			return priceOfCoin;
		}

		public void setAmount(BigDecimal amount) {
			this.amount = amount;
		}

		public void setCounterValueAmount(BigDecimal counterValueAmount) {
			this.counterValueAmount = counterValueAmount;
		}

		@Override
		public String toString() {
			return "CoinBalanceEntry bought "+ getAmount() + " " + getCoin() + " for " + getCounterValueAmount() + " " + getCounterValueCoin();
		}		
		
		
		
		
	}




	public List<CoinBalanceEntry> getBalanceHistory() {
		return balanceHistory;
	}


	public void setBalanceHistory(List<CoinBalanceEntry> balanceHistory) {
		this.balanceHistory = balanceHistory;
	}



	
	
}
