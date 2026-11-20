/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.lisn.lahdak.acquisition.expe;

import fr.lisn.lahdak.acquisition.core.acqconstraint.*;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintFactory.ConstraintSet;
import fr.lisn.lahdak.acquisition.core.acqsolver.*;
import fr.lisn.lahdak.acquisition.core.combinatorial.AllPermutationIterator;
import fr.lisn.lahdak.acquisition.core.combinatorial.CombinationIterator;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Bias;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Scope;
import fr.lisn.lahdak.acquisition.core.oracle.ACQ_Oracle;
import fr.lisn.lahdak.acquisition.core.oracle.Answer;
import fr.lisn.lahdak.acquisition.core.workspace.ACQ_WS;
import fr.lisn.lahdak.acquisition.core.workspace.DefaultExperience;

import java.util.*;

public class ExpeTOY_AllDisj extends DefaultExperience{

	boolean auto_learn;
	private int nb_tasks=5;
	private int nb_vars = nb_tasks*2;
	boolean disj=false;
	private double constrainedFraction = Double.parseDouble(System.getProperty("alldisj.constrainedFraction", "0.5"));

	public ExpeTOY_AllDisj(boolean auto_learn) {
		this.auto_learn=auto_learn;
	}

	public ACQ_ConstraintSolver createSolver() {
		return new ACQ_ChocoSolver(new ACQ_IDomain() {
			@Override
			public int getMin(int numvar) {
				return 0;
			}

			@Override
			public int getMax(int numvar) {
				return nb_tasks;
			}
		}, VarSelector.DomOverWDeg.toString(), ValSelector.IntDomainRandom.toString());
	}

	private int nbConstrainedTasks() {
		//return nb_tasks;
		return (int) Math.floor(nb_tasks * constrainedFraction);
	}

	public ACQ_Oracle createOracle() {
		return new ACQ_Oracle() {
			@Override
			public Answer ask(ACQ_Query e) {
				if (auto_learn) {
					int[] tuple = e.getTuple();
					int k = nbConstrainedTasks();
					for (int i = 0; i < k; i++) {
						int oi = tuple[2 * i];
						int di = tuple[2 * i + 1];
						if (di <= 0) continue;
						for (int j = i + 1; j < k; j++) {
							int oj = tuple[2 * j];
							int dj = tuple[2 * j + 1];
							if (dj <= 0) continue;
							if (oi + di > oj && oj + dj > oi) {
								e.classify(Answer.NO);
								return Answer.NO;
							}
						}
					}
					e.classify(Answer.YES);
					return Answer.YES;
				}else
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
				if(!userAnswer.equals("y") && !userAnswer.equals("n")) {
					System.out.println("Incorrect answer.\n Please enter y or n.");
				}
			} while(!userAnswer.equals("y") && !userAnswer.equals("n"));
			
			return userAnswer.equals("y") ? Answer.YES : Answer.NO ;
		}
    }

		public ACQ_Bias createBias() {
			int NB_VARIABLE = nb_vars;
			// build All variables set
			BitSet bs = new BitSet();
			bs.set(0, NB_VARIABLE);
			ACQ_Scope allVarSet = new ACQ_Scope(bs);
			// build Constraints
			ConstraintFactory constraintFactory=new ConstraintFactory();

			ConstraintSet constraints = constraintFactory.createSet();

			for (int i = 0; i < nb_tasks; i++) {
				int oi = 2 * i,  di = 2 * i + 1;
				for (int j = i + 1; j < nb_tasks; j++) {
					int oj = 2 * j,  dj = 2 * j + 1;

					// BE_I_J : I can ends where J begins
					//BE_I_J := oi + di <= oj
					// + sa négation oi + di > oj
					// same for BE_J_I
					TernaryArithmetic IBEJ = new TernaryArithmetic("BE_"+i+"_"+j, oi, Operator.PL, di, Operator.LE, oj, 0, "NotBE_"+i+"_"+j);
					TernaryArithmetic JBEI = new TernaryArithmetic("BE_"+j+"_"+i, oj, Operator.PL, dj, Operator.LE, oi, 0, "NotBE_"+j+"_"+i);
					constraints.add(IBEJ);
					constraints.add(JBEI);
					constraints.add(IBEJ.getNegation());
					constraints.add(JBEI.getNegation());

					// BT_I_J : I ends strictly before J
					// BT_I_J := oi + di < oj
					// + sa négation oi + di >= oj
					// same for BT_J_I
					TernaryArithmetic IBTJ = new TernaryArithmetic("BT_"+i+"_"+j, oi, Operator.PL, di, Operator.LT, oj, 0, "NotBT_"+i+"_"+j);
					TernaryArithmetic JBTI = new TernaryArithmetic("BT_"+j+"_"+i, oj, Operator.PL, dj, Operator.LT, oi, 0, "NotBT_"+j+"_"+i);
					constraints.add(IBTJ);
					constraints.add(JBTI);
					constraints.add(IBTJ.getNegation());
					constraints.add(JBTI.getNegation());

					// -----------to allow overlap we need the next constraints IN ADDITION to the first ones ---------
					// oj <= oi + sa négation oj > oi
					// et oi <= oj + sa négation oi > oj
					BinaryArithmetic OjBEOi = new BinaryArithmetic("OjBEOi_"+j+"_"+i, oj, Operator.LE, oi, "NotOjBEOi_"+j+"_"+i);
					BinaryArithmetic OiBEOj = new BinaryArithmetic("OiBEOj_"+i+"_"+j, oi, Operator.LE, oj, "NotOiBEOj_"+i+"_"+j);
					constraints.add(OiBEOj);
					constraints.add(OiBEOj.getNegation());
					constraints.add(OjBEOi);
					constraints.add(OjBEOi.getNegation());




					//constraints.add(new ACQ_DisjunctionConstraint(constraintFactory, IBeforeJ, JBeforeI));


				}
			}
			for (int i = 0; i < nb_tasks; i++) {
				UnaryArithmetic positive = new UnaryArithmetic("Dpos_" + i, 2 * i + 1, Operator.GT, 0);
				constraints.add(positive);
				constraints.add(positive.getNegation());
			}

			if(disj){
				// Conacq2 : disjunctions of 2 up to disjunctionSize() atoms. Minimal size per problem
				// (d_i <= 0 or d_j <= 0 or o_i + d_i <= o_j or o_j + d_j <= o_i).
				ACQ_ConstraintSolver solv = createSolver();
				solv.setVars(allVarSet);
				ConstraintSet toAdd = constraintFactory.createSet();
				// Restricted to one pair of tasks : the clause the concept needs only mentions two tasks
				// (d_i <= 0 or d_j <= 0 or o_i + d_i <= o_j or o_j + d_j <= o_i). Enumerating every subset of the
				// whole bias instead means 1.67 million subsets at n = 4 (measured: never finished in 15 min).
				for (int i = 0; i < nb_tasks; i++) {
					for (int j = i + 1; j < nb_tasks; j++) {
						java.util.Set<Integer> scope = new java.util.HashSet<>(
								java.util.Arrays.asList(2 * i, 2 * i + 1, 2 * j, 2 * j + 1));
						java.util.List<ACQ_IConstraint> group = new java.util.ArrayList<>();
						for (ACQ_IConstraint c : constraints) {
							boolean inside = true;
							for (int v : c.getVariables()) inside &= scope.contains(v);
							if (inside) group.add(c);
						}
						for (int k = 2; k <= Math.min(disjunctionSize(), group.size()); k++) {
							int[] idx = new int[k];
							for (int a = 0; a < k; a++) idx[a] = a;
							while (true) {
								java.util.List<ACQ_IConstraint> combo = new java.util.ArrayList<>();
								for (int a : idx) combo.add(group.get(a));
								ExpeTOY_Global.checkBiasTimeout();
								addDisjunction(constraintFactory, solv, allVarSet, toAdd, combo);
								int a = k - 1;
								while (a >= 0 && idx[a] == group.size() - k + a) a--;
								if (a < 0) break;
								idx[a]++;
								for (int b = a + 1; b < k; b++) idx[b] = idx[b - 1] + 1;
							}
						}
					}
				}
				constraints.addAll(toAdd);
			}

			ACQ_Network network = new ACQ_Network(constraintFactory, allVarSet, constraints);
			ACQ_Bias bias = new ACQ_Bias(network);
			//System.out.println("bias u : " + bias.toString());
			return bias;
		}

	/** minimal clause size for the target : one pair of tasks needs 4 literals with the duration rule */
	protected int disjunctionSize() {
		return 4;
	}

	private void addDisjunction(ConstraintFactory constraintFactory, ACQ_ConstraintSolver solv, ACQ_Scope allVarSet,
			ConstraintSet toAdd, java.util.List<ACQ_IConstraint> combo) {
		for (int a = 0; a < combo.size(); a++) {
			for (int b = a + 1; b < combo.size(); b++) {
				ACQ_IConstraint c1 = combo.get(a), c2 = combo.get(b);
				if (c1.getNegation().equals(c2)) return;
				if (c1.getNegation().getName().equals(c2.getName())
						|| c2.getNegation().getName().equals(c1.getName())) return;
			}
		}
		ACQ_Network tautNet = new ACQ_Network(constraintFactory, allVarSet);
		for (ACQ_IConstraint c : combo) tautNet.add(c.getNegation(), true);
		if (solv.solveQ(tautNet).isEmpty()) return;
		ConstraintSet set = constraintFactory.createSet();
		for (ACQ_IConstraint c : combo) set.add(c);
		ACQ_DisjunctionConstraint disjunction = new ACQ_DisjunctionConstraint(constraintFactory, set);
		ACQ_Network disjNet = new ACQ_Network(constraintFactory, allVarSet);
		disjNet.add(disjunction, true);
		if (!solv.solveQ(disjNet).isEmpty()) {
			toAdd.add(disjunction);
			toAdd.add(disjunction.getNegation());
		}
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
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ACQ_Oracle createDistOracle(int id) {
		// TODO Auto-generated method stub
		return null;
	}

	public void setDisj(boolean unable_disj){this.disj =unable_disj; }

	/** nb_tasks tasks, 2 variables each, domain [0, nb_tasks] */
	public void setNbTasks(int nb_tasks) {
		this.nb_tasks = nb_tasks;
		this.nb_vars = nb_tasks * 2;
	}


	@Override
	public ACQ_Network createTargetNetwork() {
		int NB_VARIABLE = nb_vars;

		BitSet bs = new BitSet();
		bs.set(0, NB_VARIABLE);
		ACQ_Scope allVarSet = new ACQ_Scope(bs);

		ConstraintFactory constraintFactory = new ConstraintFactory();
		ConstraintSet constraints = constraintFactory.createSet();

		int k = nbConstrainedTasks();

		// One constraint AllDisj on all the tasks
		Set<Integer> scope = new LinkedHashSet<>();
		for (int i = 0; i < 2*k; i++) {
			scope.add(i);
		}
		constraints.add(new AllDisj(new ACQ_Scope(scope), "NotAllDisj"));

		ACQ_Network targetNetwork = new ACQ_Network(constraintFactory, allVarSet, constraints);
		return targetNetwork;
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
		//return new MiniSatSolver();
		return new Sat4jMaxSATSolver();

	}

	@Override
	public boolean getJson() {
		// TODO Auto-generated method stub
		return true;
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
