package binance.job.steps;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.MultiResourceItemReader;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.item.file.transform.FieldSet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import binance.model.BinanceHistoryRecordList;
import binance.model.account.AccountType;
import binance.model.account.margin.CrossMarginAccount;
import binance.model.account.margin.IsolatedMarginAccount;
import binance.model.account.spot.SpotAccount;
import binance.struct.BinanceHistoryRecord;
import binance.struct.BinanceOperationType;
import binance.struct.TataxOperationType;
import jakarta.annotation.PostConstruct;

@Component
public class AggregateAssignStepConfig {

	private Logger logger = LogManager.getLogger(AggregateAssignStepConfig.class);

	@Autowired private JobRepository jobRepository;
	@Autowired private PlatformTransactionManager platformTransactionManager;
	@Autowired private BinanceHistoryRecordList binanceHistoryRecordList;

	@Autowired private SpotAccount spotAccount;
	@Autowired private CrossMarginAccount crossMarginAccount;
	@Autowired private IsolatedMarginAccount isolatedMarginAccount;


	@Bean
	public Step getAggregateAndAssignToAccountStep() {
		return new StepBuilder("AGGREGATE_STEP", jobRepository)
				.tasklet(new Tasklet() {

					@Override
					public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
						Map<String,Map<LocalDateTime,Map<BinanceOperationType,Map<String,List<BinanceHistoryRecord>>>>> aggregateMap = binanceHistoryRecordList.stream().collect(Collectors.groupingBy(r->r.getAccount(),Collectors.groupingBy(r->r.getUtcTime(),Collectors.groupingBy(r -> r.getOperation(),Collectors.groupingBy(r -> r.getCoin())))));
						List<BinanceHistoryRecord> aggregateList = new ArrayList<BinanceHistoryRecord>();
						for (Entry<String, Map<LocalDateTime, Map<BinanceOperationType, Map<String, List<BinanceHistoryRecord>>>>> e1 : aggregateMap.entrySet()) {
							String account = e1.getKey();
							for (Entry<LocalDateTime, Map<BinanceOperationType, Map<String, List<BinanceHistoryRecord>>>> e2 : e1.getValue().entrySet()) {
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
						binanceHistoryRecordList.clear();
						binanceHistoryRecordList.addAll(aggregateList);
						logger.info("AGGREGATED RECORDS COUNT = {}", aggregateList.size());

						//TODO Assign
						binanceHistoryRecordList.stream().forEach(r -> {
							AccountType accountType = r.getAccountType();
							switch (accountType) {
							case SPOT:
								spotAccount.addRecord(r);
								break;
							case CROSS_MARGIN:
								crossMarginAccount.addRecord(r);
								break;
							case ISOLATED_MARGIN:
								isolatedMarginAccount.addRecord(r);
								break;
							default:
								throw new RuntimeException("failed to identify account for transaction: "+r);
							}
						});
						spotAccount.verifyTransactionCoherence();
						crossMarginAccount.verifyTransactionCoherence();
						isolatedMarginAccount.verifyTransactionCoherence();
						return null;
					}
				},platformTransactionManager).build();
	}

}
