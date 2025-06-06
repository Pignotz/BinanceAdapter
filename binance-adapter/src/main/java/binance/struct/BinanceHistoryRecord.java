package binance.struct;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import lombok.Data;

@Data
public class BinanceHistoryRecord implements Comparable<BinanceHistoryRecord>{
    private String userId;
    private LocalDateTime utcTime;
    private String account;
    private BinanceOperationType operation;
    private String coin;
    private BigDecimal change;
    private String remark;
    
    
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
			delta = this.getOperation().compareTo(o.getOperation());
		}
		if(delta == 0) {
			delta = o.change.compareTo(this.change);
		}
		return delta;
	}
	public boolean isMarginAccount() {
		return getAccount().contains("Margin");
	}
    
    
	
    
}
