import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import binance.model.account.margin.CrossMarginAccount;
import binance.model.account.margin.IsolatedMarginAccount;
import binance.struct.BinanceHistoryRecord;
import binance.struct.BinanceOperationType;

class MarginAccountTest {

	@Test
	void testSimpleProfit() {
		IsolatedMarginAccount isolatedMarginAccount = new IsolatedMarginAccount();
		LocalDateTime time1 = LocalDateTime.of(2025, 01, 01, 1, 0);
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time1, "Isolated Margin", BinanceOperationType.ISOLATED_MARGIN_LOAN, "BTC", BigDecimal.valueOf(1.5), ""));

		LocalDateTime time2 = LocalDateTime.of(2025, 01, 01, 2, 0);
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time2, "Isolated Margin", BinanceOperationType.TRANSACTION_BUY, "USDC", BigDecimal.valueOf(1000), ""));
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time2, "Isolated Margin", BinanceOperationType.TRANSACTION_SPEND, "BTC", BigDecimal.valueOf(-1), ""));

		LocalDateTime time3 = LocalDateTime.of(2025, 01, 01, 3, 0);
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time3, "Isolated Margin", BinanceOperationType.TRANSACTION_BUY, "USDC", BigDecimal.valueOf(500), ""));
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time3, "Isolated Margin", BinanceOperationType.TRANSACTION_SPEND, "BTC", BigDecimal.valueOf(-0.5), ""));

		LocalDateTime time4 = LocalDateTime.of(2025, 01, 01, 4, 0);
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time4, "Isolated Margin", BinanceOperationType.TRANSACTION_BUY, "BTC", BigDecimal.valueOf(1.65), ""));
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time4, "Isolated Margin", BinanceOperationType.TRANSACTION_SPEND, "USDC", BigDecimal.valueOf(-1500), ""));

		isolatedMarginAccount.computePlusMinus();

		BigDecimal total = isolatedMarginAccount.getProfitAndLosses().stream()
				.filter(r -> r.getSymbol().equals("BTC"))
				.map(r -> r.getMovementType().getDoNegateAmount() ? r.getQuantity().negate() : r.getQuantity()
				)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		assertEquals(0, BigDecimal.valueOf(0.15).compareTo(total));

	}
	
	@Test
	void testSimpleProfit1() {
		
		
		IsolatedMarginAccount isolatedMarginAccount = new IsolatedMarginAccount();
		
		LocalDateTime time1 = LocalDateTime.of(2025, 01, 01, 1, 0);
//		,"2024-12-31 14:04:31","Isolated Margin","Isolated Margin Loan","BTC","0.08000000",
//		,"2024-12-31 14:04:32","Isolated Margin","Transaction Revenue","USDC","7633.80345010",
//		,"2024-12-31 14:04:32","Isolated Margin","Transaction Sold","BTC","-0.08000000",
//		,"2024-12-31 14:04:32","Isolated Margin","Transaction Fee","USDC","-7.25211328",
//		,"2024-12-31 16:48:41","Isolated Margin","Transaction Buy","BTC","0.08010000",
//		,"2024-12-31 16:48:41","Isolated Margin","Transaction Fee","BTC","-0.00007609",
//		,"2024-12-31 16:48:41","Isolated Margin","Transaction Spend","USDC","-7588.71989950",
//		,"2024-12-31 16:48:42","Isolated Margin","Isolated Margin Repayment","BTC","-0.08000033"
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time1, "Isolated Margin", BinanceOperationType.ISOLATED_MARGIN_LOAN, "BTC", BigDecimal.valueOf(0.08), ""));

		LocalDateTime time2 = LocalDateTime.of(2025, 01, 01, 2, 0);
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time2, "Isolated Margin", BinanceOperationType.TRANSACTION_REVENUE, "USDC", BigDecimal.valueOf(7633.80345010), ""));
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time2, "Isolated Margin", BinanceOperationType.TRANSACTION_FEE, "USDC", BigDecimal.valueOf(-7.25211328), ""));
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time2, "Isolated Margin", BinanceOperationType.TRANSACTION_SOLD, "BTC", BigDecimal.valueOf(-0.08), ""));
		
		LocalDateTime time4 = LocalDateTime.of(2025, 01, 01, 4, 0);
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time4, "Isolated Margin", BinanceOperationType.TRANSACTION_BUY, "BTC", BigDecimal.valueOf(0.0801), ""));
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time4, "Isolated Margin", BinanceOperationType.TRANSACTION_FEE, "BTC", BigDecimal.valueOf(-0.00007609), ""));
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time4, "Isolated Margin", BinanceOperationType.TRANSACTION_SPEND, "USDC", BigDecimal.valueOf(-7588.71989950), ""));
		LocalDateTime time5 = LocalDateTime.of(2025, 01, 01, 5, 0);
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time5, "Isolated Margin", BinanceOperationType.ISOLATED_MARGIN_REPAYMENT, "BTC", BigDecimal.valueOf(-0.08000033), ""));

		
		isolatedMarginAccount.computePlusMinus();

		BigDecimal total = isolatedMarginAccount.getProfitAndLosses().stream()
				.filter(r -> r.getSymbol().equals("BTC"))
				.map(r -> r.getMovementType().getDoNegateAmount() ? r.getQuantity().negate() : r.getQuantity()
				)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		assertEquals(0, BigDecimal.valueOf(0.15).compareTo(total));

	}
	
	@Test
	void testDebug() {
		CrossMarginAccount crossMarginAccount = new CrossMarginAccount();
		
		crossMarginAccount.addRecord(new BinanceHistoryRecord(",2023-11-19 21:25:27,Cross Margin,Transfer Between Main Account/Futures and Margin Account,USDT,2413.35471521,"));

		crossMarginAccount.addRecord(new BinanceHistoryRecord(",2023-11-19 22:13:32,Cross Margin,Margin Loan,SOL,120.00000000,"));
		crossMarginAccount.addRecord(new BinanceHistoryRecord(",2023-11-19 22:13:33,Cross Margin,Transaction Revenue,USDT,7314.00000000,"));
		crossMarginAccount.addRecord(new BinanceHistoryRecord(",2023-11-19 22:13:33,Cross Margin,Transaction Sold,SOL,-120.00000000,"));
		crossMarginAccount.addRecord(new BinanceHistoryRecord(",2023-11-19 23:07:30,Cross Margin,Transaction Buy,SOL,120.00000000,"));
		crossMarginAccount.addRecord(new BinanceHistoryRecord(",2023-11-19 23:07:30,Cross Margin,Transaction Spend,USDT,-7416.00000000,"));
		crossMarginAccount.addRecord(new BinanceHistoryRecord(",2023-11-19 23:07:31,Cross Margin,Margin Repayment,SOL,-120.00000000,"));
		
		
		
		crossMarginAccount.computePlusMinus();
		System.out.println("Done");
		
		
		

		
	}
	
	@Test
	void testSimpleLoss() {
		IsolatedMarginAccount isolatedMarginAccount = new IsolatedMarginAccount();
		LocalDateTime time1 = LocalDateTime.of(2025, 01, 01, 1, 0);
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time1, "Isolated Margin", BinanceOperationType.ISOLATED_MARGIN_LOAN, "BTC", BigDecimal.valueOf(1.5), ""));

		LocalDateTime time2 = LocalDateTime.of(2025, 01, 01, 2, 0);
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time2, "Isolated Margin", BinanceOperationType.TRANSACTION_BUY, "USDC", BigDecimal.valueOf(500), ""));
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time2, "Isolated Margin", BinanceOperationType.TRANSACTION_SPEND, "BTC", BigDecimal.valueOf(-1), ""));

		LocalDateTime time3 = LocalDateTime.of(2025, 01, 01, 3, 0);
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time3, "Isolated Margin", BinanceOperationType.TRANSACTION_BUY, "USDC", BigDecimal.valueOf(250), ""));
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time3, "Isolated Margin", BinanceOperationType.TRANSACTION_SPEND, "BTC", BigDecimal.valueOf(-0.5), ""));

		LocalDateTime time4 = LocalDateTime.of(2025, 01, 01, 4, 0);
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time4, "Isolated Margin", BinanceOperationType.TRANSACTION_BUY, "BTC", BigDecimal.valueOf(0.5), ""));
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time4, "Isolated Margin", BinanceOperationType.TRANSACTION_SPEND, "USDC", BigDecimal.valueOf(-500), ""));

		isolatedMarginAccount.computePlusMinus();

		BigDecimal total = isolatedMarginAccount.getProfitAndLosses().stream()
				.filter(r -> r.getSymbol().equals("BTC"))
				.map(r -> r.getMovementType().getDoNegateAmount() ? r.getQuantity().negate() : r.getQuantity()
				)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		assertEquals(0, BigDecimal.valueOf(-0.5).compareTo(total));

	}
	
	@Test
	void testMyCoinUsage() {
		IsolatedMarginAccount isolatedMarginAccount = new IsolatedMarginAccount();
		LocalDateTime time0 = LocalDateTime.of(2025, 01, 01, 0, 0);
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time0, "Isolated Margin", BinanceOperationType.TRANSFER_ACCOUNT, "USDC", BigDecimal.valueOf(2000), ""));
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time0, "Isolated Margin", BinanceOperationType.TRANSFER_ACCOUNT, "BTC", BigDecimal.valueOf(0.01), ""));
		
		LocalDateTime time1 = LocalDateTime.of(2025, 01, 01, 1, 0);
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time1, "Isolated Margin", BinanceOperationType.ISOLATED_MARGIN_LOAN, "BTC", BigDecimal.valueOf(1.5), ""));

		LocalDateTime time2 = LocalDateTime.of(2025, 01, 01, 2, 0);
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time2, "Isolated Margin", BinanceOperationType.TRANSACTION_BUY, "USDC", BigDecimal.valueOf(500), ""));
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time2, "Isolated Margin", BinanceOperationType.TRANSACTION_SPEND, "BTC", BigDecimal.valueOf(-1), ""));

		LocalDateTime time3 = LocalDateTime.of(2025, 01, 01, 3, 0);
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time3, "Isolated Margin", BinanceOperationType.TRANSACTION_BUY, "USDC", BigDecimal.valueOf(250), ""));
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time3, "Isolated Margin", BinanceOperationType.TRANSACTION_SPEND, "BTC", BigDecimal.valueOf(-0.5), ""));

		LocalDateTime time4 = LocalDateTime.of(2025, 01, 01, 4, 0);
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time4, "Isolated Margin", BinanceOperationType.TRANSACTION_BUY, "BTC", BigDecimal.valueOf(0.5), ""));
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time4, "Isolated Margin", BinanceOperationType.TRANSACTION_SPEND, "USDC", BigDecimal.valueOf(-750), ""));
		
		LocalDateTime time5 = LocalDateTime.of(2025, 01, 01, 5, 0);
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time5, "Isolated Margin", BinanceOperationType.TRANSACTION_BUY, "BTC", BigDecimal.valueOf(1), ""));
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time5, "Isolated Margin", BinanceOperationType.TRANSACTION_SPEND, "USDC", BigDecimal.valueOf(-1000), ""));
		
		LocalDateTime time6 = LocalDateTime.of(2025, 01, 01, 6, 0);	
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time6, "Isolated Margin", BinanceOperationType.ISOLATED_MARGIN_REPAYMENT, "BTC", BigDecimal.valueOf(-1.505), ""));

		isolatedMarginAccount.computePlusMinus();

		BigDecimal total = isolatedMarginAccount.getTataxRecords().stream()
				.filter(r -> r.getSymbol().equals("BTC"))
				.map(r -> r.getMovementType().getDoNegateAmount() ? r.getQuantity().negate() : r.getQuantity()
				)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		//Errore minimo
		assertTrue(BigDecimal.valueOf(-0.005).subtract(total).compareTo(BigDecimal.valueOf(0.0000001))<0);

	}

}
