/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.lisn.lahdak.acquisition.expe;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Scanner;

import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_Network;
import fr.lisn.lahdak.acquisition.core.acqconstraint.BinaryArithmetic;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintFactory;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintMapping;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ContradictionSet;
import fr.lisn.lahdak.acquisition.core.acqconstraint.Operator;
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

/**
 * AllDiff experiment
 * @author NADJIB
 */
public class ExpeTOY extends DefaultExperience{

	boolean auto_learn;
	private int nb_vars=4;
	
	public ExpeTOY(boolean auto_learn) {
		this.auto_learn=auto_learn;
	}

	public ACQ_ConstraintSolver createSolver() {
		return new ACQ_ChocoSolver(new ACQ_IDomain() {
			@Override
			public int getMin(int numvar) {
				return 1;
			}

			@Override
			public int getMax(int numvar) {
				return nb_vars;
			}
		},VarSelector.DomOverWDeg.toString(),ValSelector.IntDomainRandom.toString());
	}

	@Override
	public void setDisj(boolean unable_disj){this.disj =unable_disj; }

	public ACQ_Oracle createOracle() {
		return new ACQ_Oracle() {
			@Override
			public Answer ask(ACQ_Query e) {
				if(auto_learn) {
					int[] tuple = e.getTuple();  				
					for(int i=0; i<tuple.length-1; i++)
						for(int j=i+1; j<tuple.length; j++)
							if(tuple[i]==tuple[j]) {
								e.classify(Answer.NO);
								return Answer.NO;
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
		// génère tous les couples de deux variables parmi NB_VARIABLE
		CombinationIterator iterator = new CombinationIterator(NB_VARIABLE, 2);
		// tant qu'il reste des couples
		while (iterator.hasNext()) {
			// assignation du couple
			int[] vars = iterator.next();
			// génère les permutations entre deux variables
			AllPermutationIterator pIterator = new AllPermutationIterator(2);
			// tant qu'il reste des permutations
			while (pIterator.hasNext()) {
				// assignation de la permutation
				int[] pos = pIterator.next();
				if(vars[pos[0]]< vars[pos[1]])		//NL: commutative relations
				{
					// création de la contrainte X != Y
					constraints.add(new BinaryArithmetic("DifferentXY", vars[pos[0]], Operator.NEQ, vars[pos[1]], "EqualXY"));
					// création de la contrainte X == Y
					constraints.add(new BinaryArithmetic("EqualXY", vars[pos[0]], Operator.EQ, vars[pos[1]], "DifferentXY"));
				}
				// X >= Y
			//	constraints.add(new BinaryArithmetic("GreaterEqualXY", vars[pos[0]], Operator.GE, vars[pos[1]]));
			}
		}
		ACQ_Network network = new ACQ_Network(constraintFactory,allVarSet, constraints);
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

	
	@Override
	public ACQ_Network createTargetNetwork() {
		// TODO Auto-generated method stub
		return null;
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
