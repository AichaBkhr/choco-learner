package fr.lisn.lahdak.acquisition.core.learners;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_Network;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintFactory.ConstraintSet;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_ConstraintSolver;
import fr.lisn.lahdak.acquisition.core.workspace.IExperience;
import fr.lisn.lahdak.acquisition.expe.ExpeBuilder;

public class ACQ_BiasTest {

    private ACQ_Bias bias;
    private ACQ_Network network;
    private ACQ_Scope scope;
    private ConstraintSet constraints;
    private ACQ_ConstraintSolver solver;

    @Before
    public void setUp() {
        System.out.println("Setting up experience-based test for ACQ_Bias");

        try {
            ExpeBuilder builder = new ExpeBuilder();
            builder.setAlgo(ACQ_Algorithm.QUACQ); // Use QUACQ algorithm
            builder.setFile("golomb_4"); // Example experience file
            // builder.setVarSelector("defaultVarSelector");
            // builder.setValSelector("defaultValSelector");

            IExperience experience = builder.build();
            System.out.println("Experience built successfully");

            bias = experience.createBias();
            solver = experience.createSolver();
            network = bias.getNetwork();
            scope = bias.getVars();
            constraints = network.getConstraints();

            //System.out.println("Bias initialized: " + bias);
            System.out.println("Scope initialized: " + scope);
            System.out.println("Constraints initialized: " + constraints);

        } catch (Exception e) {
            e.printStackTrace();
            fail("Failed to set up the experience: " + e.getMessage());
        }
    }

    @Test
    public void testBiasFromExperience() {
        assertNotNull("Bias should not be null", bias);
        assertNotNull("Network should not be null", network);
        assertNotNull("Scope should not be null", scope);
        assertNotNull("Constraints should not be null", constraints);

        System.out.println("Bias size: " + bias.getSize());
        System.out.println("Scope: " + scope);
        System.out.println("Constraints: " + constraints);

        assertTrue("Scope should not be empty", scope.iterator().hasNext());
        assertFalse("Constraints should not be empty", constraints.isEmpty());
    }

    @Test
    public void testProjectionFromExperience() {
        ACQ_Network projectedNetwork = bias.getProjection(scope);
        assertNotNull("Projected network should not be null", projectedNetwork);
        assertTrue("Projected network should contain relevant constraints", projectedNetwork.getConstraints().size() > 0);

        System.out.println("Projected network: " + projectedNetwork);
    }

    @Test
    public void testExactProjectionFromExperience() {
        ACQ_Network exactProjectedNetwork = bias.getExactProjection(scope);
        assertNotNull("Exact projected network should not be null", exactProjectedNetwork);
        assertTrue("Exact projected network should contain relevant constraints", exactProjectedNetwork.getConstraints().size() > 0);

        System.out.println("Exact projected network: " + exactProjectedNetwork);
    }
}