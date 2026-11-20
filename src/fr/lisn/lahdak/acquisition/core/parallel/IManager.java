package fr.lisn.lahdak.acquisition.core.parallel;

import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_Network;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Bias;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;

public interface IManager {

	void visited_scopes();

	ACQ_Network getLearned_network();

	void setLearned_network(ACQ_Network learned_network);

	void setBias(ACQ_Bias bias);

	ACQ_Bias getBias();

	boolean isQuery_sharing();

	void setQuery_sharing(boolean memory_enabled);

	boolean ask_query(ACQ_Query example);

	void non_asked_query(ACQ_Query query);

}
