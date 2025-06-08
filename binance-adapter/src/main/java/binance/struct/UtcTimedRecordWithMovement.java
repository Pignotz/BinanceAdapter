package binance.struct;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface UtcTimedRecordWithMovement {
	
	public LocalDateTime getUtcTime();
	
	public String getInCoin();
	public String getOutCoin();
	public BigDecimal getInAmount();
	public BigDecimal getOutAmount();

	public boolean isSwap();
	
	

}
