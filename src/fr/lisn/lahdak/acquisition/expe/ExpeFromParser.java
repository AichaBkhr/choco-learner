package fr.lisn.lahdak.acquisition.expe;

import java.util.ArrayList;
import java.util.BitSet;

import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_Constraint;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_IConstraint;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_Network;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_Relation;
import fr.lisn.lahdak.acquisition.core.acqconstraint.BinaryArithmetic;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintFactory;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintMapping;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ContradictionSet;
import fr.lisn.lahdak.acquisition.core.acqconstraint.Operator;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ScalarArithmetic;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintFactory.ConstraintSet;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_ChocoSolver;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_ConstraintSolver;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_IDomain;
import fr.lisn.lahdak.acquisition.core.acqsolver.MiniSatSolver;
import fr.lisn.lahdak.acquisition.core.acqsolver.SATSolver;
import fr.lisn.lahdak.acquisition.core.acqsolver.ValSelector;
import fr.lisn.lahdak.acquisition.core.acqsolver.VarSelector;
import fr.lisn.lahdak.acquisition.core.combinatorial.AllPermutationIterator;
import fr.lisn.lahdak.acquisition.core.combinatorial.CombinationIterator;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Bias;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Scope;
import fr.lisn.lahdak.acquisition.core.oracle.ACQ_Oracle;
import fr.lisn.lahdak.acquisition.core.oracle.Answer;
import fr.lisn.lahdak.acquisition.core.workspace.ACQ_WS;
import fr.lisn.lahdak.acquisition.core.workspace.DefaultExperience;

public class ExpeFromParser extends DefaultExperience {
	
	public ExpeParser exp;

	public ExpeFromParser(ExpeParser exp) {
		this.exp = exp;
	}

	static String vls = ValSelector.IntDomainRandom.toString();
	static String vrs = VarSelector.RandomVar.toString();

	public ACQ_ConstraintSolver createSolver() {
		return new ACQ_ChocoSolver(new ACQ_IDomain() {
			@Override
			public int getMin(int numvar) {
				return exp.getMinDom();
			}

			@Override
			public int getMax(int numvar) {
				return exp.getMaxDom();
			}
		}, vrs, vls);
	}

	public ACQ_Oracle createOracle() {
		return new ACQ_Oracle() {

			@Override
			public ACQ_Network buildTargetNetwork() {
				int NB_VARIABLE = exp.getNbVars();
				// build All variables set
				BitSet bs = new BitSet();
				bs.set(0, NB_VARIABLE);

				ACQ_Scope allVarSet = new ACQ_Scope(bs);

				ConstraintFactory constraintFactory = new ConstraintFactory();

				ConstraintSet constraints = constraintFactory.createSet();

				ACQ_Network tn = new ACQ_Network(constraintFactory, allVarSet, constraints);

				for (ArrayList<String> cst : exp.getTN())
					tn.add(ACQ_Constraint.CstrFactory.getConstraint(cst), true);
				return tn;

			}

			@Override
			public Answer ask(ACQ_Query e) {
				this.setTargetNetwork();
				if (this.getTargetNetwork().isEmpty()) {
					Answer answer = getAnswer(e, exp.getNbVars());
					e.classify(answer.equals(Answer.YES)? Answer.YES:Answer.NO);
					return answer;
				} else
					for (ACQ_IConstraint cst : this.getTargetNetwork().getConstraints()) {
						if (e.getScope().containsAll(cst.getScope()) && !cst.checker(e.getProjection(cst.getScope()))) {
							e.classify(Answer.NO);
							return Answer.NO;
						}
					}
				e.classify(Answer.YES);
				return Answer.YES;
			}

		};

	}

	public ACQ_Bias createBias() {

		int NB_VARIABLE = exp.getNbVars();
		// build All variables set
		BitSet bs = new BitSet();
		bs.set(0, NB_VARIABLE);

		ACQ_Scope allVarSet = new ACQ_Scope(bs);

		ConstraintFactory constraintFactory = new ConstraintFactory();

		ConstraintSet constraints = constraintFactory.createSet();

		constraints = (convert(exp));

		ACQ_Network network = new ACQ_Network(constraintFactory, allVarSet, constraints);
		System.out.print(network.size());
		return new ACQ_Bias(network);
	}

	private ConstraintSet convert(ExpeParser experiment) {
		ConstraintFactory cf = new ConstraintFactory();
		ConstraintSet constraints = cf.createSet();
		int[] duration = null;
		if(experiment.getName().contains("scheduling"))
			duration = getDuraitons(instance);
		if(experiment.getName().contains("meetings"))
			readDataset();

		for (ArrayList<String> c : experiment.getBias()) {
			constraints.add(ACQ_Constraint.CstrFactory.getConstraint(c));
		}

		for (ArrayList<String> r : experiment.getGamma()) {
			ACQ_Relation rel = ACQ_Relation.valueOf(r.get(0));
			
			CombinationIterator iterator = new CombinationIterator(experiment.getNbVars(), rel.getArity());

			while (iterator.hasNext()) {
				int[] vars = iterator.next();
				AllPermutationIterator pIterator = new AllPermutationIterator(rel.getArity());

				// Binary constraints
				if (rel.getArity() == 2 && !rel.IsPrecedence()) {
					while (pIterator.hasNext()) {
						int[] pos = pIterator.next();
						if(vars[pos[0]]<vars[pos[1]]) {
						switch(rel.name()) {
						case "OutDiag1":
							constraints.add(new BinaryArithmetic("OutDiag1", vars[pos[0]], Operator.NEQ, vars[pos[1]],
									Operator.PL, (vars[pos[1]] - vars[pos[0]]), "InDiag1"));
							break;
						case" InDiag1":
							constraints.add(new BinaryArithmetic("InDiag1", vars[pos[0]], Operator.EQ, vars[pos[1]],
									Operator.PL, (vars[pos[1]] - vars[pos[0]]), "OutDiag1"));

							break;
						case" OutDiag2":
							constraints.add(new BinaryArithmetic("OutDiag2", vars[pos[0]], Operator.NEQ, vars[pos[1]],
									Operator.PL, (vars[pos[0]] - vars[pos[1]]), "InDiag2"));
							break;
						case" InDiag2":
							constraints.add(new BinaryArithmetic("InDiag2", vars[pos[0]], Operator.EQ, vars[pos[1]],
									Operator.PL, (vars[pos[0]] - vars[pos[1]]), "OutDiag2"));
							break;
						case "AT_Equal":
							 constraints.add(new BinaryArithmetic(rel.name(), vars[pos[0]], Operator.Dist,vars[pos[1]], Operator.EQ, distance[vars[pos[0]]][vars[pos[1]]], "AT_Diff"));
							 break;

						case "AT_Diff":
								 constraints.add( new BinaryArithmetic(rel.name(), vars[pos[0]], Operator.Dist,
										 vars[pos[1]], Operator.NEQ, distance[vars[pos[0]]][vars[pos[1]]], "AT_Equal"));
								 break;

						case "AT_GT":
								 constraints.add( new BinaryArithmetic(rel.name(), vars[pos[0]], Operator.Dist,
										 vars[pos[1]], Operator.GT,distance[vars[pos[0]]][vars[pos[1]]], "AT_LE"));
								 break;

						case "AT_LT":
								 constraints.add( new BinaryArithmetic(rel.name(), vars[pos[0]], Operator.Dist,
										 vars[pos[1]], Operator.LT, distance[vars[pos[0]]][vars[pos[1]]] ,  "AT_GE"));
							 break;

						case "AT_GE":
								 constraints.add( new BinaryArithmetic(rel.name(), vars[pos[0]], Operator.Dist,
										 vars[pos[1]], Operator.GT, (distance[vars[pos[0]]][vars[pos[1]]] - 1),  "AT_LT"));
								 break;

						case "AT_LE":
								 constraints.add( new BinaryArithmetic(rel.name(), vars[pos[0]], Operator.Dist,
										 vars[pos[1]], Operator.LT,distance[vars[pos[0]]][vars[pos[1]]]+1,  "AT_GT"));
							
								 break;
							default:
								constraints.add(new BinaryArithmetic(rel.name(), vars[pos[0]], rel.getOperator(),
										vars[pos[1]], rel.getNegation().name()));
								break;
						}
						
						}

					}
				}
				if (rel.IsPrecedence()) {
					while (pIterator.hasNext()) {
						int[] pos = pIterator.next();

						constraints.add(new ScalarArithmetic(rel.name(), new int[] { vars[pos[0]], vars[pos[1]] },
								new int[] { 1, -1 }, rel.getOperator(), -1 * duration[vars[pos[0]]],
								rel.getNegation().name()));

					}
					
				}
				
				// Ternary Constraints
				if (rel.getArity() == 3) {
					while (pIterator.hasNext()) {
						int[] pos = pIterator.next();

						if (vars[pos[0]] > vars[pos[1]] && vars[pos[1]] > vars[pos[2]]) {
							constraints.add(new ScalarArithmetic(rel.name(),
									new int[] { vars[pos[0]], vars[pos[1]], vars[pos[2]] }, new int[] { 1, -2, 1 },
									rel.getOperator(), 0, rel.getNegation().name()));

						}

					}

				}
				// Quaternary Constraints

				if (rel.getArity() == 4) {
					while (pIterator.hasNext()) {
						int[] pos = pIterator.next();

						if (vars[pos[0]] > vars[pos[1]] && vars[pos[2]] > vars[pos[3]] && vars[pos[0]] > vars[pos[2]]) {
							constraints.add(new ScalarArithmetic(rel.name(),
									new int[] { vars[pos[0]], vars[pos[1]], vars[pos[2]], vars[pos[3]] },
									new int[] { 1, -1, -1, 1 }, rel.getOperator(), 0, rel.getNegation().name()));

						}

					}

				}
			}
		}
		return constraints;
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
	public void setDisj(boolean unable_disj){this.disj =unable_disj; }



	@Override
	public ACQ_Network createTargetNetwork() {
		int NB_VARIABLE = exp.getNbVars();
		// build All variables set
		BitSet bs = new BitSet();
		bs.set(0, NB_VARIABLE-1);

		ACQ_Scope allVarSet = new ACQ_Scope(bs);

		ConstraintFactory constraintFactory = new ConstraintFactory();

		ConstraintSet constraints = constraintFactory.createSet();

		ACQ_Network tn = new ACQ_Network(constraintFactory, allVarSet, constraints);

		for (ArrayList<String> cst : exp.getTN())
			tn.add(ACQ_Constraint.CstrFactory.getConstraint(cst), true);
		return tn;
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
	public ArrayList<ACQ_Network> createStrategy(ACQ_Bias bias) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SATSolver createSATSolver() {
		return new MiniSatSolver();

	}

	@Override
	public ContradictionSet createBackgroundKnowledge(ACQ_Bias bias, ConstraintMapping mapping) {
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
		return examplesfile;
	}

	@Override
	public int getMaxRand() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMaxQueries() {
		return maxqueries;
	}


	@Override
	public ACQ_Network createInitNetwork() {
		int NB_VARIABLE = exp.getNbVars();
		// build All variables set
		BitSet bs = new BitSet();
		bs.set(0, NB_VARIABLE);

		ACQ_Scope allVarSet = new ACQ_Scope(bs);

		ConstraintFactory constraintFactory = new ConstraintFactory();

		ConstraintSet constraints = constraintFactory.createSet();

		ACQ_Network tn = new ACQ_Network(constraintFactory, allVarSet, constraints);

		for (ArrayList<String> cst : exp.getINIT())
			tn.add(ACQ_Constraint.CstrFactory.getConstraint(cst), true);
		return tn;

	}


}
