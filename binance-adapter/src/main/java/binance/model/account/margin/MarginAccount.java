package binance.model.account.margin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Logger;

import binance.model.CoinBalance;
import binance.model.CoinBalance.CoinBalanceEntry;
import binance.model.account.Account;
import binance.model.account.AccountType;
import binance.struct.BinanceHistoryRecord;
import binance.struct.BinanceOperationType;
import binance.struct.Operation;
import binance.struct.TataxOperationType;
import binance.struct.TataxRecord;
import binance.struct.UtcTimedRecordWithMovement;

public abstract class MarginAccount extends Account {

	protected List<BinanceHistoryRecord> recordsForTatax;

	protected Map<String,BigDecimal> balanceAvailableLoansForProfitAndLossCompute;
	
	protected Map<String,BigDecimal> balanceAvailableLoansForRepayments;
	
	protected List<TataxRecord> profitAndLosses;
	
	Map<String, CoinBalance> pricedCoinBalancesByCoinBought;

	public MarginAccount(AccountType accountType, Logger logger) {
		super(accountType, logger);
		recordsForTatax = new ArrayList<BinanceHistoryRecord>();
		balanceAvailableLoansForProfitAndLossCompute = new HashMap<String, BigDecimal>();
		balanceAvailableLoansForRepayments = new HashMap<String, BigDecimal>();
		profitAndLosses = new ArrayList<TataxRecord>();
		pricedCoinBalancesByCoinBought = new HashMap<String, CoinBalance>();
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
			logger.info(binanceHistoryRecord);
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

		for (int i = 0; i < movements.size(); i++) {
			UtcTimedRecordWithMovement movement = movements.get(i);
			logger.info("Movimento corrente: {}",movement.toReadableString());
			if(!movement.isSwap()) {
				BinanceHistoryRecord bhr = (BinanceHistoryRecord) movement;
				String coin = bhr.getCoin();
				BigDecimal change = bhr.getChange();

				if(!balanceAvailableLoansForProfitAndLossCompute.containsKey(coin)) {
					balanceAvailableLoansForRepayments.put(coin, BigDecimal.ZERO);
					balanceAvailableLoansForProfitAndLossCompute.put(coin, BigDecimal.ZERO);
					pricedCoinBalancesByCoinBought.put(coin, new CoinBalance(coin));
				}
				switch (bhr.getOperation()) {
				case TRANSFER_ACCOUNT:
					recordsForTatax.add(bhr);
				
					break;
				case ISOLATED_MARGIN_LOAN, 
				MARGIN_LOAN:
				balanceAvailableLoansForProfitAndLossCompute.put(coin, change.add(balanceAvailableLoansForProfitAndLossCompute.get(coin)));
				balanceAvailableLoansForRepayments.put(coin, change.add(balanceAvailableLoansForProfitAndLossCompute.get(coin)));
				break;
				case TRANSACTION_FEE:
					break;
				case ISOLATED_MARGIN_REPAYMENT, 
				MARGIN_REPAYMENT:
					balanceAvailableLoansForRepayments.get(coin);
					//Devo saturare quanto sto ripagando togliendolo in primis dagli scambi
				BigDecimal amountToExhaust = change;
				List<CoinBalanceEntry> coinBalanceHistory = pricedCoinBalancesByCoinBought.get(coin).getBalanceHistory();
				Iterator<CoinBalanceEntry> coinBalanceHistoryIterator = coinBalanceHistory.iterator();
				while(coinBalanceHistoryIterator.hasNext()) { //Per Repay
					CoinBalanceEntry coinBalanceEntry = coinBalanceHistoryIterator.next();
					BigDecimal availableFromPrevSwaps = coinBalanceEntry.getAmount();
					BigDecimal availableFromPrevSwapsToUse = coinBalanceEntry.getAmount();
					BigDecimal previousTradePrice = coinBalanceEntry.getPriceOfIncomeCoin(); //Quanti USDC per 1 BTC

					if(availableFromPrevSwapsToUse.compareTo(amountToExhaust)>0) { //E' troppo
						availableFromPrevSwapsToUse = amountToExhaust;
						coinBalanceEntry.setAmount(availableFromPrevSwaps.subtract(availableFromPrevSwapsToUse));
						coinBalanceEntry.setCounterValueAmount(coinBalanceEntry.getAmount().multiply(previousTradePrice));
						
					} else {
						//TODO Verify!!!
						
					}
				}
				//ed in secondo luogo dalle mie monete aggiungendo il tatax debit
				break;
				case BNB_FEE_DEDUCTION:
					
					break;
				case ISOLATED_MARGIN_LIQUIDATION_FEE:
				
					break;
				default:
					throw new RuntimeException("Unmanaged Case " + bhr.getOperation());
				}
			} else {
				Operation o = (Operation) movement;
				String coinBought = o.getCoinBought(); //i.e. BTC
				String coinSold = o.getCoinSold(); //i.e. USDC
				
				if(!balanceAvailableLoansForProfitAndLossCompute.containsKey(coinBought)) {
				
					balanceAvailableLoansForProfitAndLossCompute.put(coinBought, BigDecimal.ZERO);
					pricedCoinBalancesByCoinBought.put(coinBought, new CoinBalance(coinBought));
				}
				
				BigDecimal soldCoinPrice = o.getPriceOfSoldCoin(); //i.e. quanti USDC per 1 BTC?
				BigDecimal amountSold = o.getAmountSold(); //i.e. quanti USDC Venduti?
				
				BigDecimal amountAvailableFromLoans = balanceAvailableLoansForProfitAndLossCompute.get(coinSold);
				BigDecimal actualAmountFromLoansSold;
				BigDecimal nonLoanedCoinSold = amountSold.negate().subtract(amountAvailableFromLoans);
				
				if(nonLoanedCoinSold.compareTo(BigDecimal.ZERO)>=0) {
					logger.info("... sto utilizzando {} {} dal denaro non preso in prestito per la vendita", nonLoanedCoinSold, o.getCoinSold());
					actualAmountFromLoansSold = amountAvailableFromLoans;
					//Allora ho un residuo non prestato da vendere
					//Delle nonLoanedCoinSold una parte potrebbero derivare dai miei trasferimenti,
					//un'altra parte da precedenti scambi - Inizio da questi
					BigDecimal actualSoldAmountFromPreviousSwaps = BigDecimal.ZERO;
					List<CoinBalanceEntry> coinBalanceHistory = pricedCoinBalancesByCoinBought.get(coinSold).getBalanceHistory();
					Iterator<CoinBalanceEntry> coinBalanceHistoryIterator = coinBalanceHistory.iterator();
					while(coinBalanceHistoryIterator.hasNext()) { //Per P&L
						if(actualSoldAmountFromPreviousSwaps.compareTo(nonLoanedCoinSold)==0) {
							break;
						}
						CoinBalanceEntry coinBalanceEntry = coinBalanceHistoryIterator.next();
						BigDecimal previouslyBoughtAmount = coinBalanceEntry.getAmount();
						String previouslyBoughtCoin = coinBalanceEntry.getCoin();
						BigDecimal previouslySoldAmount = coinBalanceEntry.getCounterValueAmount();
						String previouslySoldCoin = coinBalanceEntry.getCounterValueCoin(); //i.e. BTC
						
						boolean stop = false;
						if(previouslySoldCoin.equals(coinBought)) {
							actualSoldAmountFromPreviousSwaps = actualSoldAmountFromPreviousSwaps.add(previouslyBoughtAmount);
							//A che prezzo il previouslySwappedAmount è stato scambiato?
							BigDecimal previousTradePrice = coinBalanceEntry.getPriceOfIncomeCoin(); //Quanti USDC per 1 BTC
							BigDecimal usedPreviouslyBoughtAmount = previouslyBoughtAmount;
							if(actualSoldAmountFromPreviousSwaps.compareTo(nonLoanedCoinSold)>0) { //Allora sto prendendo troppo da actualSoldAmountFromPricedCoinBalances
								usedPreviouslyBoughtAmount = previouslyBoughtAmount.subtract(actualSoldAmountFromPreviousSwaps.subtract(nonLoanedCoinSold));
								//Devo anche aggiornare la parte non scambiata di coinBalanceEntry //TODO VERIFY!!!
								coinBalanceEntry.setAmount(previouslyBoughtAmount.subtract(usedPreviouslyBoughtAmount));
								coinBalanceEntry.setCounterValueAmount(coinBalanceEntry.getAmount().multiply(previousTradePrice));
								actualSoldAmountFromPreviousSwaps = nonLoanedCoinSold;
								stop = true;
							} else {
								coinBalanceHistoryIterator.remove(); //Lo scambierò tutto
							}
							logger.info("... ... di cui {} ottenuti da precedenti scambi", usedPreviouslyBoughtAmount);
							BigDecimal boughtAmountToConsider = usedPreviouslyBoughtAmount.multiply(soldCoinPrice);
							if(soldCoinPrice.compareTo(previousTradePrice)>=0) {
								// allora quando avevo venduto i.e. 1 BTC li avevo venduti ad un prezzo più alto di quello a cui li sto ricomprando ora
								//Quindi dovrei realizzare un profit //TODO Verify!!!
								BigDecimal profit = boughtAmountToConsider.subtract(previouslySoldAmount);
								if(profit.compareTo(BigDecimal.ZERO)<0) {
									throw new RuntimeException("Profit can't be negative");
								}
								profitAndLosses.add(new TataxRecord(o.getUtcTime(), previouslySoldCoin, profit, TataxOperationType.CREDIT));
								
							} else {
								// allora quando avevo venduto i.e. 1 BTC li avevo venduti ad un prezzo più basso di quello a cui li sto ricomprando ora
								//Quindi dovrei realizzare un loss //TODO Verify!!!
								BigDecimal loss = boughtAmountToConsider.subtract(previouslySoldAmount);
								if(loss.compareTo(BigDecimal.ZERO)>0) {
									throw new RuntimeException("Profit can't be negative");
								}
								profitAndLosses.add(new TataxRecord(o.getUtcTime(), previouslySoldCoin, loss, TataxOperationType.DEBIT));
							}
							if(stop) break;
						}
					}
					//Qui gestisco l'eventuale residuo degli spostamenti delle mie coin: le tratto come trasferimenti spot da inserire in TATAX
					BigDecimal myCoinAmountSold = nonLoanedCoinSold.subtract(actualSoldAmountFromPreviousSwaps);
					if(myCoinAmountSold.compareTo(BigDecimal.ZERO)<0) throw new RuntimeException();
					if(myCoinAmountSold.compareTo(BigDecimal.ZERO)>0) {
						logger.info("... ... di cui {} utilizzando il mio capitale effettivo non preso in prestito e non derivante da scambi con denaro preso in prestito", myCoinAmountSold);

						//TODO Verify
						BinanceHistoryRecord debitRecordForTatax = new BinanceHistoryRecord("", movement.getUtcTime(), this.accountType.toString(), BinanceOperationType.TRANSACTION_SOLD, o.getCoinSold(), myCoinAmountSold, "MyCoinsDebit");
						recordsForTatax.add(debitRecordForTatax);
						//TODO Verify
						BigDecimal creditAmount = myCoinAmountSold.multiply(soldCoinPrice);
						BinanceHistoryRecord creditRecordForTatax = new BinanceHistoryRecord("",movement.getUtcTime(),accountType.toString(),BinanceOperationType.TRANSACTION_BUY,o.getCoinBought(),creditAmount,"MyCoinsCredit");
						recordsForTatax.add(creditRecordForTatax);
					}
				} else {
					actualAmountFromLoansSold = amountSold.negate();
				}
				logger.info("... sto utilizzando {} dal denaro preso in prestito per la vendita", actualAmountFromLoansSold);
				BigDecimal actualAmountBoughtUsingLoans = actualAmountFromLoansSold.multiply(soldCoinPrice); //TODO VERIFICA SUBITO
				balanceAvailableLoansForProfitAndLossCompute.put(coinSold, balanceAvailableLoansForProfitAndLossCompute.get(coinSold).subtract(actualAmountFromLoansSold));		

				pricedCoinBalancesByCoinBought.get(coinBought).addCoinBalanceEntry(coinBought, actualAmountBoughtUsingLoans, coinSold, actualAmountFromLoansSold);
				
			}
			//CoherenceChecks
			checkBalances();	
		}
	}


	private void checkBalances() {
		balanceAvailableLoansForProfitAndLossCompute.entrySet().stream().forEach(e -> {
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
}
