package fr.lisn.lahdak.acquisition.expe;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_Heuristic;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Algorithm;
import fr.lisn.lahdak.acquisition.core.parallel.ACQ_Partition;
import fr.lisn.lahdak.acquisition.core.workspace.IExperience;

@SuppressWarnings("unused")
public class ExpeBuilder {
	protected static String exp_name;
	protected static String exp_file;
	protected static String examplesfile;
	protected ACQ_Heuristic heuristic;
	private boolean normalizedCSP;
	private boolean shuffle;
	private boolean allDiff_detection;
	private boolean parallel;
	private static String instance;
	private static String vls;
	private static String vrs;
	private static boolean verbose;
	private static boolean log_queries;
	private static boolean gui;
	protected static int nb_threads;
	protected static ACQ_Partition partition;
	protected static long timeout=5000;
	private static ACQ_Algorithm Algo;
	private static String name;
	private static int maxqueries;
	private static int size = -1;
	private File directory;

	public ExpeBuilder() {

	}

	public ExpeBuilder setHeuristic(ACQ_Heuristic h) {
		this.heuristic = h;
		return this;
	}

	public ExpeBuilder setNormalizedCSP(boolean norm) {
		this.normalizedCSP = norm;
		return this;
	}

	public ExpeBuilder setShuffle(boolean shuffle) {
		this.shuffle = shuffle;
		return this;
	}

	public ExpeBuilder setParallel(boolean parallel) {
		this.parallel = parallel;
		return this;
	}

	public ExpeBuilder setAllDiffDetection(boolean detection) {
		this.allDiff_detection = detection;
		return this;

	}

	@SuppressWarnings("static-access")
	public ExpeBuilder setVarSelector(String vrs) {
		this.vrs = vrs;
		return this;
	}

	@SuppressWarnings("static-access")
	public ExpeBuilder setValSelector(String vls) {
		this.vls = vls;
		return this;
	}

	@SuppressWarnings("static-access")
	public ExpeBuilder setInstance(String instance) {
		this.instance = instance;
		return this;
	}

	@SuppressWarnings("static-access")
	public ExpeBuilder setNbThreads(int nb_threads) {
		this.nb_threads = nb_threads;
		return this;
	}

	@SuppressWarnings("static-access")
	public ExpeBuilder setTimeout(long timeout) {
		this.timeout = timeout;
		return this;
	}

	@SuppressWarnings("static-access")
	public ExpeBuilder setPartition(ACQ_Partition partition) {
		this.partition = partition;
		return this;
	}

	@SuppressWarnings("static-access")
	public ExpeBuilder setAlgo(ACQ_Algorithm mode) {
		this.Algo = mode;
		return this;
	}

	@SuppressWarnings("static-access")
	public ExpeBuilder setVerbose(boolean verbose) {
		this.verbose = verbose;
		return this;
	}

	@SuppressWarnings("static-access")
	public ExpeBuilder setQueries(boolean log_queries) {
		this.log_queries = log_queries;
		return this;
	}

	@SuppressWarnings("static-access")
	public ExpeBuilder setSize(int size) {
		this.size = size;
		return this;
	}

	public ExpeBuilder setDirectory(File directory) {
		this.directory = directory;
		return this;
	}

	@SuppressWarnings("static-access")
	public ExpeBuilder setExpe(String exp) {
		this.exp_name = exp;
		return this;
	}

	@SuppressWarnings("static-access")
	public ExpeBuilder setMaxqueries(int max) {
		this.maxqueries = max;
		return this;
	}

	@SuppressWarnings("static-access")
	public ExpeBuilder setFile(String file) {
		this.exp_file = file;
		return this;
	}

	@SuppressWarnings("static-access")
	public ExpeBuilder setExamplesFile(String file) {
		this.examplesfile = file;
		return this;
	}

	@SuppressWarnings("static-access")
	public ExpeBuilder setGui(boolean gui) {
		this.gui = gui;
		return this;
	}

	public String getExpe() {
		return exp_name;
	}

	public String getFile() {
		return exp_file;
	}

	public String getExamplesFile() {
		return examplesfile;
	}

	public int getMaxQueries() {
		return maxqueries;
	}

	public IExperience build() throws IOException {
		
		if(exp_file != null && !exp_file.equals("")) {
			ExpeParser e = new ExpeParser(exp_file);
			ExpeFromParser exp = new ExpeFromParser(e);
			exp.setParams(normalizedCSP, shuffle, timeout, heuristic, verbose, log_queries);
			exp.setVls(vls);
			exp.setVrs(vrs);
			exp.setInstance(instance);
			exp.setPartition(partition);
			exp.setNb_threads(nb_threads);
			exp.setAlgo(Algo);
			exp.setName(exp_file);
			if (puzzleProblem(exp_file)) {
				exp.setPuzzlePrint(true);
			}
			if(Algo.equals(ACQ_Algorithm.CONACQ1))
				exp.setExamplesfile(examplesfile);
			if(Algo.equals(ACQ_Algorithm.CONACQ2))
				exp.setMaxqueries(maxqueries);

			return exp;
		}else{
		switch (exp_name) {
		case "random":
			ExpeRandom random = new ExpeRandom();

			random.setParams(normalizedCSP, shuffle, timeout, heuristic, verbose, log_queries);
			random.setVls(vls);
			random.setVrs(vrs);
			random.setPartition(partition);
			random.setNb_threads(nb_threads);
			random.setAlgo(Algo);
			random.setInstance(instance);
			random.setName(exp_name);

			if(Algo.equals(ACQ_Algorithm.CONACQ1))
				random.setExamplesfile(examplesfile);
			if(Algo.equals(ACQ_Algorithm.CONACQ2))
				random.setMaxqueries(maxqueries);
			return random;
		case "purdey":
			ExpePurdey prudey = new ExpePurdey();

			prudey.setParams(normalizedCSP, shuffle, timeout, heuristic, verbose, log_queries);
			prudey.setParams(normalizedCSP, shuffle, timeout, heuristic, verbose, log_queries);
			prudey.setVls(vls);
			prudey.setVrs(vrs);
			prudey.setPartition(partition);
			prudey.setNb_threads(nb_threads);
			prudey.setAlgo(Algo);
			prudey.setName(exp_name);

			if(Algo.equals(ACQ_Algorithm.CONACQ1))
				prudey.setExamplesfile(examplesfile);
			if(Algo.equals(ACQ_Algorithm.CONACQ2))
				prudey.setMaxqueries(maxqueries);
			return prudey;
		case "zebra":
			ExpeZebra zebra = new ExpeZebra();

			zebra.setParams(normalizedCSP, shuffle, timeout, heuristic, verbose, log_queries);
			zebra.setVls(vls);
			zebra.setVrs(vrs);
			zebra.setPartition(partition);
			zebra.setNb_threads(nb_threads);
			zebra.setAlgo(Algo);
			zebra.setGui(gui);
			zebra.setName(exp_name);

			if(Algo.equals(ACQ_Algorithm.CONACQ1))
				zebra.setExamplesfile(examplesfile);
			if(Algo.equals(ACQ_Algorithm.CONACQ2))
				zebra.setMaxqueries(maxqueries);
			return zebra;
		case "meetings":

			ExpeMeetings meeting = new ExpeMeetings();

			meeting.setParams(normalizedCSP, shuffle, timeout, heuristic, verbose, log_queries);
			meeting.setVls(vls);
			meeting.setVrs(vrs);
			meeting.setPartition(partition);
			meeting.setNb_threads(nb_threads);
			meeting.setAlgo(Algo);
			meeting.setInstance(instance);
			meeting.setDirectory(directory);
			meeting.readDataset();
			meeting.setName(exp_name);

			if(Algo.equals(ACQ_Algorithm.CONACQ1))
				meeting.setExamplesfile(examplesfile);
			if(Algo.equals(ACQ_Algorithm.CONACQ2))
				meeting.setMaxqueries(maxqueries);
			return meeting;
		case "target":
			ExpeTarget target = new ExpeTarget();

			target.setParams(normalizedCSP, shuffle, timeout, heuristic, verbose, log_queries);
			target.setVls(vls);
			target.setVrs(vrs);
			target.setPartition(partition);
			target.setNb_threads(nb_threads);
			target.setAlgo(Algo);
			target.setInstance(instance);
			target.setDirectory(directory);
			target.readTarget();
			target.setName(exp_name);

			if(Algo.equals(ACQ_Algorithm.CONACQ1))
				target.setExamplesfile(examplesfile);
			if(Algo.equals(ACQ_Algorithm.CONACQ2))
				target.setMaxqueries(maxqueries);
			return target;

		case "sudoku4":
			ExpeSUDOKU4 sudoku4 = new ExpeSUDOKU4();

			sudoku4.setParams(normalizedCSP, shuffle, timeout, heuristic, verbose, log_queries);
			sudoku4.setVls(vls);
			sudoku4.setVrs(vrs);
			sudoku4.setPartition(partition);
			sudoku4.setNb_threads(nb_threads);
			sudoku4.setAlgo(Algo);
			sudoku4.setGui(gui);
			sudoku4.setName(exp_name);
			sudoku4.setPuzzlePrint(true);
			sudoku4.setName(exp_name);

			if(Algo.equals(ACQ_Algorithm.CONACQ1))
				sudoku4.setExamplesfile(examplesfile);
			if(Algo.equals(ACQ_Algorithm.CONACQ2))
				sudoku4.setMaxqueries(maxqueries);
			return sudoku4;
		case "sudoku":
			ExpeSUDOKU sudoku = new ExpeSUDOKU();

			sudoku.setParams(normalizedCSP, shuffle, timeout, heuristic, verbose, log_queries);
			sudoku.setVls(vls);
			sudoku.setVrs(vrs);
			sudoku.setPartition(partition);
			sudoku.setNb_threads(nb_threads);
			sudoku.setAlgo(Algo);
			sudoku.setGui(gui);
			sudoku.setName(exp_name);
			sudoku.setPuzzlePrint(true);
			sudoku.setName(exp_name);

			if(Algo.equals(ACQ_Algorithm.CONACQ1))
				sudoku.setExamplesfile(examplesfile);
			if(Algo.equals(ACQ_Algorithm.CONACQ2))
				sudoku.setMaxqueries(maxqueries);
			return sudoku;
		case "jsudoku":
			ExpeJigSawSUDOKU jgsudoku;
			try {
				jgsudoku = new ExpeJigSawSUDOKU();

				jgsudoku.setParams(normalizedCSP, shuffle, timeout, heuristic, verbose, log_queries);
				jgsudoku.setVls(vls);
				jgsudoku.setVrs(vrs);
				jgsudoku.setPartition(partition);
				jgsudoku.setNb_threads(nb_threads);
				jgsudoku.setAlgo(Algo);
				jgsudoku.setPuzzlePrint(true);
				jgsudoku.setName(exp_name);

				if(Algo.equals(ACQ_Algorithm.CONACQ1))
					jgsudoku.setExamplesfile(examplesfile);
				if(Algo.equals(ACQ_Algorithm.CONACQ2))
					jgsudoku.setMaxqueries(maxqueries);
				return jgsudoku;
			} catch (IOException e) {
				
				e.printStackTrace();
			}

		case "latin":
			ExpeLatinSquare latin = new ExpeLatinSquare();

			latin.setParams(normalizedCSP, shuffle, timeout, heuristic, verbose, log_queries);

			latin.setVls(vls);
			latin.setVrs(vrs);
			latin.setPartition(partition);
			latin.setNb_threads(nb_threads);
			latin.setAlgo(Algo);
			latin.setPuzzlePrint(true);
			latin.setName(exp_name);
			if(Algo.equals(ACQ_Algorithm.CONACQ1))
				latin.setExamplesfile(examplesfile);
			if(Algo.equals(ACQ_Algorithm.CONACQ2))
				latin.setMaxqueries(maxqueries);
			return latin;
		case "rlfap":
			ExpeRLFAP rlfap = new ExpeRLFAP();

			rlfap.setParams(normalizedCSP, shuffle, timeout, heuristic, verbose, log_queries);

			rlfap.setVls(vls);
			rlfap.setVrs(vrs);
			rlfap.setPartition(partition);
			rlfap.setNb_threads(nb_threads);
			rlfap.setAlgo(Algo);
			rlfap.setName(exp_name);

			if(Algo.equals(ACQ_Algorithm.CONACQ1))
				rlfap.setExamplesfile(examplesfile);
			if(Algo.equals(ACQ_Algorithm.CONACQ2))
				rlfap.setMaxqueries(maxqueries);
			return rlfap;
		case "queens":
			ExpeQueens queens = new ExpeQueens();
			queens.setParams(normalizedCSP, shuffle, timeout, heuristic, verbose, log_queries);
			queens.setVls(vls);
			queens.setVrs(vrs);
			queens.setInstance(instance);
			queens.setPartition(partition);
			queens.setNb_threads(nb_threads);
			queens.setGui(gui);
			queens.setAlgo(Algo);
			queens.setInstance(instance);
			queens.setPuzzlePrint(true);
			queens.setQueens(true);
			queens.setName(exp_name);
			if(Algo.equals(ACQ_Algorithm.CONACQ1))
				queens.setExamplesfile(examplesfile);
			if(Algo.equals(ACQ_Algorithm.CONACQ2))
				queens.setMaxqueries(maxqueries);
			return queens;
		case "golomb":
			ExpeGOLOMB golomb = new ExpeGOLOMB();
			golomb.setParams(normalizedCSP, shuffle, timeout, heuristic, verbose, log_queries);
			golomb.setVls(vls);
			golomb.setVrs(vrs);
			golomb.setInstance(instance);
			golomb.setPartition(partition);
			golomb.setNb_threads(nb_threads);
			golomb.setAlgo(Algo);
			golomb.setInstance(instance);
			golomb.setName(exp_name);

			if(Algo.equals(ACQ_Algorithm.CONACQ1))
				golomb.setExamplesfile(examplesfile);
			if(Algo.equals(ACQ_Algorithm.CONACQ2))
				golomb.setMaxqueries(maxqueries);
			return golomb;
		case "toy":
			ExpeTOY toy = new ExpeTOY(true);
			toy.setParams(normalizedCSP, shuffle, timeout, heuristic, verbose, log_queries);
			toy.setVls(vls);
			toy.setVrs(vrs);
			toy.setPartition(partition);
			toy.setNb_threads(nb_threads);
			toy.setAlgo(Algo);
			toy.setName(exp_name);
			if(Algo.equals(ACQ_Algorithm.CONACQ2))
				toy.setMaxqueries(maxqueries);
			if(Algo.equals(ACQ_Algorithm.CANDY))
				toy.setMaxqueries(maxqueries);
			return toy;

			case "toy_AllDisj":
				ExpeTOY_AllDisj toyAllDisj = new ExpeTOY_AllDisj(true);
				toyAllDisj.setParams(normalizedCSP, shuffle, timeout, heuristic, verbose, log_queries);
				toyAllDisj.setVls(vls);
				toyAllDisj.setVrs(vrs);
				toyAllDisj.setPartition(partition);
				toyAllDisj.setNb_threads(nb_threads);
				toyAllDisj.setAlgo(Algo);
				toyAllDisj.setName(exp_name);
				if (size > 0)
					toyAllDisj.setNbTasks(size);
				if(Algo.equals(ACQ_Algorithm.CONACQ2))
					toyAllDisj.setMaxqueries(maxqueries);
				if(Algo.equals(ACQ_Algorithm.CANDY))
					toyAllDisj.setMaxqueries(maxqueries);
				return toyAllDisj;

			case "motiv":
				ExpeMotiv motiv = new ExpeMotiv(true);
				motiv.setParams(normalizedCSP, shuffle, timeout, heuristic, verbose, log_queries);
				motiv.setVls(vls);
				motiv.setVrs(vrs);
				motiv.setPartition(partition);
				motiv.setNb_threads(nb_threads);
				motiv.setAlgo(Algo);
				motiv.setName(exp_name);
				if(Algo.equals(ACQ_Algorithm.CONACQ2))
					motiv.setMaxqueries(maxqueries);
				if(Algo.equals(ACQ_Algorithm.CANDY))
					motiv.setMaxqueries(maxqueries);
				return motiv;

			case "toy_BetweenMinMax":
			case "toy_Minimum":
			case "toy_Maximum":
			case "toy_LexLessEq":
			case "toy_Domain":
			case "toy_Ultrametric":
				ExpeTOY_Global toyGlobal = ExpeTOY_Global.create(exp_name);
				toyGlobal.setParams(normalizedCSP, shuffle, timeout, heuristic, verbose, log_queries);
				toyGlobal.setVls(vls);
				toyGlobal.setVrs(vrs);
				toyGlobal.setPartition(partition);
				toyGlobal.setNb_threads(nb_threads);
				toyGlobal.setAlgo(Algo);
				toyGlobal.setName(exp_name);
				if (size > 0)
					toyGlobal.setSize(size);
				if(Algo.equals(ACQ_Algorithm.CONACQ2))
					toyGlobal.setMaxqueries(maxqueries);
				if(Algo.equals(ACQ_Algorithm.CANDY))
					toyGlobal.setMaxqueries(maxqueries);
				return toyGlobal;
		default:{
			System.err.println("Bad partition parameter: " + name);
			System.exit(2);
		}


		}
		return null;

	}}

	private boolean puzzleProblem(String exp_name) {

		String[] problems = new String[] { "sudoku", "jsudoku", "queens" };
		Pattern name = Pattern.compile(exp_name + "*");

		for (String pb : problems) {
			Matcher matcher1 = name.matcher(pb.toLowerCase());
			Pattern name2 = Pattern.compile(pb + "*");
			Matcher matcher2 = name2.matcher(name.pattern().toLowerCase());

			if (matcher1.find() || matcher2.find()) {
				return true;
			}

		}

		return false;
	}

	

}
