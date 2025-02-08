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
import java.util.List;
import java.util.Map;
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
				.next(verifyCoherenceStep())
				.next(adaptAndWriteStep())
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
		case SIMPLE_EARN_FLEXIBLE_SUBSCRIPTION:
			//TODO controlla negativo
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
				realTimeAprRecordToReturn.setOperation(BinanceOperationType.SIMPLE_EARN_FLEXIBLE_RT_APR_INTEREST);
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
								});
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

	private void writeFileBinanceComparator(String subFolder, String fileName, List<BinanceHistoryRecord> binanceHistoryRecords, Comparator<BinanceHistoryRecord> comparator) throws IOException {
	
			List<TataxRecord> tataxAdaptedRecords = binanceHistoryRecords.stream().sorted(comparator).map(e -> new TataxRecord(e)).collect(Collectors.toList());
			writeFile(subFolder, fileName, tataxAdaptedRecords);

		
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



}
