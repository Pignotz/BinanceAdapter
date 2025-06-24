package binance.job.steps;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

import binance.prices.PriceTable;
import binance.prices.PriceTableRecord;
import binance.struct.BinanceHistoryRecord;
import binance.struct.BinanceOperationType;
import binance.struct.TataxOperationType;
import jakarta.annotation.PostConstruct;

@Component
public class ReadPriceTableStepConfig {

	private Logger logger = LogManager.getLogger(ReadPriceTableStepConfig.class);

	
	@Autowired private JobRepository jobRepository;
	@Autowired private PlatformTransactionManager platformTransactionManager;
	@Autowired private PriceTable priceTable;

	@Value("file:input/*.prices") 
	private Resource[] inputFile;


	//STEP1
	@Bean
	public Step getReadStep() {
		return new StepBuilder("READ_STEP", jobRepository)
				.<PriceTableRecord, PriceTableRecord>chunk(10,platformTransactionManager) // Process 10 records at a time
				.reader(fileReader1())
				.processor(this::processRecord)
				.writer(chunk -> chunk.forEach(e -> priceTable.addPriceTableRecord(e)))
				.build();
	}

	/**
	 * Multi-file reader to read all Binance history CSV files in order
	 */
	@Bean
	public MultiResourceItemReader<PriceTableRecord> fileReader1() {
		MultiResourceItemReader<PriceTableRecord> multiReader = new MultiResourceItemReader<>();
		multiReader.setResources(inputFile);
		multiReader.setDelegate(csvReader1());
		return multiReader;
	}

	/**
	 * Processor - modify records if needed (e.g., clean data, apply transformations)
	 */
	public PriceTableRecord processRecord(PriceTableRecord record) {
		if(record.getSymbol().equals("USDT")||record.getSymbol().equals("USDC")) {
			record.setPriceInEur(BigDecimal.ONE.divide(record.getPriceInEur(),16,RoundingMode.HALF_UP));
		}
		return record;
	}
	/**
	 * CSV Reader for Binance history files
	 */
	@Bean
	public FlatFileItemReader<PriceTableRecord> csvReader1() {
		FlatFileItemReader<PriceTableRecord> reader = new FlatFileItemReader<>();
		reader.setLinesToSkip(1); // Skip header

		DefaultLineMapper<PriceTableRecord> lineMapper = new DefaultLineMapper<>();

		// Tokenize CSV fields
		DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
		tokenizer.setNames("Symbol","Date","Open","High","Low","Close","Volume");
		tokenizer.setDelimiter(","); // Ensure delimiter is correct

		// Map CSV columns to the BinanceHistoryRecord object
		FieldSetMapper<PriceTableRecord> fieldSetMapper = new FieldSetMapper<PriceTableRecord>() {
			@Override
			public PriceTableRecord mapFieldSet(FieldSet fieldSet) {
				PriceTableRecord record = new PriceTableRecord();
				// Custom parsing for LocalDateTime (using DateTimeFormatter)
				String utcTimeString = fieldSet.readString("Date");
				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");  // Adjust the format as per your data
				record.setTime(LocalDateTime.parse(utcTimeString, formatter));
				record.setSymbol(fieldSet.readString("Symbol"));
				// Reading the change value as a string
				String priceEURString = fieldSet.readString("Close");
				// Converting the string value to BigDecimal
				BigDecimal priceEUR = new BigDecimal(priceEURString);
				// Setting the BigDecimal value on the record
				record.setPriceInEur(priceEUR);
				
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
