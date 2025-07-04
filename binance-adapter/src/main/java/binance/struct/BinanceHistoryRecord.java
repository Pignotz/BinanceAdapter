package binance.struct;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import binance.model.account.AccountType;
import lombok.Data;

@Data
public class BinanceHistoryRecord implements Comparable<BinanceHistoryRecord>, UtcTimedRecordWithMovement{
    private String userId;
    private LocalDateTime utcTime;
    private String account;
    private BinanceOperationType operation;
    private String coin;
    private BigDecimal change;
    private String remark;
    
    public BinanceHistoryRecord() {}
    
    public BinanceHistoryRecord(String userId, LocalDateTime utcTime, String account, BinanceOperationType operation, String coin, BigDecimal change, String remark) {
    	this.userId=userId;
    	this.utcTime=utcTime;
    	this.account=account;
    	this.operation = operation;
    	this.coin=coin;
    	this.change=change;
    	this.remark=remark;
    	
    }
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Costruttore da CSV (senza userId, lo lasci a null o setti dopo)
    public BinanceHistoryRecord(String csvLine) {
        // csvLine esempio:
        // ,"2023-11-19 22:13:32","Cross Margin","Margin Loan","SOL","120.00000000",
        // splitto per virgola esterna, rimuovendo virgolette e spazi inutili

        // Prima rimuovo la prima virgola se presente (la colonna userId è vuota)
        if (csvLine.startsWith(",")) {
            csvLine = csvLine.substring(1);
        }

        // split con virgola ma considerando che i campi sono racchiusi da doppi apici
        // quindi split semplice può creare problemi se ci fossero virgole nel remark
        // se i dati sono sempre ben formati, puoi fare:
        String[] parts = csvLine.split(",");

//        // Rimuovo eventuali virgolette residue
//        for (int i = 0; i < parts.length; i++) {
//            parts[i] = parts[i].replaceAll("^\"|\"$", "");
//        }

        this.userId = null; // o setta se hai userId altrove

        this.utcTime = LocalDateTime.parse(parts[0], FORMATTER);
        this.account = parts[1];
        this.operation = BinanceOperationType.fromDisplayName(parts[2]); // supponendo metodo statico di parsing
        this.coin = parts[3];
        this.change = BigDecimal.valueOf(Double.valueOf(parts[4]));
        this.remark = parts.length > 5 ? parts[5] : null;
    }
    
    
    
	private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	public String getUserId() {
		return userId;
	}
	public void setUserId(String userId) {
		this.userId = userId;
	}
	public LocalDateTime getUtcTime() {
		return utcTime;
	}
	public void setUtcTime(LocalDateTime utcTime) {
		this.utcTime = utcTime;
	}
	public String getAccount() {
		return account;
	}
	public void setAccount(String account) {
		this.account = account;
	}
	public BinanceOperationType getOperation() {
		return operation;
	}
	public void setOperation(BinanceOperationType operation) {
		this.operation = operation;
	}
	public String getCoin() {
		return coin;
	}
	public void setCoin(String coin) {
		this.coin = coin;
	}
	public BigDecimal getChange() {
		return change;
	}
	public void setChange(BigDecimal change) {
		this.change = change;
	}
	public String getRemark() {
		return remark;
	}
	public void setRemark(String remark) {
		this.remark = remark;
	}
	@Override
	public int hashCode() {
		return Objects.hash(account, change, coin, operation, remark, userId, utcTime);
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		BinanceHistoryRecord other = (BinanceHistoryRecord) obj;
		return Objects.equals(account, other.account) && Objects.equals(change, other.change)
				&& Objects.equals(coin, other.coin) && Objects.equals(operation, other.operation)
				&& Objects.equals(remark, other.remark) && Objects.equals(userId, other.userId)
				&& Objects.equals(utcTime, other.utcTime);
	}
	@Override
	public String toString() {
		return "BinanceHistoryRecord [utcTime=" + utcTime + ", operation=" + operation + ", coin=" + coin + ", change=" + change +"]";
	}
	public String[] toCsvRecord() {
		 return new String[] {
			        userId, 
			        formatter.format(utcTime),
			        account,
			        operation.getDisplayName(),
			        coin,
			        change.toString(),
			        remark
			    };		
	}
	@Override
	public int compareTo(BinanceHistoryRecord o) {
		int delta = this.getUtcTime().compareTo(o.utcTime);
		if(delta == 0) {
			if(this.getOperation().equals(BinanceOperationType.TRANSFER_ACCOUNT)) {
				if(this.getChange().compareTo(BigDecimal.ZERO)>0) {
					delta = o.getOperation().isLoan() ? 1 : -1;
				} else {
					delta = 1;
				}
			}else if (o.getOperation().equals(BinanceOperationType.TRANSFER_ACCOUNT)) {
				if(o.getChange().compareTo(BigDecimal.ZERO)>0) {
					delta = this.getOperation().isRepayment() ? 1 : -1;
				} else {
					delta = 1;
				}
			} else {
				delta = this.getOperation().compareTo(o.getOperation());
			}
		}
		if(delta == 0) {
			delta = this.change.compareTo(o.change);
		}
		return delta;
	}
	public boolean isMarginAccount() {
		return getAccount().contains("Margin");
	}
	public AccountType getAccountType() {
		return AccountType.getAccountType(this.getAccount());
	}
	public boolean isLoanOperation() {
		return getOperation().isLoan();
	}
	public boolean isTransferAccountOperation() {
		return getOperation().equals(BinanceOperationType.TRANSFER_ACCOUNT);
	}
	@Override
	public String getInCoin() {
		if(change.compareTo(BigDecimal.ZERO)>=0) {
			return getCoin();
		}
		return null;
	}
	@Override
	public String getOutCoin() {
		if(change.compareTo(BigDecimal.ZERO)<0) {
			return getCoin();
		}
		return null;
	}
	@Override
	public BigDecimal getInAmount() {
		if(change.compareTo(BigDecimal.ZERO)>=0) {
			return getChange();
		}
		return null;
	}
	@Override
	public BigDecimal getOutAmount() {
		if(change.compareTo(BigDecimal.ZERO)<0) {
			return getChange();
		}		
		return null;
	}
	@Override
	public boolean isSwap() {
		return false;
	}

	@Override
	public String toReadableString() {
		return new StringBuilder()
				.append(change)
				.append(" ")
				.append(coin)
				.append(" due to: ")
				.append(this.operation)
				.append(" on date: ")
				.append(utcTime)
				.toString();
	}
    
    
	
    
}
