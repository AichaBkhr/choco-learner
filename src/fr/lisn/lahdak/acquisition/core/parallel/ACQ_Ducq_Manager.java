package fr.lisn.lahdak.acquisition.core.parallel;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import fr.lisn.lahdak.acquisition.core.learners.ACQ_Bias;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Scope;

public class ACQ_Ducq_Manager extends ACQ_PACQ_Manager {

	private int sessionSize;
	CopyOnWriteArrayList<ACQ_Bias> list_of_biases;
	CopyOnWriteArrayList<ACQ_Scope> sessions;
	CopyOnWriteArrayList<ACQ_Scope> open_sessions;
	private int index = 0;
	private int nb_threads;

	public int getNb_threads() {
		return nb_threads;
	}

	public void setNb_threads(int nb_threads) {
		this.nb_threads = nb_threads;
	}

	private boolean all_sessions = true;

	public ACQ_Ducq_Manager(CopyOnWriteArraySet<ACQ_QueryMessage> queries_mailbox) {
		super(queries_mailbox);
		list_of_biases = new CopyOnWriteArrayList<>();
		sessions = new CopyOnWriteArrayList<>();
		open_sessions = new CopyOnWriteArrayList<>();

	}

	synchronized  public void set_sessions(int Users) {

		if (all_sessions)
			set_all_sessions(Users);
		else
			set_level_sessions(Users);
	}

	synchronized  private void set_all_sessions(int Users) {

		sessionSize = bias.getVars().size() / Users;

		if (sessionSize < bias.computeMaxArity())
			sessionSize = bias.computeMaxArity();

		sessions = ACQ_Scope.generate(bias.getVars().size(), bias.computeMaxArity());

	}

	synchronized private void set_level_sessions(int Users) {

		sessionSize = bias.getVars().size() / Users;

		if (sessionSize < bias.computeMaxArity())
			sessionSize = bias.computeMaxArity();

		sessions = bias.getVars().split_into(bias.getVars().size(), sessionSize, index);

	}

	synchronized  public boolean noSession() {
		return sessions.size() == 0;
	}

	synchronized  public ACQ_Scope getSession() {

		for (ACQ_Scope session : sessions)
			if (!isInterfering(session)) {
				sessions.remove(session);
				open_sessions.add(session);
				return session;

			}
		return new ACQ_Scope();
	}

	synchronized  private boolean isInterfering(ACQ_Scope session) {

		for (ACQ_Scope ses : open_sessions)
			if (ses.intersect(session))
				return true;
		return false;
	}

	synchronized  public void closeSession(ACQ_Scope session) {

		open_sessions.remove(session);
	}

	synchronized public int getSessionSize() {
		return sessions.size();
	}

	synchronized  public void increase_index() {
		this.index = (this.index + 1) % bias.getVars().size();
	}
}
