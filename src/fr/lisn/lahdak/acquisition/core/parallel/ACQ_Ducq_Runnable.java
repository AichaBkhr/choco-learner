package fr.lisn.lahdak.acquisition.core.parallel;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.concurrent.CopyOnWriteArraySet;

import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_ConstraintSolver;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_Heuristic;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Bias;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;
import fr.lisn.lahdak.acquisition.core.learners.algorithms.ACQ_DUCQ;
import fr.lisn.lahdak.acquisition.core.oracle.ACQ_Oracle;
import fr.lisn.lahdak.acquisition.core.oracle.ObservedOracle;
import fr.lisn.lahdak.acquisition.core.tools.Chrono;
import fr.lisn.lahdak.acquisition.core.tools.Collective_Stats;
import fr.lisn.lahdak.acquisition.core.tools.StatManager;
import fr.lisn.lahdak.acquisition.core.tools.TimeManager;
import fr.lisn.lahdak.acquisition.core.workspace.IExperience;

public class ACQ_Ducq_Runnable extends ACQ_PACQ_Runnable {

	
	public ACQ_Ducq_Runnable(int id, IExperience expe, ACQ_Bias bias, ACQ_Oracle oracle, ACQ_PACQ_Manager coop,
							 CopyOnWriteArraySet<ACQ_QueryMessage> queries_mailbox, Collective_Stats stats) {
		super(id, expe, bias, oracle, coop, queries_mailbox, stats);

	}

	
	
	public boolean executeExperience() {

		ObservedOracle observedOracle = new ObservedOracle(this.oracle);
		// observe oracle for query stats
		StatManager statManager = new StatManager(bias.getVars().size());
		PropertyChangeListener queryListener = new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				switch (evt.getPropertyName()) {
				case "ASK":
					ACQ_Query query = (ACQ_Query) evt.getNewValue();
					statManager.update(query);
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
		
		ACQ_DUCQ acquisition = new ACQ_DUCQ(solver, bias, observedOracle, (ACQ_Ducq_Manager) this.coop, heuristic, queries_mailbox);
		// Param
		acquisition.setNormalizedCSP(expe.isNormalizedCSP());
		acquisition.setShuffleSplit(expe.isShuffleSplit());
		acquisition.setAllDiffDetection(expe.isAllDiffDetection());
		acquisition.setThread_id(Thread.currentThread().getName());
		/*
		 * Run
		 */
		chrono.start("total");
		boolean result = acquisition.process();
		chrono.stop("total");
		stats.saveChronos(id, chrono);
		stats.saveTimeManager(id, timeManager);
		stats.savestatManager(id, statManager);
		//stats.saveBias(id, coop.getPartition(Thread.currentThread().getName()));
		stats.saveLearnedNetwork(id, acquisition.getLearnedNetwork());
		stats.saveResults(id, result);

		return result;

	}
	
	@Override
	public void run() {
		executeExperience();
	}

}
