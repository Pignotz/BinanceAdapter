package binance.model.account.margin;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Logger;

import binance.model.CoinBalance;
import binance.model.account.Account;
import binance.model.account.AccountType;
import binance.struct.BinanceHistoryRecord;
import binance.struct.Operation;
import binance.struct.Position.ADD_LOAN_RESULT;
import binance.struct.Position.ADD_OPERATION_RESULT;
import binance.struct.Position.ADD_REPAY_RESULT;
import binance.struct.Position.ADD_TRANSFER_RESULT;
import binance.struct.UtcTimedRecordWithMovement;

public abstract class MarginAccount extends Account {

	protected List<BinanceHistoryRecord> recordsForTatax;

	public MarginAccount(AccountType accountType, Logger logger) {
		super(accountType, logger);
		recordsForTatax = new ArrayList<BinanceHistoryRecord>();
	}


	
	
	public void computePlusMinus() {
		List<BinanceHistoryRecord> wrkBinanceHistoryRecords = this.records.stream().sorted().collect(Collectors.toList());

		Map<String,List<BinanceHistoryRecord>> transfersInByCoin = new HashMap<String,List<BinanceHistoryRecord>>();
		Map<String,List<BinanceHistoryRecord>> transfersOutByCoin = new HashMap<String,List<BinanceHistoryRecord>>();
		Map<String,List<BinanceHistoryRecord>> loansByCoin = new HashMap<String,List<BinanceHistoryRecord>>();
		List<Operation> operations = new ArrayList<Operation>();
		Map<String,List<BinanceHistoryRecord>> repaysByCoin = new HashMap<String,List<BinanceHistoryRecord>>();

		List<UtcTimedRecordWithMovement> movements = new ArrayList<UtcTimedRecordWithMovement>();
		
		Operation op = null;
		Operation liquidationOp = null;
		for (BinanceHistoryRecord binanceHistoryRecord : wrkBinanceHistoryRecords) {
			String coin = binanceHistoryRecord.getCoin();
			BigDecimal change = binanceHistoryRecord.getChange();
			switch (binanceHistoryRecord.getOperation()) {
			case TRANSFER_ACCOUNT:
				if(change.compareTo(BigDecimal.ZERO)>=0) {
					addElementToMappedList(transfersInByCoin, coin, binanceHistoryRecord);
				}else {
					addElementToMappedList(transfersOutByCoin, coin, binanceHistoryRecord);
				}
				movements.add(binanceHistoryRecord);
				break;
			case ISOLATED_MARGIN_LOAN, 
			MARGIN_LOAN:
				addElementToMappedList(loansByCoin, coin, binanceHistoryRecord);
			movements.add(binanceHistoryRecord);
			break;
			case TRANSACTION_SOLD, 
			TRANSACTION_SPEND:
				op = new Operation(binanceHistoryRecord);
			op.setUtcTime(binanceHistoryRecord.getUtcTime());
			op.setAmountSold(binanceHistoryRecord.getChange());
			op.setCoinSold(binanceHistoryRecord.getCoin());
			break;
			case TRANSACTION_FEE:
				if(binanceHistoryRecord.isMarginAccount()) {
					op.setAmountFee(binanceHistoryRecord.getChange());
					op.setCoinFee(binanceHistoryRecord.getCoin());
				}
				break;
			case TRANSACTION_BUY,
			TRANSACTION_REVENUE:
				op.setAmountBought(binanceHistoryRecord.getChange());
			op.setCoinBought(binanceHistoryRecord.getCoin());
			op.computePrice();
			operations.add(op);
			movements.add(op);
			op = null;

			break;
			case CROSS_MARGIN_LIQUIDATION_SMALL_ASSET_TAKEOVER:
				if(binanceHistoryRecord.getChange().compareTo(BigDecimal.ZERO)<0) {
					liquidationOp = new Operation(binanceHistoryRecord);
					liquidationOp.setUtcTime(binanceHistoryRecord.getUtcTime());
					liquidationOp.setAmountSold(binanceHistoryRecord.getChange());
					liquidationOp.setCoinSold(binanceHistoryRecord.getCoin());
				}else {
					liquidationOp.setAmountBought(binanceHistoryRecord.getChange());
					liquidationOp.setCoinBought(binanceHistoryRecord.getCoin());
					liquidationOp.computePrice();
					operations.add(liquidationOp);
					movements.add(liquidationOp);
					liquidationOp=null;
				}
				break;
			case ISOLATED_MARGIN_REPAYMENT, 
			MARGIN_REPAYMENT:
				addElementToMappedList(repaysByCoin, coin, binanceHistoryRecord);
			movements.add(binanceHistoryRecord);
			break;
			case BNB_FEE_DEDUCTION:
				movements.add(binanceHistoryRecord);
				break;
			case ISOLATED_MARGIN_LIQUIDATION_FEE:
				movements.add(binanceHistoryRecord);
				break;
			default:
				throw new RuntimeException("Unmanaged Case " + binanceHistoryRecord.getOperation());
			}
		}

		Map<String,BigDecimal> balanceIncludingLoans = new HashMap<String, BigDecimal>();
		Map<String,BigDecimal> balanceExcludingLoans = new HashMap<String, BigDecimal>();
		Map<String,BigDecimal> balanceOnlyLoans = new HashMap<String, BigDecimal>();
		
		Map<String, CoinBalance> coinBalances = new HashMap<String, CoinBalance>();
		
		for (int i = 0; i < movements.size(); i++) {
			UtcTimedRecordWithMovement movement = movements.get(i);
			if(!movement.isSwap()) {
				BinanceHistoryRecord bhr = (BinanceHistoryRecord) movement;
				String coin = bhr.getCoin();
				if(!balanceExcludingLoans.containsKey(coin)) {
					balanceExcludingLoans.put(coin, BigDecimal.ZERO);
					balanceIncludingLoans.put(coin, BigDecimal.ZERO);
					balanceOnlyLoans.put(coin, BigDecimal.ZERO);
				}
				BigDecimal change = bhr.getChange();
				switch (bhr.getOperation()) {
				case TRANSFER_ACCOUNT:
					recordsForTatax.add(bhr);
					balanceIncludingLoans.put(coin, change.add(balanceIncludingLoans.get(coin)));
					balanceExcludingLoans.put(coin, change.add(balanceExcludingLoans.get(coin)));
					break;
				case ISOLATED_MARGIN_LOAN, 
				MARGIN_LOAN:
					balanceIncludingLoans.put(coin, change.add(balanceIncludingLoans.get(coin)));
				balanceOnlyLoans.put(coin, change.add(balanceIncludingLoans.get(coin)));		
				break;
				case TRANSACTION_FEE:
					break;
				case ISOLATED_MARGIN_REPAYMENT, 
				MARGIN_REPAYMENT:
					balanceIncludingLoans.put(coin, change.add(balanceIncludingLoans.get(coin)));
				balanceOnlyLoans.put(coin, change.add(balanceIncludingLoans.get(coin)));
				break;
				case BNB_FEE_DEDUCTION:
					balanceIncludingLoans.put(coin, change.add(balanceIncludingLoans.get(coin)));
					balanceExcludingLoans.put(coin, change.add(balanceExcludingLoans.get(coin)));
					break;
				case ISOLATED_MARGIN_LIQUIDATION_FEE:
					balanceIncludingLoans.put(coin, change.add(balanceIncludingLoans.get(coin)));
					balanceExcludingLoans.put(coin, change.add(balanceExcludingLoans.get(coin)));
					break;
				default:
					throw new RuntimeException("Unmanaged Case " + bhr.getOperation());
				}
			}else {
				Operation o = (Operation) movement;
				
				String coinBought = o.getCoinBought();
				String coinSold = o.getCoinSold();
				BigDecimal soldCoinPrice = o.getPriceOfBoughtCoin();
				
				BigDecimal amountSold = o.getAmountSold();
				BigDecimal amountAvailableFromLoans = balanceOnlyLoans.get(coinSold);

				if(amountSold.negate().compareTo(amountAvailableFromLoans)>0) { 
					//Allora tutto il loaned è stato venduto coinvolgendo anche le mie coin
					//Devo calcolare il prezzo con cui ho acquistato vendendo il loaned per successivo P&L
					
					//Devo calcolare il residuo che ho venduto le mie coin
					
				}else { //Allora solo il loaned è coinvolto
					
				}
				
				
				
			}
			
			balanceIncludingLoans.entrySet().stream().forEach(e -> {
				if(e.getValue().compareTo(BigDecimal.ZERO)<0) {
					throw new RuntimeException("Balance for coin " + e.getKey() + " became less then zero for balanceExcludingLoans");
				}
			});
			
			balanceExcludingLoans.entrySet().stream().forEach(e -> {
				if(e.getValue().compareTo(BigDecimal.ZERO)<0) {
					throw new RuntimeException("Balance for coin " + e.getKey() + " became less then zero for balanceExcludingLoans");
				}
			});
			
		}
		
		//		List<Position> positions = new ArrayList<Position>();
		//		Position p = null;
		//		List<Operation> ops = new ArrayList<Operation>();
		//		Operation op = null;
		//		Operation liquOp = null;
		//		for (BinanceHistoryRecord binanceHistoryRecord : wrkBinanceHistoryRecords) {
		//			logger.info(binanceHistoryRecord);
		//			switch (binanceHistoryRecord.getOperation()) {
		//			case TRANSFER_ACCOUNT:
		//				if(positions.isEmpty()) {
		//					p = new Position(binanceHistoryRecord);
		//					p.addTransferAndCheckClose(binanceHistoryRecord);
		//					positions.add(p);
		//				} else {
		//					int pIdx = positions.size()-1;
		//					p = positions.get(pIdx);
		//					ADD_TRANSFER_RESULT res = p.addTransferAndCheckClose(binanceHistoryRecord);
		//					while(pIdx>0 && res.equals(ADD_TRANSFER_RESULT.INCOMPATIBLE)) {
		//						pIdx--;
		//						p = positions.get(pIdx);
		//						res = p.addTransferAndCheckClose(binanceHistoryRecord);
		//					}
		//					if(res.equals(ADD_TRANSFER_RESULT.INCOMPATIBLE)) {
		//						p = new Position(binanceHistoryRecord);
		//						p.addTransferAndCheckClose(binanceHistoryRecord);
		//						positions.add(p);
		//					}
		//					if(res.equals(ADD_TRANSFER_RESULT.OK_AND_CLOSE)) {
		//						p.closePosition();
		//					}		
		//				}
		//				break;
		//			case ISOLATED_MARGIN_LOAN, 
		//			MARGIN_LOAN:
		//				if(positions.isEmpty()) {
		//					p = new Position(binanceHistoryRecord);
		//					p.addLoan(binanceHistoryRecord);
		//					positions.add(p);
		//				}else {
		//					int pIdx = positions.size()-1;
		//					p = positions.get(pIdx);
		//					ADD_LOAN_RESULT res = p.addLoan(binanceHistoryRecord);
		//					while(pIdx>0 && res.equals(ADD_LOAN_RESULT.INCOMPATIBLE)) {
		//						pIdx--;
		//						p = positions.get(pIdx);
		//						res = p.addLoan(binanceHistoryRecord);
		//					}
		//					if(res.equals(ADD_LOAN_RESULT.INCOMPATIBLE)) {
		//						p = new Position(binanceHistoryRecord);
		//						p.addLoan(binanceHistoryRecord);
		//						positions.add(p);
		//					}
		//				}
		//			break;
		//			case TRANSACTION_SOLD, 
		//			TRANSACTION_SPEND:
		//				op = new Operation(binanceHistoryRecord);
		//			op.setUtcTime(binanceHistoryRecord.getUtcTime());
		//			op.setAmountSold(binanceHistoryRecord.getChange());
		//			op.setCoinSold(binanceHistoryRecord.getCoin());
		//			break;
		//			case TRANSACTION_FEE:
		//				if(binanceHistoryRecord.isMarginAccount()) {
		//					op.setAmountFee(binanceHistoryRecord.getChange());
		//					op.setCoinFee(binanceHistoryRecord.getCoin());
		//				}
		//				break;
		//			case TRANSACTION_BUY,
		//			TRANSACTION_REVENUE:
		//				op.setAmountBought(binanceHistoryRecord.getChange());
		//			op.setCoinBought(binanceHistoryRecord.getCoin());
		//			int pIdx = positions.size()-1;
		//			p = positions.get(pIdx);
		//			while(p.addOperation(op,false).equals(ADD_OPERATION_RESULT.LOOK_FOR_COMPATIBLE_POSITION)) {
		//				pIdx--;
		//				p = positions.get(pIdx);
		//			}
		//			ops.add(op);
		//			op = null;
		//
		//			break;
		//			case CROSS_MARGIN_LIQUIDATION_SMALL_ASSET_TAKEOVER:
		//				if(binanceHistoryRecord.getChange().compareTo(BigDecimal.ZERO)<0) {
		//					liquOp = new Operation(binanceHistoryRecord);
		//					liquOp.setUtcTime(binanceHistoryRecord.getUtcTime());
		//					liquOp.setAmountSold(binanceHistoryRecord.getChange());
		//					liquOp.setCoinSold(binanceHistoryRecord.getCoin());
		//				}else {
		//					liquOp.setAmountBought(binanceHistoryRecord.getChange());
		//					liquOp.setCoinBought(binanceHistoryRecord.getCoin());
		//					p.addOperation(liquOp,true);
		//					ops.add(liquOp);
		//					liquOp=null;
		//				}
		//				break;
		//			case ISOLATED_MARGIN_REPAYMENT, 
		//			MARGIN_REPAYMENT:
		//				pIdx = positions.size()-1;
		//			p = positions.get(pIdx);
		//
		//			ADD_REPAY_RESULT res = p.addRepay(binanceHistoryRecord);
		//			if(res.equals(ADD_REPAY_RESULT.BNB_REPAYMENT)) {
		//				//TODO
		//			}else {
		//				while(res.equals(ADD_REPAY_RESULT.INCOMPATIBLE)) {
		//					pIdx--;
		//					p = positions.get(pIdx);
		//					res = p.addRepay(binanceHistoryRecord);
		//				}
		//				
		//					
		//			}
		//			break;
		//			case BNB_FEE_DEDUCTION:
		//				recordsForTatax.add(binanceHistoryRecord);
		//				//TODO verifica
		//				break;
		//			case ISOLATED_MARGIN_LIQUIDATION_FEE:
		//				recordsForTatax.add(binanceHistoryRecord);
		//				//TODO verifica
		//				break;
		//			default:
		//				throw new RuntimeException("Unmanaged Case " + binanceHistoryRecord.getOperation());
		//			}
		//		}

	}


	protected <T> void addElementToMappedList(Map<String, List<T>> map, String key, T value) {
		List<T> list = map.get(key);
		if (list == null) {
			list = new ArrayList<>();
			map.put(key, list);
		}
		list.add(value);
	}

}
