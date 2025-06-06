package binance.model.account.margin;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import binance.job.steps.ComputePlusMinusStepConfig;
import binance.struct.BinanceHistoryRecord;
import binance.struct.BinanceOperationType;

public class Position {

	private Logger logger = LogManager.getLogger(Position.class);

	boolean isClosed;
	String loanedCoin1;
	String loanedCoin2;

	String baseCoin;
	String quoteCoin;

	List<BinanceHistoryRecord> transfersIn = new ArrayList<BinanceHistoryRecord>();
	List<BinanceHistoryRecord> transfersOut = new ArrayList<BinanceHistoryRecord>();

	List<BinanceHistoryRecord> loans = new ArrayList<BinanceHistoryRecord>();
	List<Operation> swaps = new ArrayList<Operation>();
	List<BinanceHistoryRecord> repays = new ArrayList<BinanceHistoryRecord>();

	public Position(BinanceHistoryRecord binanceHistoryRecord) {
		logger.warn("CREATING POSITION FROM: {}", binanceHistoryRecord);
		if(binanceHistoryRecord.isLoanOperation()) {
			loanedCoin1 = binanceHistoryRecord.getCoin();
		} else if(binanceHistoryRecord.isTransferAccountOperation()){
			//TODO
		} else {
			throw new RuntimeException("logic not defined");
		}
	}

	public ADD_OPERATION_RESULT addOperation(Operation op, boolean couldReopen) {
		if(isClosed) {
			if(!couldReopen) {
				return ADD_OPERATION_RESULT.LOOK_FOR_COMPATIBLE_POSITION;
			}
		}
		if(op.getCoinBought()==null || op.getCoinSold()==null) {
			throw new RuntimeException("Incomplete Operation "+ op);
		}
		if(swaps.isEmpty()) { //first op
			if(op.isBoughtIsBase()) {
				baseCoin = op.getCoinBought();
				quoteCoin = op.getCoinSold();
			} else {
				quoteCoin = op.getCoinBought();
				baseCoin = op.getCoinSold();
			}
			if(loanedCoin1!=null) {
				if(loanedCoin1.equals(baseCoin)) {
					loanedCoin2 = quoteCoin;
				}else {
					loanedCoin2 = baseCoin;
				}
			} else {
				loanedCoin1 = baseCoin;
				loanedCoin2 = quoteCoin;
			}
		} else {
			if(op.isBoughtIsBase() && (!baseCoin.equals(op.getCoinBought()) || !quoteCoin.equals(op.getCoinSold()))) {
				if(op.getCoinBought().equals("BNB")) {
					//Use my coins to buy BNB
					//TODO
				} else {
					return ADD_OPERATION_RESULT.LOOK_FOR_COMPATIBLE_POSITION;
				}
			} else if(!op.isBoughtIsBase() && (!baseCoin.equals(op.getCoinSold()) || !quoteCoin.equals(op.getCoinBought()))) {
				return ADD_OPERATION_RESULT.LOOK_FOR_COMPATIBLE_POSITION;
			}
		}
		logger.warn("ADDING OPERATION: {}", op);
		swaps.add(op);
		isClosed=false;
		return ADD_OPERATION_RESULT.OK;
	}

	public static enum ADD_OPERATION_RESULT {
		OK,
		LOOK_FOR_COMPATIBLE_POSITION;
	}

	public String getPair() {
		return baseCoin+"/"+quoteCoin;
	}

	public ADD_LOAN_RESULT addLoan(BinanceHistoryRecord binanceHistoryRecord) {
		if(isClosed) {
			return ADD_LOAN_RESULT.INCOMPATIBLE;
		}
		if(loanedCoin1==null) {
			loanedCoin1 = binanceHistoryRecord.getCoin();
		}else {
			if(!loanedCoin1.equals(binanceHistoryRecord.getCoin())) {
				if(loanedCoin2 == null) {
					loanedCoin2 = binanceHistoryRecord.getCoin();
				} else {
					if(!loanedCoin2.equals(binanceHistoryRecord.getCoin())) {
						return ADD_LOAN_RESULT.INCOMPATIBLE;	
					}
				}
			}
		}
		logger.warn("ADDING LOAN: {}", binanceHistoryRecord);
		loans.add(binanceHistoryRecord);
		return ADD_LOAN_RESULT.OK;
	}


	public static enum ADD_LOAN_RESULT {
		OK,
		INCOMPATIBLE;
	}

	public ADD_REPAY_RESULT addRepay(BinanceHistoryRecord binanceHistoryRecord) {
		if(!loanedCoin1.equals(binanceHistoryRecord.getCoin())) {
			if(loanedCoin2 == null) {
				loanedCoin2 = binanceHistoryRecord.getCoin();
			} else {
				if(!loanedCoin2.equals(binanceHistoryRecord.getCoin())) {
					if(binanceHistoryRecord.getOperation().equals(BinanceOperationType.MARGIN_REPAYMENT)
							&& binanceHistoryRecord.getCoin().equals("BNB")) {
						//TODO per ora ritorno OK e basta
						return ADD_REPAY_RESULT.BNB_REPAYMENT;
					}else {
						return ADD_REPAY_RESULT.INCOMPATIBLE;	
					}
				}
			}
		}
		if(isClosed) {
			return ADD_REPAY_RESULT.INCOMPATIBLE;
		}

		logger.warn("ADDING REPAY: {}", binanceHistoryRecord);
		repays.add(binanceHistoryRecord);
		return ADD_REPAY_RESULT.OK;
	}

	public static enum ADD_REPAY_RESULT {
		OK,
		INCOMPATIBLE,
		BNB_REPAYMENT;
	}


	public ADD_TRANSFER_RESULT addTransferAndCheckClose(BinanceHistoryRecord binanceHistoryRecord) {
		if(isClosed) {
			return ADD_TRANSFER_RESULT.INCOMPATIBLE;
		}
		String transferredCoin = binanceHistoryRecord.getCoin();
		boolean compatible = false;
		if(baseCoin==null && quoteCoin == null) {
			compatible=true;
		}else {
			if(baseCoin!=null && transferredCoin.equals(baseCoin)) {
				compatible = true;
			}else if(quoteCoin!=null && transferredCoin.equals(quoteCoin)) {
				compatible = true;
			}		
		}
		if(!compatible) return ADD_TRANSFER_RESULT.INCOMPATIBLE;
		logger.warn("ADDING TRANSFER: {}", binanceHistoryRecord);
		if(binanceHistoryRecord.getChange().compareTo(BigDecimal.ZERO)>0) {
			transfersIn.add(binanceHistoryRecord);
		}else {
			transfersOut.add(binanceHistoryRecord);
		}

		boolean doClose = true;
		Map<String,List<BinanceHistoryRecord>> myCoinIn = transfersIn.stream().collect(Collectors.groupingBy(l -> l.getCoin()));
		Map<String,List<BinanceHistoryRecord>> myCoinOut = transfersOut.stream().collect(Collectors.groupingBy(l -> l.getCoin()));
		Map<String,List<BinanceHistoryRecord>> loansByCoin = loans.stream().collect(Collectors.groupingBy(l -> l.getCoin()));
		Map<String,List<BinanceHistoryRecord>> repayByCoin = repays.stream().collect(Collectors.groupingBy(l -> l.getCoin()));
		Map<String,BigDecimal> totalInByCoin = new HashMap<String, BigDecimal>();
		Map<String,BigDecimal> totalOutByCoin = new HashMap<String, BigDecimal>();
		Map<String,BigDecimal> totalLoanedByCoin = new HashMap<String, BigDecimal>();
		Map<String,BigDecimal> totalRepayedByCoin = new HashMap<String, BigDecimal>();
		myCoinIn.entrySet().forEach(e -> {
			BigDecimal total = BigDecimal.ZERO;
			for (BinanceHistoryRecord in : e.getValue()) {
				total = total.add(in.getChange());
			}
			totalInByCoin.put(e.getKey(), total);
		});
		myCoinOut.entrySet().forEach(e -> {
			BigDecimal total = BigDecimal.ZERO;
			for (BinanceHistoryRecord out : e.getValue()) {
				total = total.add(out.getChange());
			}
			totalOutByCoin.put(e.getKey(), total);
		});
		loansByCoin.entrySet().forEach(e -> {
			BigDecimal total = BigDecimal.ZERO;
			for (BinanceHistoryRecord loan : e.getValue()) {
				total = total.add(loan.getChange());
			}
			totalLoanedByCoin.put(e.getKey(), total);
		});
		repayByCoin.entrySet().forEach(e -> {
			BigDecimal total = BigDecimal.ZERO;
			for (BinanceHistoryRecord loan : e.getValue()) {
				total = total.add(loan.getChange());
			}
			totalRepayedByCoin.put(e.getKey(), total);
		});

		for(Entry<String, BigDecimal> e : totalLoanedByCoin.entrySet()) {
			BigDecimal myInAmount = totalInByCoin.get(e.getKey());
			BigDecimal myOutAmount = totalOutByCoin.get(e.getKey());
			BigDecimal loanedAmount = e.getValue();
			BigDecimal repayedAmount = totalRepayedByCoin.get(e.getKey());
			BigDecimal remainingToRepay = loanedAmount.add(repayedAmount);
			if(remainingToRepay.compareTo(BigDecimal.ZERO)<=0) {
				BigDecimal interestPayed = remainingToRepay.negate();
				//TODO aggiunti transazione interesse

				//Ora controllo se posso chiudere
				if(myInAmount!=null) {
					BigDecimal checkCloseAmount = myInAmount.add(myOutAmount).subtract(interestPayed);
					if(!checkCloseAmount.equals(BigDecimal.ZERO)) {
						doClose=false;
					}
				}
			}else {
				doClose=false;
			}
		}			
		return doClose ? ADD_TRANSFER_RESULT.OK_AND_CLOSE : ADD_TRANSFER_RESULT.OK;
	}
	public static enum ADD_TRANSFER_RESULT {
		OK,
		OK_AND_CLOSE,
		INCOMPATIBLE,
	}

	public void closePosition() {
		isClosed = true;
	}

	@Override
	public String toString() {
		return "Position "+getPair() + 
				" - " + (isClosed ? "CLOSED" : "OPEN");
	}





}