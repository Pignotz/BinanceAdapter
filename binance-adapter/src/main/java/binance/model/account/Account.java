package binance.model.account;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

import binance.struct.BinanceHistoryRecord;

public class Account {

	protected Logger logger;

	
	protected final AccountType accountType;
	protected final List<BinanceHistoryRecord> records = new ArrayList<BinanceHistoryRecord>();
	
	public Account(AccountType accountType, Logger logger) {
		this.accountType = accountType;
		this.logger = logger;
	}
	
	public void addRecord(BinanceHistoryRecord r) {
		if(!accountType.getTransactionCrossReference().equals(r.getAccount())) {
			throw new RuntimeException("Wrong Transaction Account assignment");
		}
		records.add(r);
	}
	
	public void verifyTransactionCoherence() throws Exception {

		Map<String,List<BinanceHistoryRecord>> binanceHistoryRecordsPerCoin =  records.stream().collect(Collectors.groupingBy(e -> e.getCoin()));
		binanceHistoryRecordsPerCoin.values().forEach(list -> Collections.sort(list, 
				(e1,e2)-> {
					int delta = e1.getUtcTime().compareTo(e2.getUtcTime());
					if(delta==0) {
						delta = e2.getChange().compareTo(e1.getChange());
					}
					return delta;
				}));

		Map<String,BigDecimal> finalAmountOfCoin = new ConcurrentHashMap<String, BigDecimal>();
		binanceHistoryRecordsPerCoin.entrySet().parallelStream().forEach(entry -> {
			String coin = entry.getKey();
			BigDecimal change = BigDecimal.ZERO;
			for (BinanceHistoryRecord binanceHistoryRecord : entry.getValue()) {
				logger.info("CumulativeChange {} - record {}",change, binanceHistoryRecord);
				change = change.add(binanceHistoryRecord.getChange());
				if (change.compareTo(BigDecimal.ZERO) < 0) {
					throw new RuntimeException("Change for coin "+binanceHistoryRecord.getCoin()+" is less than 0 - change is "+change);
				}
			}
			finalAmountOfCoin.put(coin, change);
		});
		
		for (Entry<String, BigDecimal> finalAmountEntry : finalAmountOfCoin.entrySet()) {
			logger.log(Level.ALL, "Final {} balance for Account {} is {}", finalAmountEntry.getKey(), this.accountType, finalAmountEntry.getValue());
		}
	}
	
	
	
	
}
