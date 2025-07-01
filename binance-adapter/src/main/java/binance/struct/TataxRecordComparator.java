package binance.struct;

import java.util.Comparator;

public class TataxRecordComparator implements Comparator<TataxRecord>{

	@Override
	public int compare(TataxRecord e1, TataxRecord e2) {

		int delta = e1.getTimeStamp().compareTo(e2.getTimeStamp());
		if(delta==0) {
			delta = e2.getMovementType().compareTo(e1.getMovementType());
		}
		if(delta==0) {
			delta = e2.getSymbol().compareTo(e1.getSymbol());
		}
		return delta;
	
	}

}
