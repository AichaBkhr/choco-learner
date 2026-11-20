package fr.lisn.lahdak.acquisition.core.learners.generators;

import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_Network;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_ConstraintSolver;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_Heuristic;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;

public class findcTwoGenerator implements IQGenerator {


    private ACQ_ConstraintSolver solver;
    private ACQ_Network network1;
    private ACQ_Network network2;
    private ACQ_Heuristic h;

    public findcTwoGenerator(ACQ_ConstraintSolver s, ACQ_Network n1, ACQ_Network n2,
                             ACQ_Heuristic h) {
        this.solver = s;
        this.network1 = n1;
        this.network2 = n2;
        this.h = h;

    }

    @Override
    public ACQ_Query queryGen() {
        return solver.max_AnotB(network1, network2, h);
    }


}
