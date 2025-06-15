package binance.struct;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

public class Operation implements UtcTimedRecordWithMovement{
		private LocalDateTime utcTime;
		private String coinBought;
		private String coinSold;
		private String coinFee;
		private BigDecimal amountBought;
		private BigDecimal amountSold;
		private BigDecimal amountFee;


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
			if(amountBought.compareTo(BigDecimal.ZERO)<0) {
				throw new RuntimeException();
			}
			this.amountBought = amountBought;
		}
		public BigDecimal getAmountSold() {
			return amountSold;
		}
		public void setAmountSold(BigDecimal amountSold) {
			if(amountSold.compareTo(BigDecimal.ZERO)>0) {
				throw new RuntimeException();
			}
			this.amountSold = amountSold;
		}
		public BigDecimal getAmountFee() {
			return amountFee;
		}
		public void setAmountFee(BigDecimal amountFee) {
			this.amountFee = amountFee;
		}
		public BigDecimal getPriceOfSoldCoin() {
			return amountBought.negate().divide(amountSold,8, RoundingMode.HALF_UP);
		}

		public boolean isBoughtIsBase() {
			return boughtIsBase;
		}
		public void setBoughtIsBase(boolean boughtIsBase) {
			this.boughtIsBase = boughtIsBase;
		}
		@Override
		public String getInCoin() {
			return coinBought;
		}
		@Override
		public String getOutCoin() {
			return coinSold;
		}
		@Override
		public BigDecimal getInAmount() {
			return getAmountBought();
		}
		@Override
		public BigDecimal getOutAmount() {
			return getAmountSold();
		}
		@Override
		public boolean isSwap() {
			return true;
		}

		@Override
		public String toString() {
			return getAmountBought() + " " + getCoinBought() + " for " + getAmountSold() + " " + getCoinSold();
		}
		
		
		
	}