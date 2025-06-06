package binance.step;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
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

import com.opencsv.CSVWriter;

import binance.struct.BinanceHistoryRecord;
import binance.struct.BinanceOperationType;
import binance.struct.TataxOperationType;
import binance.struct.TataxRecord;
import jakarta.annotation.PostConstruct;

@Component
public class BinanceHistoryStepConfig {

	private Logger logger = LogManager.getLogger(BinanceHistoryStepConfig.class);

	@Value("file:input/*.csv") // Reads all CSV files from the "input" directory
	private Resource[] inputFiles;

	@Autowired private JobRepository jobRepository;
	@Autowired private PlatformTransactionManager platformTransactionManager;

	private List<BinanceHistoryRecord> binanceHistoryRecords;
	private Map<String,BigDecimal> realTimeAprAdjuster;


	@PostConstruct
	private void init() {
		binanceHistoryRecords = new ArrayList<BinanceHistoryRecord>();
		realTimeAprAdjuster = new ConcurrentHashMap<String, BigDecimal>();
	}

	@Bean
	public Job binanceHistoryJob() {
		return new JobBuilder("binanceHistoryJob",jobRepository)
				.incrementer(new RunIdIncrementer()) // Allows re-execution with a new run ID
				.start(readStep())
				.next(aggregate())
				.next(verifyCoherenceStep())
				.next(adaptAndWriteStep())
				.next(computeMarginPlusMinus())
				.build();
	}




	//STEP1
	@Bean
	public Step readStep() {
		return new StepBuilder("binanceHistoryStep", jobRepository)
				.<BinanceHistoryRecord, BinanceHistoryRecord>chunk(10,platformTransactionManager) // Process 10 records at a time
				.reader(multiFileReader())
				.processor(this::processRecord)
				.writer(chunk -> chunk.forEach(e -> binanceHistoryRecords.add(e)))
				.build();
	}

	/**
	 * Multi-file reader to read all Binance history CSV files in order
	 */
	@Bean
	public MultiResourceItemReader<BinanceHistoryRecord> multiFileReader() {
		MultiResourceItemReader<BinanceHistoryRecord> multiReader = new MultiResourceItemReader<>();
		multiReader.setResources(inputFiles);
		multiReader.setDelegate(csvReader());
		return multiReader;
	}



	/**
	 * Processor - modify records if needed (e.g., clean data, apply transformations)
	 */
	public BinanceHistoryRecord processRecord(BinanceHistoryRecord record) {
		logger.debug("processing {}",record);
		if(record.getCoin().equals("LDBTC"))return null;

		switch (record.getOperation()) {
		case ISOLATED_MARGIN_LOAN, MARGIN_LOAN:
			record.setUtcTime(record.getUtcTime().minusSeconds(1l));
		break;
		case ISOLATED_MARGIN_REPAYMENT, MARGIN_REPAYMENT:
			record.setUtcTime(record.getUtcTime().plusSeconds(1l));
		break;
		case SIMPLE_EARN_FLEXIBLE_SUBSCRIPTION:
			if(record.getChange().compareTo(BigDecimal.ZERO)>=0) {
				throw new RuntimeException("change is greater than 0 in a subscription");
			}
			BigDecimal startingValue = realTimeAprAdjuster.get(record.getCoin());
			if(startingValue!=null) {
				realTimeAprAdjuster.put(record.getCoin(), startingValue.add(record.getChange().negate()));
			}else {
				realTimeAprAdjuster.put(record.getCoin(), record.getChange().negate());
			}
			return null;
		case SIMPLE_EARN_FLEXIBLE_REDEMPTION:
			startingValue = realTimeAprAdjuster.get(record.getCoin());
			if(startingValue==null) {
				throw new RuntimeException("no previous accumulated value found");
			}
			if(record.getChange().compareTo(startingValue)>0) {
				realTimeAprAdjuster.remove(record.getCoin());
				BigDecimal realTimeAprReward = record.getChange().add(startingValue.negate());
				BinanceHistoryRecord realTimeAprRecordToReturn = new BinanceHistoryRecord();
				realTimeAprRecordToReturn.setAccount(record.getAccount());
				realTimeAprRecordToReturn.setChange(realTimeAprReward);
				realTimeAprRecordToReturn.setCoin(record.getCoin());
				realTimeAprRecordToReturn.setOperation(BinanceOperationType.SIMPLE_EARN_FLEXIBLE_INTEREST);
				realTimeAprRecordToReturn.setUserId(record.getUserId());
				realTimeAprRecordToReturn.setUtcTime(record.getUtcTime());
				return realTimeAprRecordToReturn;
			}else {
				realTimeAprAdjuster.put(record.getCoin(), startingValue.add(record.getChange().negate()));
				return null;
			}

		default:
			break;
		}

		if(record.getOperation().getTataxMapping().equals(TataxOperationType.IGNORE))return null;
		return record;
	}
	/**
	 * CSV Reader for Binance history files
	 */
	@Bean
	public FlatFileItemReader<BinanceHistoryRecord> csvReader() {
		FlatFileItemReader<BinanceHistoryRecord> reader = new FlatFileItemReader<>();
		reader.setLinesToSkip(1); // Skip header

		DefaultLineMapper<BinanceHistoryRecord> lineMapper = new DefaultLineMapper<>();

		// Tokenize CSV fields
		DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
		tokenizer.setNames("userId", "utcTime", "account", "operation", "coin", "change", "remark");
		tokenizer.setDelimiter(","); // Ensure delimiter is correct

		// Map CSV columns to the BinanceHistoryRecord object
		FieldSetMapper<BinanceHistoryRecord> fieldSetMapper = new FieldSetMapper<BinanceHistoryRecord>() {
			@Override
			public BinanceHistoryRecord mapFieldSet(FieldSet fieldSet) {
				BinanceHistoryRecord record = new BinanceHistoryRecord();
				record.setUserId(fieldSet.readString("userId"));
				// Custom parsing for LocalDateTime (using DateTimeFormatter)
				String utcTimeString = fieldSet.readString("utcTime");
				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");  // Adjust the format as per your data
				record.setUtcTime(LocalDateTime.parse(utcTimeString, formatter));
				record.setAccount(fieldSet.readString("account"));
				record.setOperation(BinanceOperationType.fromDisplayName(fieldSet.readString("operation")));
				record.setCoin(fieldSet.readString("coin"));
				// Reading the change value as a string
				String changeString = fieldSet.readString("change");
				// Converting the string value to BigDecimal
				BigDecimal change = new BigDecimal(changeString);
				// Setting the BigDecimal value on the record
				record.setChange(change);
				record.setRemark(fieldSet.readString("remark"));
				return record;
			}
		};

		// Customize the FieldSetMapper to parse LocalDateTime
		lineMapper.setLineTokenizer(tokenizer);
		lineMapper.setFieldSetMapper(fieldSetMapper);

		reader.setLineMapper(lineMapper);
		return reader;
	}

	@Bean
	public Step aggregate() {
		return new StepBuilder("processStep", jobRepository)
				.tasklet(new Tasklet() {

					@Override
					public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
						Map<String,Map<LocalDateTime,Map<BinanceOperationType,Map<String,List<BinanceHistoryRecord>>>>> aggregateMap = binanceHistoryRecords.stream().collect(Collectors.groupingBy(r->r.getAccount(),Collectors.groupingBy(r->r.getUtcTime(),Collectors.groupingBy(r -> r.getOperation(),Collectors.groupingBy(r -> r.getCoin())))));
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
						binanceHistoryRecords = aggregateList;
						logger.info("AGGREGATED RECORDS COUNT = {}", aggregateList.size());
						return null;
					}
				},platformTransactionManager).build();
	}

	@Bean
	public Step verifyCoherenceStep() {
		return new StepBuilder("processStep", jobRepository)
				.tasklet(new Tasklet() {

					@Override
					public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {

						binanceHistoryRecords.stream().forEach(r -> {
							switch (r.getOperation().getTataxMapping()) {
							case WITHDRAWAL, DEBIT,  EXCHANGE_FEE:
								if(r.getChange().compareTo(BigDecimal.ZERO)>=0) {
									logger.error("Expected less than zero change for: {}",r);
									throw new RuntimeException();
								}
							break;
							case AIRDROP,CREDIT,DEPOSIT,EARN :
								if(r.getChange().compareTo(BigDecimal.ZERO)<0) {
									logger.error("Expected more than or equal to zero change for: {}",r);
									throw new RuntimeException();
								}
							break;
							case DECIDE_BASED_ON_AMOUNT :
								break;
							default:
								throw new IllegalArgumentException("Unexpected value: " + r.getOperation().getTataxMapping());
							}
						});



						Map<String,List<BinanceHistoryRecord>> binanceHistoryRecordsPerCoin =  binanceHistoryRecords.stream().collect(Collectors.groupingBy(e -> e.getCoin()));
						binanceHistoryRecordsPerCoin.values().forEach(list -> Collections.sort(list, 
								(e1,e2)-> {
									int delta = e1.getUtcTime().compareTo(e2.getUtcTime());
									if(delta==0) {
										delta = e2.getChange().compareTo(e1.getChange());
									}
									return delta;
								}));

						binanceHistoryRecordsPerCoin.values().stream().forEach(list -> {
							BigDecimal change = BigDecimal.ZERO;
							for (BinanceHistoryRecord binanceHistoryRecord : list) {
								logger.info("CumulativeChange {} - record {}",change, binanceHistoryRecord);
								change = change.add(binanceHistoryRecord.getChange());
								if (change.compareTo(BigDecimal.ZERO) < 0) {
									throw new RuntimeException("Change for coin "+binanceHistoryRecord.getCoin()+" is less than 0 - change is "+change);
								}
							}
						});
						return null;
					}
				},platformTransactionManager).build();
	}



	@Bean
	public Step adaptAndWriteStep() {
		return new StepBuilder("processStep", jobRepository)
				.tasklet(new Tasklet() {

					@Override
					public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {

						Map<Integer,List<BinanceHistoryRecord>> recordsPerYear = binanceHistoryRecords.stream().collect(Collectors.groupingBy(e -> e.getUtcTime().getYear()));
						Date now = Date.from(Instant.now());
						SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
						String formattedDate = sdf.format(now);
						recordsPerYear.entrySet().forEach(entry -> {
							try {
								writeFileBinanceComparator(formattedDate,formattedDate+"_BinanceTataxAdaptedByTimeStamp_"+entry.getKey()+".csv", entry.getValue(), (BinanceHistoryRecord e1, BinanceHistoryRecord e2)-> {
									int delta = e1.getUtcTime().compareTo(e2.getUtcTime());
									if(delta==0) {
										delta = e2.getChange().compareTo(e1.getChange());
									}
									return delta;
								}, true);
								writeFileTataxRecordComparator(formattedDate,formattedDate+"_BinanceTataxAdaptedByBySymbol"+entry.getKey()+".csv", entry.getValue(), (TataxRecord e1, TataxRecord e2)-> {
									int delta = e1.getSymbol().compareTo(e2.getSymbol());
									if(delta==0) {
										delta = e2.getTimeStamp().compareTo(e1.getTimeStamp());
									}
									if(delta==0) {
										delta = e2.getMovementType().compareTo(e1.getMovementType());
									}
									return delta;
								});
								writeFileBinanceComparator(formattedDate, formattedDate+"_BinanceNotAdaptedByTimeStamp"+entry.getKey()+".csv",entry.getValue(), (BinanceHistoryRecord e1, BinanceHistoryRecord e2)-> {
									int delta = e1.getUtcTime().compareTo(e2.getUtcTime());
									if(delta==0) {
										delta = e2.getChange().compareTo(e1.getChange());
									}
									return delta;
								},false);
							} catch (IOException e1) {
								logger.error(e1.getMessage(),e1);
								throw new RuntimeException(e1);
							}
						});
						return null;
					}
				},platformTransactionManager).build();
	}

	private void writeFileTataxRecordComparator(String subFolder, String fileName,
			List<BinanceHistoryRecord> binanceHistoryRecords, Comparator<TataxRecord> comparator) throws IOException {
		List<TataxRecord> tataxAdaptedRecords = binanceHistoryRecords.stream().map(e -> new TataxRecord(e)).sorted(comparator).collect(Collectors.toList());
		writeFile(subFolder,fileName, tataxAdaptedRecords);
	}

	private void writeFileBinanceComparator(String subFolder, String fileName, List<BinanceHistoryRecord> binanceHistoryRecords, Comparator<BinanceHistoryRecord> comparator, boolean adapt) throws IOException {

		if(adapt) {
			List<TataxRecord> tataxAdaptedRecords = binanceHistoryRecords.stream().sorted(comparator).map(e -> new TataxRecord(e)).collect(Collectors.toList());
			writeFile(subFolder, fileName, tataxAdaptedRecords);
		} else {
			binanceHistoryRecords = binanceHistoryRecords.stream().sorted(comparator).collect(Collectors.toList());
			writeFile2(subFolder, fileName, binanceHistoryRecords);
		}

	}


	private void writeFile(String subFolder, String fileName, List<TataxRecord> tataxRecords) throws IOException {
		try {
			Path outputPath = Paths.get("output/"+subFolder);
			Files.createDirectories(outputPath); // Ensure directory exists

			File file = new File(outputPath.toFile(), fileName);
			// Ensure the file is deleted before writing
			if (file.exists()) {
				file.delete();
			}

			try (CSVWriter writer = new CSVWriter(new FileWriter(file), 
					CSVWriter.DEFAULT_SEPARATOR, 
					CSVWriter.DEFAULT_QUOTE_CHARACTER, 
					CSVWriter.DEFAULT_ESCAPE_CHARACTER, 
					System.lineSeparator())) {
				// Header with quotes around each field
				String[] header = {
						"Symbol", "TokenAddress", "TimeStamp", "MovementType", "Quantity", 
						"Countervalue", "SymbolCountervalue", "UserCountervalue", 
						"UserSymbolCountervalue", "SourceCountervalue", "SourceSymbolCountervalue"
				};
				writer.writeNext(header);

				// Writing records
				for (TataxRecord tataxRecord : tataxRecords) {
					// Use toCsvRecord to get the array
					String[] record = tataxRecord.toCsvRecord();
					writer.writeNext(record);
				}
			}
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
			throw e;            
		}
	}

	private void writeFile2(String subFolder, String fileName, List<BinanceHistoryRecord> binanceRecords) throws IOException {
		try {
			Path outputPath = Paths.get("output/"+subFolder);
			Files.createDirectories(outputPath); // Ensure directory exists

			File file = new File(outputPath.toFile(), fileName);
			// Ensure the file is deleted before writing
			if (file.exists()) {
				file.delete();
			}

			try (CSVWriter writer = new CSVWriter(new FileWriter(file), 
					CSVWriter.DEFAULT_SEPARATOR, 
					CSVWriter.DEFAULT_QUOTE_CHARACTER, 
					CSVWriter.DEFAULT_ESCAPE_CHARACTER, 
					System.lineSeparator())) {
				// Header with quotes around each field
				String[] header = {
						"User_ID","UTC_Time","Account","Operation","Coin","Change","Remark"
				};
				writer.writeNext(header);

				// Writing records
				for (BinanceHistoryRecord binanceHistoryRecord : binanceRecords) {
					// Use toCsvRecord to get the array
					String[] record = binanceHistoryRecord.toCsvRecord();
					writer.writeNext(record);
				}
			}
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
			throw e;            
		}
	}


	class Operation {
		LocalDateTime utcTime;
		String coinBought;
		String coinSold;
		String coinFee;
		BigDecimal amountBought;
		BigDecimal amountSold;
		BigDecimal amountFee;

		BigDecimal priceOfBoughtCoin;

		boolean boughtIsBase;
		public Operation(BinanceOperationType operation) {
			switch (operation) {
			case TRANSACTION_SPEND:
				boughtIsBase = true;
				break;
			case TRANSACTION_SOLD:
				boughtIsBase = false;
				break;
			default:
				throw new IllegalArgumentException("Unexpected value: " + operation);
			}
		}
		public void computePrice() {
			priceOfBoughtCoin = amountSold.negate().divide(amountBought);
		}
	}

	class Position {

		String loanedCoin;

		String baseCoin;
		String quoteCoin;
		List<BinanceHistoryRecord> loans = new ArrayList<BinanceHistoryRecord>();
		List<Operation> swaps = new ArrayList<Operation>();
		List<BinanceHistoryRecord> repays = new ArrayList<BinanceHistoryRecord>();

		public Position(BinanceHistoryRecord binanceHistoryRecord) {
			loanedCoin = binanceHistoryRecord.getCoin();
		}

		public void addOperation(Operation op) {
			if(swaps.isEmpty()) { //first op
				if(op.boughtIsBase) {
					baseCoin = op.coinBought;
					quoteCoin = op.coinSold;
				} else {
					quoteCoin = op.coinBought;
					baseCoin = op.coinSold;
				}
			} else {
				if(op.boughtIsBase && (!baseCoin.equals(op.coinBought) || !quoteCoin.equals(op.coinSold))) {
					throw new RuntimeException();
				} else if(!op.boughtIsBase && (!baseCoin.equals(op.coinSold) || !quoteCoin.equals(op.coinBought))) {
					throw new RuntimeException();
				}
			}
			swaps.add(op);
		}
		
		public String getPair() {
			return baseCoin+"/"+quoteCoin;
		}

		public void addLoan(BinanceHistoryRecord binanceHistoryRecord) {
			loans.add(binanceHistoryRecord);
			if(loanedCoin==null) {
				loanedCoin = binanceHistoryRecord.getCoin();
			} else if (!loanedCoin.equals(binanceHistoryRecord.getCoin())) {
				throw new RuntimeException();
			}
		}
		
		public boolean addRepayAndCheckClose(BinanceHistoryRecord binanceHistoryRecord) {
			boolean doClose = true;
			repays.add(binanceHistoryRecord);
			Map<String,List<BinanceHistoryRecord>> loansByCoin = loans.stream().collect(Collectors.groupingBy(l -> l.getCoin()));
			Map<String,List<BinanceHistoryRecord>> repayByCoin = repays.stream().collect(Collectors.groupingBy(l -> l.getCoin()));
			Map<String,BigDecimal> totalLoanedByCoin = new HashMap<String, BigDecimal>();
			Map<String,BigDecimal> totalRepayedByCoin = new HashMap<String, BigDecimal>();
			loansByCoin.entrySet().forEach(e -> {
				BigDecimal total = BigDecimal.ZERO;
				for (BinanceHistoryRecord loan : e.getValue()) {
					total = total.add(loan.getChange());
				}
				totalLoanedByCoin.put(e.getKey(), total);
			});
			repayByCoin.entrySet().forEach(e -> {
				BigDecimal total = BigDecimal.ZERO;
				for (BinanceHistoryRecord loan : e.getValue()) {
					total = total.add(loan.getChange());
				}
				totalRepayedByCoin.put(e.getKey(), total);
			});
			
			for(Entry<String, BigDecimal> e : totalLoanedByCoin.entrySet()) {
				if(totalRepayedByCoin.get(e.getKey()).compareTo(e.getValue())!=0) {
					doClose=false;
				}
			}			
			return doClose;
		}
	}




	@Bean
	public Step computeMarginPlusMinus() {
		return new StepBuilder("processStep", jobRepository)
				.tasklet(new Tasklet() {
					@Override
					public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
						
						
						
						List<BinanceHistoryRecord> wrkBinanceHistoryRecords = binanceHistoryRecords.stream().filter(r -> r.getOperation().getNeededForMargin()).sorted().collect(Collectors.toList());
						List<Position> positions = new ArrayList<BinanceHistoryStepConfig.Position>();
						Position p = null;
						List<Operation> ops = new ArrayList<BinanceHistoryStepConfig.Operation>();
						Operation op = null;
						for (BinanceHistoryRecord binanceHistoryRecord : wrkBinanceHistoryRecords) {
							logger.info(binanceHistoryRecord);
							switch (binanceHistoryRecord.getOperation()) {
							case TRANSFER_ACCOUNT:
								if(binanceHistoryRecord.getAccount().equalsIgnoreCase("spot")) {
									if(binanceHistoryRecord.getChange().compareTo(BigDecimal.ZERO)<0) {
										//TODO
									}else {
										//TODO
									}
								}else {
									if(binanceHistoryRecord.getChange().compareTo(BigDecimal.ZERO)<0) {
										//TODO
									}else {
										//TODO
									}
								}
								break;
							case ISOLATED_MARGIN_LOAN, MARGIN_LOAN:
								p = new Position(binanceHistoryRecord);
							p.addLoan(binanceHistoryRecord);
							break;
							case TRANSACTION_SOLD, TRANSACTION_SPEND:
								if(binanceHistoryRecord.isMarginAccount()) {
								op = new Operation(binanceHistoryRecord.getOperation());
								op.utcTime=binanceHistoryRecord.getUtcTime();
								op.amountSold=binanceHistoryRecord.getChange();
								op.coinSold=binanceHistoryRecord.getCoin();
								p.addOperation(op);
								}
							break;
							case TRANSACTION_FEE:
								if(binanceHistoryRecord.isMarginAccount()) {
								op.amountFee=binanceHistoryRecord.getChange();
								op.coinFee=binanceHistoryRecord.getCoin();
								}
								break;
							case TRANSACTION_BUY, TRANSACTION_REVENUE:
								if(binanceHistoryRecord.isMarginAccount()) {

								op.amountBought=binanceHistoryRecord.getChange();
							op.coinBought=binanceHistoryRecord.getCoin();
							ops.add(op);
								}
							break;
							case ISOLATED_MARGIN_REPAYMENT, MARGIN_REPAYMENT:
								if(p.addRepayAndCheckClose(binanceHistoryRecord)) {
									positions.add(p);
									p=null;
								}
								break;
							default:
								throw new RuntimeException("Unmanaged Case");
							}
						}
						ops.stream().forEach(op1 -> op1.computePrice());
						return null;	
					}
				},platformTransactionManager).build();
	}




}
