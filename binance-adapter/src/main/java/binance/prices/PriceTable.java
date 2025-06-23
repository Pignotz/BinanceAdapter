package binance.prices;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class PriceTable {

	private List<PriceTableRecord> priceTableRecords;
	private Map<String,Map<LocalDateTime,BigDecimal>> priceTableRecordsByTime;
	private boolean isMapped;
	
	public PriceTable() {
	}
	
	@PostConstruct
	private void init() {
		priceTableRecords = new ArrayList<PriceTableRecord>();
	}
	
	public List<PriceTableRecord> getPriceTableRecords() {
		return priceTableRecords;
	}

	public void setPriceTableRecords(List<PriceTableRecord> priceTableRecords) {
		this.priceTableRecords = priceTableRecords;
	}
	
	public void addPriceTableRecord(PriceTableRecord toAdd) {
		priceTableRecords.add(toAdd);
	}
	
	public synchronized void mapWithTime() {
		if(!isMapped) {
			priceTableRecordsByTime = priceTableRecords.stream().collect(Collectors.groupingBy(r -> r.getSymbol(),Collectors.toMap(r->r.getTime(), r->r.getPriceInEur())));
			isMapped=true;
		}
	}
	
	public BigDecimal getPrice(String coin, LocalDateTime time) {
		mapWithTime();
		Map<LocalDateTime,BigDecimal> priceByTime = priceTableRecordsByTime.get(coin);
		if(priceByTime==null) {
			throw new RuntimeException("No Price in EUR for coin: " + coin + " at time: " + time);
		}
		BigDecimal price = priceByTime.get(time);
		if(price==null) {
			throw new RuntimeException("No Price in EUR for coin: " + coin + " at time: " + time);
		}
		return price;
	}

	public Map<String, Map<LocalDateTime, BigDecimal>> getPriceTableRecordsByTime() {
		return priceTableRecordsByTime;
	}

	public void setPriceTableRecordsByTime(Map<String, Map<LocalDateTime, BigDecimal>> priceTableRecordsByTime) {
		this.priceTableRecordsByTime = priceTableRecordsByTime;
	}

	public boolean isMapped() {
		return isMapped;
	}

	public void setMapped(boolean isMapped) {
		this.isMapped = isMapped;
	}
	
	
	
	
}
