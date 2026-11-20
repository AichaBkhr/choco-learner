package fr.lisn.lahdak.acquisition.core.oracle;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;

/**
 * This class is a wrapper to observe a given oracle with a PropertyChangeListener
 * @author agutierr
 * @author NADJIB
 */
public class ObservedOracle extends ACQ_Oracle {
	private final transient PropertyChangeSupport pcs = new PropertyChangeSupport(this);
	protected IOracle oracle;

	public ObservedOracle(IOracle oracle) {
		this.oracle = oracle;
	}
	@Override
	public Answer ask(ACQ_Query e) {
		Answer ret = oracle.ask(e);
		pcs.firePropertyChange("ASK", ret.equals(Answer.YES), e);
		return ret;
	}
	@Override
	public Answer ask_query(ACQ_Query query) {
		if (oracle instanceof ACQ_Oracle) {
			pcs.firePropertyChange("ASK", null, query);
			return oracle.ask_query(query);
			
		}
		return Answer.NO;
		}
	
	public Answer ask(ACQ_Query e, boolean fromMSS) {
		Answer ret = oracle.ask(e);
		pcs.firePropertyChange(fromMSS ? "ASK_MSS" : "ASK", ret, e);
		return ret;
	}

	@Override
		public void non_asked_query(ACQ_Query query) {
			if (oracle instanceof ACQ_Oracle) {
				oracle.non_asked_query(query);
				pcs.firePropertyChange("NON_ASKED_QUERY", null, query);

			}
		

	}



	public void addPropertyChangeListener(PropertyChangeListener l) {
		pcs.addPropertyChangeListener(l);
	}

	public void removePropertyChangeListener(PropertyChangeListener l) {
		pcs.removePropertyChangeListener(l);
	}

}
