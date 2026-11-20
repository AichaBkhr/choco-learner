package fr.lisn.lahdak.acquisition.expe;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Scanner;

import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_IConstraint;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_Network;
import fr.lisn.lahdak.acquisition.core.acqconstraint.BinaryArithmetic;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintFactory;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintMapping;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ContradictionSet;
import fr.lisn.lahdak.acquisition.core.acqconstraint.Operator;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ScalarArithmetic;
import fr.lisn.lahdak.acquisition.core.acqconstraint.UnaryArithmetic;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintFactory.ConstraintSet;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_ChocoSolver;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_ConstraintSolver;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_Heuristic;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_IDomain;
import fr.lisn.lahdak.acquisition.core.acqsolver.MiniSatSolver;
import fr.lisn.lahdak.acquisition.core.acqsolver.SATSolver;
import fr.lisn.lahdak.acquisition.core.acqsolver.ValSelector;
import fr.lisn.lahdak.acquisition.core.acqsolver.VarSelector;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Bias;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Scope;
import fr.lisn.lahdak.acquisition.core.oracle.ACQ_Oracle;
import fr.lisn.lahdak.acquisition.core.oracle.Answer;
import fr.lisn.lahdak.acquisition.core.workspace.ACQ_WS;
import fr.lisn.lahdak.acquisition.core.workspace.DefaultExperience;

/**
 * AllEqual
 * 
 * @author teddy
 *
 */
public class ExpeTOY3 extends DefaultExperience {

	boolean auto_learn;
	private static int[] problem;
	private static ArrayList<ACQ_IConstraint> checkerconstraints = new ArrayList<ACQ_IConstraint>();
	private static ArrayList<ACQ_IConstraint> biasconstraints = new ArrayList<ACQ_IConstraint>();

	public ExpeTOY3(boolean auto_learn) {

	}

	public ACQ_ConstraintSolver createSolver() {
		return new ACQ_ChocoSolver(new ACQ_IDomain() {
			@Override
			public int getMin(int numvar) {
				return 1;
			}

			@Override
			public int getMax(int numvar) {
				return problem[1];
			}
		}, VarSelector.DomOverWDeg.toString(), ValSelector.IntDomainBest.toString());
	}

	@Override
	public void setDisj(boolean unable_disj){this.disj =unable_disj; }

	public ACQ_Oracle createOracle() {
		return new ACQ_Oracle() {
			@Override
			public Answer ask(ACQ_Query e) {
				/*	int[] tuple = new int[e.scope.getVariables().length()]; 

				for(int numvar:e.getScope())
				{

					tuple[numvar]=e.getValue(numvar);

				}

				
				for(int i=0; i<tuple.length-1; i++)
					for(int j=i+1; j<tuple.length; j++)
						if(tuple[i]>0&&tuple[j]>0&&tuple[i]==tuple[j]) {
							return false;
						}
				return true;
*/
				boolean zero=false;
				for (ACQ_IConstraint c : checkerconstraints) {
					if(e.getTuple().length==c.getVariables().length&&e.getScope().containsAll(c.getScope()))
					{
						
					int[] vars = e.getProjection(c.getScope());
					for(int i :vars)
						if(i==0)
							zero=true;
					
						if (c.checker(vars)&&!zero) {
							return Answer.YES;
						}
				}else if(e.getTuple().length>c.getVariables().length&&e.getScope().containsAll(c.getScope())){
					int[] vars = e.getProjection(c.getScope());
					for(int i :vars)
						if(i==0)
							zero=true;
					
						if (!c.checker(vars)&&!zero) {
							return Answer.NO;
						}
				}

				}
					//System.out.println("values :: " + e.toString());
				
				return Answer.YES;

			}

		};
	}

	public ACQ_Bias createBias() {
		int NB_VARIABLE = problem[0];
		// build All variables set
		BitSet bs = new BitSet();
		bs.set(0, NB_VARIABLE);
		ACQ_Scope allVarSet = new ACQ_Scope(bs);
		// build Constraints
		ConstraintFactory constraintFactory = new ConstraintFactory();

		ConstraintSet constraints = constraintFactory.createSet();
		for (ACQ_IConstraint c : biasconstraints)
			constraints.add(c);
		ACQ_Network network = new ACQ_Network(constraintFactory, allVarSet, constraints);
		return new ACQ_Bias(network);
	}

	public static void main(String args[]) {
		String bench = "src/fr/lisn/lahdak/acquisition/bench/Alldiff/benchmark_alldiff.dat";
		String ct = "src/fr/lisn/lahdak/acquisition/bench/Alldiff/CT_alldiff.dat";

		problem = getVars(bench);
		checkerconstraints = getConstraints(ct);
		biasconstraints = getConstraints(bench);

		ExpeTOY3 expe = new ExpeTOY3(false);
		expe.setParams(false, true, 100000, ACQ_Heuristic.SOL, true, false);
		ACQ_WS.executeExperience(expe);

	}

	@SuppressWarnings("unused")
	private static ArrayList<ACQ_IConstraint> getConstraints(String ct) {
		ArrayList<ACQ_IConstraint> csts = new ArrayList<ACQ_IConstraint>();
		Scanner scanner;
		try {
			scanner = new Scanner(new File(ct));
			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();
				String name = line.split(" ")[0];
				String[] vars = Arrays.copyOfRange(line.split(" "), 1, line.split(" ").length);
				String negation = "";

				if(vars.length >0)
					negation=vars[vars.length - 1];
				switch (name) {
				case "EqualXY":
					csts.add(new BinaryArithmetic(name, Integer.parseInt(vars[0]), Operator.EQ,
							Integer.parseInt(vars[1]), "DifferentXY"));
					break; 

				case "DifferentXY":
					csts.add(new BinaryArithmetic(name, Integer.parseInt(vars[0]), Operator.NEQ,
							Integer.parseInt(vars[1]), "EqualXY"));
					break;
				case "GreaterXY":
					csts.add(new BinaryArithmetic(name, Integer.parseInt(vars[0]), Operator.GT,
							Integer.parseInt(vars[1]), "LessEqualXY"));
					break;

				case "LessXY":
					csts.add(new BinaryArithmetic(name, Integer.parseInt(vars[0]), Operator.LT,
							Integer.parseInt(vars[1]), "GreaterEqualXY"));
					break;

				case "GreaterEqualXY":
					csts.add(new BinaryArithmetic(name, Integer.parseInt(vars[0]), Operator.GE,
							Integer.parseInt(vars[1]), "LessXY"));
					break;

				case "LessEqualXY":
					csts.add(new BinaryArithmetic(name, Integer.parseInt(vars[0]), Operator.LE,
							Integer.parseInt(vars[1]), "GreaterXY"));
					break;

				case "EqualX_":
					csts.add(new UnaryArithmetic("DifferentX_" + vars[0] + vars[vars.length - 1],
							Integer.parseInt(vars[1]), Operator.EQ, Integer.parseInt(vars[1])));
					break;

				case "DifferentX_":
					csts.add(new UnaryArithmetic("DifferentX_" + vars[0] + vars[vars.length - 1],
							Integer.parseInt(vars[1]), Operator.NEQ, Integer.parseInt(vars[1])));
					break;

				case "LessX_":
					csts.add(new UnaryArithmetic("DifferentX_" + vars[0] + vars[vars.length - 1],
							Integer.parseInt(vars[1]), Operator.LT, Integer.parseInt(vars[1])));
					break;

				case "GreaterX_":
					csts.add(new UnaryArithmetic("DifferentX_" + vars[0] + vars[vars.length - 1],
							Integer.parseInt(vars[1]), Operator.GT, Integer.parseInt(vars[1])));
					break;

				case "LessEqualX_":
					csts.add(new UnaryArithmetic("DifferentX_" + vars[0] + vars[vars.length - 1],
							Integer.parseInt(vars[1]), Operator.LE, Integer.parseInt(vars[1])));
					break;

				case "GreaterEqualX_":
					csts.add(new UnaryArithmetic("DifferentX_" + vars[0] + vars[vars.length - 1],
							Integer.parseInt(vars[1]), Operator.GE, Integer.parseInt(vars[1])));
					break;

				case "DistDiffXY":
					csts.add(new BinaryArithmetic(name, Integer.parseInt(vars[0]), Operator.Dist,
							Integer.parseInt(vars[1]), Operator.NEQ, Integer.parseInt(vars[2]), "DistEqXY"));
					break;

				case "DistEqXY":
					csts.add(new BinaryArithmetic(name, Integer.parseInt(vars[0]), Operator.Dist,
							Integer.parseInt(vars[1]), Operator.EQ, Integer.parseInt(vars[2]), "DistDiffXY"));
					break;

				case "AT_Equal":
					csts.add(new BinaryArithmetic(name, Integer.parseInt(vars[0]), Operator.Dist,
							Integer.parseInt(vars[1]), Operator.EQ, Integer.parseInt(vars[2]), "AT_Diff"));
					break;
				case "AT_Diff":
					csts.add(new BinaryArithmetic(name, Integer.parseInt(vars[0]), Operator.Dist,
							Integer.parseInt(vars[1]), Operator.NEQ, Integer.parseInt(vars[2]), "AT_Equal"));
					break;
				case "AT_GT":
					csts.add(new BinaryArithmetic(name, Integer.parseInt(vars[0]), Operator.Dist,
							Integer.parseInt(vars[1]), Operator.GT, Integer.parseInt(vars[2]), "AT_LE"));
					break;
				case "AT_LT":
					csts.add(new BinaryArithmetic(name, Integer.parseInt(vars[0]), Operator.Dist,
							Integer.parseInt(vars[1]), Operator.LT, Integer.parseInt(vars[2]), "AT_GE"));
					break;
				case "AT_GE":
					csts.add(new BinaryArithmetic(name, Integer.parseInt(vars[0]), Operator.Dist,
							Integer.parseInt(vars[1]), Operator.GE, Integer.parseInt(vars[2]) - 1,
							"AT_LT"));
					break;
				case "AT_LE":
					csts.add(new BinaryArithmetic(name, Integer.parseInt(vars[0]), Operator.Dist,
							Integer.parseInt(vars[1]), Operator.LE, Integer.parseInt(vars[2]) + 1,
							"AT_GT"));
					break;
				case "DistDiffQ":
					csts.add(new ScalarArithmetic("DistDiff",
							new int[] { Integer.parseInt(vars[0]), Integer.parseInt(vars[1]), Integer.parseInt(vars[2]),
									Integer.parseInt(vars[3]) },
							new int[] { 1, -1, -1, 1 }, Operator.NEQ, 0, "DistEqual"));
					break;
				case "DistEqualQ":
					csts.add(new ScalarArithmetic("DistEqual",
							new int[] { Integer.parseInt(vars[0]), Integer.parseInt(vars[1]), Integer.parseInt(vars[2]),
									Integer.parseInt(vars[3]) },
							new int[] { 1, -1, -1, 1 }, Operator.EQ, 0, "DistDiff"));
					break;
				case "DistGreaterQ":
					csts.add(new ScalarArithmetic("DistGreater",
							new int[] { Integer.parseInt(vars[0]), Integer.parseInt(vars[1]), Integer.parseInt(vars[2]),
									Integer.parseInt(vars[3]) },
							new int[] { 1, -1, -1, 1 }, Operator.GT, 0, "DistLessEqual"));
					break;
				case "DistLessQ":
					csts.add(new ScalarArithmetic("DistLess",
							new int[] { Integer.parseInt(vars[0]), Integer.parseInt(vars[1]), Integer.parseInt(vars[2]),
									Integer.parseInt(vars[3]) },
							new int[] { 1, -1, -1, 1 }, Operator.LT, 0, "DistGreaterEqual"));
					break;
				case "DistGreaterEqualQ":
					csts.add(new ScalarArithmetic("DistGreaterEqual",
							new int[] { Integer.parseInt(vars[0]), Integer.parseInt(vars[1]), Integer.parseInt(vars[2]),
									Integer.parseInt(vars[3]) },
							new int[] { 1, -1, -1, 1 }, Operator.GE, 0, "DistLess"));
					break;
				case "DistLessEqualQ":
					csts.add(new ScalarArithmetic("DistLessEqual",
							new int[] { Integer.parseInt(vars[0]), Integer.parseInt(vars[1]), Integer.parseInt(vars[2]),
									Integer.parseInt(vars[3]) },
							new int[] { 1, -1, -1, 1 }, Operator.LE, 0, "DistGreater"));
					break;
				case "DistDiffT":
					csts.add(new ScalarArithmetic("DistDiff",
							new int[] { Integer.parseInt(vars[0]), Integer.parseInt(vars[1]),
									Integer.parseInt(vars[2]) },
							new int[] { 1, -2, 1 }, Operator.NEQ, 0, "DistEqual"));
					break;
				case "DistEqualT":
					csts.add(new ScalarArithmetic("DistDiff",
							new int[] { Integer.parseInt(vars[0]), Integer.parseInt(vars[1]),
									Integer.parseInt(vars[2]) },
							new int[] { 1, -2, 1 }, Operator.EQ, 0, "DistDiff"));
					break;
				case "DistGreaterT":
					csts.add(new ScalarArithmetic("DistGreater",
							new int[] { Integer.parseInt(vars[0]), Integer.parseInt(vars[1]),
									Integer.parseInt(vars[2]) },
							new int[] { 1, -2, 1 }, Operator.GT, 0, "DistLessEqual"));
					break;
				case "DistLessT":
					csts.add(new ScalarArithmetic("DistLess",
							new int[] { Integer.parseInt(vars[0]), Integer.parseInt(vars[1]),
									Integer.parseInt(vars[2]) },
							new int[] { 1, -2, 1 }, Operator.LT, 0, "DistGreaterEqual"));
					break;
				case "DistGreaterEqualT":
					csts.add(new ScalarArithmetic("DistGreaterEqual",
							new int[] { Integer.parseInt(vars[0]), Integer.parseInt(vars[1]),
									Integer.parseInt(vars[2]) },
							new int[] { 1, -2, 1 }, Operator.GE, 0, "DistLess"));
					break;
				case "DistLessEqualT":
					csts.add(new ScalarArithmetic("DistLessEqual",
							new int[] { Integer.parseInt(vars[0]), Integer.parseInt(vars[1]),
									Integer.parseInt(vars[2]) },
							new int[] { 1, -2, 1 }, Operator.LE, 0, "DistGreater"));
					break;

				}
			}
		} catch (FileNotFoundException e) {
			
			e.printStackTrace();
		}

		return csts;
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

	private static int[] getVars(String bench) {
		int[] problem = new int[2];
		Scanner scanner;
		try {
			scanner = new Scanner(new File(bench));
			int i = 0;
			while (scanner.hasNextLine() && i <= 1) {
				String line = scanner.nextLine();
				problem[i] = Integer.parseInt(line);
				i++;
				// process the line
			}
		} catch (FileNotFoundException e) {
			
			e.printStackTrace();
		}

		return problem;
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
		return this.createTargetNetwork();
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
