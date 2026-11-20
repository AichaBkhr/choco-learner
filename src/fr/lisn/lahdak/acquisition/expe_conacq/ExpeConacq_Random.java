package fr.lisn.lahdak.acquisition.expe_conacq;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Random;

import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_IConstraint;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_Network;
import fr.lisn.lahdak.acquisition.core.acqconstraint.BinaryArithmetic;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintFactory;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintMapping;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ContradictionSet;
import fr.lisn.lahdak.acquisition.core.acqconstraint.Operator;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintFactory.ConstraintSet;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_ChocoSolver;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_ConstraintSolver;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_IDomain;
import fr.lisn.lahdak.acquisition.core.acqsolver.MiniSatSolver;
import fr.lisn.lahdak.acquisition.core.acqsolver.SATSolver;
import fr.lisn.lahdak.acquisition.core.combinatorial.AllPermutationIterator;
import fr.lisn.lahdak.acquisition.core.combinatorial.CombinationIterator;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Bias;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Scope;
import fr.lisn.lahdak.acquisition.core.oracle.ACQ_Oracle;
import fr.lisn.lahdak.acquisition.core.oracle.Answer;
import fr.lisn.lahdak.acquisition.core.workspace.ACQ_WS;
import fr.lisn.lahdak.acquisition.core.workspace.DefaultExperience;

public class ExpeConacq_Random extends DefaultExperience {
	static int n = 10;
	static int m = 10;
	static int c = 122;
	
	ACQ_Network constraints1;

	public ExpeConacq_Random() {
		constraints1 = createRandomTarget(n, c);

	}

	@Override
	public void setDisj(boolean unable_disj){this.disj =unable_disj; }

	@Override
	public ACQ_Bias createBias() {

		int NB_VARIABLE = n;
		// build All variables set
		BitSet bs = new BitSet();
		bs.set(0, NB_VARIABLE);
		ACQ_Scope allVarSet = new ACQ_Scope(bs);
		ConstraintFactory constraintFactory = new ConstraintFactory();
		// build binary Constraints
		ConstraintSet constraints = constraintFactory.createSet();

		CombinationIterator iterator = new CombinationIterator(NB_VARIABLE, 2);
		while (iterator.hasNext()) {
			int[] vars = iterator.next();
			AllPermutationIterator pIterator = new AllPermutationIterator(2);
			while (pIterator.hasNext()) {
				int[] pos = pIterator.next();

				if (vars[pos[0]] < vars[pos[1]]) // NL: commutative relations
				{
					// X != Y
					constraints.add(
							new BinaryArithmetic("DifferentXY", vars[pos[0]], Operator.NEQ, vars[pos[1]], "EqualXY"));
					// X == Y
					constraints.add(
							new BinaryArithmetic("EqualXY", vars[pos[0]], Operator.EQ, vars[pos[1]], "DifferentXY"));

				}
				// X >= Y
				constraints
						.add(new BinaryArithmetic("GreaterEqualXY", vars[pos[0]], Operator.GE, vars[pos[1]], "LessXY"));
				// X <= Y
				constraints
						.add(new BinaryArithmetic("LessEqualXY", vars[pos[0]], Operator.LE, vars[pos[1]], "GreaterXY"));

				// X > Y
				constraints
						.add(new BinaryArithmetic("GreaterXY", vars[pos[0]], Operator.GT, vars[pos[1]], "LessEqualXY"));

				// X < Y
				constraints
						.add(new BinaryArithmetic("LessXY", vars[pos[0]], Operator.LT, vars[pos[1]], "GreaterEqualXY"));

			}
		}

		ACQ_Network network = new ACQ_Network(constraintFactory, allVarSet, constraints);

		return new ACQ_Bias(network);

	}

	@Override
	public ACQ_Oracle createOracle() {
		return new ACQ_Oracle() {
			@Override
			public Answer ask(ACQ_Query e) {

				int[] vars = new int[n];

				for (int numvar : e.getScope())
					vars[numvar] = e.getValue(numvar);

				for (ACQ_IConstraint c : constraints1) {

					for (int i = 0; i < vars.length - 1; i++) {
						for (int j = i + 1; j < vars.length; j++) {
							if (vars[i] > 0 && vars[j] > 0 && (c.getVariables()[0] == i && c.getVariables()[1] == j)) {

								if (!c.checker(new int[] { vars[i], vars[j] })) {
									e.classify(Answer.NO);
									return Answer.NO;
								}
							}
						}

					}
				}

				e.classify(Answer.YES);
				return Answer.YES;
			}

		};

	}

	@Override
	public ACQ_ConstraintSolver createSolver() {
		return new ACQ_ChocoSolver(new ACQ_IDomain() {
			@Override
			public int getMin(int numvar) {
				return 1;
			}

			@Override
			public int getMax(int numvar) {
				return m;
			}
		}, vrs, vls);
	}

	/*
	 * generating constraints by adding constraints randomly iff (network U c) is
	 * solvable
	 * 
	 * 
	 */
	public ACQ_Network createRandomTarget(int n, int c) {

		int NB_VARIABLE = n;
		// build All variables set
		BitSet bs = new BitSet();
		bs.set(0, NB_VARIABLE);
		ACQ_Scope allVarSet = new ACQ_Scope(bs);
		ConstraintFactory constraintFactory = new ConstraintFactory();
		// build binary Constraints
		ConstraintSet constraints = constraintFactory.createSet();

		CombinationIterator iterator = new CombinationIterator(NB_VARIABLE, 2);
		while (iterator.hasNext()) {
			int[] vars = iterator.next();
			AllPermutationIterator pIterator = new AllPermutationIterator(2);
			while (pIterator.hasNext()) {
				int[] pos = pIterator.next();

				if (vars[pos[0]] < vars[pos[1]]) // NL: commutative relations
				{
					// X != Y
					constraints.add(
							new BinaryArithmetic("DifferentXY", vars[pos[0]], Operator.NEQ, vars[pos[1]], "EqualXY"));

				}

			}
		}

		ACQ_Network network = new ACQ_Network(constraintFactory, allVarSet, constraints);

		ACQ_Network network1 = new ACQ_Network(constraintFactory, network.getVariables(),
				constraintFactory.createSet());

		ACQ_ChocoSolver solver = (ACQ_ChocoSolver) this.createSolver();
		solver.setVars(network.getVariables());
		int i = 0;
		int item = 0;
		int size = network.getConstraints().size();

		while (i < c && size > 0) {
			item = new Random().nextInt(size);
			network1.add(network.getConstraints().get_Constraint(item), true);
			network.getConstraints().remove(network.getConstraints().get_Constraint(item));

			if (!solver.solveA(network1).isEmpty()) {
				i++;
			} else {
				network1.getConstraints().remove(network.getConstraints().get_Constraint(item));
			}
			size = network.size();
		}

		return network1;
	}

	
	public static void main(String[] args) {
		
		}
	
	@Override
	public ArrayList<ACQ_Bias> createDistBias() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ACQ_Oracle createDistOracle(int id) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SATSolver createSATSolver() {
		//return new Z3SATSolver();
		return new MiniSatSolver();
	}

	@Override
	public ACQ_Network createTargetNetwork() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void process() {
		if(instance!=null)
			n = getInstance() ;

			ACQ_WS.executeConacqV2Experience(this);
	
	}
	@Override
	public ContradictionSet createBackgroundKnowledge(ACQ_Bias bias, ConstraintMapping mapping) {
		return null;
	}
	@Override
	public ArrayList<ACQ_Network> createStrategy(ACQ_Bias bias) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean getJson() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getDataFile() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getMaxRand() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMaxQueries() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public ACQ_Network createInitNetwork() {
		// TODO Auto-generated method stub
		return null;
	}
}

