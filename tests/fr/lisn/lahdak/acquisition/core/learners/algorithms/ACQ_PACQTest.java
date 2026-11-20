// package fr.lisn.lahdak.acquisition.core.learners.algorithms;

// import org.junit.Before;
// import org.junit.Test;
// import static org.junit.Assert.*;

// import java.util.concurrent.CopyOnWriteArraySet;

// import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_ConstraintSolver;
// import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_Heuristic;
// import fr.lisn.lahdak.acquisition.core.learners.ACQ_Algorithm;
// import fr.lisn.lahdak.acquisition.core.learners.ACQ_Bias;
// import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;
// import fr.lisn.lahdak.acquisition.core.learners.ACQ_Scope;
// import fr.lisn.lahdak.acquisition.core.oracle.ACQ_Oracle;
// import fr.lisn.lahdak.acquisition.core.parallel.ACQ_PACQ_Manager;
// import fr.lisn.lahdak.acquisition.core.parallel.ACQ_QueryMessage;
// import fr.lisn.lahdak.acquisition.core.parallel.ObservedProtfolioManager;
// import fr.lisn.lahdak.acquisition.core.tools.Chrono;
// import fr.lisn.lahdak.acquisition.expe.ExpeBuilder;
// import fr.lisn.lahdak.acquisition.core.workspace.IExperience;

// public class ACQ_PACQTest {

//     private ACQ_PACQ pacq;
//     private ACQ_ConstraintSolver solver;
//     private ACQ_Bias bias;
//     private ACQ_Oracle oracle;
//     private IExperience experience;
//     private ACQ_PACQ_Manager pacqManager;
//     private ACQ_QueryMessage qm;
//     private ACQ_Query query;
//     private ACQ_Scope scope;
//     private int[] values;
//     private ObservedProtfolioManager observedPortfolio;
//     private CopyOnWriteArraySet<ACQ_QueryMessage> queriesMailbox;
//     private Chrono chrono;

//     @Before
//     public void setUp() {
//         System.out.println("Setting up ACQ_PACQTest");

//         try {
//             // Build the experience
//             ExpeBuilder builder = new ExpeBuilder();
//             builder.setAlgo(ACQ_Algorithm.PACQ);
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

//         scope = bias.getVars();
//         values = new int[scope.size()];
//         for (int i = 0; i < scope.size(); i++) {
//             values[i] = i * 10; 
//         }
//         queriesMailbox = new CopyOnWriteArraySet<>();
        
//         // queriesMailbox.add(qm);
//         // query = new ACQ_Query(scope, values);
//         // qm = new ACQ_QueryMessage("testSender", query);
//         pacqManager = new ACQ_PACQ_Manager(queriesMailbox);
//         observedPortfolio = new ObservedProtfolioManager(queriesMailbox);

//         // Initialize the ACQ_PACQ instance
//         pacq = new ACQ_PACQ(solver, bias, oracle, pacqManager, observedPortfolio, ACQ_Heuristic.SOL, queriesMailbox);
//         assertNotNull("PACQ instance should not be null", pacq);

//         chrono = new Chrono("ACQ_PACQTest");
//     }

//     @Test
//     public void testProcess() {
//         System.out.println("Running testProcess");
//         chrono = new Chrono("testPACQ");
//         boolean result = pacq.process(chrono);

//         System.out.println("Process result: " + result);
//         assertTrue("Process should return true for a valid configuration", result);
//     }

//     @Test
//     public void testBiasInitialization() {
//         System.out.println("Running testBiasInitialization");
//         System.out.println(bias + " should not be null");

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

//     @Test
//     public void testQueryMailbox() {
//         System.out.println("Running testQueryMailbox");
//         System.out.println(queriesMailbox + " : queryMb");

//         assertNotNull("Query mailbox should not be null", queriesMailbox);
//         assertTrue("Query mailbox should be empty initially", queriesMailbox.isEmpty());
//         System.out.println("Query mailbox initialized successfully");
//     }
// }