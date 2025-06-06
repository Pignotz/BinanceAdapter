package binance.model;

import java.util.ArrayList;

import org.hibernate.mapping.List;
import org.springframework.stereotype.Repository;

import binance.struct.BinanceHistoryRecord;

@Repository
public class BinanceHistoryRecordList extends ArrayList<BinanceHistoryRecord> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
}
