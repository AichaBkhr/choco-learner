package fr.lisn.lahdak.acquisition.core.acqsolver;

import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_IConstraint;
import fr.lisn.lahdak.acquisition.core.acqconstraint.Unit;

abstract public class SATModel {
	
	public abstract Boolean get(Unit unit);
	
	public abstract String toString();

	public ACQ_IConstraint[] getPositive() {
		// TODO Auto-generated method stub
		return null;
	}


}
