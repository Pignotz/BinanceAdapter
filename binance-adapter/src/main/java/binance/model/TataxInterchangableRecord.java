package binance.model;

import binance.struct.TataxRecord;

public class TataxInterchangableRecord {
	
	private final TataxRecord record1;
	private final TataxRecord record2;
	private TataxInterchangableRecord dependentRecord;
	private boolean choose2 = false;
	
	private int numOfChoseChanges = 0;

	public TataxInterchangableRecord(TataxRecord record1, TataxRecord record2) {
		this.record1=record1;
		this.record2=record2;
	}
	
	public TataxInterchangableRecord(TataxRecord record) {
		this.record1=record;
		this.record2=null;
	}

	public TataxRecord getRecord1() {
		return record1;
	}

	public TataxRecord getRecord2() {
		return record2;
	}

	public TataxRecord getChosenRecord() {
		return choose2 ? record2 : record1;
	}
	
	public TataxRecord getNonChosenRecord() {
		return choose2 ? record1 : record2;
	}
	
	public boolean isChoose2() {
		return choose2;
	}
	
	public boolean changeChose(boolean allowMoreThanOneChange) {
		if(!allowMoreThanOneChange && numOfChoseChanges>=1)return false;
		numOfChoseChanges++;
		choose2=!choose2;
		if(dependentRecord!=null) {
			dependentRecord.choose2=!dependentRecord.choose2;
		}
		return true;
	}

	@Override
	public String toString() {
		return new StringBuilder()
				.append(choose2 + " ")
				.append(System.lineSeparator())
				.append(record1.toString())
				.append(System.lineSeparator())
				.append(record2!=null ? record2.toString() : "null")
				.toString();
	}

	public void addDependent(TataxInterchangableRecord tataxInterchangableRecord2) {
		this.dependentRecord=tataxInterchangableRecord2;
	}

	public boolean isCredit() {
		return !record1.getMovementType().getDoNegateAmount();
	}

	public boolean hasDependentRecord() {
		return dependentRecord!=null;
	}

	public TataxInterchangableRecord getDependentRecord() {
		return dependentRecord;
	}

	
	
	
	
	

}
