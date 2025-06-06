package binance.model.account.margin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import binance.struct.BinanceHistoryRecord;

public class Operation {
		private LocalDateTime utcTime;
		private String coinBought;
		private String coinSold;
		private String coinFee;
		private BigDecimal amountBought;
		private BigDecimal amountSold;
		private BigDecimal amountFee;

		private BigDecimal priceOfBoughtCoin;

		private Boolean boughtIsBase;
		public Operation(BinanceHistoryRecord binanceHistoryRecord) {
			switch (binanceHistoryRecord.getOperation()) {
			case TRANSACTION_SPEND:
				boughtIsBase = true;
				break;
			case TRANSACTION_SOLD:
				boughtIsBase = false;
				break;
			case CROSS_MARGIN_LIQUIDATION_SMALL_ASSET_TAKEOVER:
				//TODO verify
				boughtIsBase = true;
				break;
			default:
				throw new IllegalArgumentException("Unexpected operation type for: " + binanceHistoryRecord);
			}
		}
		public void computePrice() {
			priceOfBoughtCoin = amountSold.negate().divide(amountBought,8, RoundingMode.CEILING);
		}
		public LocalDateTime getUtcTime() {
			return utcTime;
		}
		public void setUtcTime(LocalDateTime utcTime) {
			this.utcTime = utcTime;
		}
		public String getCoinBought() {
			return coinBought;
		}
		public void setCoinBought(String coinBought) {
			this.coinBought = coinBought;
		}
		public String getCoinSold() {
			return coinSold;
		}
		public void setCoinSold(String coinSold) {
			this.coinSold = coinSold;
		}
		public String getCoinFee() {
			return coinFee;
		}
		public void setCoinFee(String coinFee) {
			this.coinFee = coinFee;
		}
		public BigDecimal getAmountBought() {
			return amountBought;
		}
		public void setAmountBought(BigDecimal amountBought) {
			this.amountBought = amountBought;
		}
		public BigDecimal getAmountSold() {
			return amountSold;
		}
		public void setAmountSold(BigDecimal amountSold) {
			this.amountSold = amountSold;
		}
		public BigDecimal getAmountFee() {
			return amountFee;
		}
		public void setAmountFee(BigDecimal amountFee) {
			this.amountFee = amountFee;
		}
		public BigDecimal getPriceOfBoughtCoin() {
			return priceOfBoughtCoin;
		}
		public void setPriceOfBoughtCoin(BigDecimal priceOfBoughtCoin) {
			this.priceOfBoughtCoin = priceOfBoughtCoin;
		}
		public boolean isBoughtIsBase() {
			return boughtIsBase;
		}
		public void setBoughtIsBase(boolean boughtIsBase) {
			this.boughtIsBase = boughtIsBase;
		}
		
		
		
	}