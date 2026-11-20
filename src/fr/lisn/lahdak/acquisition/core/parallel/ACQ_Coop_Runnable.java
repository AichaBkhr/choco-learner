package fr.lisn.lahdak.acquisition.core.parallel;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.BitSet;
import java.util.concurrent.CopyOnWriteArraySet;

import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_ConstraintSolver;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_Heuristic;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;
import fr.lisn.lahdak.acquisition.core.learners.algorithms.ACQ_COOPERATION;
import fr.lisn.lahdak.acquisition.core.oracle.ACQ_Oracle;
import fr.lisn.lahdak.acquisition.core.oracle.ObservedOracle;
import fr.lisn.lahdak.acquisition.core.tools.Chrono;
import fr.lisn.lahdak.acquisition.core.tools.Collective_Stats;
import fr.lisn.lahdak.acquisition.core.tools.StatManager;
import fr.lisn.lahdak.acquisition.core.tools.TimeManager;
import fr.lisn.lahdak.acquisition.core.workspace.IExperience;

public class ACQ_Coop_Runnable implements Runnable {
	IExperience expe;
	public ACQ_Oracle oracle;
	public ACQ_Coop_Manager coop;
	public Collective_Stats stats;

	CopyOnWriteArraySet<ACQ_QueryMessage> queries_mailbox;

	private int id;

	public ACQ_Coop_Runnable(int id, IExperience expe, ACQ_Oracle oracle, ACQ_Coop_Manager coop,
							 CopyOnWriteArraySet<ACQ_QueryMessage> queries_mailbox, Collective_Stats stats) {

		this.id = id;
		this.expe = expe;
		this.oracle = oracle;
		this.coop = coop;
		this.queries_mailbox = queries_mailbox;
		this.stats = stats;

	}

	public boolean executeExperience() {

		ObservedOracle observedOracle = new ObservedOracle(this.oracle);
		// observe oracle for query stats
		StatManager statManager = new StatManager(coop.bias.getVars().size());
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
		solver.setVars(coop.bias.getVars());
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
		coop.setSharing_enabled(true);

		ACQ_COOPERATION acquisition = new ACQ_COOPERATION(solver, observedOracle, coop, heuristic, queries_mailbox,
				id);
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
		stats.saveBias(id, acquisition.getBias());
		stats.saveLearnedNetwork(id, acquisition.getLearnedNetwork());
		stats.saveResults(id, result);
		return result;

	}

	public static int[] bitSet2Int(BitSet bs) {
		int[] result = new int[bs.cardinality()];
		int counter = 0;
		for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
			result[counter++] = i;
		}
		return result;
	}

	public static int[] mergeWithoutDuplicates(int[] a, int[] b) {
		BitSet bs = new BitSet();
		for (int numvar : a)
			bs.set(numvar);
		for (int numvar : b)
			bs.set(numvar);
		return bitSet2Int(bs);
	}

	@Override
	public void run() {
		executeExperience();
	}

}
