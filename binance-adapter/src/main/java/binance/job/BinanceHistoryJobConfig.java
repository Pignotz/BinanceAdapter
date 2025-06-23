package binance.job;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
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
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import com.opencsv.CSVWriter;

import binance.job.steps.AggregateAssignStepConfig;
import binance.job.steps.ComputePlusMinusStepConfig;
import binance.job.steps.ReadBinanceHistoryStepConfig;
import binance.job.steps.ReadPriceTableStepConfig;
import binance.job.steps.ValidateStepConfig;
import binance.model.BinanceHistoryRecordList;
import binance.model.account.margin.CrossMarginAccount;
import binance.model.account.margin.IsolatedMarginAccount;
import binance.struct.BinanceHistoryRecord;
import binance.struct.TataxRecord;
import jakarta.annotation.PostConstruct;
import prices.PriceTable;

@Component
public class BinanceHistoryJobConfig {

	private Logger logger = LogManager.getLogger(BinanceHistoryJobConfig.class);



	@Autowired private JobRepository jobRepository;
	@Autowired private PlatformTransactionManager platformTransactionManager;


	@Autowired private ReadBinanceHistoryStepConfig readStepConfig;
	@Autowired private ReadPriceTableStepConfig readPriceTableStepConfig;
	@Autowired private ValidateStepConfig validateStepConfig;
	@Autowired private AggregateAssignStepConfig aggregateAndAssignToAccountsStepConfig;
	@Autowired private ComputePlusMinusStepConfig computePlusMinusStepConfig;
	@Autowired private BinanceHistoryRecordList binanceHistoryRecordList;

	@Autowired private CrossMarginAccount crossMarginAccount;
	@Autowired private IsolatedMarginAccount isolatedMarginAccount;
	@Autowired private PriceTable priceTable;
	@PostConstruct
	private void init() {

	}

	@Bean
	public Job binanceHistoryJob() {
		return new JobBuilder("binanceHistoryJob",jobRepository)
				.incrementer(new RunIdIncrementer()) // Allows re-execution with a new run ID
				.start(readStepConfig.getReadStepBinanceHistory())
				.next(readPriceTableStepConfig.getReadStep())
				.next(validateStepConfig.getValidateStep())
				.next(aggregateAndAssignToAccountsStepConfig.getAggregateAndAssignToAccountStep())
				.next(computePlusMinusStepConfig.getComputePlusMinusStep())
				.next(adaptAndWriteStep())
				.build();
	}


	@Bean
	public Step adaptAndWriteStep() {
		return new StepBuilder("processStep", jobRepository)
				.tasklet(new Tasklet() {

					@Override
					public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {

						Map<Integer,List<BinanceHistoryRecord>> recordsPerYear = binanceHistoryRecordList.stream().collect(Collectors.groupingBy(e -> e.getUtcTime().getYear()));
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
//								writeFileTataxRecordComparator(formattedDate,formattedDate+"_BinanceTataxAdaptedByBySymbol"+entry.getKey()+".csv", entry.getValue(), (TataxRecord e1, TataxRecord e2)-> {
//									int delta = e1.getSymbol().compareTo(e2.getSymbol());
//									if(delta==0) {
//										delta = e2.getTimeStamp().compareTo(e1.getTimeStamp());
//									}
//									if(delta==0) {
//										delta = e2.getMovementType().compareTo(e1.getMovementType());
//									}
//									return delta;
//								});
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

						Map<Integer, List<TataxRecord>> crossMarginPAndL = crossMarginAccount.getProfitAndLosses().stream().collect(Collectors.groupingBy(r->r.getTimeStamp().getYear()));
						crossMarginPAndL.entrySet().stream().forEach(entry -> {
							try {
								writeFileTataxRecordComparator(formattedDate,formattedDate+"_CrossMarginProfit&Losses"+entry.getKey()+".csv", entry.getValue(), (TataxRecord e1, TataxRecord e2)-> {
									int delta = e1.getSymbol().compareTo(e2.getSymbol());
									if(delta==0) {
										delta = e2.getTimeStamp().compareTo(e1.getTimeStamp());
									}
									if(delta==0) {
										delta = e2.getMovementType().compareTo(e1.getMovementType());
									}
									return delta;
								},true);
							} catch (IOException e1) {
								logger.error(e1.getMessage(),e1);
								throw new RuntimeException(e1);
							}
						});
						Map<Integer, List<TataxRecord>> crossMarginTataxRecords = crossMarginAccount.getProfitAndLosses().stream().collect(Collectors.groupingBy(r->r.getTimeStamp().getYear()));
						crossMarginTataxRecords.entrySet().stream().forEach(entry -> {
							try {
								writeFileTataxRecordComparator(formattedDate,formattedDate+"_CrossMarginMyCoins"+entry.getKey()+".csv", entry.getValue(), (TataxRecord e1, TataxRecord e2)-> {
									int delta = e1.getSymbol().compareTo(e2.getSymbol());
									if(delta==0) {
										delta = e2.getTimeStamp().compareTo(e1.getTimeStamp());
									}
									if(delta==0) {
										delta = e2.getMovementType().compareTo(e1.getMovementType());
									}
									return delta;
								},false);
							} catch (IOException e1) {
								logger.error(e1.getMessage(),e1);
								throw new RuntimeException(e1);
							}
						});
						
						
						Map<Integer, List<TataxRecord>> isolatedMarginPAndL = isolatedMarginAccount.getProfitAndLosses().stream().collect(Collectors.groupingBy(r->r.getTimeStamp().getYear()));
						isolatedMarginPAndL.entrySet().stream().forEach(entry -> {
							try {
								writeFileTataxRecordComparator(formattedDate,formattedDate+"_IsolatedMarginProfit&Losses"+entry.getKey()+".csv", entry.getValue(), (TataxRecord e1, TataxRecord e2)-> {
									int delta = e1.getSymbol().compareTo(e2.getSymbol());
									if(delta==0) {
										delta = e2.getTimeStamp().compareTo(e1.getTimeStamp());
									}
									if(delta==0) {
										delta = e2.getMovementType().compareTo(e1.getMovementType());
									}
									return delta;
								},true);
							} catch (IOException e1) {
								logger.error(e1.getMessage(),e1);
								throw new RuntimeException(e1);
							}
						});
						Map<Integer, List<TataxRecord>> isolatedMarginTataxRecords = isolatedMarginAccount.getProfitAndLosses().stream().collect(Collectors.groupingBy(r->r.getTimeStamp().getYear()));
						isolatedMarginTataxRecords.entrySet().stream().forEach(entry -> {
							try {
								writeFileTataxRecordComparator(formattedDate,formattedDate+"_IsolatedMarginMyCoins"+entry.getKey()+".csv", entry.getValue(), (TataxRecord e1, TataxRecord e2)-> {
									int delta = e1.getSymbol().compareTo(e2.getSymbol());
									if(delta==0) {
										delta = e2.getTimeStamp().compareTo(e1.getTimeStamp());
									}
									if(delta==0) {
										delta = e2.getMovementType().compareTo(e1.getMovementType());
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
			List<TataxRecord> tataxRecords, Comparator<TataxRecord> comparator,boolean computeTotalEUR) throws IOException {
		List<TataxRecord> tataxAdaptedRecords = tataxRecords.stream().sorted(comparator).collect(Collectors.toList());
		writeFile(subFolder,fileName, tataxAdaptedRecords);
		if(computeTotalEUR) {
			tataxAdaptedRecords.stream().map(r -> {
				BigDecimal q = r.getMovementType().getDoNegateAmount() ? r.getQuantity().negate() : r.getQuantity();
				return q.multiply(priceTable.getPrice(r.getSymbol(), r.getTimeStamp()));
			})
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		}

		
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
}
