// package fr.lisn.lahdak.acquisition.core.learners.algorithms;

// import org.junit.Before;
// import org.junit.Test;
// import static org.junit.Assert.*;

// import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_IConstraint;
// import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_Network;
// import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintFactory;
// import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintFactory.ConstraintSet;
// import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_ConstraintSolver;
// import fr.lisn.lahdak.acquisition.core.acqsolver.SATSolver;
// import fr.lisn.lahdak.acquisition.core.learners.ACQ_Algorithm;
// import fr.lisn.lahdak.acquisition.core.learners.ACQ_Bias;
// import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;
// import fr.lisn.lahdak.acquisition.core.learners.ACQ_Scope;
// import fr.lisn.lahdak.acquisition.core.learners.Query_type;
// import fr.lisn.lahdak.acquisition.core.oracle.ACQ_Oracle;
// import fr.lisn.lahdak.acquisition.core.parallel.ACQ_Coop_Manager;
// import fr.lisn.lahdak.acquisition.core.parallel.ACQ_QueryMessage;
// import fr.lisn.lahdak.acquisition.core.workspace.IExperience;
// import fr.lisn.lahdak.acquisition.expe.ExpeBuilder;
// import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_Heuristic;

// import java.io.IOException;
// import java.util.BitSet;
// import java.util.concurrent.CopyOnWriteArraySet;

// public class ACQ_COOPERATIONTest {

//     private ACQ_COOPERATION cooperation;
//     private ACQ_ConstraintSolver solver;
//     private ACQ_Oracle oracle;
//     private ExpeBuilder builder;
//     private IExperience expe;
//     private ACQ_Heuristic heuristic = ACQ_Heuristic.SOL;
//     private ACQ_Coop_Manager coopManager;
//     private SATSolver satSolver;
//     private BitSet bs = new BitSet();
//     private ACQ_Scope scope = new ACQ_Scope(bs);
//     private ACQ_Query query = new ACQ_Query();
//     private Query_type type;
//     private ACQ_Network network;
//     private ACQ_Network network1;
//     private ACQ_Network network2;
//     private CopyOnWriteArraySet<ACQ_QueryMessage> queriesMailbox;
//     private ACQ_Bias bias;// = new ACQ_Bias(network);
//     private ConstraintFactory factory;
//     private ConstraintSet constraints;

    
//     @Before
//     public void setUp() {
        
//         try {
//             System.out.println("Building experience");
//             builder = new ExpeBuilder();
//             builder.setAlgo(ACQ_Algorithm.COOP);
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

//         //circular dependency issue
//         //bias needs network to be not null and network needs bias to be not null
//         bias = new ACQ_Bias(network);
//         oracle = expe.createOracle();
//         bias = expe.createBias();
//         //satSolver = expe.createSATSolver();
//         solver = expe.createSolver();
        
//         if (solver == null) {
//             System.out.println("createSolver() returned null. Please check the implementation");
//         }
//         solver.setVars(bias.getVars());
//         System.out.println(bias.getVars() + " : bias.getVars() is not null");

//         scope = bias.getVars();
//         queriesMailbox = new CopyOnWriteArraySet<ACQ_QueryMessage>();
//         coopManager = new ACQ_Coop_Manager(queriesMailbox); 
//         factory = bias.network.getFactory();
//         constraints = factory.createSet();
//         constraints.addAll(bias.network.getConstraints());
        
//         network = new ACQ_Network(factory, scope, constraints);
//         //this.bias = new ACQ_Bias(network);
        

//         System.out.println(factory + " : factory is not null");
//         System.out.println(scope + " : scope is not null");
//         System.out.println(constraints + " : constraints is not null");
//         System.out.println(queriesMailbox + " : queriesMailbox is not null");
//         System.out.println(coopManager + " : coopManager is not null");
//         System.out.println(solver + " : solver is not null");
//         System.out.println(oracle + " : oracle is not null");
//         //System.out.println(bias + " : bias is not null");

//         // Initialize the ACQ_COOPERATION instance
//         cooperation = new ACQ_COOPERATION(solver, oracle, coopManager, heuristic, queriesMailbox, 1);
//         if (cooperation == null) {
//             System.out.println(cooperation +" : algo is null");
//         }
//         System.out.println(cooperation +" : algo is not null");
    
//     }

//     @Test
//     public void testFindC() {
//         System.out.println("testFindC() is being executed");

//         type = Query_type.findc1;
//         network1 = new ACQ_Network(factory, network.getVariables(), factory.createSet());
//         //network2 = new ACQ_Network(factory, network.getVariables(), factory.createSet());
        
//         cooperation.query_gen(network, network1, scope, type, heuristic);
        
//         System.out.println(cooperation + " : cooperation is not null");
//         System.out.println(bias + "bias is not null");
//         ACQ_IConstraint icc = cooperation.findC(scope, query, true);

//         assertNull("Constraint should be null for an empty scope and query", icc);
//     }

//     @Test
//     public void testFindScope() {
//         ACQ_Query negativeExample = new ACQ_Query();
//         ACQ_Scope resultScope = cooperation.findScope(negativeExample, scope, new ACQ_Scope());

//         assertNotNull("Resulting scope should not be null", resultScope);
//     }

//     @Test
//     public void testProcess() {
//         boolean result = cooperation.process();

//         assertFalse("Process should not collapse for an empty bias", result);
//     }

//     @Test
//     public void testQueryGeneration() {
//         ACQ_Network learnedNetwork = new ACQ_Network();
//         ACQ_Network biasNetwork = new ACQ_Network();
//         ACQ_Query query = cooperation.query_gen(learnedNetwork, biasNetwork, new ACQ_Scope(), null, null);

//         assertNotNull("Generated query should not be null", query);
//     }
// }