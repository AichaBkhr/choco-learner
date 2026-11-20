// package fr.lisn.lahdak.acquisition.core.learners.algorithms;

// import static org.junit.Assert.*;

// import java.io.IOException;
// import java.util.BitSet;

// import org.chocosolver.solver.constraints.Constraint;
// import org.chocosolver.solver.constraints.Propagator;
// import org.junit.Before;
// import org.junit.Test;

// import fr.lisn.lahdak.acquisition.core.acqconstraint.*;
// import fr.lisn.lahdak.acquisition.core.acqsolver.*;
// import fr.lisn.lahdak.acquisition.core.learners.*;
// import fr.lisn.lahdak.acquisition.core.oracle.*;
// import fr.lisn.lahdak.acquisition.core.tools.Chrono;
// import fr.lisn.lahdak.acquisition.core.workspace.IExperience;
// import fr.lisn.lahdak.acquisition.expe.ExpeBuilder;

// public class ACQ_CONACQv1Test {

//     private ACQ_Oracle oracle;
//     private ACQ_Bias bias;
//     private SATSolver satSolver;
//     private ACQ_ConstraintSolver constraintSolver;
//     private ACQ_CONACQv1 algorithm;
//     private IExperience expe;
//     private ExpeBuilder builder;
//     private Propagator<?>[] propagators;
//     private ACQ_ConstraintSolver solver;
//     private Constraint constraint;
//     private ACQ_CNF cnf = new ACQ_CNF();
//     //private ArrayList<Object> runnable=new ArrayList<>();
//     private ConstraintMapping mapping = new ConstraintMapping();
//     private ACQ_Clause clause = new ACQ_Clause();
//     private ACQ_Scope scope = new ACQ_Scope(new BitSet());
//     private ConstraintFactory factory;  

    
//     @Before
//     public void setUp() {
//         // Initialize the oracle
//         try {

//             // to be able to continue this test we have to create valid .init file for the tested experience
//             // they already have been created but are empty for now
//             // to do later
//             System.out.println("Building experience");
//             builder = new ExpeBuilder();
//             builder.setAlgo(ACQ_Algorithm.CONACQ1);
//             builder.setFile("instance00"); //.bias, .data, .target (.data is not .init so it doesn't work)
//             //builder.setExpe("zebra");
//             System.out.println(builder.getExpe());
//             expe = builder.build();
//             System.out.println("Experience built");
//             //System.out.println("Generated example file : " + expe.getExamplesfile());
//             //System.out.println("Max queries : " + expe.getMaxQueries());
//         } catch (IOException e) {
//             e.printStackTrace();
//             System.err.println("Error while building the experience");
//         }

//         oracle = expe.createOracle();
//         System.out.println(oracle +" : oracle");
//         bias = expe.createBias();
//         System.out.println(bias +" : bias");
//         satSolver = expe.createSATSolver();
//         System.out.println(satSolver +" : satSolver");

//         solver = expe.createSolver();
//         if (solver == null) {
//             System.out.println("createSolver() returned null. Please check the implementation");
//         }
//         solver.setVars(bias.getVars());
//         System.out.println("\nsolver is not null");

//         // Initialize the algorithm
//         algorithm = new ACQ_CONACQv1(oracle, bias, satSolver, constraintSolver, expe.getDataFile());
//         if (algorithm == null) {
//             System.out.println(algorithm +" : algo is null");
//         }
//         System.out.println(algorithm +" : algo is not null");
        
//         factory = bias.network.getFactory();
//     }
    
//         @Test
//         public void testQueryGeneration() throws Exception {
//         propagators = new Propagator[1];
//         constraint = new Constraint("test1", propagators);
//         constraint.post();
        
//         for (ACQ_IConstraint c : bias.getConstraints()) {
//             String newvarname = c.getName() + c.getVariables();
//             Unit unit = satSolver.addVar(c, newvarname);
//             this.mapping.add(c, unit);
//             clause.add(unit);
//         }
//         cnf.add(clause);
//         assert mapping.size() == bias.getSize(); //mapping and bias must contain the same number of elements
       
//         System.out.println(factory +" : factory is not null");
//         System.out.println(scope +" : scope is not null");
//         System.out.println(mapping +" : mapping is not null");

//         ContradictionSet contradictionSet = new ContradictionSet(factory, scope, mapping);

//         // Generate a query
//         ACQ_Query query = algorithm.query_gen(cnf, contradictionSet);

//         // Verify the query is not null
//         assertNotNull("Query should not be null", query);
//     }

//     @Test
//     public void testPreprocess() throws Exception {
//         //ACQ_CNF cnf = new ACQ_CNF();
//         ContradictionSet contradictionSet = new ContradictionSet(factory, scope, mapping);
//         boolean result = algorithm.preprocess(cnf, contradictionSet, 10);

//         assertFalse("Preprocess should not collapse by default", result);
//     }

//     @Test
//     public void testProcess() throws Exception {
//         Chrono chrono = new Chrono("testACQ_CONACQv1");
//         boolean result = algorithm.process(chrono, 10);

//         assertTrue("Process should return true if no collapse occurs", result);
//     }
// }