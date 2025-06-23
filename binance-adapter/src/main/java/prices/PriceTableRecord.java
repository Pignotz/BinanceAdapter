package prices;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PriceTableRecord {

	private LocalDateTime time;
	private String Symbol;
	private BigDecimal priceInEur;
	public LocalDateTime getTime() {
		return time;
	}
	public void setTime(LocalDateTime time) {
		this.time = time;
	}
	public String getSymbol() {
		return Symbol;
	}
	public void setSymbol(String symbol) {
		Symbol = symbol;
	}
	public BigDecimal getPriceInEur() {
		return priceInEur;
	}
	public void setPriceInEur(BigDecimal priceInEur) {
		this.priceInEur = priceInEur;
	}
	
	
	
}
