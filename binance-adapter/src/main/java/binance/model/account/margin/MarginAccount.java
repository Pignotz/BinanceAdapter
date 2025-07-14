package binance.model.account.margin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Logger;

import binance.CommonDef;
import binance.model.CoinBalance;
import binance.model.CoinBalance.CoinBalanceEntry;
import binance.model.account.Account;
import binance.model.account.AccountType;
import binance.prices.PriceTable;
import binance.struct.BinanceHistoryRecord;
import binance.struct.Operation;
import binance.struct.TataxOperationType;
import binance.struct.TataxRecord;
import binance.struct.UtcTimedRecordWithMovement;

public abstract class MarginAccount extends Account {

	protected List<TataxRecord> recordsForTatax;

	protected Map<String,BigDecimal> balanceAvailableLoans;


	protected List<TataxRecord> profitAndLosses;

	Map<String, CoinBalance> pricedCoinBalancesByCoinBoughtForProfitAndLoss;

	List<UtcTimedRecordWithMovement> movements = new ArrayList<UtcTimedRecordWithMovement>();
	Map<String,BigDecimal> balance = new HashMap<String, BigDecimal>();

	private boolean addPAndLToTataxRecords = true;
	
	public MarginAccount(AccountType accountType, Logger logger) {
		super(accountType, logger);
		recordsForTatax = new ArrayList<TataxRecord>();
		balanceAvailableLoans = new HashMap<String, BigDecimal>();
		profitAndLosses = new ArrayList<TataxRecord>();
		pricedCoinBalancesByCoinBoughtForProfitAndLoss = new HashMap<String, CoinBalance>();
	}


	public void computePlusMinus() {
		List<BinanceHistoryRecord> wrkBinanceHistoryRecords = this.records.stream().sorted().collect(Collectors.toList());

		Map<String,List<BinanceHistoryRecord>> transfersInByCoin = new HashMap<String,List<BinanceHistoryRecord>>();
		Map<String,List<BinanceHistoryRecord>> transfersOutByCoin = new HashMap<String,List<BinanceHistoryRecord>>();
		Map<String,List<BinanceHistoryRecord>> loansByCoin = new HashMap<String,List<BinanceHistoryRecord>>();
		List<Operation> operations = new ArrayList<Operation>();
		Map<String,List<BinanceHistoryRecord>> repaysByCoin = new HashMap<String,List<BinanceHistoryRecord>>();


		Operation op = null;
		Operation prevOp = null;
		Operation liquidationOp = null;
		for (BinanceHistoryRecord binanceHistoryRecord : wrkBinanceHistoryRecords) {
			logger.info(binanceHistoryRecord);
			String coin = binanceHistoryRecord.getCoin();
			BigDecimal change = binanceHistoryRecord.getChange();
			balanceAvailableLoans.putIfAbsent(coin, BigDecimal.ZERO);
			pricedCoinBalancesByCoinBoughtForProfitAndLoss.putIfAbsent(coin, new CoinBalance(coin));
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
			prevOp = op;
			op.setUtcTime(binanceHistoryRecord.getUtcTime());
			op.setAmountSold(binanceHistoryRecord.getChange());
			op.setCoinSold(binanceHistoryRecord.getCoin());
			break;
			case TRANSACTION_FEE:
				if(binanceHistoryRecord.isMarginAccount()) {
					String feeCoin = binanceHistoryRecord.getCoin();
					if(feeCoin.equals(prevOp.getCoinBought())) {
						prevOp.setAmountBought(prevOp.getAmountBought().subtract(change.negate()));
					}else {
						if(feeCoin.equals(prevOp.getCoinSold())) {
							prevOp.setAmountSold(prevOp.getAmountSold().add(change));
						}else {
							movements.add(binanceHistoryRecord);
						}
					}

				}
				break;
			case TRANSACTION_BUY,
			TRANSACTION_REVENUE:
				op.setAmountBought(binanceHistoryRecord.getChange());
			op.setCoinBought(binanceHistoryRecord.getCoin());
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
		computePL();
	}

	private void addRecordForTatax(TataxRecord tataxRecord, String reason, boolean doCheckBalance, boolean doGenerateCreditFromPrevSwaps) {
		String coin = tataxRecord.getSymbol();
		BigDecimal amount = tataxRecord.getMovementType().getDoNegateAmount() ? tataxRecord.getQuantity().negate() : tataxRecord.getQuantity();
		balance.putIfAbsent(coin, BigDecimal.ZERO);
		if(doCheckBalance && !doGenerateCreditFromPrevSwaps && amount.compareTo(BigDecimal.ZERO)<0) {
			if(balance.get(coin).add(amount).compareTo(BigDecimal.ZERO)<0) {
				BigDecimal reducedAmount = amount.negate();
				List<CoinBalanceEntry> coinBalanceHistory = pricedCoinBalancesByCoinBoughtForProfitAndLoss.get(coin).getBalanceHistory();
				Iterator<CoinBalanceEntry> coinBalanceHistoryIterator = coinBalanceHistory.iterator();
				while(coinBalanceHistoryIterator.hasNext()) { 
					if(reducedAmount.compareTo(BigDecimal.ZERO)==0) break;
					CoinBalanceEntry coinBalanceEntry = coinBalanceHistoryIterator.next();
					BigDecimal availableFromPrevSwaps = coinBalanceEntry.getAmount();
					BigDecimal availableFromPrevSwapsToUse = coinBalanceEntry.getAmount();
					BigDecimal previousTradePrice = coinBalanceEntry.getPriceOfIncomeCoin(); //Quanti USDC per 1 BTC

					if(availableFromPrevSwapsToUse.compareTo(reducedAmount)>0) { //E' troppo
						availableFromPrevSwapsToUse = reducedAmount;
						coinBalanceEntry.setAmount(availableFromPrevSwaps.subtract(availableFromPrevSwapsToUse));
						coinBalanceEntry.setCounterValueAmount(coinBalanceEntry.getAmount().multiply(previousTradePrice));
					} else {
						coinBalanceHistoryIterator.remove(); //Lo esaurirò tutto						
					}
					reducedAmount = reducedAmount.subtract(availableFromPrevSwapsToUse);
				}
				tataxRecord= new TataxRecord(tataxRecord.getTimeStamp(),tataxRecord.getSymbol(),reducedAmount,tataxRecord.getMovementType());
			}
		}
		amount = tataxRecord.getMovementType().getDoNegateAmount() ? tataxRecord.getQuantity().negate() : tataxRecord.getQuantity();
		if(amount.compareTo(BigDecimal.ZERO)!=0) {
			balance.put(coin,balance.get(coin).add(amount));
			if(doCheckBalance) {
				checkBalance(coin, tataxRecord.getTimeStamp());
			}
			logger.info("{} {}",reason,tataxRecord.toSimpleString());
			recordsForTatax.add(tataxRecord);
		}
	}

	private void checkBalance(String coin, LocalDateTime time) {
		logger.debug("Balance: {} {}",coin,balance.get(coin));
		if(pricedCoinBalancesByCoinBoughtForProfitAndLoss.containsKey(coin)) {
			logger.debug("Adjusted Balance {} {}",coin,balance.get(coin).doubleValue() + (this.pricedCoinBalancesByCoinBoughtForProfitAndLoss.get(coin).getBalanceHistory().stream().mapToDouble(e -> e.getAmount().doubleValue()).sum()));
		}
		if(balance.get(coin).compareTo(BigDecimal.ZERO)<0) {
			BigDecimal amountToExhaust = balance.get(coin).negate();
			List<CoinBalanceEntry> coinBalanceHistory = pricedCoinBalancesByCoinBoughtForProfitAndLoss.get(coin).getBalanceHistory();
			Iterator<CoinBalanceEntry> coinBalanceHistoryIterator = coinBalanceHistory.iterator();
			while(coinBalanceHistoryIterator.hasNext()) { 
				if(amountToExhaust.compareTo(BigDecimal.ZERO)==0) break;
				CoinBalanceEntry coinBalanceEntry = coinBalanceHistoryIterator.next();
				BigDecimal availableFromPrevSwaps = coinBalanceEntry.getAmount();
				BigDecimal availableFromPrevSwapsToUse = coinBalanceEntry.getAmount();
				BigDecimal previousTradePrice = coinBalanceEntry.getPriceOfIncomeCoin(); //Quanti USDC per 1 BTC

				if(availableFromPrevSwapsToUse.compareTo(amountToExhaust)>0) { //E' troppo
					availableFromPrevSwapsToUse = amountToExhaust;
					coinBalanceEntry.setAmount(availableFromPrevSwaps.subtract(availableFromPrevSwapsToUse));
					coinBalanceEntry.setCounterValueAmount(coinBalanceEntry.getAmount().multiply(previousTradePrice));
				} else {
					coinBalanceHistoryIterator.remove(); //Lo esaurirò tutto						
				}
				TataxRecord credit = new TataxRecord(coinBalanceEntry.getUtcTime(),coin,availableFromPrevSwapsToUse,TataxOperationType.CREDIT, getPriceTable().getPrice(coin, time).multiply(availableFromPrevSwapsToUse),"EUR");
				addRecordForTatax(credit, "... credit from prev swap for fix",false, true);
				amountToExhaust = amountToExhaust.subtract(availableFromPrevSwapsToUse);
			}
			if(amountToExhaust.compareTo(BigDecimal.ZERO)>0) {
				TataxRecord fix = new TataxRecord(time, coin, amountToExhaust, TataxOperationType.CREDIT, getPriceTable().getPrice(coin, time).multiply(amountToExhaust), "EUR");
				addRecordForTatax(fix, "... fix", true,true);
				//throw new RuntimeException();
			}
		}
	}

	public void computePL() {

		for (int i = 0; i < movements.size(); i++) {
			UtcTimedRecordWithMovement movement = movements.get(i);
			logger.info("Movimento corrente: {}",movement.toReadableString());
			if(!movement.isSwap()) {
				BinanceHistoryRecord bhr = (BinanceHistoryRecord) movement;
				String coin = bhr.getCoin();
				BigDecimal change = bhr.getChange();
				switch (bhr.getOperation()) {
				case TRANSFER_ACCOUNT:
					if(bhr.getChange().compareTo(BigDecimal.ZERO)>0) {
						TataxRecord transferDepositTataxRecord =new TataxRecord(movement.getUtcTime(), coin, bhr.getChange(), TataxOperationType.DEPOSIT);
						addRecordForTatax(transferDepositTataxRecord, "... transfer in: ", true, false);
					} else {
						transferOut(bhr);
					}
					break;
				case BNB_FEE_DEDUCTION, ISOLATED_MARGIN_LIQUIDATION_FEE, TRANSACTION_FEE:
					if(bhr.getChange().compareTo(BigDecimal.ZERO)>0) {
						TataxRecord credit =new TataxRecord(movement.getUtcTime(), coin, bhr.getChange(), TataxOperationType.CREDIT);
						logger.info("... {}", credit);
						addRecordForTatax(credit, "... credit special", true, true);
					} else {
						specialDebit(bhr);

					}
				break;
				case ISOLATED_MARGIN_LOAN, MARGIN_LOAN:
					balanceAvailableLoans.put(coin, change.add(balanceAvailableLoans.get(coin)));
				if(balanceAvailableLoans.get(coin).compareTo(BigDecimal.ZERO)<0) throw new RuntimeException();

				break;
				case ISOLATED_MARGIN_REPAYMENT, 
				MARGIN_REPAYMENT:
					BigDecimal amountToExhaust = change.negate();
				if(amountToExhaust.compareTo(BigDecimal.ZERO)<0) throw new RuntimeException();
				//Devo saturare quanto sto ripagando togliendolo in primis dal credito non utilizzato
				BigDecimal availableFromNotUsedLoans = balanceAvailableLoans.get(coin);
				BigDecimal usedAvailableFromNotUsedLoans = availableFromNotUsedLoans;
				if(usedAvailableFromNotUsedLoans.compareTo(amountToExhaust)>0) { //E' troppo
					usedAvailableFromNotUsedLoans = amountToExhaust;
				}
				balanceAvailableLoans.put(coin, balanceAvailableLoans.get(coin).subtract(usedAvailableFromNotUsedLoans));
				if(balanceAvailableLoans.get(coin).compareTo(BigDecimal.ZERO)<0) throw new RuntimeException();
				amountToExhaust = amountToExhaust.subtract(usedAvailableFromNotUsedLoans);
				if(amountToExhaust.compareTo(BigDecimal.ZERO)<0) throw new RuntimeException();
				if(amountToExhaust.compareTo(BigDecimal.ZERO)>0) {
					//ed in secondo luogo dalle mie monete aggiungendo il tatax debit
					TataxRecord repaymentMyCoins = new TataxRecord(movement.getUtcTime().plusSeconds(3), coin, amountToExhaust, TataxOperationType.DEBIT);
					addRecordForTatax(repaymentMyCoins, "... repayment", true,false);		
				}
				break;
				default:
					throw new RuntimeException("Unmanaged Case " + bhr.getOperation());
				}
			} else {
				Operation operation = (Operation) movement;
				String coinBought = operation.getCoinBought(); //i.e. BTC
				String coinSold = operation.getCoinSold(); //i.e. USDC

				BigDecimal soldCoinPrice = operation.getPriceOfSoldCoin(); //i.e. quanti USDC per 1 BTC?
				BigDecimal amountSold = operation.getAmountSold(); //i.e. quanti USDC Venduti?
				BigDecimal residualAmountBought = operation.getAmountBought();
				BigDecimal amountAvailableFromLoans = balanceAvailableLoans.get(coinSold);

				BigDecimal amountToExaust = amountSold.negate();
				BigDecimal usedFromLoans = null;
				if(amountAvailableFromLoans.compareTo(BigDecimal.ZERO)>0) {
					if(amountAvailableFromLoans.compareTo(amountToExaust)>0) {
						usedFromLoans=amountToExaust;
					}else {
						usedFromLoans=amountAvailableFromLoans;
					}
					logger.info("... sto utilizzando {} dal denaro preso in prestito per la vendita", usedFromLoans);
					amountToExaust = amountToExaust.subtract(usedFromLoans);
					residualAmountBought = residualAmountBought.subtract(usedFromLoans.multiply(soldCoinPrice));
					balanceAvailableLoans.put(coinSold, balanceAvailableLoans.get(coinSold).subtract(usedFromLoans));

					BigDecimal actualAmountBoughtUsingLoans = usedFromLoans.multiply(soldCoinPrice);
					pricedCoinBalancesByCoinBoughtForProfitAndLoss.get(coinBought).addCoinBalanceEntry(operation.getUtcTime(), coinBought, actualAmountBoughtUsingLoans, coinSold, usedFromLoans);
					if(balanceAvailableLoans.get(coinSold).compareTo(BigDecimal.ZERO)<0) throw new RuntimeException();
				}
				BigDecimal remaining = amountSold.negate();
				if(usedFromLoans!=null)
					remaining = remaining.subtract(usedFromLoans);
				if(remaining.compareTo(BigDecimal.ZERO)>0) {
					pricedCoinBalancesByCoinBoughtForProfitAndLoss.get(coinBought).addCoinBalanceEntry(operation.getUtcTime(), coinBought, remaining.multiply(soldCoinPrice), coinSold, remaining);
				}

				if(amountToExaust.compareTo(BigDecimal.ZERO)>0) {
					List<TataxRecord> profitAndLossesWrk = new ArrayList<TataxRecord>();
					List<CoinBalanceEntry> coinBalanceHistory = pricedCoinBalancesByCoinBoughtForProfitAndLoss.get(coinSold).getBalanceHistory();
					Iterator<CoinBalanceEntry> coinBalanceHistoryIterator = coinBalanceHistory.iterator();
					while(coinBalanceHistoryIterator.hasNext()) { //Per P&L
						if(amountToExaust.compareTo(BigDecimal.ZERO)==0)break; 
						CoinBalanceEntry coinBalanceEntry = coinBalanceHistoryIterator.next();
						BigDecimal previouslyBoughtAmount = coinBalanceEntry.getAmount();
						String previouslyBoughtCoin = coinBalanceEntry.getCoin();
						BigDecimal previouslySoldAmount = coinBalanceEntry.getCounterValueAmount();
						String previouslySoldCoin = coinBalanceEntry.getCounterValueCoin(); //i.e. BTC
						BigDecimal previousTradePrice = coinBalanceEntry.getPriceOfIncomeCoin(); //Quanti USDC per 1 BTC
						if(previouslySoldCoin.equals(coinBought)) {
							BigDecimal usedPreviouslyBoughtAmount = previouslyBoughtAmount;
							BigDecimal correspondingPreviouslySoldAmountPortion = previouslySoldAmount;
							if(previouslyBoughtAmount.compareTo(amountToExaust)>0) { //Allora sto prendendo troppo da actualSoldAmountFromPricedCoinBalances
								usedPreviouslyBoughtAmount = amountToExaust;
								coinBalanceEntry.setAmount(previouslyBoughtAmount.subtract(usedPreviouslyBoughtAmount));
								coinBalanceEntry.setCounterValueAmount(coinBalanceEntry.getAmount().multiply(previousTradePrice));
								correspondingPreviouslySoldAmountPortion = usedPreviouslyBoughtAmount.multiply(previousTradePrice);
							} else {
								coinBalanceHistoryIterator.remove(); //Lo scambierò tutto
							}
							amountToExaust = amountToExaust.subtract(usedPreviouslyBoughtAmount);
							residualAmountBought = residualAmountBought.subtract(usedPreviouslyBoughtAmount.multiply(soldCoinPrice));

							BigDecimal boughtAmountToConsider = usedPreviouslyBoughtAmount.multiply(soldCoinPrice);

							if(soldCoinPrice.compareTo(previousTradePrice)>=0) {
								// allora quando avevo venduto i.e. 1 BTC li avevo venduti ad un prezzo più alto di quello a cui li sto ricomprando ora
								//Quindi dovrei realizzare un profit //TODO Verify!!!
								BigDecimal profitInPrevSold = boughtAmountToConsider.subtract(correspondingPreviouslySoldAmountPortion);
								if(profitInPrevSold.compareTo(BigDecimal.ZERO)<0) {
									throw new RuntimeException("Profit can't be negative value is: " + profitInPrevSold.toString());
								}
								BigDecimal profitInPrevBought =  profitInPrevSold.divide(previousTradePrice,CommonDef.BIG_DECIMAL_DIVISION_SCALE, RoundingMode.HALF_UP);
								TataxRecord profitTataxRecord = new TataxRecord(operation.getUtcTime(),previouslyBoughtCoin,profitInPrevBought,TataxOperationType.CREDIT,BigDecimal.valueOf(0),"EUR");

								profitAndLosses.add(profitTataxRecord);
								profitAndLossesWrk.add(profitTataxRecord);
							} else {
								// allora quando avevo venduto i.e. 1 BTC li avevo venduti ad un prezzo più basso di quello a cui li sto ricomprando ora
								//Quindi dovrei realizzare un loss //TODO Verify!!!
								BigDecimal lossInSoldCoin = boughtAmountToConsider.subtract(correspondingPreviouslySoldAmountPortion);
								if(lossInSoldCoin.compareTo(BigDecimal.ZERO)>0) {
									throw new RuntimeException("Loss can't be positive value is: "+ lossInSoldCoin.toString());
								}
								BigDecimal lossInBoughtCoin = lossInSoldCoin.divide(previousTradePrice,CommonDef.BIG_DECIMAL_DIVISION_SCALE, RoundingMode.HALF_UP);
								TataxRecord lossTataxRecord = new TataxRecord(operation.getUtcTime().plusNanos(1), previouslyBoughtCoin, lossInBoughtCoin.negate(), TataxOperationType.DEBIT,BigDecimal.valueOf(0),"EUR");
								profitAndLosses.add(lossTataxRecord);
								profitAndLossesWrk.add(lossTataxRecord);
							}							
						}
					}
					if(addPAndLToTataxRecords) {
				
						for (TataxRecord profitOrLoss : profitAndLossesWrk) {
							if(profitOrLoss.getMovementType().equals(TataxOperationType.CREDIT)) {
								addRecordForTatax(profitOrLoss, "... profit",true,false);	
							}
						}
						for (TataxRecord profitOrLoss : profitAndLossesWrk) {
							if(profitOrLoss.getMovementType().equals(TataxOperationType.DEBIT)) {
								addRecordForTatax(profitOrLoss, "... loss", true, true);
							}
						}
					}
				}
				if(amountToExaust.compareTo(BigDecimal.ZERO)>0) {
					//Qui gestisco l'eventuale residuo degli spostamenti delle mie coin: le tratto come trasferimenti spot da inserire in TATAX
					BigDecimal residualAmountSold = amountToExaust;
					if(residualAmountSold.compareTo(BigDecimal.ZERO)<0) throw new RuntimeException();
					TataxRecord debitRecordForTatax = new TataxRecord(movement.getUtcTime().plusSeconds(1), operation.getCoinSold(), residualAmountSold, TataxOperationType.DEBIT,getPriceTable().getPrice(operation.getCoinSold(), operation.getUtcTime()).multiply(residualAmountSold),"EUR");
					//TataxRecord debitRecordForTatax2 = new TataxRecord(movement.getUtcTime(), operation.getCoinBought(), myCoinAmountSold.multiply(soldCoinPrice), TataxOperationType.DEBIT,"MyCoinsChosable");
					TataxRecord creditRecordForTatax = new TataxRecord(movement.getUtcTime().plusSeconds(1),operation.getCoinBought(),residualAmountBought,TataxOperationType.DEPOSIT, getPriceTable().getPrice(operation.getCoinBought(), movement.getUtcTime()).multiply(residualAmountBought), "EUR");
					//TataxRecord creditRecordForTatax2 = new TataxRecord(movement.getUtcTime(),operation.getCoinSold(),myCoinAmountSold,TataxOperationType.CREDIT,"MyCoinsChosable");
					
					addRecordForTatax(creditRecordForTatax, "... credit", true, true);

					List<CoinBalanceEntry> coinBalanceHistory = pricedCoinBalancesByCoinBoughtForProfitAndLoss.get(creditRecordForTatax.getSymbol()).getBalanceHistory();
					Iterator<CoinBalanceEntry> coinBalanceHistoryIterator = coinBalanceHistory.iterator();
					BigDecimal amountToExhaust = creditRecordForTatax.getQuantity();

					while(coinBalanceHistoryIterator.hasNext()) { 
						if(amountToExhaust.compareTo(BigDecimal.ZERO)==0) break;
						CoinBalanceEntry coinBalanceEntry = coinBalanceHistoryIterator.next();
						BigDecimal availableFromPrevSwaps = coinBalanceEntry.getAmount();
						BigDecimal availableFromPrevSwapsToUse = coinBalanceEntry.getAmount();
						BigDecimal previousTradePrice = coinBalanceEntry.getPriceOfIncomeCoin(); //Quanti USDC per 1 BTC

						if(availableFromPrevSwapsToUse.compareTo(amountToExhaust)>0) { //E' troppo
							availableFromPrevSwapsToUse = amountToExhaust;
							coinBalanceEntry.setAmount(availableFromPrevSwaps.subtract(availableFromPrevSwapsToUse));
							coinBalanceEntry.setCounterValueAmount(coinBalanceEntry.getAmount().multiply(previousTradePrice));
						} else {
							coinBalanceHistoryIterator.remove(); //Lo esaurirò tutto						
						}
						amountToExhaust = amountToExhaust.subtract(availableFromPrevSwapsToUse);
					}
					addRecordForTatax(debitRecordForTatax, "... debit", true, true);

				}
			}
			//CoherenceChecks
			checkBalances();
		}
		balanceAvailableLoans.entrySet().stream().forEach(e -> {
			logger.info("Final availableLoans: {} {}",e.getKey(), e.getValue());
		});
		pricedCoinBalancesByCoinBoughtForProfitAndLoss.entrySet().forEach(e -> {
			logger.info("Final priced: {} {}",e.getKey(),e.getValue().getBalanceHistory().stream().mapToDouble(e1 -> e1.getAmount().doubleValue()).sum());
		});
		balance.entrySet().stream().forEach(e -> {
			logger.info("Final balance: {} {}",e.getKey(), e.getValue());
		});	
		logger.info("END");
	}

	private void specialDebit(BinanceHistoryRecord bhr) {
		String coin = bhr.getCoin();
		TataxRecord debit =new TataxRecord(bhr.getUtcTime(),coin,bhr.getChange().negate(),TataxOperationType.DEBIT);
		addRecordForTatax(debit, "... special debit",true, true);
	}


	private void transferOut(BinanceHistoryRecord bhr) {
		String coin = bhr.getCoin();
		TataxRecord withdrawalTataxRecord = new TataxRecord(bhr.getUtcTime(),coin,bhr.getChange().negate(),TataxOperationType.WITHDRAWAL);
		addRecordForTatax(withdrawalTataxRecord, "... transfer out", true,true);
	}


	private void checkBalances() {
		balanceAvailableLoans.entrySet().stream().forEach(e -> {
			logger.debug("balanceAvailableLoans for {}: {}",e.getKey(), e.getValue());
			if(e.getValue().compareTo(BigDecimal.ZERO)<0) {
				throw new RuntimeException("Balance for coin " + e.getKey() + " is " +e.getValue() +" - error LESS than ZERO balanceAvailableLoans");
			}
		});
	}

	protected <T> void addElementToMappedList(Map<String, List<T>> map, String key, T value) {
		List<T> list = map.get(key);
		if (list == null) {
			list = new ArrayList<>();
			map.put(key, list);
		}
		list.add(value);
	}


	public List<TataxRecord> getProfitAndLosses() {
		return profitAndLosses;
	}

	public List<TataxRecord> getTataxRecords() {
		return recordsForTatax;
	}

	public abstract PriceTable getPriceTable();

}
