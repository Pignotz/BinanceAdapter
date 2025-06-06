package binance.job.steps;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
import binance.model.account.margin.CrossMarginAccount;
import binance.model.account.margin.IsolatedMarginAccount;
import binance.struct.BinanceHistoryRecord;
import binance.struct.BinanceOperationType;
import binance.struct.TataxOperationType;
import jakarta.annotation.PostConstruct;

@Component
public class ComputePlusMinusStepConfig {

	private Logger logger = LogManager.getLogger(ComputePlusMinusStepConfig.class);

	
	@Autowired private JobRepository jobRepository;
	@Autowired private PlatformTransactionManager platformTransactionManager;
	@Autowired private BinanceHistoryRecordList binanceHistoryRecordList;
	
	@Autowired private CrossMarginAccount crossMarginAccount;
	@Autowired private IsolatedMarginAccount isolatedMarginAccount;


	@Bean
	public Step getComputePlusMinusStep() {
		return new StepBuilder("COMPUTE_PLUS_MINUS_STEP", jobRepository)
				.tasklet(new Tasklet() {
					@Override
					public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
						crossMarginAccount.computePlusMinus();
						isolatedMarginAccount.computePlusMinus();
						return null;
					}
				}, platformTransactionManager)
				.build();
	}

}
