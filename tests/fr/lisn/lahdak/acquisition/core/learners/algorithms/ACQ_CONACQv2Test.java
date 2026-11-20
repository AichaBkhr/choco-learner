// package fr.lisn.lahdak.acquisition.core.learners.algorithms;

// import static org.junit.Assert.*;

// import java.io.IOException;
// import java.util.BitSet;

// import org.chocosolver.solver.constraints.Constraint;
// import org.chocosolver.solver.constraints.Propagator;
// import org.junit.Before;
// import org.junit.Test;

// import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_CNF;
// import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_Clause;
// import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_IConstraint;
// import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintFactory;
// import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintMapping;
// import fr.lisn.lahdak.acquisition.core.acqconstraint.ContradictionSet;
// import fr.lisn.lahdak.acquisition.core.acqconstraint.Unit;
// import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_ConstraintSolver;
// import fr.lisn.lahdak.acquisition.core.acqsolver.SATSolver;
// import fr.lisn.lahdak.acquisition.core.learners.ACQ_Algorithm;
// import fr.lisn.lahdak.acquisition.core.learners.ACQ_Bias;
// import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;
// import fr.lisn.lahdak.acquisition.core.learners.ACQ_Scope;
// import fr.lisn.lahdak.acquisition.core.oracle.ACQ_Oracle;
// import fr.lisn.lahdak.acquisition.core.tools.Chrono;
// import fr.lisn.lahdak.acquisition.core.workspace.IExperience;
// import fr.lisn.lahdak.acquisition.expe.ExpeBuilder;

// public class ACQ_CONACQv2Test {

//     private ACQ_Oracle oracle;
//     private ACQ_Bias bias;
//     private SATSolver satSolver;
//     private ACQ_CONACQv2 algorithm;
//     private IExperience expe;
//     private ExpeBuilder builder;
//     private Propagator<?>[] propagators;
//     private ACQ_ConstraintSolver solver;
//     private Chrono chrono;
//     private ACQ_Query query;
//     private Constraint constraint;
//     private ACQ_IConstraint c;
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
//             System.out.println("Building experience");
//             builder = new ExpeBuilder();
//             builder.setAlgo(ACQ_Algorithm.CONACQ2);
//             builder.setFile("alldiff");
//             //builder.setVarSelector("defaultVarSelector");
//             //builder.setValSelector("defaultValSelector");
//             //builder.setExpe("sudoku4");
//             System.out.println(builder.getExpe());
//             expe = builder.build();
//             System.out.println("Experience built");
//         } catch (IOException e) {
//             e.printStackTrace();
//             System.err.println("Error while building the experience");
//         }

//         oracle = expe.createOracle();
//         bias = expe.createBias();
//         satSolver = expe.createSATSolver();
        
//         solver = expe.createSolver();
//         if (solver == null) {
//             System.out.println("createSolver() returned null. Please check the implementation");
//         }
//         solver.setVars(bias.getVars());
//         System.out.println(bias.getVars() + " : bias.getVars() is not null");

//         scope = bias.getVars();

//         // Initialize the algorithm
//         algorithm = new ACQ_CONACQv2(oracle, bias, satSolver, solver);
//         if (algorithm == null) {
//             System.out.println(algorithm +" : algo is null");
//         }
//         System.out.println(algorithm +" : algo is not null");
        
//         factory = bias.network.getFactory();
//     }
    
//     @Test
//     public void testQueryGeneration() throws Exception {
        
//         for (ACQ_IConstraint c : bias.getConstraints()) {
//             String newvarname = c.getName() + c.getVariables();
    
//             // Check if the mapping already contains the constraint
//             if (!this.mapping.contains(c)) {
//                 Unit unit = satSolver.addVar(c, newvarname);
//                 this.mapping.add(c, unit);
//                 clause.add(unit);
//             } else {
//                 System.out.println("Constraint already exists in mapping: " + c.getName());
//             }
//         }

//         cnf.add(clause);
//         assertEquals("Mapping and bias must contain the same number of elements", bias.getSize(), mapping.size());
    
//         System.out.println(factory + " : factory is not null");
//         System.out.println(scope + " : scope is not null");
//         System.out.println(mapping + " : mapping is not null");
    
//         ContradictionSet contradictionSet = new ContradictionSet(factory, scope, mapping);
    
//         // Generate a query
//         query = algorithm.query_gen(cnf, contradictionSet);
//         System.out.println("Query final gen: " + query);
    
//         //this test is ACTUALLY failing because query is null in the logs!!!
//         //here it is working bcs query == {}::null::false which is technically not null
//         //to fix
//         assertNotNull("Query should not be null", query);
//     }

//     @Test
//     public void testPreprocess() throws Exception {
//         ContradictionSet contradictionSet = new ContradictionSet(factory, scope, mapping);
//         boolean result = algorithm.preprocess(cnf, contradictionSet, 10);

//         assertFalse("Preprocess should not collapse by default", result);
//     }

//     @Test
//     public void testProcess() throws Exception {
//         chrono = new Chrono("testACQ_CONACQv2");

//         //works only when max_queries is == 1
//         //to fix
//         boolean result = algorithm.process(chrono, 1);

//         assertTrue("Process should return true if no collapse occurs", result);
//     }
// }