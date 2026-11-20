package fr.lisn.lahdak.acquisition.core.acqsolver;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_CNF;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_Formula;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_IConstraint;
import fr.lisn.lahdak.acquisition.core.acqconstraint.Unit;

abstract public class SATSolver {
	
	private final transient PropertyChangeSupport pcs = new PropertyChangeSupport(this);
	
	public abstract SATModel solve(ACQ_CNF T);
	
	public abstract SATModel solve(ACQ_Formula F);
	
	public abstract void setVars();
	
	public abstract void setLimit(Long timeout);
	
	public abstract Unit addVar(ACQ_IConstraint constr, String name);
	
	public abstract SATModel maxsat(ACQ_Formula Fhard, ACQ_Formula Fsoft, ACQ_Formula Fsofter);

	public abstract SATModel maxsat(ACQ_Formula Fhard, ACQ_Formula Fsoft);
	
	public abstract Boolean isTimeoutReached();
	
	public void addPropertyChangeListener(PropertyChangeListener l) {
		pcs.addPropertyChangeListener(l);
	}
	
	public void fireSolverEvent(String name,Object oldValue,Object newValue){
		pcs.firePropertyChange(name, oldValue, newValue);
	}

    public abstract SATModel solveConacq(ACQ_Formula form);
}
