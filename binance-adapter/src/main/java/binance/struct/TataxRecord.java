package binance.struct;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TataxRecord {

	private String symbol = "";
	private String tokenAddress = "";
	private LocalDateTime timeStamp;
	private TataxOperationType movementType;
	private BigDecimal quantity;
	private String countervalue = "";
	private String symbolCountervalue = "";
	private String userCountervalue = "";
	private String userSymbolCountervalue = "";
	private String sourceCountervalue = "";
	private String sourceSymbolCountervalue = "";


	private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	
	public TataxRecord(BinanceHistoryRecord binanceHistoryRecord) {
		this.symbol=binanceHistoryRecord.getCoin();
		this.timeStamp=binanceHistoryRecord.getUtcTime();
		switch (binanceHistoryRecord.getOperation().getTataxMapping()) {
		case DECIDE_BASED_ON_AMOUNT: {
			if(binanceHistoryRecord.getChange().compareTo(BigDecimal.ZERO)<0) {
				this.movementType=TataxOperationType.DEBIT;
			}else {
				this.movementType=TataxOperationType.CREDIT;
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

	public String getCountervalue() {
		return countervalue;
	}


	public String getSymbolCountervalue() {
		return symbolCountervalue;
	}

	public String getUserCountervalue() {
		return userCountervalue;
	}

	public String getUserSymbolCountervalue() {
		return userSymbolCountervalue;
	}

	public String getSourceCountervalue() {
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
	        quantity.toString(),
	        countervalue.toString(), 
	        symbolCountervalue, 
	        userCountervalue, 
	        userSymbolCountervalue,
	        sourceCountervalue, 
	        sourceSymbolCountervalue
	    };
	}
	
	
	
}
