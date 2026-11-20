package fr.lisn.lahdak.acquisition.expe;

import fr.lisn.lahdak.acquisition.core.acqconstraint.*;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintFactory.ConstraintSet;
import fr.lisn.lahdak.acquisition.core.acqsolver.*;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Bias;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Scope;
import fr.lisn.lahdak.acquisition.core.oracle.ACQ_Oracle;
import fr.lisn.lahdak.acquisition.core.oracle.Answer;
import fr.lisn.lahdak.acquisition.core.workspace.ACQ_WS;
import fr.lisn.lahdak.acquisition.core.workspace.DefaultExperience;

import java.util.*;

public abstract class ExpeTOY_Global extends DefaultExperience {

	boolean auto_learn;
	/** number of variables, set with AcqApp -n/--size (-1 = the toy's default) */
	protected int size = -1;
	boolean disj = false;

	public ExpeTOY_Global(boolean auto_learn) {
		this.auto_learn = auto_learn;
	}

	protected abstract int defaultNbVars();

	protected int nbVars() {
		return size > 0 ? size : defaultNbVars();
	}

	public void setSize(int size) {
		this.size = size;
	}

	/** upper bound of every domain, the lower bound is 0 */
	protected int domainMax() {
		return nbVars() - 1;
	}

	protected int varMin(int v) {
		return 0;
	}

	protected int varMax(int v) {
		return domainMax();
	}

	protected abstract GlobalConstraint targetConstraint();

	protected abstract Operator[] biasOperators();

	private final boolean reducedBasis = Boolean.getBoolean("bias.reduced");

	protected boolean targetPair(int i, int j) {
		return true;
	}

	public static ExpeTOY_Global create(String exp_name) {
		switch (exp_name) {
		case "toy_BetweenMinMax":
			return new BetweenMinMaxToy(true);
		case "toy_Minimum":
			return new MinimumToy(true);
		case "toy_Maximum":
			return new MaximumToy(true);
		case "toy_LexLessEq":
			return new LexLessEqToy(true);
		case "toy_Domain":
			return new DomainToy(true);
		case "toy_Ultrametric":
			return new UltrametricToy(true);
		default:
			return null;
		}
	}

	protected static int[] range(int from, int to) {
		int[] vars = new int[to - from];
		for (int i = from; i < to; i++)
			vars[i - from] = i;
		return vars;
	}

	public ACQ_ConstraintSolver createSolver() {
		return new ACQ_ChocoSolver(new ACQ_IDomain() {
			@Override
			public int getMin(int numvar) {
				return varMin(numvar);
			}

			@Override
			public int getMax(int numvar) {
				return varMax(numvar);
			}
		}, VarSelector.DomOverWDeg.toString(), ValSelector.IntDomainRandom.toString());
	}

	public ACQ_Oracle createOracle() {
		GlobalConstraint target = targetConstraint();
		return new ACQ_Oracle() {
			@Override
			public Answer ask(ACQ_Query e) {
				if (auto_learn) {
					// the target can only be violated if the query assigns its whole scope
					boolean valid = !e.getScope().containsAll(target.getScope()) || target.check(e);
					Answer answer = valid ? Answer.YES : Answer.NO;
					e.classify(answer);
					return answer;
				} else
					return getAnswer(e);
			}
		};
	}

	public Answer getAnswer(ACQ_Query e) {
		System.out.println("QUACQ");
		System.out.println(e.oracleAskingFormat());
		System.out.println("Is it a solution ? (y/n)");
		try (Scanner in = new Scanner(System.in)) {
			String userAnswer;
			do {
				userAnswer = in.next();
				System.out.println("You entered:" + userAnswer);
				if (!userAnswer.equals("y") && !userAnswer.equals("n")) {
					System.out.println("Incorrect answer.\n Please enter y or n.");
				}
			} while (!userAnswer.equals("y") && !userAnswer.equals("n"));

			return userAnswer.equals("y") ? Answer.YES : Answer.NO;
		}
	}

	private ACQ_Scope allVarSet() {
		BitSet bs = new BitSet();
		bs.set(0, nbVars());
		return new ACQ_Scope(bs);
	}

	public ACQ_Bias createBias() {
		ConstraintFactory constraintFactory = new ConstraintFactory();
		ConstraintSet constraints = constraintFactory.createSet();
		addAtoms(constraints);

		if (disj) {
			constraints.addAll(disjunctions(constraintFactory, constraints));
		}

		ACQ_Network network = new ACQ_Network(constraintFactory, allVarSet(), constraints);
		return new ACQ_Bias(network);
	}


	protected void addAtoms(ConstraintSet constraints) {
		for (int i = 0; i < nbVars(); i++) {
			for (int j = i + 1; j < nbVars(); j++) {
				for (Operator op : biasOperators()) {
					if (reducedBasis && !targetPair(i, j) && op != Operator.LE)
						continue;
					BinaryArithmetic atom = new BinaryArithmetic(op.name() + "_" + i + "_" + j, i, op, j,
							Operator.getOpposite(op).name() + "_" + i + "_" + j);
					constraints.add(atom);
					constraints.add(atom.getNegation());
				}
			}
		}
	}

	
	protected int disjunctionSize() {
		return 2;
	}

	
	static final long BIAS_T0 = java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();
	static final long BIAS_LIMIT_MS = Long.getLong("bias.timeout", 0L) * 60 * 1000L;

	static void checkBiasTimeout() {
		if (BIAS_LIMIT_MS > 0 && System.currentTimeMillis() - BIAS_T0 > BIAS_LIMIT_MS) {
			System.out.println("BIAS_TIMEOUT");
			System.out.flush();
			System.exit(3);
		}
	}

	
	private ConstraintSet disjunctions(ConstraintFactory constraintFactory, ConstraintSet atoms) {
		ACQ_ConstraintSolver solv = createSolver();
		solv.setVars(allVarSet());
		ConstraintSet toAdd = constraintFactory.createSet();
		List<ACQ_IConstraint> list = new ArrayList<>();
		for (ACQ_IConstraint c : atoms) list.add(c);
		int kmax = Math.min(disjunctionSize(), list.size());
		for (int k = 2; k <= kmax; k++) {
			int[] idx = new int[k];
			for (int i = 0; i < k; i++) idx[i] = i;
			while (true) {
				List<ACQ_IConstraint> combo = new ArrayList<>();
				for (int i : idx) combo.add(list.get(i));
				checkBiasTimeout();
				addDisjunction(constraintFactory, solv, toAdd, combo);
				int i = k - 1;
				while (i >= 0 && idx[i] == list.size() - k + i) i--;
				if (i < 0) break;
				idx[i]++;
				for (int j = i + 1; j < k; j++) idx[j] = idx[j - 1] + 1;
			}
		}
		return toAdd;
	}

	private void addDisjunction(ConstraintFactory constraintFactory, ACQ_ConstraintSolver solv,
			ConstraintSet toAdd, List<ACQ_IConstraint> combo) {
		for (int a = 0; a < combo.size(); a++) {
			for (int b = a + 1; b < combo.size(); b++) {
				ACQ_IConstraint c1 = combo.get(a), c2 = combo.get(b);
				if (c1.getNegation().equals(c2)) return;
				if (c1.getNegation().getName().equals(c2.getName())
						|| c2.getNegation().getName().equals(c1.getName())) return;
			}
		}
		// tautology : if the conjunction of the negations is unsatisfiable, the disjunction is always true
		ACQ_Network tautNet = new ACQ_Network(constraintFactory, allVarSet());
		for (ACQ_IConstraint c : combo) tautNet.add(c.getNegation(), true);
		if (solv.solveQ(tautNet).isEmpty()) return;
		ConstraintSet set = constraintFactory.createSet();
		for (ACQ_IConstraint c : combo) set.add(c);
		ACQ_DisjunctionConstraint disjunction = new ACQ_DisjunctionConstraint(constraintFactory, set);
		ACQ_Network disjNet = new ACQ_Network(constraintFactory, allVarSet());
		disjNet.add(disjunction, true);
		if (!solv.solveQ(disjNet).isEmpty()) {
			toAdd.add(disjunction);
			toAdd.add(disjunction.getNegation());
		}
	}

	@Override
	public ACQ_Network createTargetNetwork() {
		ConstraintFactory constraintFactory = new ConstraintFactory();
		ConstraintSet constraints = constraintFactory.createSet();
		constraints.add(targetConstraint());
		return new ACQ_Network(constraintFactory, allVarSet(), constraints);
	}

	@Override
	public void process() {

		switch (algo) {
		case QUACQ:
			ACQ_WS.executeExperience(this);
			break;
		case PACQ:
			ACQ_WS.executeExperience(this, this.getAlgo(), this.getNb_threads(), this.getPartition());
			break;
		case CONACQ1:
			ACQ_WS.executeConacqV1Experience(this);
			break;
		case CONACQ2:
			ACQ_WS.executeConacqV2Experience(this);
			break;
		case CANDY:
			ACQ_WS.executeCANDYExperience(this);
			break;
		case CANDY_1N:
			ACQ_WS.executeCANDY_1NExperience(this);
			break;
		case CANDY_ALTER_DCA:
			ACQ_WS.executeCANDY_Alter_DCAExperience(this);
			break;
		case DCA:
			ACQ_WS.executeDCAExperience(this);
			break;
		default:
			ACQ_WS.executeExperience(this, this.getAlgo(), this.getNb_threads(), this.getPartition());
			break;
		}
	}

	@Override
	public ArrayList<ACQ_Bias> createDistBias() {
		return null;
	}

	@Override
	public ACQ_Oracle createDistOracle(int id) {
		return null;
	}

	@Override
	public ArrayList<ACQ_Network> createStrategy(ACQ_Bias bias) {
		return null;
	}

	@Override
	public ContradictionSet createBackgroundKnowledge(ACQ_Bias bias, ConstraintMapping mapping) {
		return null;
	}

	@Override
	public SATSolver createSATSolver() {
		return new Sat4jMaxSATSolver();
	}

	@Override
	public boolean getJson() {
		return true;
	}

	@Override
	public String getDataFile() {
		return examplesfile;
	}

	@Override
	public int getMaxRand() {
		return 0;
	}

	@Override
	public int getMaxQueries() {
		return maxqueries;
	}

	@Override
	public ACQ_Network createInitNetwork() {
		return null;
	}

	@Override
	public void setDisj(boolean unable_disj) {
		this.disj = unable_disj;
	}

	/** between_min_max(VAR, X1..X5) : 6 variables, bias 15 * 4 = 60 */
	public static class BetweenMinMaxToy extends ExpeTOY_Global {
		protected int disjunctionSize() { return nbVars() - 1; }
		public BetweenMinMaxToy(boolean auto_learn) { super(auto_learn); }
		protected boolean targetPair(int i, int j) { return i == 0; }
		protected int defaultNbVars() { return 6; }
		protected GlobalConstraint targetConstraint() { return new BetweenMinMax(0, range(1, nbVars())); }
		protected Operator[] biasOperators() { return new Operator[] { Operator.LE, Operator.GE }; }
	}

	/** minimum(MIN, X1..X4) : 5 variables, bias 10 * 6 = 60 */
	public static class MinimumToy extends ExpeTOY_Global {
		protected int disjunctionSize() { return nbVars() - 1; }
		public MinimumToy(boolean auto_learn) { super(auto_learn); }
		protected int defaultNbVars() { return 5; }
		protected GlobalConstraint targetConstraint() { return new Minimum(0, range(1, nbVars())); }
		protected Operator[] biasOperators() { return new Operator[] { Operator.LE, Operator.GE, Operator.EQ }; }
	}

	/** maximum(MAX, X1..X4) : 5 variables, bias 10 * 6 = 60 */
	public static class MaximumToy extends ExpeTOY_Global {
		protected int disjunctionSize() { return nbVars() - 1; }
		public MaximumToy(boolean auto_learn) { super(auto_learn); }
		protected int defaultNbVars() { return 5; }
		protected GlobalConstraint targetConstraint() { return new Maximum(0, range(1, nbVars())); }
		protected Operator[] biasOperators() { return new Operator[] { Operator.LE, Operator.GE, Operator.EQ }; }
	}

	/** lex_lesseq(X1..X3, Y1..Y3) : 6 variables, bias 15 * 4 = 60 */
	public static class LexLessEqToy extends ExpeTOY_Global {
		protected int disjunctionSize() { return nbVars() / 2; }
		public LexLessEqToy(boolean auto_learn) { super(auto_learn); }
		protected boolean targetPair(int i, int j) { return j == i + nbVars() / 2; }
		protected int defaultNbVars() { return 6; }
		protected GlobalConstraint targetConstraint() {
			int n = nbVars();
			if (n % 2 != 0)
				throw new IllegalArgumentException("toy_LexLessEq needs an even number of variables, got " + n);
			return new LexLessEq(range(0, n / 2), range(n / 2, n));
		}
		protected Operator[] biasOperators() { return new Operator[] { Operator.LE, Operator.GE }; }
	}

	/** domain(X, X1..Xn) (DOM_n) : X in 1..n, Xi in 0..1, X = i iff Xi = 1. -n gives n, so n + 1 variables.
	 *  Bias : X <= i, X >= i and Xi >= 1 with their negations, 6n atoms */
	public static class DomainToy extends ExpeTOY_Global {
		public DomainToy(boolean auto_learn) { super(auto_learn); }
		protected int defaultNbVars() { return 4; }
		protected int nbVars() { return (size > 0 ? size : 3) + 1; }
		protected int varMin(int v) { return v == 0 ? 1 : 0; }
		protected int varMax(int v) { return v == 0 ? nbVars() - 1 : 1; }
		protected GlobalConstraint targetConstraint() { return new Domain(0, range(1, nbVars())); }
		protected Operator[] biasOperators() { return new Operator[0]; }
		protected void addAtoms(ConstraintSet constraints) {
			for (int i = 1; i < nbVars(); i++) {
				UnaryArithmetic[] atoms = { new UnaryArithmetic("XLE_" + i, 0, Operator.LE, i),
						new UnaryArithmetic("XGE_" + i, 0, Operator.GE, i), new UnaryArithmetic("XiGE1_" + i, i, Operator.GE, 1) };
				for (UnaryArithmetic atom : atoms) {
					constraints.add(atom);
					constraints.add(atom.getNegation());
				}
			}
		}
	}

	/** ultrametric constraint (UM_k): on every triple of the k variables, the minimum of any three of them
	 *  appears at least twice. Domain [0, k-1], bias 4 atoms per pair */
	public static class UltrametricToy extends ExpeTOY_Global {
		public UltrametricToy(boolean auto_learn) { super(auto_learn); }
		protected int defaultNbVars() { return 3; }
		protected GlobalConstraint targetConstraint() { return new Ultrametric(range(0, nbVars())); }
		protected Operator[] biasOperators() { return new Operator[] { Operator.LE, Operator.GE }; }
	}
}
