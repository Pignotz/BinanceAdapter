package binance.struct;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import binance.CommonDef;

public class Operation implements UtcTimedRecordWithMovement{
	private LocalDateTime utcTime;
	private String coinBought;
	private String coinSold;
	private String coinFee;
	private BigDecimal amountBought;
	private BigDecimal amountSold;
	private BigDecimal amountFee;


	private final Boolean longOperation;
	public Operation(BinanceHistoryRecord binanceHistoryRecord) {
		switch (binanceHistoryRecord.getOperation()) {
		case TRANSACTION_SPEND:
			longOperation = true;
			break;
		case TRANSACTION_SOLD:
			longOperation = false;
			break;
		case CROSS_MARGIN_LIQUIDATION_SMALL_ASSET_TAKEOVER:
			//TODO verify
			longOperation = true;
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
		return amountBought.negate().divide(amountSold,CommonDef.BIG_DECIMAL_DIVISION_SCALE, RoundingMode.HALF_UP);
	}

	public boolean isLongOperation() {
		return longOperation;
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

	@Override
	public String toReadableString() {
		return new StringBuilder()
				.append(toString())
				.append(" ")
				.append(" due to: SWAP with a price of ")
				.append(getPriceOfSoldCoin())
				.append(" ")
				.append(getCoinBought())
				.append(" per 1 ")
				.append(getCoinSold())
				.append(" on date: ")
				.append(utcTime)
				.toString();		
	}

}