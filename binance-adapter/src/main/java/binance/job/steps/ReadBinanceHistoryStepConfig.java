package binance.job.steps;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.MultiResourceItemReader;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.item.file.transform.FieldSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import binance.model.BinanceHistoryRecordList;
import binance.struct.BinanceHistoryRecord;
import binance.struct.BinanceOperationType;
import binance.struct.TataxOperationType;
import jakarta.annotation.PostConstruct;

@Component
public class ReadBinanceHistoryStepConfig {

	private Logger logger = LogManager.getLogger(ReadBinanceHistoryStepConfig.class);

	
	@Autowired private JobRepository jobRepository;
	@Autowired private PlatformTransactionManager platformTransactionManager;
	@Autowired private BinanceHistoryRecordList binanceHistoryRecordList;

	@Value("file:input/*.csv") // Reads all CSV files from the "input" directory
	private Resource[] inputFiles;

	private Map<String,BigDecimal> realTimeAprAdjuster;

	@PostConstruct
	private void init() {
		realTimeAprAdjuster = new ConcurrentHashMap<String, BigDecimal>();
	}

	//STEP1
	@Bean
	public Step getReadStepBinanceHistory() {
		return new StepBuilder("READ_STEP", jobRepository)
				.<BinanceHistoryRecord, BinanceHistoryRecord>chunk(10,platformTransactionManager) // Process 10 records at a time
				.reader(multiFileReader())
				.processor(this::processRecord)
				.writer(chunk -> chunk.forEach(e -> binanceHistoryRecordList.add(e)))
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
				
				//Validazione alcune volte l'operation type non corrisponde alla direzione del movimento
				if(record.getOperation().equals(BinanceOperationType.TRANSACTION_SOLD) && change.compareTo(BigDecimal.ZERO)>0) {
					logger.warn("BinanceOperationType changed from {} to {} due to inchoerence between type and change - original record {}",BinanceOperationType.TRANSACTION_SOLD, BinanceOperationType.TRANSACTION_BUY,record);
					record.setOperation(BinanceOperationType.TRANSACTION_BUY);
				}
				if(record.getOperation().equals(BinanceOperationType.TRANSACTION_BUY) && change.compareTo(BigDecimal.ZERO)<0) {
					logger.warn("BinanceOperationType changed from {} to {} due to inchoerence between type and change - original record {}",BinanceOperationType.TRANSACTION_BUY, BinanceOperationType.TRANSACTION_SOLD,record);
					record.setOperation(BinanceOperationType.TRANSACTION_SOLD);
				}
				if(record.getOperation().equals(BinanceOperationType.TRANSACTION_SPEND) && change.compareTo(BigDecimal.ZERO)>0) {
					logger.warn("BinanceOperationType changed from {} to {} due to inchoerence between type and change - original record {}",BinanceOperationType.TRANSACTION_SPEND, BinanceOperationType.TRANSACTION_REVENUE,record);
					record.setOperation(BinanceOperationType.TRANSACTION_REVENUE);
				}
				if(record.getOperation().equals(BinanceOperationType.TRANSACTION_REVENUE) && change.compareTo(BigDecimal.ZERO)<0) {
					logger.warn("BinanceOperationType changed from {} to {} due to inchoerence between type and change - original record {}",BinanceOperationType.TRANSACTION_REVENUE, BinanceOperationType.TRANSACTION_SPEND,record);
					record.setOperation(BinanceOperationType.TRANSACTION_SPEND);
				}
				return record;
			}
		};

		// Customize the FieldSetMapper to parse LocalDateTime
		lineMapper.setLineTokenizer(tokenizer);
		lineMapper.setFieldSetMapper(fieldSetMapper);

		reader.setLineMapper(lineMapper);
		return reader;
	}
}
