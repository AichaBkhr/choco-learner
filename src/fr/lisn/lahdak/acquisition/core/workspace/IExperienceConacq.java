package fr.lisn.lahdak.acquisition.core.workspace;

import java.util.ArrayList;

import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_Network;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintMapping;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ContradictionSet;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_ConstraintSolver;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_Heuristic;
import fr.lisn.lahdak.acquisition.core.acqsolver.SATSolver;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Bias;
import fr.lisn.lahdak.acquisition.core.oracle.ACQ_Oracle;

public interface IExperienceConacq {

	public ACQ_Bias createBias();

	public ACQ_Oracle createOracle();

	public ArrayList<ACQ_Bias> createDistBias();

	public ACQ_Oracle createDistOracle(int id);

	public ACQ_Network createTargetNetwork();

	public ACQ_ConstraintSolver createSolver();

	public boolean isNormalizedCSP();

	public ACQ_Heuristic getHeuristic();

	public boolean isShuffleSplit();

	public boolean isAllDiffDetection();

	public int getDimension();

	public Long getTimeout();

	public String getVrs();

	public boolean isPuzzlePrint();

	public boolean isVerbose();

	public boolean isLog_queries();

	public void process();

	public boolean isQueens();

	public boolean convergenceCheck(ACQ_Network target_network, ACQ_Network learned_network);

	public int convergenceRate(ACQ_ConstraintSolver solver, ACQ_Network target_network, ACQ_Network learned_network);
public ArrayList<ACQ_Network> createStrategy(ACQ_Bias bias);
	
	public ContradictionSet createBackgroundKnowledge(ACQ_Bias bias, ConstraintMapping mapping);
	public SATSolver createSATSolver();


public boolean getJson();
public String getDataFile();

	public int getMaxRand();
	
	public int getMaxQueries();
	public String getName();

}
