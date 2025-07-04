package binance.struct;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TataxRecord {

	private String symbol = "";
	private String tokenAddress = "";
	private LocalDateTime timeStamp;
	private TataxOperationType movementType;
	private BigDecimal quantity;
	private BigDecimal countervalue;
	private String symbolCountervalue = "";
	private BigDecimal userCountervalue;
	private String userSymbolCountervalue = "";
	private BigDecimal sourceCountervalue;
	private String sourceSymbolCountervalue = "";
	
	//Variable for app control
	private boolean ignore = false;


	private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	public TataxRecord() {};
	
	
	public TataxRecord(LocalDateTime timeStamp, String symbol, BigDecimal quantity, TataxOperationType tataxOperationType) {
		this.timeStamp=timeStamp;
		this.symbol = symbol;
		this.quantity = quantity;
		this.movementType = tataxOperationType;
	}
	
	public TataxRecord(LocalDateTime timeStamp, String symbol, BigDecimal quantity, TataxOperationType tataxOperationType, BigDecimal counterValue, String symbolCounterValue) {
		this.timeStamp=timeStamp;
		this.symbol = symbol;
		this.quantity = quantity;
		this.movementType = tataxOperationType;
		this.countervalue=counterValue;
		this.symbolCountervalue=symbolCounterValue;
		this.userCountervalue=counterValue;
		this.userSymbolCountervalue=symbolCounterValue;
		this.sourceCountervalue=counterValue;
		this.sourceSymbolCountervalue=symbolCounterValue;
	}
	
	public TataxRecord(LocalDateTime timeStamp, String symbol, BigDecimal quantity, TataxOperationType tataxOperationType, BigDecimal counterValue, String symbolCounterValue, boolean ignore) {
		this.timeStamp=timeStamp;
		this.symbol = symbol;
		this.quantity = quantity;
		this.movementType = tataxOperationType;
		this.countervalue=counterValue;
		this.symbolCountervalue=symbolCounterValue;
		this.userCountervalue=counterValue;
		this.userSymbolCountervalue=symbolCounterValue;
		this.sourceCountervalue=counterValue;
		this.sourceSymbolCountervalue=symbolCounterValue;
		this.ignore=true;
	}
		
	public TataxRecord(BinanceHistoryRecord binanceHistoryRecord) {
		this.symbol=binanceHistoryRecord.getCoin();
		this.timeStamp=binanceHistoryRecord.getUtcTime();
		switch (binanceHistoryRecord.getOperation().getTataxMapping()) {
		case DECIDE_BASED_ON_AMOUNT: {
			if(binanceHistoryRecord.getChange().compareTo(BigDecimal.ZERO)<0) {
				if(binanceHistoryRecord.getOperation().equals(BinanceOperationType.TRANSFER_ACCOUNT)) {
					this.movementType=TataxOperationType.WITHDRAWAL;

				}else {
					this.movementType=TataxOperationType.DEBIT;
				}
			}else {
				if(binanceHistoryRecord.getOperation().equals(BinanceOperationType.TRANSFER_ACCOUNT)) {
					this.movementType=TataxOperationType.DEPOSIT;
				}else {
					this.movementType=TataxOperationType.CREDIT;
				}
			}
			break;
		}
		default:
			this.movementType=binanceHistoryRecord.getOperation().getTataxMapping();
			break;
		}

		if(binanceHistoryRecord.getChange().compareTo(BigDecimal.ZERO)<0) {
			this.quantity=binanceHistoryRecord.getChange().negate();
		}else {
			this.quantity=binanceHistoryRecord.getChange();
		}
	}


	public String getSymbol() {
		return symbol;
	}

	public String getTokenAddress() {
		return tokenAddress;
	}

	public LocalDateTime getTimeStamp() {
		return timeStamp;
	}
	public TataxOperationType getMovementType() {
		return movementType;
	}


	public BigDecimal getQuantity() {
		return quantity;
	}

	public BigDecimal getCountervalue() {
		return countervalue;
	}


	public String getSymbolCountervalue() {
		return symbolCountervalue;
	}

	public BigDecimal getUserCountervalue() {
		return userCountervalue;
	}

	public String getUserSymbolCountervalue() {
		return userSymbolCountervalue;
	}

	public BigDecimal getSourceCountervalue() {
		return sourceCountervalue;
	}

	public String getSourceSymbolCountervalue() {
		return sourceSymbolCountervalue;
	}


	public String[] toCsvRecord() {
	    return new String[] {
	        symbol, 
	        tokenAddress, 
	        formatter.format(timeStamp), 
	        movementType.toString(), 
	        quantity.setScale(12,RoundingMode.HALF_UP).toString(),
	        countervalue == null ? "" : countervalue.setScale(12,RoundingMode.HALF_UP).toString(), 
	        symbolCountervalue, 
	        userCountervalue == null ? "" : userCountervalue.setScale(12,RoundingMode.HALF_UP).toString(), 
	        userSymbolCountervalue,
	        sourceCountervalue == null ? "" : sourceCountervalue.setScale(12,RoundingMode.HALF_UP).toString(),
	        sourceSymbolCountervalue
	    };
	}

	@Override
	public String toString() {
		return "TataxRecord [symbol=" + symbol + ", timeStamp=" + timeStamp
				+ ", movementType=" + movementType + ", quantity=" + quantity + ", countervalue=" + countervalue
				+ ", symbolCountervalue=" + symbolCountervalue+ "]";
	}


	public void setQuantity(BigDecimal quantity) {
		this.quantity = quantity;
	}


	public boolean isIgnore() {
		return ignore;
	}


	public void setIgnore(boolean ignore) {
		this.ignore = ignore;
	}	
	
}
