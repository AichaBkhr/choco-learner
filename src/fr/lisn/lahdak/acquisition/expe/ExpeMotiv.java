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

public class ExpeMotiv extends DefaultExperience {

	private static final int NB_VARS = 3;
	private static final int DOMAIN_MIN = -4;
	private static final int DOMAIN_MAX = 4;
	private static final int DISJUNCTION_SIZE = Integer.getInteger("motiv.disjSize", 2);

	boolean auto_learn;
	boolean disj = false;

	public ExpeMotiv(boolean auto_learn) {
		this.auto_learn = auto_learn;
	}

	public ACQ_ConstraintSolver createSolver() {
		return new ACQ_ChocoSolver(new ACQ_IDomain() {
			@Override
			public int getMin(int numvar) {
				return DOMAIN_MIN;
			}

			@Override
			public int getMax(int numvar) {
				return DOMAIN_MAX;
			}
		}, VarSelector.DomOverWDeg.toString(), ValSelector.IntDomainRandom.toString());
	}

	private static boolean target(int[] t) {
		return t[0] > 0 && (t[1] <= 0 || t[2] != 0);
	}

	public ACQ_Oracle createOracle() {
		return new ACQ_Oracle() {
			@Override
			public Answer ask(ACQ_Query e) {
				boolean complete = e.getScope().size() == NB_VARS;
				Answer answer = !complete || target(e.getTuple()) ? Answer.YES : Answer.NO;
				e.classify(answer);
				return answer;
			}
		};
	}

	private ACQ_Scope allVarSet() {
		BitSet bs = new BitSet();
		bs.set(0, NB_VARS);
		return new ACQ_Scope(bs);
	}

	public ACQ_Bias createBias() {
		ConstraintFactory constraintFactory = new ConstraintFactory();
		ConstraintSet constraints = constraintFactory.createSet();
		Operator[] ops = { Operator.GT, Operator.GE, Operator.EQ };
		for (int i = 0; i < NB_VARS; i++) {
			for (Operator op : ops) {
				UnaryArithmetic atom = new UnaryArithmetic(op.name() + "0_" + i, i, op, 0);
				constraints.add(atom);
				constraints.add(atom.getNegation());
			}
		}
		if (disj)
			constraints.addAll(disjunctions(constraintFactory, constraints));
		return new ACQ_Bias(new ACQ_Network(constraintFactory, allVarSet(), constraints));
	}

	/** All disjunctions of 2 to DISJUNCTION_SIZE atoms and their negations, minus tautologies and unsatisfiable ones */
	private ConstraintSet disjunctions(ConstraintFactory constraintFactory, ConstraintSet atoms) {
		ACQ_ConstraintSolver solv = createSolver();
		solv.setVars(allVarSet());
		ConstraintSet toAdd = constraintFactory.createSet();
		List<ACQ_IConstraint> list = new ArrayList<>();
		for (ACQ_IConstraint c : atoms)
			list.add(c);
		for (int k = 2; k <= Math.min(DISJUNCTION_SIZE, list.size()); k++) {
			int[] idx = new int[k];
			for (int a = 0; a < k; a++)
				idx[a] = a;
			while (true) {
				List<ACQ_IConstraint> combo = new ArrayList<>();
				for (int a : idx)
					combo.add(list.get(a));
				addDisjunction(constraintFactory, solv, toAdd, combo);
				int a = k - 1;
				while (a >= 0 && idx[a] == list.size() - k + a)
					a--;
				if (a < 0)
					break;
				idx[a]++;
				for (int b = a + 1; b < k; b++)
					idx[b] = idx[b - 1] + 1;
			}
		}
		return toAdd;
	}

	private void addDisjunction(ConstraintFactory constraintFactory, ACQ_ConstraintSolver solv, ConstraintSet toAdd,
			List<ACQ_IConstraint> combo) {
		for (int a = 0; a < combo.size(); a++) {
			for (int b = a + 1; b < combo.size(); b++) {
				ACQ_IConstraint c1 = combo.get(a), c2 = combo.get(b);
				if (c1.getNegation().equals(c2))
					return;
				if (c1.getNegation().getName().equals(c2.getName())
						|| c2.getNegation().getName().equals(c1.getName()))
					return;
			}
		}
		ACQ_Network tautNet = new ACQ_Network(constraintFactory, allVarSet());
		for (ACQ_IConstraint c : combo)
			tautNet.add(c.getNegation(), true);
		if (solv.solveQ(tautNet).isEmpty())
			return;
		ConstraintSet set = constraintFactory.createSet();
		for (ACQ_IConstraint c : combo)
			set.add(c);
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
		constraints.add(new UnaryArithmetic("GT0_0", 0, Operator.GT, 0));
		ConstraintSet or = constraintFactory.createSet();
		or.add(new UnaryArithmetic("LE0_1", 1, Operator.LE, 0));
		or.add(new UnaryArithmetic("NEQ0_2", 2, Operator.NEQ, 0));
		constraints.add(new ACQ_DisjunctionConstraint(constraintFactory, or));
		return new ACQ_Network(constraintFactory, allVarSet(), constraints);
	}

	@Override
	public void process() {
		switch (algo) {
		case CONACQ2:
			ACQ_WS.executeConacqV2Experience(this);
			break;
		case CANDY:
			ACQ_WS.executeCANDYExperience(this);
			break;
		case DCA:
			ACQ_WS.executeDCAExperience(this);
			break;
		default:
			ACQ_WS.executeExperience(this, this.getAlgo(), this.getNb_threads(), this.getPartition());
			break;
		}
	}

	public void setDisj(boolean unable_disj) {
		this.disj = unable_disj;
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
}
