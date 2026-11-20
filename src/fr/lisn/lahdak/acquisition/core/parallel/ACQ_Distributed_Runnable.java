package fr.lisn.lahdak.acquisition.core.parallel;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_ConstraintSolver;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_Heuristic;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Bias;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;
import fr.lisn.lahdak.acquisition.core.learners.algorithms.ACQ_QUACQ;
import fr.lisn.lahdak.acquisition.core.oracle.ACQ_Oracle;
import fr.lisn.lahdak.acquisition.core.oracle.ObservedOracle;
import fr.lisn.lahdak.acquisition.core.tools.Chrono;
import fr.lisn.lahdak.acquisition.core.tools.Collective_Stats;
import fr.lisn.lahdak.acquisition.core.tools.StatManager;
import fr.lisn.lahdak.acquisition.core.tools.TimeManager;
import fr.lisn.lahdak.acquisition.core.workspace.IExperience;

public class ACQ_Distributed_Runnable extends Thread {
	IExperience expe;
	public  ACQ_Bias bias;
	public ACQ_Oracle oracle;
	public  Collective_Stats stats;

	protected int id;
	StatManager statManager;

	public ACQ_Distributed_Runnable(int id, IExperience expe, ACQ_Bias bias, ACQ_Oracle oracle, Collective_Stats stats) {
		this.id = id;
		this.expe = expe;
		this.oracle = oracle;
		this.bias = bias;
		this.stats = stats;
		this.statManager = new StatManager(bias.getVars().size());
	}

	public  Collective_Stats LAUNCH_DIST() {
		ObservedOracle observedOracle = new ObservedOracle(oracle);
	final StatManager statManager = new StatManager(bias.getVars().size());
	PropertyChangeListener queryListener = new PropertyChangeListener() {
		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			switch (evt.getPropertyName()) {
			case "ASK":
				ACQ_Query query = (ACQ_Query) evt.getNewValue();
				statManager.update(query);
				break;
			case "NON_ASKED_QUERY":
				ACQ_Query query_ = (ACQ_Query) evt.getNewValue();
				statManager.update_non_asked_query(query_);
				break;

			}
		}
	};
	observedOracle.addPropertyChangeListener(queryListener);
	/*
	 * prepare solver
	 *
	 */

	ACQ_Heuristic heuristic = expe.getHeuristic();
	final ACQ_ConstraintSolver solver = expe.createSolver();
	solver.setVars(bias.getVars());
	solver.setLimit(expe.getTimeout());
	// observe solver for time measurement
	final TimeManager timeManager = new TimeManager();
	Chrono chrono = new Chrono(expe.getClass().getName());
	solver.addPropertyChangeListener(new PropertyChangeListener() {
		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			if (evt.getPropertyName().startsWith("TIMECOUNT")) {
				timeManager.add((Float) evt.getNewValue());
			} else if (evt.getPropertyName().startsWith("BEG")) {
				chrono.start(evt.getPropertyName().substring(4));
			} else if (evt.getPropertyName().startsWith("END")) {
				chrono.stop(evt.getPropertyName().substring(4));
			}
		}
	});
	/*
	 * Instantiate Acquisition algorithm
	 */
	ACQ_QUACQ acquisition = new ACQ_QUACQ(solver, bias, observedOracle, heuristic);
	// Param
	acquisition.setNormalizedCSP(expe.isNormalizedCSP());
	acquisition.setShuffleSplit(expe.isShuffleSplit());
	acquisition.setAllDiffDetection(expe.isAllDiffDetection());
	/*
	 * Run
	 */
	chrono.start("total");
	boolean result = acquisition.process(chrono);
	chrono.stop("total");
	stats.saveChronos(id, chrono);
	stats.saveTimeManager(id, timeManager);
	stats.savestatManager(id, statManager);
	stats.saveBias(id, acquisition.getBias());
	stats.saveLearnedNetwork(id, acquisition.getLearnedNetwork());
	stats.saveResults(id, result);


		return stats;
	}
	


	@Override
	public void run() {
		LAUNCH_DIST();
	}

}
