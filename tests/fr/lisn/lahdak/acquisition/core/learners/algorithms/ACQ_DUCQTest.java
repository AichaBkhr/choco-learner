// package fr.lisn.lahdak.acquisition.core.learners.algorithms;

// import org.junit.Before;
// import org.junit.Test;
// import static org.junit.Assert.*;

// import java.util.concurrent.CopyOnWriteArraySet;

// import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintFactory.ConstraintSet;
// import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_ConstraintSolver;
// import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_Heuristic;
// import fr.lisn.lahdak.acquisition.core.learners.ACQ_Algorithm;
// import fr.lisn.lahdak.acquisition.core.learners.ACQ_Bias;
// import fr.lisn.lahdak.acquisition.core.oracle.ACQ_Oracle;
// import fr.lisn.lahdak.acquisition.core.parallel.ACQ_Coop_Manager;
// import fr.lisn.lahdak.acquisition.core.parallel.ACQ_Ducq_Manager;
// import fr.lisn.lahdak.acquisition.core.parallel.ACQ_QueryMessage;
// import fr.lisn.lahdak.acquisition.core.tools.Chrono;
// import fr.lisn.lahdak.acquisition.expe.ExpeBuilder;
// import fr.lisn.lahdak.acquisition.core.workspace.IExperience;

// public class ACQ_DUCQTest {

//     private ACQ_DUCQ ducq;
//     private ACQ_ConstraintSolver solver;
//     private ACQ_Bias bias;
//     private ACQ_Oracle oracle;
//     private IExperience experience;
//     private ACQ_Coop_Manager coopManager;
//     private CopyOnWriteArraySet<ACQ_QueryMessage> queriesMailbox;
//     private Chrono chrono;

//     @Before
//     public void setUp() {
//         System.out.println("Setting up ACQ_DUCQTest");

//         try {
//             // Build the experience
//             ExpeBuilder builder = new ExpeBuilder();
//             builder.setAlgo(ACQ_Algorithm.DUCQ);
//             builder.setFile("golomb_4"); // Replace with a valid file
//             // builder.setValSelector("IntDomainMin");
//             // builder.setVarSelector("ActivityBased");
//             experience = builder.build();
//             System.out.println("Experience built successfully");
//         } catch (Exception e) {
//             e.printStackTrace();
//             fail("Failed to build the experience: " + e.getMessage());
//         }

//         // Initialize components from the experience
//         bias = experience.createBias();
//         assertNotNull("Bias should not be null", bias);

//         solver = experience.createSolver();
//         assertNotNull("Solver should not be null", solver);

//         oracle = experience.createOracle();
//         assertNotNull("Oracle should not be null", oracle);

//         queriesMailbox = new CopyOnWriteArraySet<ACQ_QueryMessage>();
//         coopManager = new ACQ_Coop_Manager(queriesMailbox); 

//         // Initialize the ACQ_DUCQ instance
//         ducq = new ACQ_DUCQ(solver, bias, oracle, new ACQ_Ducq_Manager(queriesMailbox), ACQ_Heuristic.SOL, null);
//         assertNotNull("DUCQ instance should not be null", ducq);

//         chrono = new Chrono("ACQ_DUCQTest");
//     }

//     @Test
//     public void testProcess() {
//         System.out.println("Running testProcess");

//         boolean result = ducq.process(chrono);

//         System.out.println("Process result: " + result);
//         assertTrue("Process should return true for a valid configuration", result);
//     }

//     @Test
//     public void testBiasInitialization() {
//         System.out.println("Running testBiasInitialization");

//         assertNotNull("Bias should not be null", bias);
//         assertTrue("Bias should have constraints", bias.getConstraints().size() > 0);
//         System.out.println("Bias initialized with constraints: " + bias.getConstraints().size());
//     }

//     @Test
//     public void testSolverExecution() {
//         System.out.println("Running testSolverExecution");

//         boolean solved = solver.solve(bias.network);
//         assertTrue("Solver should solve the bias network", solved);
//         System.out.println("Solver executed successfully");
//     }
// }