/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.lisn.lahdak.acquisition.expe;

import java.util.ArrayList;
import java.util.BitSet;

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

/**
 *
 * @author NASSIM
 */
public class ExpeRLFAP extends DefaultExperience {

	// Heuristic heuristic = Heuristic.SOL;
	// ACQ_ConstraintSolver solver;
	// ACQ_Bias bias;
	// private final ACQ_oracle oracle;
	private static int p_vars = 50; // m
	private static int domain = 500; //d

	public ExpeRLFAP() {
	}

	public ACQ_ConstraintSolver createSolver() {
		return new ACQ_ChocoSolver(new ACQ_IDomain() {
			@Override
			public int getMin(int numvar) {
				return 1;
			}

			@Override
			public int getMax(int numvar) {
				return domain;
			}
		}, vrs, vls);
	}

	@Override
	public void setDisj(boolean unable_disj){this.disj =unable_disj; }

	public ACQ_Oracle createOracle() {
		return new ACQ_Oracle() {
			@Override
			public Answer ask(ACQ_Query e) {

			
				int vars[] = new int[p_vars*p_vars];
				for (int numvar : e.getScope()) {
					vars[numvar] = e.getValue(numvar);

				}

			
			
 

				for (int i = 0; i < p_vars - 1; i++) {
					for (int j = i + 1; j < p_vars; j++) {
						if ((vars[i] > 0 && vars[j] > 0) && vars[i] == vars[j]) {
							e.classify(Answer.NO);
							return Answer.NO;
						}
					}
				}
				for (int i = p_vars; i < p_vars * 2 - 1; i++) {
					for (int j = i + 1; j < p_vars * 2; j++) {
						if ((vars[i] > 0 && vars[j] > 0) && vars[i] == vars[j]) {
							e.classify(Answer.NO);
							return Answer.NO;
						}
					}
				}
				for (int i = p_vars * 2; i < p_vars * 3 - 1; i++) {
					for (int j = i + 1; j < p_vars * 3; j++) {
						if ((vars[i] > 0 && vars[j] > 0) && vars[i] == vars[j]) {
							e.classify(Answer.NO);
							return Answer.NO;
						}
					}
				}

				for (int i = p_vars * 3; i < p_vars * 4 - 1; i++) {
					for (int j = i + 1; j < p_vars * 4; j++) {
						if ((vars[i] > 0 && vars[j] > 0) && vars[i] == vars[j]) {
							e.classify(Answer.NO);
							return Answer.NO;
						}
					}

				}

				for (int i = p_vars * 4; i < p_vars * 5 - 1; i++) {
					for (int j = i + 1; j < p_vars * 5; j++) {
						if ((vars[i] > 0 && vars[j] > 0) && vars[i] == vars[j]) {
							e.classify(Answer.NO);
							return Answer.NO;
						}

					}
				}


				
				for (int i = 0; i < p_vars - 1; i++) {
					for (int j = i + 1; j < p_vars; j++) {
						if ((vars[i] > 0 && vars[j] > 0) && Math.abs(vars[i]-vars[j]) <=3) {
							e.classify(Answer.NO);
							return Answer.NO;
						}
					}
				}
				for (int i = p_vars; i < (p_vars * 2) - 1; i++) {
					for (int j = i + 1; j < p_vars * 2; j++) {
						if ((vars[i] > 0 && vars[j] > 0) && Math.abs(vars[i]-vars[j]) <=3) {
							e.classify(Answer.NO);
							return Answer.NO;
						}
					}
				}
				for (int i = p_vars * 2; i < (p_vars * 3) - 1; i++) {
					for (int j = i + 1; j < p_vars * 3; j++) {
						if ((vars[i] > 0 && vars[j] > 0) && Math.abs(vars[i]-vars[j]) <=3) {
							e.classify(Answer.NO);
							return Answer.NO;
						}
					}
				}

				for (int i = p_vars * 3; i < (p_vars * 4) - 1; i++) {
					for (int j = i + 1; j < p_vars * 4; j++) {
						if ((vars[i] > 0 && vars[j] > 0) && Math.abs(vars[i]-vars[j]) <=3) {
							e.classify(Answer.NO);
							return Answer.NO;
						}
					}

				}

				for (int i = p_vars * 4; i < (p_vars * 5) - 1; i++) {
					for (int j = i + 1; j < p_vars * 5; j++) {
						if ((vars[i] > 0 && vars[j] > 0) && Math.abs(vars[i]-vars[j]) <=3) {
							e.classify(Answer.NO);
							return Answer.NO;
						}

					}
				}
				
				for (int i = 0; i < p_vars ; i++) {
					for (int j = p_vars; j < p_vars*2; j++) {
						if ((vars[i] > 0 && vars[j] > 0) && Math.abs(vars[i]-vars[j]) <= 2 ) {
							e.classify(Answer.NO);
							return Answer.NO;
						}
					}
				}
				for (int i = p_vars*2; i < p_vars * 3 ; i++) {
					for (int j = (p_vars * 3) ; j < p_vars * 4; j++) {
						if ((vars[i] > 0 && vars[j] > 0) && Math.abs(vars[i]-vars[j]) <= 2) {
							e.classify(Answer.NO);
							return Answer.NO;
						}
					}
				}
				for (int i = p_vars * 4; i < p_vars * 5; i++) {
					for (int j = 0; j < p_vars; j++) {
						if ((vars[i] > 0 && vars[j] > 0) && Math.abs(vars[i]-vars[j]) <= 2) {
							e.classify(Answer.NO);
							return Answer.NO;
						}
					}
				}

				
				e.classify(Answer.YES);

				return Answer.YES;

			

			}
		};

	}

	public ACQ_Bias createBias() {
		int NB_VARIABLE = p_vars * p_vars;

		// build All variables set
		BitSet bs = new BitSet();
		bs.set(0, NB_VARIABLE);
		ACQ_Scope allVarSet = new ACQ_Scope(bs);
		ConstraintFactory constraintFactory = new ConstraintFactory();
		// build binary Constraints
		ConstraintSet constraints = constraintFactory.createSet();

		// Binary Constraints
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
				
			for(int i=1; i<10; i++) {
				// abs(X-Y) > 1
				constraints.add(new BinaryArithmetic("AT_GT_"+i, vars[pos[0]], Operator.Dist, vars[pos[1]],
						Operator.GT, i, "AT_LE_"+i));

				// abs(X-Y) <= 1
				constraints.add(new BinaryArithmetic("AT_LE_"+i, vars[pos[0]], Operator.Dist, vars[pos[1]],
						Operator.LT, i + 1, "AT_GT_"+i));
			}


			}
		}

		ACQ_Network network = new ACQ_Network(constraintFactory, allVarSet, constraints);

		return new ACQ_Bias(network);
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
		default:
			ACQ_WS.executeExperience(this, this.getAlgo(), this.getNb_threads(), this.getPartition());

			break;

		}

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
	public ACQ_Network createTargetNetwork() {
		// TODO Auto-generated method stub
		return new ACQ_Network();
	}

	@Override
	public ArrayList<ACQ_Network> createStrategy(ACQ_Bias bias) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ContradictionSet createBackgroundKnowledge(ACQ_Bias bias, ConstraintMapping mapping) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SATSolver createSATSolver() {
		return new MiniSatSolver();

	}

	@Override
	public boolean getJson() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getDataFile() {
		// TODO Auto-generated method stub
		return examplesfile;
	}

	@Override
	public int getMaxRand() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMaxQueries() {
		// TODO Auto-generated method stub
		return maxqueries;
	}

	@Override
	public ACQ_Network createInitNetwork() {
		// TODO Auto-generated method stub
		return null;
	}


}
