import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

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
				.map(r -> r.getQuantity())
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		assertEquals(0, BigDecimal.valueOf(0.15).compareTo(total));

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
				.map(r -> r.getQuantity())
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		assertEquals(0, BigDecimal.valueOf(0.5).compareTo(total));

	}
	
	@Test
	void testMyCoinUsage() {
		IsolatedMarginAccount isolatedMarginAccount = new IsolatedMarginAccount();
		LocalDateTime time0 = LocalDateTime.of(2025, 01, 01, 1, 0);
		isolatedMarginAccount.addRecord(new BinanceHistoryRecord("", time0, "Isolated Margin", BinanceOperationType.TRANSFER_ACCOUNT, "USDC", BigDecimal.valueOf(2000), ""));
		
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
		

		isolatedMarginAccount.computePlusMinus();

		BigDecimal total = isolatedMarginAccount.getProfitAndLosses().stream()
				.map(r -> r.getQuantity())
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		assertEquals(0, BigDecimal.valueOf(-0.5).compareTo(total));

	}

}
