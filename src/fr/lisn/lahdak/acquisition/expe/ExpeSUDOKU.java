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
import fr.lisn.lahdak.acquisition.gui.GUI_Utils;

public class ExpeSUDOKU extends DefaultExperience {
	private static boolean gui = true;
	static boolean parallel = true;

	public ExpeSUDOKU() {
		setDimension(9);
	}

	public ACQ_ConstraintSolver createSolver() {
		return new ACQ_ChocoSolver(new ACQ_IDomain() {
			@Override
			public int getMin(int numvar) {
				return 1;
			}

			@Override
			public int getMax(int numvar) {
				return 9;
			}
		}, vrs, vls);

	}
	@Override
	public void setDisj(boolean unable_disj){this.disj =unable_disj; }

	public ACQ_Oracle createOracle() {
		return new ACQ_Oracle() {
			@Override
			public Answer ask(ACQ_Query e) {

				int s[][] = new int[9][9];
				int l, c;

				for (int numvar : e.getScope()) {
					l = numvar / 9;
					c = numvar % 9;
					s[l][c] = e.getValue(numvar);

				}

				// row checker
				for (int row = 0; row < 9; row++)
					for (int col = 0; col < 8; col++)
						for (int col2 = col + 1; col2 < 9; col2++)
							if (s[row][col] != 0 && s[row][col] == s[row][col2]) {
								e.classify(Answer.NO);
								return Answer.NO;
							}

				// column checker
				for (int col = 0; col < 9; col++)
					for (int row = 0; row < 8; row++)
						for (int row2 = row + 1; row2 < 9; row2++)
							if (s[row][col] != 0 && s[row][col] == s[row2][col]) {
								e.classify(Answer.NO);
								return Answer.NO;
							}

				// grid checker
				for (int row = 0; row < 9; row += 3)
					for (int col = 0; col < 9; col += 3)
						// row, col is start of the 3 by 3 grid
						for (int pos = 0; pos < 8; pos++)
							for (int pos2 = pos + 1; pos2 < 9; pos2++)
								if (s[row + pos % 3][col + pos / 3] != 0
										&& s[row + pos % 3][col + pos / 3] == s[row + pos2 % 3][col + pos2 / 3]) {
									e.classify(Answer.NO);
									return Answer.NO;
								}
				e.classify(Answer.YES);
				return Answer.YES;
			}
		};
	}

	public ACQ_Bias createBias() {
		int NB_VARIABLE = 81;
		// build All variables set
		BitSet bs = new BitSet();
		bs.set(0, NB_VARIABLE);
		ACQ_Scope allVarSet = new ACQ_Scope(bs);
		// build Constraints
		ConstraintFactory constraintFactory = new ConstraintFactory();

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

					// X >= Y
					constraints.add(
							new BinaryArithmetic("GreaterEqualXY", vars[pos[0]], Operator.GE, vars[pos[1]], "LessXY"));
					// X <= Y
					constraints.add(
							new BinaryArithmetic("LessEqualXY", vars[pos[0]], Operator.LE, vars[pos[1]], "GreaterXY"));

					// X > Y
					constraints.add(
							new BinaryArithmetic("GreaterXY", vars[pos[0]], Operator.GT, vars[pos[1]], "LessEqualXY"));
					// X < Y
					constraints.add(
							new BinaryArithmetic("LessXY", vars[pos[0]], Operator.LT, vars[pos[1]], "GreaterEqualXY"));
				}
			}
		}
		ACQ_Network network = new ACQ_Network(constraintFactory, allVarSet, constraints);
		return new ACQ_Bias(network);
	}

	@Override
	public void process() {
		if (gui) {
			GUI_Utils.executeCoop(this, this.getNb_threads());
		} else
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

	@SuppressWarnings("static-access")
	public void setGui(boolean gui) {
		this.gui = gui;
	}



	@Override
	public ACQ_Network createTargetNetwork() {
		int NB_VARIABLE = 81;
		// build All variables set
		BitSet bs = new BitSet();
		bs.set(0, NB_VARIABLE);
		ACQ_Scope allVarSet = new ACQ_Scope(bs);
		// build Constraints
		ConstraintFactory constraintFactory = new ConstraintFactory();

		ConstraintSet constraints = constraintFactory.createSet();

		int s[][] = new int[9][9];
		int l, c;

		for (int numvar : allVarSet) {
			l = numvar / 9;
			c = numvar % 9;
			s[l][c] = numvar;

		}

		// row constraints
		for (int row = 0; row < 9; row++)
			for (int col = 0; col < 8; col++)
				for (int col2 = col + 1; col2 < 9; col2++)
					constraints.add(
							new BinaryArithmetic("DifferentXY", s[row][col], Operator.NEQ, s[row][col2], "EqualXY"));

		// column constraints
		for (int col = 0; col < 9; col++)
			for (int row = 0; row < 8; row++)
				for (int row2 = row + 1; row2 < 9; row2++)
					constraints.add(
							new BinaryArithmetic("DifferentXY", s[row][col], Operator.NEQ, s[row2][col], "EqualXY"));

		// grid constraints
		for (int row = 0; row < 9; row += 3)
			for (int col = 0; col < 9; col += 3)
				// row, col is start of the 3 by 3 grid
				for (int pos = 0; pos < 8; pos++)
					for (int pos2 = pos + 1; pos2 < 9; pos2++)
						constraints.add(new BinaryArithmetic("DifferentXY", s[row + pos % 3][col + pos / 3],
								Operator.NEQ, s[row + pos2 % 3][col + pos2 / 3], "EqualXY"));

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
