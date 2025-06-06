package binance.model.account.margin;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Logger;

import binance.model.account.Account;
import binance.model.account.AccountType;
import binance.model.account.margin.Position.ADD_LOAN_RESULT;
import binance.model.account.margin.Position.ADD_OPERATION_RESULT;
import binance.model.account.margin.Position.ADD_REPAY_RESULT;
import binance.model.account.margin.Position.ADD_TRANSFER_RESULT;
import binance.struct.BinanceHistoryRecord;

public abstract class MarginAccount extends Account {


	protected List<BinanceHistoryRecord> recordsForTatax;

	public MarginAccount(AccountType accountType, Logger logger) {
		super(accountType, logger);
		recordsForTatax = new ArrayList<BinanceHistoryRecord>();
	}


	public void computePlusMinus() {
		List<BinanceHistoryRecord> wrkBinanceHistoryRecords = this.records.stream().sorted().collect(Collectors.toList());
		Map<String,BigDecimal> loanedByCoin = new HashMap<String, BigDecimal>();
		Map<String,BigDecimal> loanedCapitalBalanceByCoin = new HashMap<String, BigDecimal>();

		List<Position> positions = new ArrayList<Position>();
		Position p = null;
		List<Operation> ops = new ArrayList<Operation>();
		Operation op = null;
		Operation liquOp = null;
		for (BinanceHistoryRecord binanceHistoryRecord : wrkBinanceHistoryRecords) {
			logger.info(binanceHistoryRecord);
			switch (binanceHistoryRecord.getOperation()) {
			case TRANSFER_ACCOUNT:
				if(positions.isEmpty()) {
					p = new Position(binanceHistoryRecord);
					p.addTransferAndCheckClose(binanceHistoryRecord);
					positions.add(p);
				} else {
					int pIdx = positions.size()-1;
					p = positions.get(pIdx);
					ADD_TRANSFER_RESULT res = p.addTransferAndCheckClose(binanceHistoryRecord);
					while(pIdx>0 && res.equals(ADD_TRANSFER_RESULT.INCOMPATIBLE)) {
						pIdx--;
						p = positions.get(pIdx);
						res = p.addTransferAndCheckClose(binanceHistoryRecord);
					}
					if(res.equals(ADD_TRANSFER_RESULT.INCOMPATIBLE)) {
						p = new Position(binanceHistoryRecord);
						p.addTransferAndCheckClose(binanceHistoryRecord);
						positions.add(p);
					}
					if(res.equals(ADD_TRANSFER_RESULT.OK_AND_CLOSE)) {
						p.closePosition();
					}		
				}
				break;
			case ISOLATED_MARGIN_LOAN, 
			MARGIN_LOAN:
				if(positions.isEmpty()) {
					p = new Position(binanceHistoryRecord);
					p.addLoan(binanceHistoryRecord);
					positions.add(p);
				}else {
					int pIdx = positions.size()-1;
					p = positions.get(pIdx);
					ADD_LOAN_RESULT res = p.addLoan(binanceHistoryRecord);
					while(pIdx>0 && res.equals(ADD_LOAN_RESULT.INCOMPATIBLE)) {
						pIdx--;
						p = positions.get(pIdx);
						res = p.addLoan(binanceHistoryRecord);
					}
					if(res.equals(ADD_LOAN_RESULT.INCOMPATIBLE)) {
						p = new Position(binanceHistoryRecord);
						p.addLoan(binanceHistoryRecord);
						positions.add(p);
					}
				}
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
			int pIdx = positions.size()-1;
			p = positions.get(pIdx);
			while(p.addOperation(op,false).equals(ADD_OPERATION_RESULT.LOOK_FOR_COMPATIBLE_POSITION)) {
				pIdx--;
				p = positions.get(pIdx);
			}
			ops.add(op);
			op = null;

			break;
			case CROSS_MARGIN_LIQUIDATION_SMALL_ASSET_TAKEOVER:
				if(binanceHistoryRecord.getChange().compareTo(BigDecimal.ZERO)<0) {
					liquOp = new Operation(binanceHistoryRecord);
					liquOp.setUtcTime(binanceHistoryRecord.getUtcTime());
					liquOp.setAmountSold(binanceHistoryRecord.getChange());
					liquOp.setCoinSold(binanceHistoryRecord.getCoin());
				}else {
					liquOp.setAmountBought(binanceHistoryRecord.getChange());
					liquOp.setCoinBought(binanceHistoryRecord.getCoin());
					p.addOperation(liquOp,true);
					ops.add(liquOp);
					liquOp=null;
				}
				break;
			case ISOLATED_MARGIN_REPAYMENT, 
			MARGIN_REPAYMENT:
				pIdx = positions.size()-1;
			p = positions.get(pIdx);

			ADD_REPAY_RESULT res = p.addRepay(binanceHistoryRecord);
			if(res.equals(ADD_REPAY_RESULT.BNB_REPAYMENT)) {
				//TODO
			}else {
				while(res.equals(ADD_REPAY_RESULT.INCOMPATIBLE)) {
					pIdx--;
					p = positions.get(pIdx);
					res = p.addRepay(binanceHistoryRecord);
				}
				
					
			}
			break;
			case BNB_FEE_DEDUCTION:
				recordsForTatax.add(binanceHistoryRecord);
				//TODO verifica
				break;
			case ISOLATED_MARGIN_LIQUIDATION_FEE:
				recordsForTatax.add(binanceHistoryRecord);
				//TODO verifica
				break;
			default:
				throw new RuntimeException("Unmanaged Case " + binanceHistoryRecord.getOperation());
			}
		}
		ops.stream().forEach(op1 -> op1.computePrice());

	}

}
