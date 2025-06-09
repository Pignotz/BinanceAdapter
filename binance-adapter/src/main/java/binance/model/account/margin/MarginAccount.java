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

	protected Map<String,BigDecimal> balanceIncludingLoans = new HashMap<String, BigDecimal>();
	protected Map<String,BigDecimal> balanceExcludingLoans = new HashMap<String, BigDecimal>();
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
			if(!movement.isSwap()) {
				BinanceHistoryRecord bhr = (BinanceHistoryRecord) movement;
				String coin = bhr.getCoin();
				if(!balanceExcludingLoans.containsKey(coin)) {
					balanceExcludingLoans.put(coin, BigDecimal.ZERO);
					balanceIncludingLoans.put(coin, BigDecimal.ZERO);
					balanceAvailableLoans.put(coin, BigDecimal.ZERO);
					pricedCoinBalances.put(coin, new CoinBalance(coin));
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
				balanceAvailableLoans.put(coin, change.add(balanceAvailableLoans.get(coin)));		
				break;
				case TRANSACTION_FEE:
					break;
				case ISOLATED_MARGIN_REPAYMENT, 
				MARGIN_REPAYMENT:
					balanceIncludingLoans.put(coin, change.add(balanceIncludingLoans.get(coin)));
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
				String coinBought = o.getCoinBought(); //i.e. BTC
				String coinSold = o.getCoinSold(); //i.e. USDC
				
				if(!balanceExcludingLoans.containsKey(coinBought)) {
					balanceExcludingLoans.put(coinBought, BigDecimal.ZERO);
					balanceIncludingLoans.put(coinBought, BigDecimal.ZERO);
					balanceAvailableLoans.put(coinBought, BigDecimal.ZERO);
					pricedCoinBalances.put(coinBought, new CoinBalance(coinBought));
				}
				
				BigDecimal boughtCoinPrice = o.getPriceOfBoughtCoin(); //i.e. quanti USDC per 1 BTC?
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
							if(previousTradePrice.compareTo(boughtCoinPrice)>=0) {
								// allora quando avevo venduto i.e. 1 BTC li avevo venduti ad un prezzo più alto di quello a cui li sto ricomprando ora
								//Quindi dovrei realizzare un profit //TODO Verify!!!
								
							}else {
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
						balanceIncludingLoans.put(o.getCoinSold(), balanceIncludingLoans.get(o.getCoinSold()).subtract(myCoinAmountSold));
						balanceExcludingLoans.put(o.getCoinSold(), balanceExcludingLoans.get(o.getCoinSold()).subtract(myCoinAmountSold));
						
						//TODO Verify
						BigDecimal creditAmount = myCoinAmountSold.divide(boughtCoinPrice,8, RoundingMode.HALF_UP);
						TataxRecord creditRecordForTatax = new TataxRecord(o.getUtcTime(), o.getCoinBought(), creditAmount, TataxOperationType.CREDIT);					
						balanceIncludingLoans.put(o.getCoinBought(), balanceIncludingLoans.get(o.getCoinBought()).add(creditAmount));
						balanceExcludingLoans.put(o.getCoinBought(), balanceExcludingLoans.get(o.getCoinBought()).add(creditAmount));
					}
				} else {
					actualAmountFromLoansSold = amountSold.negate();
				}
				BigDecimal actualAmountBoughtUsingLoans = actualAmountFromLoansSold.multiply(nonLoanedCoinSold); //TODO VERIFICA SUBITO
				pricedCoinBalances.get(coinBought).addCoinBalanceEntry(coinBought, actualAmountBoughtUsingLoans, coinSold, actualAmountFromLoansSold);
				balanceIncludingLoans.put(coinBought, balanceIncludingLoans.get(coinBought).add(actualAmountBoughtUsingLoans));
				
				balanceIncludingLoans.put(coinSold, balanceIncludingLoans.get(coinBought).subtract(actualAmountFromLoansSold));
				balanceAvailableLoans.put(coinSold, balanceAvailableLoans.get(coinSold).add(actualAmountBoughtUsingLoans));
			}
			//CoherenceChecks
			checkBalances();	
		}
	}


	private void checkBalances() {
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
		balanceAvailableLoans.entrySet().stream().forEach(e -> {
			if(e.getValue().compareTo(BigDecimal.ZERO)<0) {
				throw new RuntimeException("Balance for coin " + e.getKey() + " became less then zero for balanceExcludingLoans");
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
