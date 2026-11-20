package fr.lisn.lahdak.acquisition.core.workspace;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Date;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import fr.lisn.lahdak.acquisition.core.learners.algorithms.*;
import org.chocosolver.util.tools.StringUtils;
import org.json.JSONObject;

import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_ConjunctionConstraint;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_IConstraint;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_Network;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ContradictionSet;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_ConstraintSolver;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_Heuristic;
import fr.lisn.lahdak.acquisition.core.acqsolver.SATSolver;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Algorithm;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Bias;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;
import fr.lisn.lahdak.acquisition.core.oracle.ACQ_Oracle;
import fr.lisn.lahdak.acquisition.core.oracle.Answer;
import fr.lisn.lahdak.acquisition.core.oracle.ObservedOracle;
import fr.lisn.lahdak.acquisition.core.parallel.ACQ_Coop_Manager;
import fr.lisn.lahdak.acquisition.core.parallel.ACQ_Coop_Runnable;
import fr.lisn.lahdak.acquisition.core.parallel.ACQ_Distributed_Runnable;
import fr.lisn.lahdak.acquisition.core.parallel.ACQ_Ducq_Manager;
import fr.lisn.lahdak.acquisition.core.parallel.ACQ_Ducq_Runnable;
import fr.lisn.lahdak.acquisition.core.parallel.ACQ_PACQ_Manager;
import fr.lisn.lahdak.acquisition.core.parallel.ACQ_PACQ_Runnable;
import fr.lisn.lahdak.acquisition.core.parallel.ACQ_Partition;
import fr.lisn.lahdak.acquisition.core.parallel.ACQ_QueryMessage;
import fr.lisn.lahdak.acquisition.core.tools.Chrono;
import fr.lisn.lahdak.acquisition.core.tools.Collective_Stats;
import fr.lisn.lahdak.acquisition.core.tools.FileManager;
import fr.lisn.lahdak.acquisition.core.tools.StatManager;
import fr.lisn.lahdak.acquisition.core.tools.TimeManager;
import fr.lisn.lahdak.acquisition.core.tools.TimeUnit;
import fr.lisn.lahdak.acquisition.core.tools.convergenceTest.AcquisitionRate;

public class ACQ_WS {


	/** JSON output file, set with AcqApp -o/--out. When null, the historical per-algorithm name is used. */
	public static String outputJson = null;

	private static String jsonOutputName(IExperience expe, String defaultSuffix) {
		return outputJson != null ? outputJson : expe.getName() + defaultSuffix;
	}
	public static Collective_Stats stats = new Collective_Stats();
	public static int instance = 0;
	private static boolean printLearnedNetworkInLogFile = false;
	private static boolean printBiasInLogFile = false;

	/** -Dfirstquery=neg : first random example (java.util.Random seed 0, uniform over the domains) classified negative by
	 *  the oracle (these classifications are not counted as queries). The same example is given to every tool. */
	public static ACQ_Query firstNegativeQuery(ACQ_Oracle rawOracle, ACQ_Bias bias, ACQ_ConstraintSolver solver) {
		if (!"neg".equals(System.getProperty("firstquery"))) return null;
		fr.lisn.lahdak.acquisition.core.learners.ACQ_Scope scope = bias.getVars();
		int n = scope.size();
		java.util.Random rnd = new java.util.Random(0);
		for (int tries = 0; tries < 100000000; tries++) {
			int[] v = new int[n];
			for (int i = 0; i < n; i++) v[i] = solver.getDomain().getMin(i) + rnd.nextInt(solver.getDomain().getMax(i) - solver.getDomain().getMin(i) + 1);
			ACQ_Query probe = new ACQ_Query(scope, v.clone());
			if (rawOracle.ask(probe) != fr.lisn.lahdak.acquisition.core.oracle.Answer.YES) {
				System.out.println("FIRSTQUERY neg " + java.util.Arrays.toString(v));
				return new ACQ_Query(scope, v.clone());
			}
		}
		throw new IllegalStateException("no negative example");
	}

	public static Collective_Stats executeExperience(IExperience expe) {

		/*
		 * prepare bias
		 */
		int id = 0;
		ACQ_Bias bias = expe.createBias();
		/*
		 * prepare oracle
		 */
		ACQ_Oracle oracle = expe.createOracle();
		ObservedOracle observedOracle = new ObservedOracle(oracle);
		// observe oracle for query stats
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
			private boolean printTimeInLogFile = true;

			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				if (evt.getPropertyName().startsWith("TIMECOUNT")) {
					float time = (Float) evt.getNewValue();
					timeManager.add((Float) time);
					if (printTimeInLogFile)
						FileManager.writeLog(time, "chrono");
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
		acquisition.setVerbose(expe.isVerbose());
		acquisition.setLog_queries(expe.isLog_queries());

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
		stats.ComputeGlobalStats(0, observedOracle, bias, (DefaultExperience) expe, oracle.memory.size());

		/*
		 * Print results
		 */
		System.out.println("Learned Network Size: " + acquisition.getLearnedNetwork().size());
		System.out.println("Initial Bias size: " + acquisition.getBias().getInitial_size());
		System.out.println("Final Bias size: " + acquisition.getBias().getSize());

		System.out.println(statManager + "\n" + timeManager.getResults());
		DecimalFormat df = new DecimalFormat("0.00E0");
		double totalTime = (double) chrono.getResult("total") / 1000.0;
		double total_acq_time = (double) chrono.getLast("total_acq_time") / 1000.0;

		System.out.println("------Execution times------");
		for (String serieName : chrono.getSerieNames()) {
			if (!serieName.contains("total")) {
				double serieTime = (double) chrono.getResult(serieName) / 1000.0;
				System.out.println(serieName + " : " + df.format(serieTime));
			}
		}
		System.out.println("Convergence time : " + df.format(totalTime));
		System.out.println("Acquisition time : " + df.format(total_acq_time));
		System.out.println("*************Learned Network CL example ******");
		ACQ_Query q = solver.solveA(acquisition.getLearnedNetwork());

		q.classify(oracle.ask(q).equals(Answer.YES)? Answer.YES:Answer.NO);
		System.out.println("query : " + q.toString());

		ACQ_Network target_network = expe.createTargetNetwork();
		int convergenceRate = 0;
		double RelativeAcquisitionRate = 0;
		double AbsoluteAcquisitionRate = 0;

		AcquisitionRate ar = new AcquisitionRate(acquisition.getLearnedNetwork(), target_network,
				solver.getDomain().getMin(0), solver.getDomain().getMax(0));
		try {
			convergenceRate = expe.convergenceRate(solver, target_network, acquisition.getLearnedNetwork());
			RelativeAcquisitionRate = ar.relativeAcquisitionRate;
			AbsoluteAcquisitionRate = ar.absoluteAcquisitionRate;

			System.out.println("convergenceRate: " + convergenceRate);
			System.out.println("RelativeAcquisitionRate: " + RelativeAcquisitionRate);
			System.out.println("AbsoluteAcquisitionRate: " + AbsoluteAcquisitionRate);

		} catch (Exception e) {
			System.out.print("Target network not implemented");
		}
		if (expe.isPuzzlePrint())
			puzzleprint(q, expe.isQueens());
		else
			System.out.println("Query :: " + Arrays.toString(q.values));
		System.out.println("Classification :: " + oracle.ask(q));

		if (result)
			System.out.println("YES...Converged");
		else
			System.out.println("NO...Collapsed");
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss"); // 2020/04/17 16:15:43
		Date date = new Date();

		String input = dateFormat.format(date) + "\t" + 1 + "\t" + acquisition.getLearnedNetwork().size() + "\t"
				+ RelativeAcquisitionRate + "\t" + AbsoluteAcquisitionRate + "\t" + convergenceRate + "\t"
				+ +(statManager.getNbCompleteQuery() + statManager.getNbPartialQuery()) + "\t"
				+ ((statManager.getNbCompleteQuery() + statManager.getNbPartialQuery())
						/ acquisition.getLearnedNetwork().size())
				+ "\t" + statManager.getNbCompleteQuery() + "\t" + statManager.getQuerySize() + "\t"
				+ df.format(total_acq_time) + "\t" + df.format(totalTime) + "\t" + df.format(timeManager.getMax())
				+ "\t" + 0 + statManager.getNon_asked_query() + "\t" + 0 + "\t"
				+ acquisition.getBias().getInitial_size() + "\t" + acquisition.getBias().getSize() + "\t"
				+ expe.getVrs() + "\t" + expe.getHeuristic();

		// FileManager.printFile(bias,"bias");

		// System.out.println("query :: " + Arrays.toString(q.values));
		// System.out.println("Classification :: " + oracle.ask(q));

		// System.out.println("Network :: " + learned_network);
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH:mm:ss");
		LocalDateTime now = LocalDateTime.now();
		System.out.println(dtf.format(now));
		FileManager.writeResults(acquisition.getLearnedNetwork(),
				expe.getName() + "_" + dtf.format(now) + ".LearnedNetwork");

		FileManager.writeResults(input, expe.getName() + "_" + instance + "_" + dtf.format(now) + ".results");

		return stats;

	}

	public static Collective_Stats executeDistExperience(IExperience expe) {
		/*
		 * prepare bias
		 */
		int id = 0;
		ArrayList<ACQ_Bias> biases = expe.createDistBias();
		ArrayList<ACQ_Bias> biases1 = expe.createDistBias();
		for (ACQ_Bias bias : biases1)
			biases1.get(0).getNetwork().addAll(bias.getNetwork(), true);

		/*
		 * prepare oracle
		 */
		ExecutorService executor = Executors.newCachedThreadPool();

		// observe oracle for query stats
		for (ACQ_Bias bias : biases) {

			ACQ_Oracle oracle = expe.createDistOracle(id);
			System.out.print(bias.getVars());
			ACQ_Distributed_Runnable r = new ACQ_Distributed_Runnable(id, expe, bias, oracle, stats);
			executor.execute(r);
			id++;

		}

		executor.shutdown();

		while (!executor.isTerminated()) {

		}
		System.out.print(biases1.get(0).getSize());
		PrintstatsDist(id, stats, biases1.get(0), (DefaultExperience) expe);

		return stats;

	}

	public static Collective_Stats executeExperience(DefaultExperience expe, ACQ_Algorithm algo, int nbThread,
			ACQ_Partition partition) {

		if (algo.equals(ACQ_Algorithm.QUACQ))
			return executeExperience(expe);
		else // PACQ
			return LAUNCH_PORTFOLIO(nbThread, partition, expe);

	}

	public static Collective_Stats LAUNCH_CHICQnDUCQ(int nb_threads, DefaultExperience expe) {
		// of current CL
		CopyOnWriteArraySet<ACQ_QueryMessage> queries_mailbox = new CopyOnWriteArraySet<>();

		Collective_Stats stats = new Collective_Stats();

		ExecutorService executor = Executors.newCachedThreadPool();

		ACQ_Bias bias = expe.createBias();

		ACQ_Coop_Manager coop = new ACQ_Coop_Manager(queries_mailbox);
		coop.set_nb_Threads(nb_threads);

		coop.setBias(bias);
		coop.setLearnedNetwork(bias.getVars());
		coop.applyPartitioning(nb_threads);

		ACQ_Oracle oracle = expe.createOracle();
		ACQ_Coop_Runnable p;
		for (int i = 0; i < nb_threads; i++) {
			p = new ACQ_Coop_Runnable(i, expe, oracle, coop, queries_mailbox, stats);
			executor.execute(p);

		}

		executor.shutdown();
		while (!executor.isTerminated()) {

		}
		Printstats(stats, nb_threads, oracle, bias, expe, null);
		stats.ComputeGlobalStats(nb_threads, oracle, bias, expe, queries_mailbox.size());
		return stats;
	}

	public static Collective_Stats LAUNCH_PORTFOLIO(int nb_threads, ACQ_Partition partition, DefaultExperience expe) {

		System.gc();

		CopyOnWriteArraySet<ACQ_QueryMessage> queries_mailbox = new CopyOnWriteArraySet<>();

		Collective_Stats stats = new Collective_Stats();

		ExecutorService executor = Executors.newCachedThreadPool();

		ACQ_Bias bias = expe.createBias();

		ACQ_PACQ_Manager coop = new ACQ_PACQ_Manager(queries_mailbox);

		coop.setBias(bias);

		coop.setLearnedNetwork(bias.getVars());

		coop.setPartitionType(partition);

		coop.applyPartitioning(nb_threads);

		ACQ_Oracle oracle = expe.createOracle();

		for (int i = 0; i < nb_threads; i++) {
			ACQ_PACQ_Runnable p = new ACQ_PACQ_Runnable(i, expe, bias, oracle, coop, queries_mailbox, stats);

			executor.execute(p);

		}

		executor.shutdown();

		while (!executor.isTerminated()) {

		}

		System.out.println(Thread.currentThread().getName() + ":: TERMINATED!");

		Printstats(stats, nb_threads, oracle, bias, expe, coop.getPartitionType());

		return stats;
	}

	public static Collective_Stats LAUNCH_PACQDIST(int nb_threads, ACQ_Partition partition, DefaultExperience expe) {

		System.gc();

		CopyOnWriteArraySet<ACQ_QueryMessage> queries_mailbox = new CopyOnWriteArraySet<>();

		Collective_Stats stats = new Collective_Stats();

		ExecutorService executor = Executors.newCachedThreadPool();

		ArrayList<ACQ_Bias> biases = expe.createDistBias();

		for (int i = 1; i < biases.size(); i++)
			biases.get(0).getNetwork().addAll(biases.get(i).getNetwork(), true);
		ACQ_Bias bias = biases.get(0);

		ACQ_PACQ_Manager coop = new ACQ_PACQ_Manager(queries_mailbox);

		coop.setBias(bias);

		coop.setLearnedNetwork(bias.getVars());

		coop.setPartitionType(partition);

		coop.applyPartitioning(nb_threads);

		ACQ_Oracle oracle = expe.createOracle();

		for (int i = 0; i < nb_threads; i++) {
			ACQ_PACQ_Runnable p = new ACQ_PACQ_Runnable(i, expe, bias, oracle, coop, queries_mailbox, stats);

			executor.execute(p);

		}

		executor.shutdown();

		while (!executor.isTerminated()) {

		}

		System.out.println(Thread.currentThread().getName() + ":: TERMINATED!");

		Printstats(stats, nb_threads, oracle, bias, expe, coop.getPartitionType());

		return stats;
	}

	public static Collective_Stats LAUNCH_DUCQ(int nb_threads, DefaultExperience expe) {

		CopyOnWriteArraySet<ACQ_QueryMessage> queries_mailbox = new CopyOnWriteArraySet<>();

		Collective_Stats stats = new Collective_Stats();
		ExecutorService executor = Executors.newCachedThreadPool();

		ACQ_Bias bias = expe.createBias();

		ACQ_Ducq_Manager coop = new ACQ_Ducq_Manager(queries_mailbox);
		coop.setBias(bias);
		coop.setLearnedNetwork(bias.getVars());
		coop.set_sessions(nb_threads);
		coop.setNb_threads(nb_threads);
		System.out.println("Number of sessions:: " + coop.getSessionSize());
		ACQ_Oracle oracle = expe.createOracle();

		for (int i = 0; i < nb_threads; i++) {
			ACQ_Ducq_Runnable p = new ACQ_Ducq_Runnable(i, expe, bias, oracle, coop, queries_mailbox, stats);

			executor.execute(p);

		}

		executor.shutdown();
		while (!executor.isTerminated()) {

		}
		Printstats(stats, nb_threads, oracle, bias, expe, null);
		stats.ComputeGlobalStats(nb_threads, oracle, bias, expe, queries_mailbox.size());
		return stats;
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

	public static void Printstats(Collective_Stats stats, int nb_threads, ACQ_Oracle oracle, ACQ_Bias bias,
								  DefaultExperience expe, ACQ_Partition partition) {
		double wallTime = 0.0;
		double acqTime = 0.0;
		int totalqueries = 0;
		int totalvisits = 0;
		int totalmqueries = 0;
		int totalquerysize = 0;
		int total_non_asked = 0;
		double tmax = Double.MIN_VALUE;
		for (int i = 0; i < stats.getChronos().size(); i++) {
			System.out.print("\n\n");
			System.out.println("================== Thread :: " + i + "========================");
			try {
				System.out.println(stats.getStatManagers().get(i) + "\n" + stats.getTimeManagers().get(i).getResults());
				totalqueries += stats.getStatManagers().get(i).getNbCompleteQuery()
						+ stats.getStatManagers().get(i).getNbPartialQuery();
				totalmqueries += stats.getStatManagers().get(i).getNbCompleteQuery();
				totalvisits += stats.getStatManagers().get(i).getVisited_scopes();
				totalquerysize += stats.getStatManagers().get(i).getQuerySize();
				total_non_asked += stats.getStatManagers().get(i).getNon_asked_query();
				if (stats.getTimeManagers().get(i).getMax() > tmax)
					tmax = stats.getTimeManagers().get(i).getMax();
				DecimalFormat df = new DecimalFormat("0.00E0");
				double totaltime_per_thread = 0;
				double totaltime_per_thread_cpu = 0;

				double acq_time = 0;

				totaltime_per_thread = (double) stats.getChronos().get(i).getResult("total") / 1000.0;
				totaltime_per_thread_cpu = (double) stats.getChronosCPU().get(i).getResult("total") / 1000.0;

				acq_time = (double) stats.getChronos().get(i).getLast("total_acq_time") / 1000.0;

				System.out.println("------Execution times-------");

				for (String serieName : stats.getChronos().get(i).getSerieNames()) {
					if (!serieName.contains("total")) {
						double serieTime = (double) stats.getChronos().get(i).getResult(serieName) / 1000.0;
						System.out.println(serieName + " : " + df.format(serieTime));
					}

				}
				System.out.println("Convergence time Th : " + df.format(totaltime_per_thread));
				System.out.println("Acquisition time Th : " + df.format(acq_time));
				double wall_time_per_thread = (stats.getWallTime(i) / 1_000_000_000.0);
				double acq_time_per_thread = (stats.getAcqTime(i) / 1_000_000_000.0);

				if (acqTime < acq_time_per_thread)
					acqTime = acq_time_per_thread;
				if (wallTime < wall_time_per_thread)
					wallTime = wall_time_per_thread;
				FileManager.writeLog("wall time::" + df.format(wall_time_per_thread) + "wall time alain:: "
						+ df.format(totaltime_per_thread_cpu), "cpu_time_comparaison");
				System.err.println("wall time alain:: " + df.format(totaltime_per_thread_cpu));
				System.err.println("wall time::" + df.format(wall_time_per_thread));
				System.err.println("acq time::" + df.format(acq_time_per_thread));

				System.out.println("-------Learned Network & Bias Size--------");
				ACQ_Network learned_network = new ACQ_Network(bias.getNetwork().getFactory(), bias.getVars());
				for (ACQ_IConstraint cst : stats.getCL_i().get(i).getConstraints()) {
					if (cst instanceof ACQ_ConjunctionConstraint) {
						for (ACQ_IConstraint c : ((ACQ_ConjunctionConstraint) cst).constraintSet) {
							learned_network.add(c, true);
						}
					} else if (cst instanceof ACQ_IConstraint) {
						learned_network.add(cst, true);
					}

				}
				double queries = (stats.getStatManagers().get(i).getNbCompleteQuery()
						+ stats.getStatManagers().get(i).getNbPartialQuery());
				double r = queries / (double) learned_network.size();
				if (stats.getResults().get(i)) {
				}
				System.out.println("Learned Network Size : " + learned_network.size());
				if (printLearnedNetworkInLogFile)
					FileManager.writeLog(learned_network, "LearnedNetwork");
				System.out.println("R : " + Math.round(r));

				if (printBiasInLogFile)
					FileManager.writeLog(bias, "bias");
				// System.out.println("Bias Initial Size : " +
				// stats.getBiases().get(i).getInitial_size());
				// System.out.println("Bias Final Size : " +
				// stats.getBiases().get(i).getSize());
				System.out.println("==========================================================");

			} catch (Exception e) {
			}
		}

		// ConstraintFactory constraintFactory=new ConstraintFactory();
		ACQ_Network learned_network = new ACQ_Network(bias.getNetwork().getFactory(), bias.getVars());

		for (ACQ_Network n : stats.getCL().values()) {

			learned_network.addAll(n, false);
		}
		int Bias_init = 0;
		int Bias_final = 0;
		for (ACQ_Bias b : stats.getBiases().values()) {
			Bias_init += b.getInitial_size();
			Bias_final += b.getSize();
		}
		System.out.print("Complete CL :: \n\n");
		final ACQ_ConstraintSolver solver = expe.createSolver();
		DecimalFormat df = new DecimalFormat("0.00E0");
		solver.setVars(bias.getVars());
		solver.setTimeout(false);
		// ACQ_Query q = solver.solveA(learned_network);
		System.out.println("================== Total Stats========================");
		System.out.println("Learned Network Size : " + learned_network.size());
		System.out.println("Total Queries : " + totalqueries);
		System.out.println("Total MQ : " + totalmqueries);
		System.out.println("Total AVG Query size : " + totalquerysize / nb_threads);
		System.out.println("Query Load Balancing : " + stats.calculate_query_SD());
		System.out.println("min session : " + stats.get_query_min());
		System.out.println("max session : " + stats.get_query_max());
		System.out.println("-------------------------------------------------------");
		// System.out.println("Total Acquisition time : " + df.format(total_acq_time));
		// System.out.println("Total Execution time : " + df.format(totaltime));
		System.out.println("ACQ time : " + df.format(acqTime));
		System.out.println("WALL time : " + df.format(wallTime));
		System.out.println("Total T_Max : " + df.format(tmax));
		System.out.println("-------------------------------------------------------");
		System.out.println("Non Asked Queries : " + total_non_asked);
		System.out.println("Redundant Scopes Visits : " + totalvisits);
		System.out.println("-------------------------------------------------------");
		// System.out.println("AVG Execution time : " + df.format(totaltime /
		// nb_threads));
		// System.out.println("AVG Acquisition time : " + df.format(total_acq_time /
		// nb_threads));
		// System.out.println("Total T_Avg : " + df.format(total_tavg / nb_threads));
		// System.out.println("Total AVG R : " + (totalr / nb_threads));
		// System.out.println("Total Convergence: " + total_convergence);
		System.out.println("Total Biases Initial Size :" + Bias_init);
		System.out.println("Total Biases Final Size :" + Bias_final);

		int collapsed_threads = 0;

		for (Integer key : stats.results.keySet())
			if (stats.results.get(key) != null && !stats.results.get(key))
				collapsed_threads++;
		System.out.println("Threads Collapsed:: " + collapsed_threads);

		// System.out.println("query :: " + Arrays.toString(q.values));
		// System.out.println("Classification :: " + oracle.ask(q));

		// System.out.println("Network :: " + learned_network);

		// FileManager.printFile(bias,"bias");

		// System.out.println("query :: " + Arrays.toString(q.values));
		// System.out.println("Classification :: " + oracle.ask(q));

		// System.out.println("Network :: " + learned_network);
		int convergenceRate = 0;
		double RelativeAcquisitionRate = 0;
		double AbsoluteAcquisitionRate = 0;
		ACQ_Network target_network = expe.createTargetNetwork();
		AcquisitionRate ar = new AcquisitionRate(learned_network, target_network, solver.getDomain().getMin(0),
				solver.getDomain().getMax(0));
		try {
			convergenceRate = expe.convergenceRate(solver, target_network, learned_network);
			RelativeAcquisitionRate = ar.relativeAcquisitionRate;
			AbsoluteAcquisitionRate = ar.absoluteAcquisitionRate;

			System.out.println("convergenceRate: " + convergenceRate);
			System.out.println("RelativeAcquisitionRate: " + RelativeAcquisitionRate);
			System.out.println("AbsoluteAcquisitionRate: " + AbsoluteAcquisitionRate);

		} catch (Exception e) {
			System.out.print("Target network not implemented");
		}
		DateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_HH:mm:ss"); // 2020/04/17 16:15:43
		Date date = new Date();
		FileManager.writeResults(learned_network, expe.getName() + dateFormat.format(date) + ".LearnedNetwork");

		String input = (dateFormat.format(date) + "\t" + nb_threads + "\t" + learned_network.size() + "\t"
				+ RelativeAcquisitionRate + "\t" + AbsoluteAcquisitionRate + "\t" + convergenceRate
				+ +learned_network.size() + "\t" + (totalqueries / nb_threads) + "\t"
				+ (totalqueries / learned_network.size()) + "\t" + (totalmqueries / nb_threads) + "\t"
				+ (totalquerysize / nb_threads) + "\t" + df.format(acqTime) + "\t" + df.format(wallTime) + "\t"
				+ df.format(tmax) + "\t" + 0 + "\t" + (total_non_asked / nb_threads) + "\t" + (totalvisits / nb_threads)
				+ "\t" + Bias_init + "\t" + Bias_final + "\t" + expe.getVrs() + "\t" + expe.getHeuristic() + "\t"
				+ partition + "\t" + collapsed_threads);
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH:mm:ss");
		LocalDateTime now = LocalDateTime.now();
		System.out.println(dtf.format(now));
		FileManager.writeResults(input, expe.getName() + instance + "_" + dtf.format(now) + ".results");

	}

	public static void PrintstatsDist(int agents, Collective_Stats stats, ACQ_Bias bias, DefaultExperience expe) {
		double wallTime = 0.0;
		double acqTime = 0.0;
		int totalqueries = 0;
		int totalvisits = 0;
		int totalmqueries = 0;
		int totalquerysize = 0;
		int total_non_asked = 0;
		double tmax = Double.MIN_VALUE;
		for (int i = 0; i < stats.getChronos().size(); i++) {
			System.out.print("\n\n");
			System.out.println("================== Thread :: " + i + "========================");
			try {
				System.out.println(stats.getStatManagers().get(i) + "\n" + stats.getTimeManagers().get(i).getResults());
				totalqueries += stats.getStatManagers().get(i).getNbCompleteQuery()
						+ stats.getStatManagers().get(i).getNbPartialQuery();
				totalmqueries += stats.getStatManagers().get(i).getNbCompleteQuery();
				totalvisits += stats.getStatManagers().get(i).getVisited_scopes();
				totalquerysize += stats.getStatManagers().get(i).getQuerySize();
				total_non_asked += stats.getStatManagers().get(i).getNon_asked_query();
				if (stats.getTimeManagers().get(i).getMax() > tmax)
					tmax = stats.getTimeManagers().get(i).getMax();
				DecimalFormat df = new DecimalFormat("0.00E0");
				double totaltime_per_thread = 0;
				double acq_time = 0;

				totaltime_per_thread = (double) stats.getChronos().get(i).getResult("total") / 1000.0;

				acq_time = (double) stats.getChronos().get(i).getLast("total_acq_time") / 1000.0;

				System.out.println("------Execution times-------");

				for (String serieName : stats.getChronos().get(i).getSerieNames()) {
					if (!serieName.contains("total")) {
						double serieTime = (double) stats.getChronos().get(i).getResult(serieName) / 1000.0;
						System.out.println(serieName + " : " + df.format(serieTime));
					}

				}

				System.out.println("Convergence time Th : " + df.format(totaltime_per_thread));
				System.out.println("Acquisition time Th : " + df.format(acq_time));
				System.out.println("CL ::" + stats.getCL().get(i).size());

				if (acqTime < acq_time)
					acqTime = acq_time;
				if (wallTime < totaltime_per_thread)
					wallTime = totaltime_per_thread;

				System.out.println("-------Learned Network & Bias Size--------");
				ACQ_Network learned_network = new ACQ_Network();
				for (ACQ_Network network : stats.getCL().values()) {
					learned_network.addAll(network.getConstraints(), true);
				}
				double queries = (stats.getStatManagers().get(i).getNbCompleteQuery()
						+ stats.getStatManagers().get(i).getNbPartialQuery());
				double r = queries / (double) learned_network.size();
				if (stats.getResults().get(i)) {
				}
				System.out.println("Learned Network Size : " + learned_network.size());
				System.out.println("R : " + Math.round(r));

				// System.out.println("Bias Initial Size : " +
				// stats.getBiases().get(i).getInitial_size());
				// System.out.println("Bias Final Size : " +
				// stats.getBiases().get(i).getSize());
				System.out.println("==========================================================");
			} catch (Exception e) {
			}
		}

		// ConstraintFactory constraintFactory=new ConstraintFactory();
		int learned_network = 0;
		for (ACQ_Network n : stats.getCL().values()) {
			learned_network += n.size();
		}
		int Bias_init = bias.getSize();
		int Bias_final = 0;
		for (ACQ_Bias b : stats.getBiases().values()) {
			Bias_final += b.getSize();
		}
		System.out.print("Complete CL :: \n\n");
		final ACQ_ConstraintSolver solver = expe.createSolver();
		DecimalFormat df = new DecimalFormat("0.00E0");
		solver.setVars(bias.getVars());
		solver.setTimeout(false);
		// ACQ_Query q = solver.solveA(learned_network);
		System.out.println("================== Total Stats========================");
		System.out.println("Learned Network Size : " + learned_network);
		System.out.println("Agents : " + agents);
		System.out.println("Total Queries : " + (totalqueries / agents));
		System.out.println("Total MQ : " + (totalmqueries / agents));
		System.out.println("Total AVG Query size : " + (totalquerysize / agents));
		System.out.println("-------------------------------------------------------");
		// System.out.println("Total Acquisition time : " + df.format(total_acq_time));
		// System.out.println("Total Execution time : " + df.format(totaltime));
		System.out.println("ACQ time : " + df.format(acqTime));
		System.out.println("WALL time : " + df.format(wallTime));
		System.out.println("Total T_Max : " + df.format(tmax));
		System.out.println("-------------------------------------------------------");
		System.out.println("Non Asked Queries : " + total_non_asked);
		System.out.println("Redundant Scopes Visits : " + totalvisits);
		System.out.println("-------------------------------------------------------");
		// System.out.println("AVG Execution time : " + df.format(totaltime /
		// nb_threads));
		// System.out.println("AVG Acquisition time : " + df.format(total_acq_time /
		// nb_threads));
		// System.out.println("Total T_Avg : " + df.format(total_tavg / nb_threads));
		// System.out.println("Total AVG R : " + (totalr / nb_threads));
		// System.out.println("Total Convergence: " + total_convergence);
		System.out.println("Total Biases Initial Size :" + Bias_init);
		System.out.println("Total Biases Final Size :" + Bias_final);

		for (Integer key : stats.results.keySet()) {

			if (stats.results.get(key) != null && !stats.results.get(key))

				System.out.println("Thread n° : " + key + " = Collapsed");
			else
				System.out.println("Thread n° : " + key + " = Converged");

		}

		// System.out.println("query :: " + Arrays.toString(q.values));
		// System.out.println("Classification :: " + oracle.ask(q));

		// System.out.println("Network :: " + learned_network);

		String input = (stats.results.size() + "\t" + learned_network + "\t" + (totalqueries / agents) + "\t"
				+ (totalqueries / learned_network) + "\t" + (totalmqueries / agents) + "\t" + (totalquerysize / agents)
				+ "\t" + df.format(acqTime) + "\t" + df.format(wallTime) + "\t" + df.format(tmax) + "\t"
				+ (total_non_asked / agents) + "\t" + (totalvisits / agents) + "\t" + Bias_init + "\t" + Bias_final
				+ "\t" + expe.getVrs());
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH:mm:ss");
		LocalDateTime now = LocalDateTime.now();
		System.out.println(dtf.format(now));

		FileManager.writeResults(learned_network, expe.getName() + "_" + dtf.format(now) + ".LearnedNetwork");

		FileManager.writeLog(bias, "bias");
		FileManager.writeResults(input, expe.getName() + instance + "_" + dtf.format(now) + ".results");

	}

	private static final java.util.Random RANDOM = new java.util.Random(0); // fixed seed : reproducible runs

	public ACQ_IConstraint random(ACQ_Network coll) {
		int num = (int) (RANDOM.nextDouble() * coll.size());
		for (ACQ_IConstraint t : coll)
			if (--num < 0)
				return t;
		throw new AssertionError();
	}

	public static void puzzleprint(ACQ_Query query, boolean queens) {
		int n;
		if (!queens) {
			n = (int) Math.sqrt(query.getScope().size());
			StringBuilder st = new StringBuilder();
			String line = "+";
			for (int i = 0; i < n; i++) {
				line += "----+";
			}
			line += "\n";
			st.append(line);
			for (int i = 0; i < n; i++) {
				st.append("|");
				for (int j = 0; j < n; j++) {
					st.append(StringUtils.pad((query.values[i * n + j]) + "", -3, " ")).append(" |");
				}
				st.append(MessageFormat.format("\n{0}", line));
			}
			st.append("\n\n\n");
			System.out.println(st.toString());
		} else {
			n = query.getScope().size();
			StringBuilder st = new StringBuilder();
			String line = "+";
			for (int i = 0; i < n; i++) {
				line += "----+";
			}
			line += "\n";
			st.append(line);
			for (int i = 0; i < n; i++) {
				st.append("|");
				for (int j = 0; j < n; j++) {
					if (j == query.values[i])
						st.append(StringUtils.pad(("Q") + "", -3, " ")).append(" |");
					else
						st.append(StringUtils.pad(("*") + "", -3, " ")).append(" |");

				}
				st.append(MessageFormat.format("\n{0}", line));
			}
			st.append("\n\n\n");
			System.out.println(st.toString());
		}
	}

	public static Collective_Stats executeConacqV2Experience(IExperience expe) {

		/*
		 * 
		 * prepare bias
		 * 
		 */



		expe.setDisj(true);
		ACQ_Bias bias = expe.createBias();
		//System.out.println(bias.toString());

		/*
		 * 
		 * prepare oracle
		 * 
		 */

		ACQ_Oracle oracle = expe.createOracle();

		ObservedOracle observedOracle = new ObservedOracle(oracle);

		// observe oracle for query stats

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
		 * 
		 * prepare solver
		 *
		 * 
		 * 
		 */

		TimeUnit unit = TimeUnit.S;

		// ACQ_Heuristic heuristic = expe.getHeuristic();

		final ACQ_ConstraintSolver solver = expe.createSolver();

		solver.setVars(bias.getVars());

		solver.setLimit(expe.getTimeout());

		// observe solver for time measurement

		final TimeManager timeManager = new TimeManager();

		Chrono chrono = new Chrono(expe.getClass().getName());

		solver.addPropertyChangeListener(new PropertyChangeListener() {

			private boolean printTimeInLogFile = true;

			@Override

			public void propertyChange(PropertyChangeEvent evt) {

				if (evt.getPropertyName().startsWith("TIMECOUNT")) {

					float time = (Float) evt.getNewValue();

					timeManager.add((Float) time);

					if (printTimeInLogFile)

						FileManager.writeLog(time, "chrono");

				} else if (evt.getPropertyName().startsWith("BEG")) {

					chrono.start(evt.getPropertyName().substring(4));

				} else if (evt.getPropertyName().startsWith("END")) {

					chrono.stop(evt.getPropertyName().substring(4));

				}

			}

		});

		final SATSolver satSolver = expe.createSATSolver();

		satSolver.setLimit(expe.getTimeout());

		// observe sat solver time measurement

		final TimeManager satTimeManager = new TimeManager("SAT");

		satTimeManager.setUnit(unit);

		satSolver.addPropertyChangeListener(new PropertyChangeListener() {

			@Override

			public void propertyChange(PropertyChangeEvent evt) {

				if (evt.getPropertyName().startsWith("BEG")) {

					chrono.start(evt.getPropertyName().substring(4));

				} else if (evt.getPropertyName().startsWith("END")) {

					chrono.stop(evt.getPropertyName().substring(4));

					if (evt.getPropertyName().startsWith("END_TIMECOUNT")) {

						satTimeManager.add(chrono.getLast(evt.getPropertyName().substring(4), unit));

					}

				}

			}

		});

		/*
		 * 
		 * Instantiate Strategies
		 * 
		 */

		ArrayList<ACQ_Network> strat = expe.createStrategy(bias);

		/*
		 * 
		 * Instantiate Acquisition algorithm
		 * 
		 */

		ACQ_CONACQv2 acquisition = new ACQ_CONACQv2(observedOracle, bias, satSolver, solver);
		acquisition.setFirstQuery(firstNegativeQuery(oracle, bias, solver));

		if (expe.isVerbose())

			System.out.println(String.format("Bias size : %d", acquisition.getBias().getSize()));

		/*
		 * 
		 * Instantiate Background knowledge
		 * 
		 */

		ContradictionSet backknow = expe.createBackgroundKnowledge(bias, acquisition.mapping);

		if (expe.isVerbose() && backknow != null)

			System.out.println(String.format("BN size : %d", backknow.getSize()));

		acquisition.setBackgroundKnowledge(backknow);

		// Param

		acquisition.setVerbose(expe.isVerbose());

		acquisition.setLog_queries(expe.isLog_queries());

		acquisition.setStrat(strat);

		acquisition.setMaxRand(expe.getMaxRand());

		Long learningTimeout = 60 * 60 * 1000L;
		acquisition.setLearningTimeout(expe.getTimeout());

		/*
		 * 
		 * Run
		 * 
		 */

		chrono.start("total");

		boolean result;

		try {

			result = acquisition.process(chrono, expe.getMaxQueries());

		} catch (Exception e) {

			e.printStackTrace();

			return null;

		}

		chrono.stop("total");

		/*
		 * 
		 * Print results
		 * 
		 */

		if (expe.getJson()) {

			JSONObject obj = new JSONObject();

			obj.put("time_unit", unit.toString());

			obj.put("nb_queries", statManager.getNbCompleteQuery());

			obj.put("constrSolvTime", chrono.getResult("solveQ", unit) + chrono.getResult("solve_network", unit));

			obj.put("constrSolvNbCall", chrono.nbInstances("solveQ") + chrono.nbInstances("solve_network"));

			obj.put("satSolvTime", chrono.getResult("satsolve", unit));

			obj.put("satSolvNbCall", chrono.nbInstances("satsolve"));

			obj.put("convTime", chrono.getResult("total", unit));

			ACQ_Network learnedMG = acquisition.getLearnedNetwork();

			obj.put("network", learnedMG.toSmtlib());

			obj.put("unitPropTime", chrono.getResult("unit_propagate", unit));

			obj.put("unitPropNbCall", chrono.nbInstances("unit_propagate"));

			obj.put("quickxTime", chrono.getResult("quick_explain", unit));

			obj.put("quickxNbCall", chrono.nbInstances("quick_explain"));

			obj.put("buildFormulaTime", chrono.getResult("build_formula", unit));

			obj.put("buildFormulaNbCall", chrono.nbInstances("build_formula"));

			obj.put("toNetworkTime", chrono.getResult("to_network", unit));

			obj.put("toNetworkNbCall", chrono.nbInstances("to_network"));

			obj.put("rate_bias_removed", acquisition.getPreprocessDiminution());

			double[] gpqa = chrono.getAllExact("pos_queries", unit);
			obj.put("pos_query_gen_all", gpqa != null ? gpqa : JSONObject.NULL);

			double[] gnqa = chrono.getAllExact("neg_queries", unit);
			obj.put("neg_query_gen_all", gnqa != null ? gnqa : JSONObject.NULL);

			ArrayList<String> MGes = acquisition.getListMG();
			obj.put("MGes", MGes != null ? MGes : JSONObject.NULL);

			ArrayList<String> MSes = acquisition.getListMS();
			obj.put("MSes", MSes != null ? MSes : JSONObject.NULL);

			obj.put("Bias size", acquisition.getBias().getSize());
			obj.put("nbVars", acquisition.getBias().getVars().size());


			//---------------- post process stats ---------------------
			int convergenceRate = 0;

			double RelativeAcquisitionRate = 0;

			double AbsoluteAcquisitionRate = 0;

			ACQ_Network target_network = expe.createTargetNetwork();

			AcquisitionRate ar = new AcquisitionRate(acquisition.getLearnedNetwork(), target_network,

					solver.getDomain().getMin(0), solver.getDomain().getMax(0));

			try {

				convergenceRate = expe.convergenceRate(solver, target_network, acquisition.getLearnedNetwork());

				RelativeAcquisitionRate = ar.relativeAcquisitionRate;

				AbsoluteAcquisitionRate = ar.absoluteAcquisitionRate;

				obj.put("convergenceRate: ",convergenceRate);

				obj.put("RelativeAcquisitionRate: ", RelativeAcquisitionRate);

				obj.put("AbsoluteAcquisitionRate: ", AbsoluteAcquisitionRate);

			} catch (Exception e) {

				System.out.print("Target network not implemented");

			}

			ACQ_Query counterExample = solver.solve_AnotB(
					target_network,
					learnedMG,
					false,
					ACQ_Heuristic.SOL
			);
			boolean targetImpliesNetwork = counterExample.isEmpty();
			//System.out.println("counter example: " + Arrays.toString(counterExample.values));
			obj.put("target_implies_network", targetImpliesNetwork);

			// reverse implication, as for CAnDy / DCA : both together mean the learned concept is equivalent
			ACQ_Query counterExample2 = solver.solve_AnotB(
					learnedMG,
					target_network,
					false,
					ACQ_Heuristic.SOL
			);
			obj.put("Network_Implies_Target", counterExample2.isEmpty());
			// same field as CAnDy / DCA : the run stopped on its budget, either in the learner or in a SAT call
			obj.put("timeouted", acquisition.timeouted || satSolver.isTimeoutReached());

			System.out.println(obj.toString());

			try (FileWriter fw = new FileWriter(jsonOutputName(expe, "_Conacq2_halfCons_fQ_MG.json"))) {
				fw.write(obj.toString(2));
			} catch (IOException e) {
				e.printStackTrace();
			}

		} else {

			System.out.println((expe.isVerbose() ? "\n" : "") + statManager + "\n" + timeManager.getResults() + "\n"

					+ satTimeManager.getResults());

			DecimalFormat df = new DecimalFormat("0.00E0");

			double totalTime = (double) chrono.getResult("total", unit);

			double total_acq_time = (double) chrono.getLast("total_acq_time", unit);

			double mean_query_time = (double) chrono.getMean("gen_query", unit);

			System.out.println("------Execution times------");

			System.out.println("------Execution times------");
			for (String serieName : chrono.getSerieNames()) {
				// if (!serieName.contains("total") && !serieName.contains("TIMECOUNT")
				// && !serieName.contains("gen_query")) {
				System.out.println("\n------ " + serieName + " ------");

				double serieTime = (double) chrono.getResult(serieName, unit);
				System.out.println(
						"total : " + df.format(serieTime) + unit + " (#call: " + chrono.nbInstances(serieName) + ")");

				serieTime = (double) chrono.getMin(serieName, unit);
				System.out.println(
						"Min : " + df.format(serieTime) + unit + " (#call: " + chrono.nbInstances(serieName) + ")");

				serieTime = (double) chrono.getMax(serieName, unit);
				System.out.println(
						"Max : " + df.format(serieTime) + unit + " (#call: " + chrono.nbInstances(serieName) + ")");

				// }
			}

			System.out.println("Preprocess removed " + acquisition.getPreprocessDiminution() + "% of the bias");

			System.out.println("Mean query generation time: " + df.format(mean_query_time) + unit);

			System.out.println("Convergence time : " + df.format(totalTime) + unit);

			System.out.println("Acquisition time : " + df.format(total_acq_time) + unit);

			System.out.println("\n*************Learned Network CL example ******");

			System.out.println(acquisition.getLearnedNetwork().toString());

			ACQ_Query q = solver.solveA(acquisition.getLearnedNetwork());

			System.out.println("query :: " + Arrays.toString(q.values));

			System.out.println("Classification :: " + oracle.ask(q));

			DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH:mm:ss");

			LocalDateTime now = LocalDateTime.now();

			System.out.println(dtf.format(now));

			int convergenceRate = 0;

			double RelativeAcquisitionRate = 0;

			double AbsoluteAcquisitionRate = 0;

			ACQ_Network target_network = expe.createTargetNetwork();

			AcquisitionRate ar = new AcquisitionRate(acquisition.getLearnedNetwork(), target_network,

					solver.getDomain().getMin(0), solver.getDomain().getMax(0));

			try {

				convergenceRate = expe.convergenceRate(solver, target_network, acquisition.getLearnedNetwork());

				RelativeAcquisitionRate = ar.relativeAcquisitionRate;

				AbsoluteAcquisitionRate = ar.absoluteAcquisitionRate;

				System.out.println("convergenceRate: " + convergenceRate);

				System.out.println("RelativeAcquisitionRate: " + RelativeAcquisitionRate);

				System.out.println("AbsoluteAcquisitionRate: " + AbsoluteAcquisitionRate);

			} catch (Exception e) {

				System.out.print("Target network not implemented");

			}

			String input = dtf.format(now) + "\t" + 1 + "\t" + acquisition.getLearnedNetwork().size() + "\t"

					+ RelativeAcquisitionRate + "\t" + AbsoluteAcquisitionRate + "\t" + convergenceRate + "\t"

					+ +(statManager.getNbCompleteQuery() + statManager.getNbPartialQuery()) + "\t"

					+ ((statManager.getNbCompleteQuery() + statManager.getNbPartialQuery())

							/ acquisition.getLearnedNetwork().size())

					+ "\t" + statManager.getNbCompleteQuery() + "\t" + statManager.getQuerySize() + "\t"

					+ df.format(total_acq_time) + "\t" + df.format(totalTime) + "\t" + df.format(timeManager.getMax())

					+ "\t" + 0 + statManager.getNon_asked_query() + "\t" + 0 + "\t"

					+ acquisition.getBias().getInitial_size() + "\t" + acquisition.getBias().getSize() + "\t"

					+ expe.getVrs() + "\t" + expe.getHeuristic();

			// FileManager.printFile(bias,"bias");

			// System.out.println("query :: " + Arrays.toString(q.values));

			// System.out.println("Classification :: " + oracle.ask(q));

			// System.out.println("Network :: " + learned_network);

			FileManager.writeResults(acquisition.getLearnedNetwork(),

					expe.getName() + "_" + dtf.format(now) + ".LearnedNetwork");

			FileManager.writeResults(input, expe.getName() + "_" + instance + "_" + dtf.format(now) + ".results");

			if (result)

				System.out.println("YES...Converged");

			else

				System.out.println("NO...Collapsed");

		}

		return stats;

	}

	public static Collective_Stats executeCANDYExperience(IExperience expe) {

		/*
		 *
		 * prepare bias
		 *
		 */


		ACQ_Bias bias = expe.createBias();

		/*
		 *
		 * prepare oracle
		 *
		 */

		ACQ_Oracle oracle = expe.createOracle();

		ObservedOracle observedOracle = new ObservedOracle(oracle);

		// observe oracle for query stats

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
		 *
		 * prepare solver
		 *
		 *
		 *
		 */

		TimeUnit unit = TimeUnit.S;

		// ACQ_Heuristic heuristic = expe.getHeuristic();

		final ACQ_ConstraintSolver solver = expe.createSolver();

		solver.setVars(bias.getVars());

		solver.setLimit(expe.getTimeout());

		// observe solver for time measurement

		final TimeManager timeManager = new TimeManager();

		Chrono chrono = new Chrono(expe.getClass().getName());

		solver.addPropertyChangeListener(new PropertyChangeListener() {

			private boolean printTimeInLogFile = true;

			@Override

			public void propertyChange(PropertyChangeEvent evt) {

				if (evt.getPropertyName().startsWith("TIMECOUNT")) {

					float time = (Float) evt.getNewValue();

					timeManager.add((Float) time);

					if (printTimeInLogFile)

						FileManager.writeLog(time, "chrono");

				} else if (evt.getPropertyName().startsWith("BEG")) {

					chrono.start(evt.getPropertyName().substring(4));

				} else if (evt.getPropertyName().startsWith("END")) {

					chrono.stop(evt.getPropertyName().substring(4));

				}

			}

		});

		final SATSolver satSolver = expe.createSATSolver();

		satSolver.setLimit(expe.getTimeout());

		// observe sat solver time measurement

		final TimeManager satTimeManager = new TimeManager("SAT");

		satTimeManager.setUnit(unit);

		satSolver.addPropertyChangeListener(new PropertyChangeListener() {

			@Override

			public void propertyChange(PropertyChangeEvent evt) {

				if (evt.getPropertyName().startsWith("BEG")) {

					chrono.start(evt.getPropertyName().substring(4));

				} else if (evt.getPropertyName().startsWith("END")) {

					chrono.stop(evt.getPropertyName().substring(4));

					if (evt.getPropertyName().startsWith("END_TIMECOUNT")) {

						satTimeManager.add(chrono.getLast(evt.getPropertyName().substring(4), unit));

					}

				}

			}

		});

		/*
		 *
		 * Instantiate Strategies
		 *
		 */

		ArrayList<ACQ_Network> strat = expe.createStrategy(bias);

		/*
		 *
		 * Instantiate Acquisition algorithm
		 *
		 */

		ACQ_CANDY acquisition = new ACQ_CANDY(firstNegativeQuery(oracle, bias, solver),bias,observedOracle,satSolver,solver);

		if (expe.isVerbose())

			System.out.println(String.format("Bias size : %d", acquisition.getBias().getSize()));

		/*
		 *
		 * Instantiate Background knowledge
		 *
		 */

		ContradictionSet backknow = expe.createBackgroundKnowledge(bias, acquisition.mapping);

		if (expe.isVerbose() && backknow != null)

			System.out.println(String.format("BN size : %d", backknow.getSize()));

		//acquisition.setBackgroundKnowledge(backknow);

		// Param

		acquisition.setVerbose(expe.isVerbose());

		//acquisition.setLog_queries(expe.isLog_queries());

		acquisition.setStrat(strat);

		//acquisition.setMaxRand(expe.getMaxRand());

		/*
		 *
		 * Run
		 *
		 */

		chrono.start("total");

		boolean result;

		try {

			//result = acquisition.process(chrono, expe.getMaxQueries());
			acquisition.setLearningTimeout(expe.getTimeout());
			//Long learningTimeout =  60 * 1000L;
			//acquisition.setLearningTimeout(learningTimeout);
			result = acquisition.process(chrono);

		} catch (Exception e) {

			e.printStackTrace();

			return null;

		}

		chrono.stop("total");

		/*
		 *
		 * Print results
		 *
		 */

		if (expe.getJson()) {

			JSONObject obj = new JSONObject();

			obj.put("time_unit", unit.toString());

			obj.put("nb_queries", statManager.getNbCompleteQuery());

			obj.put("constrSolvTime", chrono.getResult("solveQ", unit) + chrono.getResult("solve_network", unit));

			obj.put("constrSolvNbCall", chrono.nbInstances("solveQ") + chrono.nbInstances("solve_network"));

			obj.put("satSolvTime", chrono.getResult("satsolve", unit));

			obj.put("satSolvNbCall", chrono.nbInstances("satsolve"));

			obj.put("convTime", chrono.getResult("total", unit));
			ACQ_Network MG =  acquisition.getLearnedNetworkMG();
			obj.put("network", MG != null ? MG.toSmtlib(): JSONObject.NULL);

			obj.put("unitPropTime", chrono.getResult("unit_propagate", unit));

			obj.put("unitPropNbCall", chrono.nbInstances("unit_propagate"));

			obj.put("quickxTime", chrono.getResult("quick_explain", unit));

			obj.put("quickxNbCall", chrono.nbInstances("quick_explain"));

			obj.put("buildFormulaTime", chrono.getResult("build_formula", unit));

			obj.put("buildFormulaNbCall", chrono.nbInstances("build_formula"));

			obj.put("toNetworkTime", chrono.getResult("to_network", unit));

			obj.put("toNetworkNbCall", chrono.nbInstances("to_network"));

			obj.put("rate_bias_removed", acquisition.getPreprocessDiminution());

			obj.put("Bias size", acquisition.getBias().getSize());

			obj.put("nbVars", acquisition.getBias().getVars().size());

			double[] gpqa = chrono.getAllExact("pos_queries", unit);
			obj.put("pos_query_gen_all", gpqa != null ? gpqa : JSONObject.NULL);
			obj.put("nbPos", gpqa != null ? gpqa.length : JSONObject.NULL);

			double[] gnqa = chrono.getAllExact("neg_queries", unit);
			obj.put("neg_query_gen_all", gnqa != null ? gnqa : JSONObject.NULL);
			obj.put("nbNeg", gnqa != null ? gnqa.length : JSONObject.NULL);

			ArrayList<String> NegMSSes = acquisition.getLearnedNegMSSes();
			obj.put("NegMSSes", NegMSSes != null ? NegMSSes : JSONObject.NULL);

			ArrayList<String> PosMSSes = acquisition.getLearnedPosMSSes();
			obj.put("PosMSSes", PosMSSes != null ? PosMSSes : JSONObject.NULL);

			ArrayList<ACQ_Network> muses = acquisition.getMuses();
			obj.put("nbMuses", muses != null ? muses.size() : JSONObject.NULL);

			//---------------- post process stats ---------------------
			int convergenceRate = 0;

			double RelativeAcquisitionRate = 0;

			double AbsoluteAcquisitionRate = 0;

			ACQ_Network target_network = expe.createTargetNetwork();

			obj.put("target network", target_network != null ? target_network.toSmtlib() : JSONObject.NULL);

			/*AcquisitionRate ar = new AcquisitionRate(MG, target_network,

					solver.getDomain().getMin(0), solver.getDomain().getMax(0));

			try {

				convergenceRate = expe.convergenceRate(solver, target_network, MG);

				RelativeAcquisitionRate = ar.relativeAcquisitionRate;

				AbsoluteAcquisitionRate = ar.absoluteAcquisitionRate;

				obj.put("convergenceRate: ",convergenceRate);

				obj.put("RelativeAcquisitionRate: ", RelativeAcquisitionRate);

				obj.put("AbsoluteAcquisitionRate: ", AbsoluteAcquisitionRate);

			} catch (Exception e) {

				System.out.print("Target network not implemented");

			}*/
			// soundness checks, as for DCA : target => learned MG must always hold,
			// learned MG => target too once converged (equivalence)
			ACQ_Query counterExample = solver.solve_AnotB(
					target_network,
					MG,
					false,
					ACQ_Heuristic.SOL
			);
			boolean targetImpliesNetwork = counterExample.isEmpty();
			obj.put("target_implies_network", targetImpliesNetwork);

			ACQ_Query counterExample2 = solver.solve_AnotB(
					MG,
					target_network,
					false,
					ACQ_Heuristic.SOL
			);
			boolean NetworkImpliesTarget = counterExample2.isEmpty();
			obj.put("Network_Implies_Target", NetworkImpliesTarget);
			// the SAT solver stops at the learning budget too (setLimit) : a run stopped that way is a timeout,
			// not a converged run
			obj.put("timeouted", acquisition.timeouted || satSolver.isTimeoutReached());

			System.out.println(obj.toString());

			try (FileWriter fw = new FileWriter(jsonOutputName(expe, "_Candy_min_halfConst_fQ_4T.json"))) {
				fw.write(obj.toString(2));
			} catch (IOException e) {
				e.printStackTrace();
			}

		} else {

			System.out.println((expe.isVerbose() ? "\n" : "") + statManager + "\n" + timeManager.getResults() + "\n"

					+ satTimeManager.getResults());

			DecimalFormat df = new DecimalFormat("0.00E0");

			double totalTime = (double) chrono.getResult("total", unit);

			double total_acq_time = (double) chrono.getLast("total_acq_time", unit);

			double mean_query_time = (double) chrono.getMean("gen_query", unit);

			System.out.println("------Execution times------");

			System.out.println("------Execution times------");
			for (String serieName : chrono.getSerieNames()) {
				// if (!serieName.contains("total") && !serieName.contains("TIMECOUNT")
				// && !serieName.contains("gen_query")) {
				System.out.println("\n------ " + serieName + " ------");

				double serieTime = (double) chrono.getResult(serieName, unit);
				System.out.println(
						"total : " + df.format(serieTime) + unit + " (#call: " + chrono.nbInstances(serieName) + ")");

				serieTime = (double) chrono.getMin(serieName, unit);
				System.out.println(
						"Min : " + df.format(serieTime) + unit + " (#call: " + chrono.nbInstances(serieName) + ")");

				serieTime = (double) chrono.getMax(serieName, unit);
				System.out.println(
						"Max : " + df.format(serieTime) + unit + " (#call: " + chrono.nbInstances(serieName) + ")");

				// }
			}

			System.out.println("Preprocess removed " + acquisition.getPreprocessDiminution() + "% of the bias");

			System.out.println("Mean query generation time: " + df.format(mean_query_time) + unit);

			System.out.println("Convergence time : " + df.format(totalTime) + unit);

			System.out.println("Acquisition time : " + df.format(total_acq_time) + unit);

			System.out.println("\n*************Learned Network CL example ******");

			ACQ_Network MG = acquisition.getLearnedNetworkMG();
			ACQ_Network MS = acquisition.getLearnedNetworkMS();

			System.out.println(MG.toString());

			ACQ_Query q1 = solver.solveA(acquisition.getLearnedNetworkMG());
			ACQ_Query q2 = solver.solveA(acquisition.getLearnedNetworkMS());
			System.out.println("query from MG :: " + Arrays.toString(q1.values));
			System.out.println("Classification :: " + oracle.ask(q1));

			System.out.println("query from MS :: " + Arrays.toString(q2.values));
			System.out.println("Classification :: " + oracle.ask(q2));

			DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH:mm:ss");

			LocalDateTime now = LocalDateTime.now();

			System.out.println(dtf.format(now));

			int convergenceRate = 0;

			double RelativeAcquisitionRate = 0;

			double AbsoluteAcquisitionRate = 0;

			ACQ_Network target_network = expe.createTargetNetwork();

			AcquisitionRate ar = new AcquisitionRate(acquisition.getLearnedNetworkMG(), target_network,

					solver.getDomain().getMin(0), solver.getDomain().getMax(0));
			//Evaluate MG
			try {

				convergenceRate = expe.convergenceRate(solver, target_network, MG);

				RelativeAcquisitionRate = ar.relativeAcquisitionRate;

				AbsoluteAcquisitionRate = ar.absoluteAcquisitionRate;

				System.out.println("convergenceRate: " + convergenceRate);

				System.out.println("RelativeAcquisitionRate: " + RelativeAcquisitionRate);

				System.out.println("AbsoluteAcquisitionRate: " + AbsoluteAcquisitionRate);

			} catch (Exception e) {

				System.out.print("Target network not implemented");

			}

			String input = dtf.format(now) + "\t" + 1 + "\t" + MG.size() + "\t"

					+ RelativeAcquisitionRate + "\t" + AbsoluteAcquisitionRate + "\t" + convergenceRate + "\t"

					+ +(statManager.getNbCompleteQuery() + statManager.getNbPartialQuery()) + "\t"

					+ ((statManager.getNbCompleteQuery() + statManager.getNbPartialQuery())

					/ MG.size())

					+ "\t" + statManager.getNbCompleteQuery() + "\t" + statManager.getQuerySize() + "\t"

					+ df.format(total_acq_time) + "\t" + df.format(totalTime) + "\t" + df.format(timeManager.getMax())

					+ "\t" + 0 + statManager.getNon_asked_query() + "\t" + 0 + "\t"

					+ acquisition.getBias().getInitial_size() + "\t" + acquisition.getBias().getSize() + "\t"

					+ expe.getVrs() + "\t" + expe.getHeuristic();

			// FileManager.printFile(bias,"bias");

			// System.out.println("query :: " + Arrays.toString(q.values));

			// System.out.println("Classification :: " + oracle.ask(q));

			// System.out.println("Network :: " + learned_network);

			FileManager.writeResults(MG,

					expe.getName() + "_" + dtf.format(now) + ".LearnedNetwork");

			FileManager.writeResults(input, expe.getName() + "_" + instance + "_" + dtf.format(now) + ".results");

			if (result)

				System.out.println("YES...Converged");

			else

				System.out.println("NO...Collapsed");

		}

		return stats;

	}

	public static Collective_Stats executeCANDY_Alter_DCAExperience(IExperience expe) {

		/*
		 *
		 * prepare bias
		 *
		 */


		ACQ_Bias bias = expe.createBias();

		/*
		 *
		 * prepare oracle
		 *
		 */

		ACQ_Oracle oracle = expe.createOracle();

		ObservedOracle observedOracle = new ObservedOracle(oracle);

		// observe oracle for query stats

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
		 *
		 * prepare solver
		 *
		 *
		 *
		 */

		TimeUnit unit = TimeUnit.S;

		// ACQ_Heuristic heuristic = expe.getHeuristic();

		final ACQ_ConstraintSolver solver = expe.createSolver();

		solver.setVars(bias.getVars());

		solver.setLimit(expe.getTimeout());

		// observe solver for time measurement

		final TimeManager timeManager = new TimeManager();

		Chrono chrono = new Chrono(expe.getClass().getName());

		solver.addPropertyChangeListener(new PropertyChangeListener() {

			private boolean printTimeInLogFile = true;

			@Override

			public void propertyChange(PropertyChangeEvent evt) {

				if (evt.getPropertyName().startsWith("TIMECOUNT")) {

					float time = (Float) evt.getNewValue();

					timeManager.add((Float) time);

					if (printTimeInLogFile)

						FileManager.writeLog(time, "chrono");

				} else if (evt.getPropertyName().startsWith("BEG")) {

					chrono.start(evt.getPropertyName().substring(4));

				} else if (evt.getPropertyName().startsWith("END")) {

					chrono.stop(evt.getPropertyName().substring(4));

				}

			}

		});

		final SATSolver satSolver = expe.createSATSolver();

		satSolver.setLimit(expe.getTimeout());

		// observe sat solver time measurement

		final TimeManager satTimeManager = new TimeManager("SAT");

		satTimeManager.setUnit(unit);

		satSolver.addPropertyChangeListener(new PropertyChangeListener() {

			@Override

			public void propertyChange(PropertyChangeEvent evt) {

				if (evt.getPropertyName().startsWith("BEG")) {

					chrono.start(evt.getPropertyName().substring(4));

				} else if (evt.getPropertyName().startsWith("END")) {

					chrono.stop(evt.getPropertyName().substring(4));

					if (evt.getPropertyName().startsWith("END_TIMECOUNT")) {

						satTimeManager.add(chrono.getLast(evt.getPropertyName().substring(4), unit));

					}

				}

			}

		});

		/*
		 *
		 * Instantiate Strategies
		 *
		 */

		ArrayList<ACQ_Network> strat = expe.createStrategy(bias);

		/*
		 *
		 * Instantiate Acquisition algorithm
		 *
		 */

		ACQ_CANDY_AlterDCA acquisition = new ACQ_CANDY_AlterDCA(null,bias,observedOracle,satSolver,solver);

		if (expe.isVerbose())

			System.out.println(String.format("Bias size : %d", acquisition.getBias().getSize()));

		/*
		 *
		 * Instantiate Background knowledge
		 *
		 */

		ContradictionSet backknow = expe.createBackgroundKnowledge(bias, acquisition.mapping);

		if (expe.isVerbose() && backknow != null)

			System.out.println(String.format("BN size : %d", backknow.getSize()));

		//acquisition.setBackgroundKnowledge(backknow);

		// Param

		acquisition.setVerbose(expe.isVerbose());

		//acquisition.setLog_queries(expe.isLog_queries());

		acquisition.setStrat(strat);

		//acquisition.setMaxRand(expe.getMaxRand());

		/*
		 *
		 * Run
		 *
		 */

		chrono.start("total");

		boolean result;

		try {

			//result = acquisition.process(chrono, expe.getMaxQueries());
			acquisition.setLearningTimeout(expe.getTimeout());
			//Long learningTimeout =  60 * 1000L;
			//acquisition.setLearningTimeout(learningTimeout);
			result = acquisition.process(chrono);

		} catch (Exception e) {

			e.printStackTrace();

			return null;

		}

		chrono.stop("total");

		/*
		 *
		 * Print results
		 *
		 */

		if (expe.getJson()) {

			JSONObject obj = new JSONObject();

			obj.put("time_unit", unit.toString());

			obj.put("nb_queries", statManager.getNbCompleteQuery());

			obj.put("constrSolvTime", chrono.getResult("solveQ", unit) + chrono.getResult("solve_network", unit));

			obj.put("constrSolvNbCall", chrono.nbInstances("solveQ") + chrono.nbInstances("solve_network"));

			obj.put("satSolvTime", chrono.getResult("satsolve", unit));

			obj.put("satSolvNbCall", chrono.nbInstances("satsolve"));

			obj.put("convTime", chrono.getResult("total", unit));
			ACQ_Network MG =  acquisition.getLearnedNetworkMG();
			obj.put("network", MG != null ? MG.toSmtlib(): JSONObject.NULL);

			obj.put("unitPropTime", chrono.getResult("unit_propagate", unit));

			obj.put("unitPropNbCall", chrono.nbInstances("unit_propagate"));

			obj.put("quickxTime", chrono.getResult("quick_explain", unit));

			obj.put("quickxNbCall", chrono.nbInstances("quick_explain"));

			obj.put("buildFormulaTime", chrono.getResult("build_formula", unit));

			obj.put("buildFormulaNbCall", chrono.nbInstances("build_formula"));

			obj.put("toNetworkTime", chrono.getResult("to_network", unit));

			obj.put("toNetworkNbCall", chrono.nbInstances("to_network"));

			obj.put("rate_bias_removed", acquisition.getPreprocessDiminution());

			obj.put("Bias size", acquisition.getBias().getSize());

			obj.put("nbVars", acquisition.getBias().getVars().size());

			double[] gpqa = chrono.getAllExact("pos_queries", unit);
			obj.put("pos_query_gen_all", gpqa != null ? gpqa : JSONObject.NULL);
			obj.put("nbPos", gpqa != null ? gpqa.length : JSONObject.NULL);

			double[] gnqa = chrono.getAllExact("neg_queries", unit);
			obj.put("neg_query_gen_all", gnqa != null ? gnqa : JSONObject.NULL);
			obj.put("nbNeg", gnqa != null ? gnqa.length : JSONObject.NULL);

			ArrayList<String> NegMSSes = acquisition.getLearnedNegMSSes();
			obj.put("NegMSSes", NegMSSes != null ? NegMSSes : JSONObject.NULL);

			ArrayList<ACQ_Network> muses = acquisition.getMuses();
			obj.put("nbMuses", muses != null ? muses.size() : JSONObject.NULL);

			//---------------- post process stats ---------------------
			int convergenceRate = 0;

			double RelativeAcquisitionRate = 0;

			double AbsoluteAcquisitionRate = 0;

			ACQ_Network target_network = expe.createTargetNetwork();

			obj.put("target network", target_network != null ? target_network.toSmtlib() : JSONObject.NULL);

			AcquisitionRate ar = new AcquisitionRate(MG, target_network,

					solver.getDomain().getMin(0), solver.getDomain().getMax(0));

			try {

				convergenceRate = expe.convergenceRate(solver, target_network, MG);

				RelativeAcquisitionRate = ar.relativeAcquisitionRate;

				AbsoluteAcquisitionRate = ar.absoluteAcquisitionRate;

				obj.put("convergenceRate: ",convergenceRate);

				obj.put("RelativeAcquisitionRate: ", RelativeAcquisitionRate);

				obj.put("AbsoluteAcquisitionRate: ", AbsoluteAcquisitionRate);

			} catch (Exception e) {

				System.out.print("Target network not implemented");

			}
			ACQ_Query counterExample = solver.solve_AnotB(
					target_network,
					MG,
					false,
					ACQ_Heuristic.SOL
			);
			boolean targetImpliesNetwork = counterExample.isEmpty();
			//System.out.println("counter example: " + Arrays.toString(counterExample.values));
			obj.put("target_implies_network", targetImpliesNetwork);

			System.out.println(obj.toString());

			try (FileWriter fw = new FileWriter(jsonOutputName(expe, "_Candy_min_Alter_DCA_halfConst_MG_1000_5_50.json"))) {
				fw.write(obj.toString(2));
			} catch (IOException e) {
				e.printStackTrace();
			}

		} else {

			System.out.println((expe.isVerbose() ? "\n" : "") + statManager + "\n" + timeManager.getResults() + "\n"

					+ satTimeManager.getResults());

			DecimalFormat df = new DecimalFormat("0.00E0");

			double totalTime = (double) chrono.getResult("total", unit);

			double total_acq_time = (double) chrono.getLast("total_acq_time", unit);

			double mean_query_time = (double) chrono.getMean("gen_query", unit);

			System.out.println("------Execution times------");

			System.out.println("------Execution times------");
			for (String serieName : chrono.getSerieNames()) {
				// if (!serieName.contains("total") && !serieName.contains("TIMECOUNT")
				// && !serieName.contains("gen_query")) {
				System.out.println("\n------ " + serieName + " ------");

				double serieTime = (double) chrono.getResult(serieName, unit);
				System.out.println(
						"total : " + df.format(serieTime) + unit + " (#call: " + chrono.nbInstances(serieName) + ")");

				serieTime = (double) chrono.getMin(serieName, unit);
				System.out.println(
						"Min : " + df.format(serieTime) + unit + " (#call: " + chrono.nbInstances(serieName) + ")");

				serieTime = (double) chrono.getMax(serieName, unit);
				System.out.println(
						"Max : " + df.format(serieTime) + unit + " (#call: " + chrono.nbInstances(serieName) + ")");

				// }
			}

			System.out.println("Preprocess removed " + acquisition.getPreprocessDiminution() + "% of the bias");

			System.out.println("Mean query generation time: " + df.format(mean_query_time) + unit);

			System.out.println("Convergence time : " + df.format(totalTime) + unit);

			System.out.println("Acquisition time : " + df.format(total_acq_time) + unit);

			System.out.println("\n*************Learned Network CL example ******");

			ACQ_Network MG = acquisition.getLearnedNetworkMG();
			ACQ_Network MS = acquisition.getLearnedNetworkMS();

			System.out.println(MG.toString());

			ACQ_Query q1 = solver.solveA(acquisition.getLearnedNetworkMG());
			ACQ_Query q2 = solver.solveA(acquisition.getLearnedNetworkMS());
			System.out.println("query from MG :: " + Arrays.toString(q1.values));
			System.out.println("Classification :: " + oracle.ask(q1));

			System.out.println("query from MS :: " + Arrays.toString(q2.values));
			System.out.println("Classification :: " + oracle.ask(q2));

			DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH:mm:ss");

			LocalDateTime now = LocalDateTime.now();

			System.out.println(dtf.format(now));

			int convergenceRate = 0;

			double RelativeAcquisitionRate = 0;

			double AbsoluteAcquisitionRate = 0;

			ACQ_Network target_network = expe.createTargetNetwork();

			AcquisitionRate ar = new AcquisitionRate(acquisition.getLearnedNetworkMG(), target_network,

					solver.getDomain().getMin(0), solver.getDomain().getMax(0));
			//Evaluate MG
			try {

				convergenceRate = expe.convergenceRate(solver, target_network, MG);

				RelativeAcquisitionRate = ar.relativeAcquisitionRate;

				AbsoluteAcquisitionRate = ar.absoluteAcquisitionRate;

				System.out.println("convergenceRate: " + convergenceRate);

				System.out.println("RelativeAcquisitionRate: " + RelativeAcquisitionRate);

				System.out.println("AbsoluteAcquisitionRate: " + AbsoluteAcquisitionRate);

			} catch (Exception e) {

				System.out.print("Target network not implemented");

			}

			String input = dtf.format(now) + "\t" + 1 + "\t" + MG.size() + "\t"

					+ RelativeAcquisitionRate + "\t" + AbsoluteAcquisitionRate + "\t" + convergenceRate + "\t"

					+ +(statManager.getNbCompleteQuery() + statManager.getNbPartialQuery()) + "\t"

					+ ((statManager.getNbCompleteQuery() + statManager.getNbPartialQuery())

					/ MG.size())

					+ "\t" + statManager.getNbCompleteQuery() + "\t" + statManager.getQuerySize() + "\t"

					+ df.format(total_acq_time) + "\t" + df.format(totalTime) + "\t" + df.format(timeManager.getMax())

					+ "\t" + 0 + statManager.getNon_asked_query() + "\t" + 0 + "\t"

					+ acquisition.getBias().getInitial_size() + "\t" + acquisition.getBias().getSize() + "\t"

					+ expe.getVrs() + "\t" + expe.getHeuristic();

			// FileManager.printFile(bias,"bias");

			// System.out.println("query :: " + Arrays.toString(q.values));

			// System.out.println("Classification :: " + oracle.ask(q));

			// System.out.println("Network :: " + learned_network);

			FileManager.writeResults(MG,

					expe.getName() + "_" + dtf.format(now) + ".LearnedNetwork");

			FileManager.writeResults(input, expe.getName() + "_" + instance + "_" + dtf.format(now) + ".results");

			if (result)

				System.out.println("YES...Converged");

			else

				System.out.println("NO...Collapsed");

		}

		return stats;

	}

	public static Collective_Stats executeCANDY_1NExperience(IExperience expe) {

		/*
		 *
		 * prepare bias
		 *
		 */


		ACQ_Bias bias = expe.createBias();

		/*
		 *
		 * prepare oracle
		 *
		 */

		ACQ_Oracle oracle = expe.createOracle();

		ObservedOracle observedOracle = new ObservedOracle(oracle);

		// observe oracle for query stats

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
		 *
		 * prepare solver
		 *
		 *
		 *
		 */

		TimeUnit unit = TimeUnit.S;

		// ACQ_Heuristic heuristic = expe.getHeuristic();

		final ACQ_ConstraintSolver solver = expe.createSolver();

		solver.setVars(bias.getVars());

		solver.setLimit(expe.getTimeout());

		// observe solver for time measurement

		final TimeManager timeManager = new TimeManager();

		Chrono chrono = new Chrono(expe.getClass().getName());

		solver.addPropertyChangeListener(new PropertyChangeListener() {

			private boolean printTimeInLogFile = true;

			@Override

			public void propertyChange(PropertyChangeEvent evt) {

				if (evt.getPropertyName().startsWith("TIMECOUNT")) {

					float time = (Float) evt.getNewValue();

					timeManager.add((Float) time);

					if (printTimeInLogFile)

						FileManager.writeLog(time, "chrono");

				} else if (evt.getPropertyName().startsWith("BEG")) {

					chrono.start(evt.getPropertyName().substring(4));

				} else if (evt.getPropertyName().startsWith("END")) {

					chrono.stop(evt.getPropertyName().substring(4));

				}

			}

		});

		final SATSolver satSolver = expe.createSATSolver();

		satSolver.setLimit(expe.getTimeout());

		// observe sat solver time measurement

		final TimeManager satTimeManager = new TimeManager("SAT");

		satTimeManager.setUnit(unit);

		satSolver.addPropertyChangeListener(new PropertyChangeListener() {

			@Override

			public void propertyChange(PropertyChangeEvent evt) {

				if (evt.getPropertyName().startsWith("BEG")) {

					chrono.start(evt.getPropertyName().substring(4));

				} else if (evt.getPropertyName().startsWith("END")) {

					chrono.stop(evt.getPropertyName().substring(4));

					if (evt.getPropertyName().startsWith("END_TIMECOUNT")) {

						satTimeManager.add(chrono.getLast(evt.getPropertyName().substring(4), unit));

					}

				}

			}

		});

		/*
		 *
		 * Instantiate Strategies
		 *
		 */

		ArrayList<ACQ_Network> strat = expe.createStrategy(bias);

		/*
		 *
		 * Instantiate Acquisition algorithm
		 *
		 */

		ACQ_CANDY_1N acquisition = new ACQ_CANDY_1N(null,bias,observedOracle,satSolver,solver);

		if (expe.isVerbose())

			System.out.println(String.format("Bias size : %d", acquisition.getBias().getSize()));

		/*
		 *
		 * Instantiate Background knowledge
		 *
		 */

		ContradictionSet backknow = expe.createBackgroundKnowledge(bias, acquisition.mapping);

		if (expe.isVerbose() && backknow != null)

			System.out.println(String.format("BN size : %d", backknow.getSize()));

		//acquisition.setBackgroundKnowledge(backknow);

		// Param

		acquisition.setVerbose(expe.isVerbose());

		//acquisition.setLog_queries(expe.isLog_queries());

		acquisition.setStrat(strat);

		//acquisition.setMaxRand(expe.getMaxRand());

		/*
		 *
		 * Run
		 *
		 */

		chrono.start("total");

		boolean result;

		try {

			//result = acquisition.process(chrono, expe.getMaxQueries());
			acquisition.setLearningTimeout(expe.getTimeout());
			//Long learningTimeout =  60 * 1000L;
			//acquisition.setLearningTimeout(learningTimeout);
			result = acquisition.process(chrono);

		} catch (Exception e) {

			e.printStackTrace();

			return null;

		}

		chrono.stop("total");

		/*
		 *
		 * Print results
		 *
		 */

		if (expe.getJson()) {

			JSONObject obj = new JSONObject();

			obj.put("time_unit", unit.toString());

			obj.put("nb_queries", statManager.getNbCompleteQuery());

			obj.put("constrSolvTime", chrono.getResult("solveQ", unit) + chrono.getResult("solve_network", unit));

			obj.put("constrSolvNbCall", chrono.nbInstances("solveQ") + chrono.nbInstances("solve_network"));

			obj.put("satSolvTime", chrono.getResult("satsolve", unit));

			obj.put("satSolvNbCall", chrono.nbInstances("satsolve"));

			obj.put("convTime", chrono.getResult("total", unit));
			ACQ_Network MG =  acquisition.getLearnedNetworkMG();
			obj.put("network", MG != null ? MG.toSmtlib(): JSONObject.NULL);

			obj.put("unitPropTime", chrono.getResult("unit_propagate", unit));

			obj.put("unitPropNbCall", chrono.nbInstances("unit_propagate"));

			obj.put("quickxTime", chrono.getResult("quick_explain", unit));

			obj.put("quickxNbCall", chrono.nbInstances("quick_explain"));

			obj.put("buildFormulaTime", chrono.getResult("build_formula", unit));

			obj.put("buildFormulaNbCall", chrono.nbInstances("build_formula"));

			obj.put("toNetworkTime", chrono.getResult("to_network", unit));

			obj.put("toNetworkNbCall", chrono.nbInstances("to_network"));

			obj.put("rate_bias_removed", acquisition.getPreprocessDiminution());

			obj.put("Bias size", acquisition.getBias().getSize());

			obj.put("nbVars", acquisition.getBias().getVars().size());

			double[] gpqa = chrono.getAllExact("pos_queries", unit);
			obj.put("pos_query_gen_all", gpqa != null ? gpqa : JSONObject.NULL);
			obj.put("nbPos", gpqa != null ? gpqa.length : JSONObject.NULL);

			double[] gnqa = chrono.getAllExact("neg_queries", unit);
			obj.put("neg_query_gen_all", gnqa != null ? gnqa : JSONObject.NULL);
			obj.put("nbNeg", gnqa != null ? gnqa.length : JSONObject.NULL);

			ArrayList<String> NegMSSes = acquisition.getLearnedNegMSSes();
			obj.put("NegMSSes", NegMSSes != null ? NegMSSes : JSONObject.NULL);

			ArrayList<ACQ_Network> muses = acquisition.getMuses();
			obj.put("nbMuses", muses != null ? muses.size() : JSONObject.NULL);

			//---------------- post process stats ---------------------
			int convergenceRate = 0;

			double RelativeAcquisitionRate = 0;

			double AbsoluteAcquisitionRate = 0;

			ACQ_Network target_network = expe.createTargetNetwork();

			obj.put("target network", target_network != null ? target_network.toSmtlib() : JSONObject.NULL);

			AcquisitionRate ar = new AcquisitionRate(MG, target_network,

					solver.getDomain().getMin(0), solver.getDomain().getMax(0));

			try {

				convergenceRate = expe.convergenceRate(solver, target_network, MG);

				RelativeAcquisitionRate = ar.relativeAcquisitionRate;

				AbsoluteAcquisitionRate = ar.absoluteAcquisitionRate;

				obj.put("convergenceRate: ",convergenceRate);

				obj.put("RelativeAcquisitionRate: ", RelativeAcquisitionRate);

				obj.put("AbsoluteAcquisitionRate: ", AbsoluteAcquisitionRate);

			} catch (Exception e) {

				System.out.print("Target network not implemented");

			}
			ACQ_Query counterExample = solver.solve_AnotB(
					target_network,
					MG,
					false,
					ACQ_Heuristic.SOL
			);
			boolean targetImpliesNetwork = counterExample.isEmpty();
			//System.out.println("counter example: " + Arrays.toString(counterExample.values));
			obj.put("target_implies_network", targetImpliesNetwork);

			System.out.println(obj.toString());

			try (FileWriter fw = new FileWriter(jsonOutputName(expe, "_Candy_1N_max_halfConst_MG.json"))) {
				fw.write(obj.toString(2));
			} catch (IOException e) {
				e.printStackTrace();
			}

		} else {

			System.out.println((expe.isVerbose() ? "\n" : "") + statManager + "\n" + timeManager.getResults() + "\n"

					+ satTimeManager.getResults());

			DecimalFormat df = new DecimalFormat("0.00E0");

			double totalTime = (double) chrono.getResult("total", unit);

			double total_acq_time = (double) chrono.getLast("total_acq_time", unit);

			double mean_query_time = (double) chrono.getMean("gen_query", unit);

			System.out.println("------Execution times------");

			System.out.println("------Execution times------");
			for (String serieName : chrono.getSerieNames()) {
				// if (!serieName.contains("total") && !serieName.contains("TIMECOUNT")
				// && !serieName.contains("gen_query")) {
				System.out.println("\n------ " + serieName + " ------");

				double serieTime = (double) chrono.getResult(serieName, unit);
				System.out.println(
						"total : " + df.format(serieTime) + unit + " (#call: " + chrono.nbInstances(serieName) + ")");

				serieTime = (double) chrono.getMin(serieName, unit);
				System.out.println(
						"Min : " + df.format(serieTime) + unit + " (#call: " + chrono.nbInstances(serieName) + ")");

				serieTime = (double) chrono.getMax(serieName, unit);
				System.out.println(
						"Max : " + df.format(serieTime) + unit + " (#call: " + chrono.nbInstances(serieName) + ")");

				// }
			}

			System.out.println("Preprocess removed " + acquisition.getPreprocessDiminution() + "% of the bias");

			System.out.println("Mean query generation time: " + df.format(mean_query_time) + unit);

			System.out.println("Convergence time : " + df.format(totalTime) + unit);

			System.out.println("Acquisition time : " + df.format(total_acq_time) + unit);

			System.out.println("\n*************Learned Network CL example ******");

			ACQ_Network MG = acquisition.getLearnedNetworkMG();
			ACQ_Network MS = acquisition.getLearnedNetworkMS();

			System.out.println(MG.toString());

			ACQ_Query q1 = solver.solveA(acquisition.getLearnedNetworkMG());
			ACQ_Query q2 = solver.solveA(acquisition.getLearnedNetworkMS());
			System.out.println("query from MG :: " + Arrays.toString(q1.values));
			System.out.println("Classification :: " + oracle.ask(q1));

			System.out.println("query from MS :: " + Arrays.toString(q2.values));
			System.out.println("Classification :: " + oracle.ask(q2));

			DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH:mm:ss");

			LocalDateTime now = LocalDateTime.now();

			System.out.println(dtf.format(now));

			int convergenceRate = 0;

			double RelativeAcquisitionRate = 0;

			double AbsoluteAcquisitionRate = 0;

			ACQ_Network target_network = expe.createTargetNetwork();

			AcquisitionRate ar = new AcquisitionRate(acquisition.getLearnedNetworkMG(), target_network,

					solver.getDomain().getMin(0), solver.getDomain().getMax(0));
			//Evaluate MG
			try {

				convergenceRate = expe.convergenceRate(solver, target_network, MG);

				RelativeAcquisitionRate = ar.relativeAcquisitionRate;

				AbsoluteAcquisitionRate = ar.absoluteAcquisitionRate;

				System.out.println("convergenceRate: " + convergenceRate);

				System.out.println("RelativeAcquisitionRate: " + RelativeAcquisitionRate);

				System.out.println("AbsoluteAcquisitionRate: " + AbsoluteAcquisitionRate);

			} catch (Exception e) {

				System.out.print("Target network not implemented");

			}

			String input = dtf.format(now) + "\t" + 1 + "\t" + MG.size() + "\t"

					+ RelativeAcquisitionRate + "\t" + AbsoluteAcquisitionRate + "\t" + convergenceRate + "\t"

					+ +(statManager.getNbCompleteQuery() + statManager.getNbPartialQuery()) + "\t"

					+ ((statManager.getNbCompleteQuery() + statManager.getNbPartialQuery())

					/ MG.size())

					+ "\t" + statManager.getNbCompleteQuery() + "\t" + statManager.getQuerySize() + "\t"

					+ df.format(total_acq_time) + "\t" + df.format(totalTime) + "\t" + df.format(timeManager.getMax())

					+ "\t" + 0 + statManager.getNon_asked_query() + "\t" + 0 + "\t"

					+ acquisition.getBias().getInitial_size() + "\t" + acquisition.getBias().getSize() + "\t"

					+ expe.getVrs() + "\t" + expe.getHeuristic();

			// FileManager.printFile(bias,"bias");

			// System.out.println("query :: " + Arrays.toString(q.values));

			// System.out.println("Classification :: " + oracle.ask(q));

			// System.out.println("Network :: " + learned_network);

			FileManager.writeResults(MG,

					expe.getName() + "_" + dtf.format(now) + ".LearnedNetwork");

			FileManager.writeResults(input, expe.getName() + "_" + instance + "_" + dtf.format(now) + ".results");

			if (result)

				System.out.println("YES...Converged");

			else

				System.out.println("NO...Collapsed");

		}

		return stats;

	}
	public static Collective_Stats executeDCAExperience(IExperience expe) {

		/*
		 *
		 * prepare bias
		 *
		 */


		ACQ_Bias bias = expe.createBias();

		/*
		 *
		 * prepare oracle
		 *
		 */

		ACQ_Oracle oracle = expe.createOracle();

		ObservedOracle observedOracle = new ObservedOracle(oracle);

		// observe oracle for query stats

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
		 *
		 * prepare solver
		 *
		 *
		 *
		 */

		TimeUnit unit = TimeUnit.S;

		// ACQ_Heuristic heuristic = expe.getHeuristic();

		final ACQ_ConstraintSolver solver = expe.createSolver();

		solver.setVars(bias.getVars());

		solver.setLimit(expe.getTimeout());

		// observe solver for time measurement

		final TimeManager timeManager = new TimeManager();

		Chrono chrono = new Chrono(expe.getClass().getName());

		solver.addPropertyChangeListener(new PropertyChangeListener() {

			private boolean printTimeInLogFile = true;

			@Override

			public void propertyChange(PropertyChangeEvent evt) {

				if (evt.getPropertyName().startsWith("TIMECOUNT")) {

					float time = (Float) evt.getNewValue();

					timeManager.add((Float) time);

					if (printTimeInLogFile)

						FileManager.writeLog(time, "chrono");

				} else if (evt.getPropertyName().startsWith("BEG")) {

					chrono.start(evt.getPropertyName().substring(4));

				} else if (evt.getPropertyName().startsWith("END")) {

					chrono.stop(evt.getPropertyName().substring(4));

				}

			}

		});

		final SATSolver satSolver = expe.createSATSolver();

		satSolver.setLimit(expe.getTimeout());

		// observe sat solver time measurement

		final TimeManager satTimeManager = new TimeManager("SAT");

		satTimeManager.setUnit(unit);

		satSolver.addPropertyChangeListener(new PropertyChangeListener() {

			@Override

			public void propertyChange(PropertyChangeEvent evt) {

				if (evt.getPropertyName().startsWith("BEG")) {

					chrono.start(evt.getPropertyName().substring(4));

				} else if (evt.getPropertyName().startsWith("END")) {

					chrono.stop(evt.getPropertyName().substring(4));

					if (evt.getPropertyName().startsWith("END_TIMECOUNT")) {

						satTimeManager.add(chrono.getLast(evt.getPropertyName().substring(4), unit));

					}

				}

			}

		});

		/*
		 *
		 * Instantiate Strategies
		 *
		 */

		ArrayList<ACQ_Network> strat = expe.createStrategy(bias);

		/*
		 *
		 * Instantiate Acquisition algorithm
		 *
		 */

		ACQ_DCA acquisition = new ACQ_DCA(bias,observedOracle,satSolver,solver);
		acquisition.setFirstQuery(firstNegativeQuery(oracle, bias, solver));

		if (expe.isVerbose())

			System.out.println(String.format("Bias size : %d", acquisition.getBias().getSize()));

		/*
		 *
		 * Instantiate Background knowledge
		 *
		 */

		ContradictionSet backknow = expe.createBackgroundKnowledge(bias, acquisition.mapping);

		if (expe.isVerbose() && backknow != null)

			System.out.println(String.format("BN size : %d", backknow.getSize()));

		acquisition.setBackgroundKnowledge(backknow);

		// Param

		acquisition.setVerbose(expe.isVerbose());

		acquisition.setLog_queries(expe.isLog_queries());

		acquisition.setStrat(strat);

		acquisition.setMaxRand(expe.getMaxRand());

		/*
		 *
		 * Run
		 *
		 */

		chrono.start("total");

		boolean result;

		try {

			//result = acquisition.process(chrono, expe.getMaxQueries());
			acquisition.setLearningTimeout(expe.getTimeout());
			result = acquisition.process(chrono);

		} catch (Exception e) {

			e.printStackTrace();

			return null;

		}

		chrono.stop("total");

		/*
		 *
		 * Print results
		 *
		 */

		if (expe.getJson()) {

			JSONObject obj = new JSONObject();

			obj.put("time_unit", unit.toString());

			obj.put("nb_queries", statManager.getNbCompleteQuery());

			obj.put("constrSolvTime", chrono.getResult("solveQ", unit) + chrono.getResult("solve_network", unit));

			obj.put("constrSolvNbCall", chrono.nbInstances("solveQ") + chrono.nbInstances("solve_network"));

			obj.put("satSolvTime", chrono.getResult("satsolve", unit));

			obj.put("satSolvNbCall", chrono.nbInstances("satsolve"));

			obj.put("convTime", chrono.getResult("total", unit));

			ACQ_Network learnedMG = acquisition.getLearnedNetwork();

			obj.put("network", learnedMG != null ?learnedMG.toSmtlib(): JSONObject.NULL);

			obj.put("unitPropTime", chrono.getResult("unit_propagate", unit));

			obj.put("unitPropNbCall", chrono.nbInstances("unit_propagate"));

			obj.put("quickxTime", chrono.getResult("quick_explain", unit));

			obj.put("quickxNbCall", chrono.nbInstances("quick_explain"));

			obj.put("buildFormulaTime", chrono.getResult("build_formula", unit));

			obj.put("buildFormulaNbCall", chrono.nbInstances("build_formula"));

			obj.put("toNetworkTime", chrono.getResult("to_network", unit));

			obj.put("toNetworkNbCall", chrono.nbInstances("to_network"));

			obj.put("rate_bias_removed", acquisition.getPreprocessDiminution());

			obj.put("Bias size", acquisition.getBias().getSize());

			obj.put("nbVars", acquisition.getBias().getVars().size());

			double[] gpqa = chrono.getAllExact("pos_queries", unit);
			obj.put("pos_query_gen_all", gpqa != null ? gpqa : JSONObject.NULL);

			obj.put("nbPos", gpqa != null ? gpqa.length : JSONObject.NULL);

			double[] gnqa = chrono.getAllExact("neg_queries", unit);
			obj.put("neg_query_gen_all", gnqa != null ? gnqa : JSONObject.NULL);
			obj.put("nbNeg", gnqa != null ? gnqa.length : JSONObject.NULL);

			ArrayList<String> NegMSSes = acquisition.getLearnedNegMSSes();
			obj.put("NegMSSes", NegMSSes != null ? NegMSSes : JSONObject.NULL);

			ArrayList<String> PosMSSes = acquisition.getLearnedPosMSSes();
			obj.put("PosMSSes", PosMSSes != null ? PosMSSes : JSONObject.NULL);

			ArrayList<ACQ_Network> muses = acquisition.getMuses();
			obj.put("nbMuses", muses != null ? muses.size() : JSONObject.NULL);

			//---------------- post process stats ---------------------
			int convergenceRate = 0;

			double RelativeAcquisitionRate = 0;

			double AbsoluteAcquisitionRate = 0;

			ACQ_Network target_network = expe.createTargetNetwork();

			obj.put("target network", target_network != null ? target_network.toSmtlib() : JSONObject.NULL);

			AcquisitionRate ar = new AcquisitionRate(acquisition.getLearnedNetwork(), target_network,

					solver.getDomain().getMin(0), solver.getDomain().getMax(0));

			try {

				convergenceRate = expe.convergenceRate(solver, target_network, acquisition.getLearnedNetwork());

				RelativeAcquisitionRate = ar.relativeAcquisitionRate;

				AbsoluteAcquisitionRate = ar.absoluteAcquisitionRate;

				obj.put("convergenceRate: ",convergenceRate);

				obj.put("RelativeAcquisitionRate: ", RelativeAcquisitionRate);

				obj.put("AbsoluteAcquisitionRate: ", AbsoluteAcquisitionRate);

			} catch (Exception e) {

				System.out.print("Target network not implemented");

			}
			ACQ_Query counterExample = solver.solve_AnotB(
					target_network,
					learnedMG,
					false,
					ACQ_Heuristic.SOL
			);
			boolean targetImpliesNetwork = counterExample.isEmpty();
			//System.out.println("counter example: " + Arrays.toString(counterExample.values));
			obj.put("target_implies_network", targetImpliesNetwork);

			ACQ_Query counterExample2 = solver.solve_AnotB(
					learnedMG,
					target_network,
					false,
					ACQ_Heuristic.SOL
			);
			boolean NetworkImpliesTarget = counterExample2.isEmpty();
			//System.out.println("counter example: " + Arrays.toString(counterExample.values));
			obj.put("Network_Implies_Target", NetworkImpliesTarget);
			// the SAT solver stops at the learning budget too (setLimit) : a run stopped that way is a timeout,
			// not a converged run
			obj.put("timeouted", acquisition.timeouted || satSolver.isTimeoutReached());

			System.out.println(obj.toString());

			try (FileWriter fw = new FileWriter(jsonOutputName(expe, "_dca_MG_halfCons_fQ_1000_toAlter.json"))) {
				fw.write(obj.toString(2));
			} catch (IOException e) {
				e.printStackTrace();
			}

			System.out.println(obj.toString());

		} else {

			System.out.println((expe.isVerbose() ? "\n" : "") + statManager + "\n" + timeManager.getResults() + "\n"

					+ satTimeManager.getResults());

			DecimalFormat df = new DecimalFormat("0.00E0");

			double totalTime = (double) chrono.getResult("total", unit);

			double total_acq_time = (double) chrono.getLast("total_acq_time", unit);

			double mean_query_time = (double) chrono.getMean("gen_query", unit);

			System.out.println("------Execution times------");

			System.out.println("------Execution times------");
			for (String serieName : chrono.getSerieNames()) {
				// if (!serieName.contains("total") && !serieName.contains("TIMECOUNT")
				// && !serieName.contains("gen_query")) {
				System.out.println("\n------ " + serieName + " ------");

				double serieTime = (double) chrono.getResult(serieName, unit);
				System.out.println(
						"total : " + df.format(serieTime) + unit + " (#call: " + chrono.nbInstances(serieName) + ")");

				serieTime = (double) chrono.getMin(serieName, unit);
				System.out.println(
						"Min : " + df.format(serieTime) + unit + " (#call: " + chrono.nbInstances(serieName) + ")");

				serieTime = (double) chrono.getMax(serieName, unit);
				System.out.println(
						"Max : " + df.format(serieTime) + unit + " (#call: " + chrono.nbInstances(serieName) + ")");

				// }
			}

			System.out.println("Preprocess removed " + acquisition.getPreprocessDiminution() + "% of the bias");

			System.out.println("Mean query generation time: " + df.format(mean_query_time) + unit);

			System.out.println("Convergence time : " + df.format(totalTime) + unit);

			System.out.println("Acquisition time : " + df.format(total_acq_time) + unit);

			System.out.println("\n*************Learned Network CL example ******");

			System.out.println(acquisition.getLearnedNetwork().toString());

			ACQ_Query q = solver.solveA(acquisition.getLearnedNetwork());

			System.out.println("query :: " + Arrays.toString(q.values));

			System.out.println("Classification :: " + oracle.ask(q));

			DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH:mm:ss");

			LocalDateTime now = LocalDateTime.now();

			System.out.println(dtf.format(now));

			int convergenceRate = 0;

			double RelativeAcquisitionRate = 0;

			double AbsoluteAcquisitionRate = 0;

			ACQ_Network target_network = expe.createTargetNetwork();

			AcquisitionRate ar = new AcquisitionRate(acquisition.getLearnedNetwork(), target_network,

					solver.getDomain().getMin(0), solver.getDomain().getMax(0));

			try {

				convergenceRate = expe.convergenceRate(solver, target_network, acquisition.getLearnedNetwork());

				RelativeAcquisitionRate = ar.relativeAcquisitionRate;

				AbsoluteAcquisitionRate = ar.absoluteAcquisitionRate;

				System.out.println("convergenceRate: " + convergenceRate);

				System.out.println("RelativeAcquisitionRate: " + RelativeAcquisitionRate);

				System.out.println("AbsoluteAcquisitionRate: " + AbsoluteAcquisitionRate);

			} catch (Exception e) {

				System.out.print("Target network not implemented");

			}

			String input = dtf.format(now) + "\t" + 1 + "\t" + acquisition.getLearnedNetwork().size() + "\t"

					+ RelativeAcquisitionRate + "\t" + AbsoluteAcquisitionRate + "\t" + convergenceRate + "\t"

					+ +(statManager.getNbCompleteQuery() + statManager.getNbPartialQuery()) + "\t"

					+ ((statManager.getNbCompleteQuery() + statManager.getNbPartialQuery())

					/ acquisition.getLearnedNetwork().size())

					+ "\t" + statManager.getNbCompleteQuery() + "\t" + statManager.getQuerySize() + "\t"

					+ df.format(total_acq_time) + "\t" + df.format(totalTime) + "\t" + df.format(timeManager.getMax())

					+ "\t" + 0 + statManager.getNon_asked_query() + "\t" + 0 + "\t"

					+ acquisition.getBias().getInitial_size() + "\t" + acquisition.getBias().getSize() + "\t"

					+ expe.getVrs() + "\t" + expe.getHeuristic();

			// FileManager.printFile(bias,"bias");

			// System.out.println("query :: " + Arrays.toString(q.values));

			// System.out.println("Classification :: " + oracle.ask(q));

			// System.out.println("Network :: " + learned_network);

			FileManager.writeResults(acquisition.getLearnedNetwork(),

					expe.getName() + "_" + dtf.format(now) + ".LearnedNetwork");

			FileManager.writeResults(input, expe.getName() + "_" + instance + "_" + dtf.format(now) + ".results");

			if (result)

				System.out.println("YES...Converged");

			else

				System.out.println("NO...Collapsed");

		}

		return stats;

	}

	public static Collective_Stats executeConacqV1Experience(IExperience expe) {
		/*
		 * prepare bias
		 */
		ACQ_Bias bias = expe.createBias();

		/*
		 * prepare oracle
		 */
		ACQ_Oracle oracle = expe.createOracle();
		ObservedOracle observedOracle = new ObservedOracle(oracle);

		// observe oracle for query stats
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

		// ACQ_Heuristic heuristic = expe.getHeuristic();
		final ACQ_ConstraintSolver solver = expe.createSolver();
		solver.setVars(bias.getVars());
		solver.setLimit(expe.getTimeout());

		TimeUnit unit = TimeUnit.S;

		// observe solver for time measurement
		final TimeManager timeManager = new TimeManager("CP");
		timeManager.setUnit(unit);
		Chrono chrono = new Chrono(expe.getClass().getName(), true);
		solver.addPropertyChangeListener(new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				if (evt.getPropertyName().startsWith("TIMECOUNT")) {
					timeManager.add(secondToUnit((Float) evt.getNewValue(), unit));
				} else if (evt.getPropertyName().startsWith("BEG")) {
					chrono.start(evt.getPropertyName().substring(4));
				} else if (evt.getPropertyName().startsWith("END")) {
					chrono.stop(evt.getPropertyName().substring(4));
				}
			}
		});

		final SATSolver satSolver = expe.createSATSolver();
		satSolver.setLimit(expe.getTimeout());

		// observe sat solver time measurement
		final TimeManager satTimeManager = new TimeManager("SAT");
		satTimeManager.setUnit(unit);
		satSolver.addPropertyChangeListener(new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				if (evt.getPropertyName().startsWith("BEG")) {
					chrono.start(evt.getPropertyName().substring(4));
				} else if (evt.getPropertyName().startsWith("END")) {
					chrono.stop(evt.getPropertyName().substring(4));

					if (evt.getPropertyName().startsWith("END_TIMECOUNT")) {
						satTimeManager.add(chrono.getLast(evt.getPropertyName().substring(4), unit));
					}
				}
			}
		});

		/*
		 * Instantiate Strategies
		 */
		ArrayList<ACQ_Network> strat = expe.createStrategy(bias);

		/*
		 * Instantiate Acquisition algorithm
		 */
		ACQ_CONACQv1 acquisition = new ACQ_CONACQv1(observedOracle, bias, satSolver, solver, expe.getDataFile());
		if (expe.isVerbose())
			System.out.println(String.format("Bias size : %d", acquisition.getBias().getSize()));
		/*
		 * Instantiate Background knowledge
		 */
		ContradictionSet backknow = expe.createBackgroundKnowledge(bias, acquisition.mapping);
		if (expe.isVerbose() && backknow != null)
			System.out.println(String.format("BN size : %d", backknow.getSize()));
		acquisition.setBackgroundKnowledge(backknow);

		// Param
		acquisition.setVerbose(expe.isVerbose());
		acquisition.setLog_queries(expe.isLog_queries());
		acquisition.setStrat(strat);
		acquisition.setMaxRand(expe.getMaxRand());
		acquisition.setInitN(expe.createInitNetwork());

		/*
		 * Run
		 */
		chrono.start("total");
		boolean result;
		try {
			result = acquisition.process(chrono, expe.getMaxQueries());
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		chrono.stop("total");

		/*
		 * Print results
		 */

		if (expe.getJson()) {
			JSONObject obj = new JSONObject();
			obj.put("time_unit", unit.toString());
			obj.put("nb_queries", statManager.getNbCompleteQuery());
			obj.put("constrSolvTime", chrono.getResult("solveQ", unit) + chrono.getResult("solve_network", unit));
			obj.put("constrSolvNbCall", chrono.nbInstances("solveQ") + chrono.nbInstances("solve_network"));
			obj.put("satSolvTime", chrono.getResult("satsolve", unit));
			obj.put("satSolvNbCall", chrono.nbInstances("satsolve"));
			obj.put("convTime", chrono.getResult("total", unit));
			obj.put("network", acquisition.getLearnedNetwork().toString());
			obj.put("unitPropTime", chrono.getResult("unit_propagate", unit));
			obj.put("unitPropNbCall", chrono.nbInstances("unit_propagate"));
			obj.put("quickxTime", chrono.getResult("quick_explain", unit));
			obj.put("quickxNbCall", chrono.nbInstances("quick_explain"));
			obj.put("buildFormulaTime", chrono.getResult("build_formula", unit));
			obj.put("buildFormulaNbCall", chrono.nbInstances("build_formula"));
			obj.put("toNetworkTime", chrono.getResult("to_network", unit));
			obj.put("toNetworkNbCall", chrono.nbInstances("to_network"));
			obj.put("rate_bias_removed", acquisition.getPreprocessDiminution());
			System.out.println(obj.toString());
		} else {
			System.out.println((expe.isVerbose() ? "\n" : "") + statManager + "\n" + timeManager.getResults() + "\n"
					+ satTimeManager.getResults());
			DecimalFormat df = new DecimalFormat("0.00E0");
			double totalTime = (double) chrono.getResult("total", unit);
			double total_acq_time = (double) chrono.getLast("total_acq_time", unit);
			double mean_query_time = (double) chrono.getMean("gen_query", unit);

			System.out.println("------Execution times------");
			for (String serieName : chrono.getSerieNames()) {
				if (!serieName.contains("total") && !serieName.contains("TIMECOUNT")
						&& !serieName.contains("gen_query")) {
					double serieTime = (double) chrono.getResult(serieName, unit);
					System.out.println(serieName + " : " + df.format(serieTime) + unit + " (#call: "
							+ chrono.nbInstances(serieName) + ")");
				}
			}
			System.out.println("Preprocess removed " + acquisition.getPreprocessDiminution() + "% of the bias");
			System.out.println("Mean query generation time: " + df.format(mean_query_time) + unit);
			System.out.println("Convergence time : " + df.format(totalTime) + unit);
			System.out.println("Acquisition time : " + df.format(total_acq_time) + unit);
			System.out.println("\n*************Learned Network CL example ******");
			// System.out.println(acquisition.getLearnedNetwork().toString());
			ACQ_Query q = solver.solveA(acquisition.getLearnedNetwork());
			System.out.println("query :: " + Arrays.toString(q.values));
			// System.out.println("Classification :: " + oracle.ask(q));

			if (result)
				System.out.println("YES...Converged");
			else
				System.out.println("NO...Collapsed");
			DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH:mm:ss");
			LocalDateTime now = LocalDateTime.now();
			System.out.println(dtf.format(now));
			int convergenceRate = 0;
			double RelativeAcquisitionRate = 0;
			double AbsoluteAcquisitionRate = 0;
			// ACQ_Network target_network = expe.createTargetNetwork();
			// AcquisitionRate ar = new AcquisitionRate(acquisition.getLearnedNetwork(), target_network,
			// 		solver.getDomain().getMin(0), solver.getDomain().getMax(0));
			
			// try { convergenceRate = expe.convergenceRate(solver, target_network,
			// acquisition.getLearnedNetwork()); RelativeAcquisitionRate =
			// ar.relativeAcquisitionRate; AbsoluteAcquisitionRate =
			// ar.absoluteAcquisitionRate;
			
			// System.out.println("convergenceRate: " + convergenceRate);
			// System.out.println("RelativeAcquisitionRate: " + RelativeAcquisitionRate);
			// System.out.println("AbsoluteAcquisitionRate: " + AbsoluteAcquisitionRate);
			
			// } catch (Exception e) { System.out.print("Target network not implemented"); }
			
			FileManager.writeResults(acquisition.getLearnedNetwork(),
					expe.getName() + "_" + dtf.format(now) + ".LearnedNetwork");
			String input = dtf.format(now) + "\t" + 1 + "\t" + acquisition.getLearnedNetwork().size() + "\t"
					+ RelativeAcquisitionRate + "\t" + AbsoluteAcquisitionRate + "\t" + convergenceRate + "\t"
					+ +(statManager.getNbCompleteQuery() + statManager.getNbPartialQuery()) + "\t"
					+ ((statManager.getNbCompleteQuery() + statManager.getNbPartialQuery())
							/ acquisition.getLearnedNetwork().size())
					+ "\t" + statManager.getNbCompleteQuery() + "\t" + statManager.getQuerySize() + "\t"
					+ df.format(total_acq_time) + "\t" + df.format(totalTime) + "\t" + df.format(timeManager.getMax())
					+ "\t" + 0 + statManager.getNon_asked_query() + "\t" + 0 + "\t"
					+ acquisition.getBias().getInitial_size() + "\t" + acquisition.getBias().getSize() + "\t"
					+ expe.getVrs() + "\t" + expe.getHeuristic();

			// FileManager.printFile(bias,"bias");

			// System.out.println("query :: " + Arrays.toString(q.values));
			// System.out.println("Classification :: " + oracle.ask(q));

			// System.out.println("Network :: " + learned_network);

			FileManager.writeResults(acquisition.getLearnedNetwork(),
					expe.getName() + "_" + dtf.format(now) + ".LearnedNetwork");

			FileManager.writeResults(input, expe.getName() + "_" + instance + "_" + dtf.format(now) + ".results");

		}
		return stats;
	}

	public static Collective_Stats executeHybridModeExperience(IExperience expe) {
		/*
		 * prepare bias
		 */
		int id = 0;
		ACQ_Bias bias = expe.createBias();

		/*
		 * prepare oracle
		 */
		ACQ_Oracle oracle = expe.createOracle();
		ObservedOracle observedOracle = new ObservedOracle(oracle);

		// observe oracle for query stats
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

		// ACQ_Heuristic heuristic = expe.getHeuristic();
		final ACQ_ConstraintSolver solver = expe.createSolver();
		solver.setVars(bias.getVars());
		solver.setLimit(expe.getTimeout());

		TimeUnit unit = TimeUnit.S;

		// observe solver for time measurement
		final TimeManager timeManager = new TimeManager("CP");
		timeManager.setUnit(unit);
		Chrono chrono = new Chrono(expe.getClass().getName(), true);
		solver.addPropertyChangeListener(new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				if (evt.getPropertyName().startsWith("TIMECOUNT")) {
					timeManager.add(secondToUnit((Float) evt.getNewValue(), unit));
				} else if (evt.getPropertyName().startsWith("BEG")) {
					chrono.start(evt.getPropertyName().substring(4));
				} else if (evt.getPropertyName().startsWith("END")) {
					chrono.stop(evt.getPropertyName().substring(4));
				}
			}
		});

		final SATSolver satSolver = expe.createSATSolver();
		System.out.print(expe.getTimeout());
		satSolver.setLimit(expe.getTimeout());

		// observe sat solver time measurement
		final TimeManager satTimeManager = new TimeManager("SAT");
		satTimeManager.setUnit(unit);
		satSolver.addPropertyChangeListener(new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				if (evt.getPropertyName().startsWith("BEG")) {
					chrono.start(evt.getPropertyName().substring(4));
				} else if (evt.getPropertyName().startsWith("END")) {
					chrono.stop(evt.getPropertyName().substring(4));

					if (evt.getPropertyName().startsWith("END_TIMECOUNT")) {
						satTimeManager.add(chrono.getLast(evt.getPropertyName().substring(4), unit));
					}
				}
			}
		});

		/*
		 * Instantiate Strategies
		 */
		ArrayList<ACQ_Network> strat = expe.createStrategy(bias);

		/*
		 * Instantiate Acquisition algorithm
		 */
		ACQ_CONACQv1 acquisition = new ACQ_CONACQv1(observedOracle, bias, satSolver, solver, expe.getDataFile());
		if (expe.isVerbose())
			System.out.println(String.format("Bias size : %d", acquisition.getBias().getSize()));
		/*
		 * Instantiate Background knowledge
		 */
		ContradictionSet backknow = expe.createBackgroundKnowledge(bias, acquisition.mapping);
		if (expe.isVerbose() && backknow != null)
			System.out.println(String.format("BN size : %d", backknow.getSize()));
		acquisition.setBackgroundKnowledge(backknow);

		// Param
		acquisition.setVerbose(expe.isVerbose());
		acquisition.setLog_queries(expe.isLog_queries());
		acquisition.setStrat(strat);
		acquisition.setMaxRand(expe.getMaxRand());

		/*
		 * Run
		 */
		chrono.start("total");
		boolean result;
		try {
			result = acquisition.process(chrono, expe.getMaxQueries());
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		chrono.stop("total");

		/*
		 * Print results
		 */

		if (expe.getJson()) {
			JSONObject obj = new JSONObject();
			obj.put("time_unit", unit.toString());
			obj.put("nb_queries", statManager.getNbCompleteQuery());
			obj.put("constrSolvTime", chrono.getResult("solveQ", unit) + chrono.getResult("solve_network", unit));
			obj.put("constrSolvNbCall", chrono.nbInstances("solveQ") + chrono.nbInstances("solve_network"));
			obj.put("satSolvTime", chrono.getResult("satsolve", unit));
			obj.put("satSolvNbCall", chrono.nbInstances("satsolve"));
			obj.put("convTime", chrono.getResult("total", unit));
			obj.put("network", acquisition.getLearnedNetwork().toString());
			obj.put("unitPropTime", chrono.getResult("unit_propagate", unit));
			obj.put("unitPropNbCall", chrono.nbInstances("unit_propagate"));
			obj.put("quickxTime", chrono.getResult("quick_explain", unit));
			obj.put("quickxNbCall", chrono.nbInstances("quick_explain"));
			obj.put("buildFormulaTime", chrono.getResult("build_formula", unit));
			obj.put("buildFormulaNbCall", chrono.nbInstances("build_formula"));
			obj.put("toNetworkTime", chrono.getResult("to_network", unit));
			obj.put("toNetworkNbCall", chrono.nbInstances("to_network"));
			obj.put("rate_bias_removed", acquisition.getPreprocessDiminution());
			System.out.println(obj.toString());
		}

		System.out.println("Finished Passive Learning");

		System.out.println("Bias Size: " + bias.getSize());

		/*
		 * prepare solver
		 *
		 */

		ACQ_Heuristic heuristic = expe.getHeuristic();
		solver.setVars(bias.getVars());
		solver.setLimit(expe.getTimeout());
		// observe solver for time measurement
		solver.addPropertyChangeListener(new PropertyChangeListener() {
		private boolean printTimeInLogFile = true;

			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				if (evt.getPropertyName().startsWith("TIMECOUNT")) {
					float time = (Float) evt.getNewValue();
					timeManager.add((Float) time);
					if (printTimeInLogFile)
						FileManager.writeLog(time, "chrono");
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
		ACQ_QUACQ acquisition_ = new ACQ_QUACQ(solver, bias, observedOracle, heuristic);
		// Param
		acquisition_.setNormalizedCSP(expe.isNormalizedCSP());
		acquisition_.setShuffleSplit(expe.isShuffleSplit());
		acquisition_.setAllDiffDetection(expe.isAllDiffDetection());
		acquisition_.setVerbose(expe.isVerbose());
		acquisition_.setLog_queries(expe.isLog_queries());
		acquisition_.getLearnedNetwork().addAll(acquisition.getLearnedNetwork(), true);

		/*
		 * Run
		 */
		chrono.start("total");
		chrono.stop("total");
		stats.saveChronos(id, chrono);
		stats.saveTimeManager(id, timeManager);
		stats.savestatManager(id, statManager);
		stats.saveBias(id, acquisition.getBias());
		stats.saveLearnedNetwork(id, acquisition.getLearnedNetwork());
		stats.saveResults(id, result);
		stats.ComputeGlobalStats(0, observedOracle, bias, (DefaultExperienceConacq) expe, oracle.memory.size());

		/*
		 * Print results
		 */

		System.out.println("Learned Network Size: " + acquisition_.getLearnedNetwork().size());
		System.out.println("Initial Bias size: " + acquisition_.getBias().getInitial_size());
		System.out.println("Final Bias size: " + acquisition_.getBias().getSize());

		System.out.println(statManager + "\n" + timeManager.getResults());
		DecimalFormat df = new DecimalFormat("0.00E0");
		double totalTime = (double) chrono.getResult("total") / 1000.0;
		double total_acq_time = (double) chrono.getLast("total_acq_time") / 1000.0;

		System.out.println("------Execution times------");
		for (String serieName : chrono.getSerieNames()) {
			if (!serieName.contains("total")) {
				double serieTime = (double) chrono.getResult(serieName) / 1000.0;
				System.out.println(serieName + " : " + df.format(serieTime));
			}
		}
		System.out.println("Convergence time : " + df.format(totalTime));
		System.out.println("Acquisition time : " + df.format(total_acq_time));
		System.out.println("*************Learned Network CL example ******");
		ACQ_Query q = solver.solveA(acquisition.getLearnedNetwork());

		q.classify(oracle.ask(q).equals(Answer.YES)?Answer.YES:Answer.NO);
		FileManager.writeResults(acquisition_.learned_network, expe.getName() + ".LearnedNetwork");

		FileManager.writeLog(bias, "bias");

		// System.out.println("query :: " + Arrays.toString(q.values));
		// System.out.println("Classification :: " + oracle.ask(q));

		// System.out.println("Network :: " + learned_network);

		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss"); // 2020/04/17 16:15:43
		Date date = new Date();

		String input = dateFormat.format(date) + "\t" + 1 + "\t" + acquisition.getLearnedNetwork().size() + "\t"
				+ (statManager.getNbCompleteQuery() + statManager.getNbPartialQuery()) + "\t"
				+ ((statManager.getNbCompleteQuery() + statManager.getNbPartialQuery())
						/ acquisition.getLearnedNetwork().size())
				+ "\t" + statManager.getNbCompleteQuery() + "\t" + statManager.getQuerySize() + "\t"
				+ df.format(total_acq_time) + "\t" + df.format(totalTime) + "\t" + df.format(timeManager.getMax())
				+ "\t" + 0 + statManager.getNon_asked_query() + "\t" + 0 + "\t"
				+ acquisition.getBias().getInitial_size() + "\t" + acquisition.getBias().getSize() + "\t"
				+ expe.getVrs() + "\t" + expe.getHeuristic();

		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
		LocalDateTime now = LocalDateTime.now();
		System.out.println(dtf.format(now));
		FileManager.writeResults(input, expe.getName() + instance + "_" + dtf.format(now) + ".results");

		return stats;
	}

	private static Float secondToUnit(Float f, TimeUnit unit) {
		switch (unit) {
		case S:
			return f;
		case MS:
			return f * 1000;
		case NS:
			return f * 1000000000;

		default:
			assert false : "unknow time unit";
			return null;
		}
	}

}
