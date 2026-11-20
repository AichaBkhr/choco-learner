// package fr.lisn.lahdak.acquisition.core.learners.algorithms;

// import org.junit.Before;
// import org.junit.Test;
// import static org.junit.Assert.*;

// import java.util.BitSet;

// import fr.lisn.lahdak.acquisition.core.acqconstraint.*;
// import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintFactory.ConstraintSet;
// import fr.lisn.lahdak.acquisition.core.acqsolver.*;
// import fr.lisn.lahdak.acquisition.core.learners.*;
// import fr.lisn.lahdak.acquisition.core.oracle.*;
// import fr.lisn.lahdak.acquisition.core.tools.Chrono;
// import fr.lisn.lahdak.acquisition.expe.ExpeBuilder;
// import fr.lisn.lahdak.acquisition.core.workspace.IExperience;

// /**
//  * Unit tests for the PACQ algorithm.
//  */
// public class ACQ_QUACQTest {

//     private ACQ_QUACQ quacq;
//     private ACQ_ConstraintSolver solver;
//     private ACQ_Bias bias;
//     private ACQ_Oracle oracle;
//     private ACQ_Heuristic heuristic = ACQ_Heuristic.SOL;
//     private ACQ_Network network;
//     private ConstraintFactory factory;
//     private ConstraintSet constraints;
//     private BitSet bs = new BitSet();
//     private ACQ_Scope scope = new ACQ_Scope(bs);
//     private int[] values;
//     private IExperience experience;
//     private Chrono chrono = new Chrono("Test QUACQ");

//     /**
//      * Sets up the test environment by initializing the PACQ algorithm.
//      */
//     @Before
//     public void setUp() {
//         System.out.println("Setting up test");
//         try {
//             // Build the experience
//             ExpeBuilder builder = new ExpeBuilder();
//             builder.setAlgo(ACQ_Algorithm.QUACQ); 
//             builder.setFile("golomb_4");
//             builder.setValSelector("IntDomainMin");
//             builder.setVarSelector("ActivityBased");
//             experience = builder.build();
//             System.out.println("Experience built successfully");
//         } catch (Exception e) {
//             e.printStackTrace();
//             fail("Failed to build the experience: " + e.getMessage());
//         }

//         // Initialize components from the experience
//         bias = experience.createBias();
//         if (bias == null) {
//             fail("Bias is null. Check experience configuration.\n");
//         }
//         System.out.println("\nBias initialized: " + bias + "\n");

//         solver = experience.createSolver();
//         if (solver == null) {
//             fail("Solver is null. Check experience configuration.");
//         }
//         System.out.println("Solver initialized: " + solver);

//         oracle = experience.createOracle();
//         if (oracle == null) {
//             fail("Oracle is null. Check experience configuration.");
//         }
//         System.out.println("Oracle initialized: " + oracle);

//         scope = bias.getVars();
//         System.out.println("Scope initialized: " + scope);
//         // System.out.println(scope.iterator().hasNext()+ " not nulllllllll");

//         factory = bias.network.getFactory();
//         constraints = factory.createSet();
//         constraints.addAll(bias.network.getConstraints());
//         System.out.println("Constraints initialized: " + constraints);

//         network = new ACQ_Network(factory, scope, constraints);
//         System.out.println("Network initialized: " + network);

//         System.out.println(experience.getVrs() + " : experience.getVrs() is not null");
        
//         quacq = new ACQ_QUACQ(solver, bias, oracle, heuristic);
//         System.out.println("ACQ_QUACQ initialized: " + quacq + "\n\n\n\n");
//     }

//     /**
//      * Tests the `findC` method of the QUACQ algorithm.
//      * <p>
//      * This test verifies that the `findC` method correctly identifies a constraint
//      * for a given query. It performs the following steps:
//      * <ul>
//      *   <li>Initializes an array of values corresponding to the scope size.</li>
//      *   <li>Creates a query using the scope and values.</li>
//      *   <li>Calls the `findC` method to find a constraint for the query.</li>
//      * </ul>
//      * The test should asserts that the returned constraint is not null.
//      */
//     @Test
//     public void testFindC() {
//         System.out.println("Running testFindC");

//         values = new int[scope.size()];
//         for (int i = 0; i < scope.size(); i++) {
//             values[i] = i * 10; 
//         }

//         System.out.println(scope + " scope is not null");  
//         System.out.println(values + " values is not null");
//         ACQ_Query query = new ACQ_Query(scope, values); 
//         System.out.println(query + " query is not null");   
        
//         // problem caused by something in findC that makes this.vars null
//         ACQ_IConstraint constraint = quacq.findC(scope, query, true);

//         //aucune contrainte n'est trouvée sur la query {0, 1, 2, 3}::[0, 1, 2, 3]::false
//         System.out.println("Constraint found: " + constraint + "\n\n\n\n"); 
//         assertNotNull("constraint shouldn't be null", constraint);
//     }

//     /**
//      * Tests the query generation functionality of the QUACQ algorithm.
//      * <p>
//      * This test verifies that the query generation process works correctly by:
//      * <ul>
//      *   <li>Initializing two networks with the same scope and constraints.</li>
//      *   <li>Ensuring that the networks are not empty.</li>
//      *   <li>Generating a query using the `query_gen` method of the QUACQ algorithm.</li>
//      * </ul>
//      * The test should asserts that the generated query is not null.
//      */
//     @Test
//     public void testQueryGen() {
//         System.out.println("Running testQueryGen");

//         System.out.println(factory + " factory is not null");
//         System.out.println(scope + " scope is not null");
//         System.out.println(constraints+ " constraint is not null");

//         ACQ_Network network1 = new ACQ_Network(factory, scope, constraints);
//         //ACQ_Network network2 = new ACQ_Network(factory, scope, constraints);
        
//         ConstraintSet constraintsForNetwork2 = factory.createSet();
//         constraintsForNetwork2.addAll(constraints);
//         ACQ_Network network2 = new ACQ_Network(factory, scope, constraintsForNetwork2);
        
//         if (network1.isEmpty() || network2.isEmpty()) {
//             fail("Networks are null");
//         }else{
//             System.out.println("Networks initialized: " + network1 + " and " + network2);
//         }
//         System.out.println("Scope in query_gen: " + scope);
//         System.out.println("Scope vars: " + scope.iterator().hasNext());
//         System.out.println("Calling solve_AnotB...");
//         //System.out.println("solve_AnotB result: " + solver.solve_AnotB(network1, network2, true, heuristic));

//         // problem caused by something in query_gen that makes this.vars null
//         ACQ_Query query = quacq.query_gen(network1, network2, scope, Query_type.findc2, heuristic);

//         System.out.println("Generated query: " + query + "\n\n\n\n");
//         assertNotNull("Generated query should not be null", query);
//     }

//     /**
//      * Tests the process method of the PACQ algorithm.
//      */
//     @Test
//     public void testProcess() {
//         System.out.println("Running testProcess");

//         chrono = new Chrono("testACQ_QUACQ");
//         boolean result = quacq.process(chrono);

//         System.out.println("Process result: " + result + "\n\n\n\n");
//         assertTrue("Process should return true for a valid configuration", result);
//     }
// }