package binance.model.account;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
import binance.struct.BinanceOperationType;

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
		binanceHistoryRecordsPerCoin.entrySet().stream().forEach(entry -> {
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

	public List<BinanceHistoryRecord> getRecords() {
		return records;
	}
	
	public void aggregate() {
		Map<String,Map<LocalDateTime,Map<BinanceOperationType,Map<String,List<BinanceHistoryRecord>>>>> aggregateMap = records.stream().collect(Collectors.groupingBy(r->r.getAccount(),Collectors.groupingBy(r->r.getUtcTime(),Collectors.groupingBy(r -> r.getOperation(),Collectors.groupingBy(r -> r.getCoin())))));
		List<BinanceHistoryRecord> aggregateList = new ArrayList<BinanceHistoryRecord>();
		for (Entry<String, Map<LocalDateTime, Map<BinanceOperationType, Map<String, List<BinanceHistoryRecord>>>>> e1 : aggregateMap.entrySet()) {
			String account = e1.getKey();
			for (Entry<LocalDateTime, Map<BinanceOperationType, Map<String, List<BinanceHistoryRecord>>>> e2 : e1.getValue().entrySet().stream().sorted((a, b)-> a.getKey().compareTo(b.getKey())).collect(Collectors.toList())) {
				LocalDateTime utcTime = e2.getKey();
				for (Entry<BinanceOperationType, Map<String, List<BinanceHistoryRecord>>> e3 : e2.getValue().entrySet()) {
					BinanceOperationType operation = e3.getKey();
					for (Entry<String, List<BinanceHistoryRecord>> e4 : e3.getValue().entrySet()) {
						String coin = e4.getKey();
						BigDecimal amount = BigDecimal.ZERO;
						for (BinanceHistoryRecord binanceHistoryRecord : e4.getValue()) {
							amount = amount.add(binanceHistoryRecord.getChange());
						}
						BinanceHistoryRecord aggregateBinanceHistoryRecord = new BinanceHistoryRecord();
						aggregateBinanceHistoryRecord.setUserId(e4.getValue().stream().findAny().get().getUserId());
						aggregateBinanceHistoryRecord.setAccount(account);
						aggregateBinanceHistoryRecord.setChange(amount);
						aggregateBinanceHistoryRecord.setCoin(coin);
						aggregateBinanceHistoryRecord.setOperation(operation);
						aggregateBinanceHistoryRecord.setUtcTime(utcTime);
						aggregateList.add(aggregateBinanceHistoryRecord);
					}
				}
			}
		}
		records.clear();
		aggregateList = aggregateList.stream().filter(r -> r.getChange().compareTo(BigDecimal.ZERO)!=0).collect(Collectors.toList());
		records.addAll(aggregateList);
	}
	
	
	
	
	
	
}
