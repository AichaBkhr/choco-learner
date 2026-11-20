package fr.lisn.lahdak.acquisition.core.combinatorial.mss;

import java.util.ArrayList;
import java.util.concurrent.TimeoutException;

import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_Formula;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_Network;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ContradictionSet;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;
import fr.lisn.lahdak.acquisition.core.tools.Chrono;

public abstract class MSSIter {
	
	protected Chrono chrono = null;
	
	abstract public boolean hasNext();
	
	abstract public ACQ_Query next() throws TimeoutException ;
	
	public void setMUS(ContradictionSet muses) {
		// by default do nothing
	}
	
	public void setChrono(Chrono chrono) {
		this.chrono = chrono;	
	}
	
	public void setCurrMSS(ACQ_Network M){
		// by default do nothing
	}

	public ArrayList<ACQ_Network> getMuses(){
		return null;
	}

	public ACQ_Formula getFormula(){
		return null;
	}

	public ACQ_Query getLastQuery() {
		return null;
	}
}
