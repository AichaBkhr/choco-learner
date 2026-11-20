package fr.lisn.lahdak.acquisition.expe;

import java.util.ArrayList;

import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_Network;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintMapping;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ContradictionSet;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_ConstraintSolver;
import fr.lisn.lahdak.acquisition.core.acqsolver.MiniSatSolver;
import fr.lisn.lahdak.acquisition.core.acqsolver.SATSolver;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Bias;
import fr.lisn.lahdak.acquisition.core.oracle.ACQ_Oracle;
import fr.lisn.lahdak.acquisition.core.workspace.DefaultExperience;

public class ExpeCustomized extends DefaultExperience {

	@Override
	public ACQ_Bias createBias() {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public void setDisj(boolean unable_disj){this.disj =unable_disj; }

	@Override
	public ACQ_Oracle createOracle() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ACQ_ConstraintSolver createSolver() {
		// TODO Auto-generated method stub
		return null;
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
	public void process() {
		// TODO Auto-generated method stub
		
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
