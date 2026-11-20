package fr.lisn.lahdak.acquisition.expe_conacq;

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
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_Heuristic;
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

public class ExpeConacq_SUDOKUHybrid extends DefaultExperience {

	public ExpeConacq_SUDOKUHybrid() {
		
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
		},VarSelector.DomOverWDeg.toString(),ValSelector.IntDomainBest.toString());
	}
	@Override
	public void setDisj(boolean unable_disj){this.disj =unable_disj; }
	public ACQ_Oracle createOracle() {
		return new ACQ_Oracle() {
			@Override
			public Answer ask(ACQ_Query e) {

				int s[][]= new int[9][9];	
				int l,c;

				for(int numvar:e.getScope())
				{
					l=numvar/9; 						
					c=numvar%9;
					s[l][c]=e.getValue(numvar);

				}

				// row checker
				for(int row = 0; row < 9; row++)
					for(int col = 0; col < 8; col++)
						for(int col3 = col + 1; col3 < 9; col3++)
							if(s[row][col]!=0 && s[row][col]==s[row][col3])
							{
								e.classify(Answer.NO);
								return Answer.NO;
							}

				// column checker
				for(int col = 0; col < 9; col++)
					for(int row = 0; row < 8; row++)
						for(int row3 = row + 1; row3 < 9; row3++)
							if( s[row][col]!=0 &&  s[row][col]==s[row3][col])
							{
								e.classify(Answer.NO);
								return Answer.NO;
							}

				// grid checker
				for(int row = 0; row < 9; row += 3)
					for(int col = 0; col < 9; col += 3)
						// row, col is start of the 8 by 8 grid
						for(int pos = 0; pos < 8; pos++)
							for(int pos3 = pos + 1; pos3 < 9; pos3++)
								if(s[row + pos%3][col + pos/3]!=0 &&  s[row + pos%3][col + pos/3]==s[row + pos3%3][col + pos3/3])
								{
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
		ConstraintFactory constraintFactory=new ConstraintFactory();

		ConstraintSet constraints = constraintFactory.createSet();
		CombinationIterator iterator = new CombinationIterator(NB_VARIABLE, 3);
		while (iterator.hasNext()) {
			int[] vars = iterator.next();
			AllPermutationIterator pIterator = new AllPermutationIterator(3);
			while (pIterator.hasNext()) {
				int[] pos = pIterator.next();

				if(vars[pos[0]]< vars[pos[1]])		//NL: commutative relations
				{
					// X != Y
					constraints.add(new BinaryArithmetic("DifferentXY", vars[pos[0]], Operator.NEQ, vars[pos[1]],"EqualXY"));
					// X == Y
					constraints.add(new BinaryArithmetic("EqualXY", vars[pos[0]], Operator.EQ, vars[pos[1]], "DifferentXY"));
				}
				
				
				// X >= Y
				constraints.add(new BinaryArithmetic("GreaterEqualXY", vars[pos[0]], Operator.GE, vars[pos[1]], "LessXY"));
				// X >= Y
				constraints.add(new BinaryArithmetic("LessEqualXY", vars[pos[0]], Operator.LE, vars[pos[1]], "GreaterXY"));
			
				// X < Y
				constraints.add(new BinaryArithmetic("LessXY", vars[pos[0]], Operator.LT, vars[pos[1]], "GreaterEqualXY"));
				// X > Y
				constraints.add(new BinaryArithmetic("GreaterXY", vars[pos[0]], Operator.GT, vars[pos[1]], "LessEqualXY"));}
				
			
		}
		ACQ_Network network = new ACQ_Network(constraintFactory,allVarSet, constraints);
		return new ACQ_Bias(network);
	}
 
	
	public static void main(String[] args) {
		new ExpeConacq_SUDOKUHybrid().process();
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
		//return new Z8SATSolver();
		return new MiniSatSolver();
	}

	@Override
	public ACQ_Network createTargetNetwork() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void process() {
		ExpeConacq_SUDOKUHybrid expe;
		try {
			expe = new ExpeConacq_SUDOKUHybrid();
			expe.setParams(true, // normalized csp
					true, // shuffle_split,
					60000, // timeout
					ACQ_Heuristic.SOL, 
				 true,true
			);
			expe.setLog_queries(true);
			ACQ_WS.executeHybridModeExperience(expe);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}		
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

