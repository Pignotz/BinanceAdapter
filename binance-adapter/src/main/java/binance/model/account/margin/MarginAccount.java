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
import binance.struct.Operation;
import binance.struct.TataxOperationType;
import binance.struct.TataxRecord;
import binance.struct.UtcTimedRecordWithMovement;

public abstract class MarginAccount extends Account {

	protected List<BinanceHistoryRecord> recordsForTatax;

	protected Map<String,BigDecimal> balanceAvailableLoans = new HashMap<String, BigDecimal>();
	
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

		

		Map<String, CoinBalance> pricedCoinBalances = new HashMap<String, CoinBalance>();

		for (int i = 0; i < movements.size(); i++) {
			UtcTimedRecordWithMovement movement = movements.get(i);
			logger.info(movement);
			if(!movement.isSwap()) {
				BinanceHistoryRecord bhr = (BinanceHistoryRecord) movement;
				String coin = bhr.getCoin();
				if(!balanceAvailableLoans.containsKey(coin)) {
					
					balanceAvailableLoans.put(coin, BigDecimal.ZERO);
					pricedCoinBalances.put(coin, new CoinBalance(coin));
				}
				BigDecimal change = bhr.getChange();
				switch (bhr.getOperation()) {
				case TRANSFER_ACCOUNT:
					recordsForTatax.add(bhr);
				
					break;
				case ISOLATED_MARGIN_LOAN, 
				MARGIN_LOAN:
				balanceAvailableLoans.put(coin, change.add(balanceAvailableLoans.get(coin)));		
				break;
				case TRANSACTION_FEE:
					break;
				case ISOLATED_MARGIN_REPAYMENT, 
				MARGIN_REPAYMENT:
				
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
				
				if(!balanceAvailableLoans.containsKey(coinBought)) {
				
					balanceAvailableLoans.put(coinBought, BigDecimal.ZERO);
					pricedCoinBalances.put(coinBought, new CoinBalance(coinBought));
				}
				
				BigDecimal soldCoinPrice = o.getPriceOfSoldCoin(); //i.e. quanti USDC per 1 BTC?
				BigDecimal amountSold = o.getAmountSold(); //i.e. quanti USDC Venduti?
				
				BigDecimal amountAvailableFromLoans = balanceAvailableLoans.get(coinSold);
				BigDecimal actualAmountFromLoansSold;
				BigDecimal nonLoanedCoinSold = amountSold.negate().subtract(amountAvailableFromLoans);
				
				if(nonLoanedCoinSold.compareTo(BigDecimal.ZERO)>=0) {
					actualAmountFromLoansSold = amountAvailableFromLoans;
					//Allora ho un residuo non prestato da vendere
					//Delle nonLoanedCoinSold una parte potrebbero derivare dai miei trasferimenti,
					//un'altra parte da precedenti scambi - Inizio da questi
					BigDecimal actualSoldAmountFromPricedCoinBalances = BigDecimal.ZERO;
					Iterator<CoinBalanceEntry> coinBalanceHistoryIterator = pricedCoinBalances.get(coinSold).getBalanceHistory().iterator();

					while(coinBalanceHistoryIterator.hasNext()) { //Per P&L
						CoinBalanceEntry coinBalanceEntry = coinBalanceHistoryIterator.next();
						String counterValueCoin = coinBalanceEntry.getCounterValueCoin(); //i.e. BTC
						boolean stop = false;
						if(counterValueCoin.equals(coinBought)) {
							actualSoldAmountFromPricedCoinBalances = actualSoldAmountFromPricedCoinBalances.add(coinBalanceEntry.getAmount());
							//A che prezzo il previouslySwappedAmount è stato scambiato?
							BigDecimal previousTradePrice = coinBalanceEntry.getPriceOfIncomeCoin(); //Quanti USDC per 1 BTC
							BigDecimal previouslySwappedAmountToConsider = coinBalanceEntry.getAmount();
							if(actualSoldAmountFromPricedCoinBalances.compareTo(nonLoanedCoinSold)>0) { //Allora sto prendendo troppo da amount1
								previouslySwappedAmountToConsider = coinBalanceEntry.getAmount().subtract(actualSoldAmountFromPricedCoinBalances.subtract(nonLoanedCoinSold));
								//Devo anche aggiornare la parte non scambiata di coinBalanceEntry //TODO VERIFY!!!
								coinBalanceEntry.setAmount(coinBalanceEntry.getAmount().subtract(previouslySwappedAmountToConsider));
								coinBalanceEntry.setCounterValueAmount(coinBalanceEntry.getAmount().multiply(previousTradePrice));
								actualSoldAmountFromPricedCoinBalances = nonLoanedCoinSold;
								stop = true;
							} else {
								coinBalanceHistoryIterator.remove(); //Lo scambierò tutto
							}
							if(previousTradePrice.compareTo(soldCoinPrice)>=0) {
								// allora quando avevo venduto i.e. 1 BTC li avevo venduti ad un prezzo più alto di quello a cui li sto ricomprando ora
								//Quindi dovrei realizzare un profit //TODO Verify!!!
								
							} else {
								// allora quando avevo venduto i.e. 1 BTC li avevo venduti ad un prezzo più basso di quello a cui li sto ricomprando ora
								//Quindi dovrei realizzare un loss //TODO Verify!!!
							}
							
							if(stop) break;
						}
					}
					//Qui gestisco l'eventuale residuo degli spostamenti delle mie coin: le tratto come trasferimenti spot da inserire in TATAX
					BigDecimal myCoinAmountSold = nonLoanedCoinSold.subtract(actualSoldAmountFromPricedCoinBalances);
					if(myCoinAmountSold.compareTo(BigDecimal.ZERO)<0) throw new RuntimeException();
					if(myCoinAmountSold.compareTo(BigDecimal.ZERO)>0) {
						TataxRecord debitRecordForTatax = new TataxRecord(o.getUtcTime(), o.getCoinSold(), myCoinAmountSold, TataxOperationType.DEBIT);
						
						//TODO Verify
						BigDecimal creditAmount = myCoinAmountSold.divide(soldCoinPrice,8, RoundingMode.HALF_UP);
						TataxRecord creditRecordForTatax = new TataxRecord(o.getUtcTime(), o.getCoinBought(), creditAmount, TataxOperationType.CREDIT);					
					
					}
				} else {
					actualAmountFromLoansSold = amountSold.negate();
				}
				BigDecimal actualAmountBoughtUsingLoans = actualAmountFromLoansSold.multiply(soldCoinPrice); //TODO VERIFICA SUBITO
				balanceAvailableLoans.put(coinSold, actualAmountFromLoansSold.add(balanceAvailableLoans.get(coinSold)));		

				pricedCoinBalances.get(coinBought).addCoinBalanceEntry(coinBought, actualAmountBoughtUsingLoans, coinSold, actualAmountFromLoansSold);
				
			}
			//CoherenceChecks
			checkBalances();	
		}
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

}
