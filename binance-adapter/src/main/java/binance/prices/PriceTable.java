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
	private Map<String,Map<Integer,Map<Integer,Map<Integer,BigDecimal>>>> priceTableRecordsByTime;
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
	
	public synchronized void mapWithTime() {if (!isMapped) {
	    priceTableRecordsByTime = priceTableRecords.stream()
	            .collect(Collectors.groupingBy(
	                r -> r.getSymbol(),
	                Collectors.groupingBy(
	                    r -> r.getTime().getYear(),
	                    Collectors.groupingBy(
	                        r -> r.getTime().getMonthValue(),
	                        Collectors.toMap(
	                            r -> r.getTime().getDayOfMonth(),
	                            r -> r.getPriceInEur()
	                        )
	                    )
	                )
	            ));
	        isMapped = true;
	    }
}
	
	public BigDecimal getPrice(String coin, LocalDateTime time) {
		mapWithTime();try {
			return priceTableRecordsByTime.get(coin).get(time.getYear()).get(time.getMonthValue()).get(time.getDayOfMonth());
		}catch (NullPointerException e) {
			System.out.println("NotFound "+coin + "at time "+time);
			throw e;
		}
	}	

	public boolean isMapped() {
		return isMapped;
	}

	public void setMapped(boolean isMapped) {
		this.isMapped = isMapped;
	}
	
	
	
	
}
