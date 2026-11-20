// package fr.lisn.lahdak.acquisition.core.acqsolver;

// import org.chocosolver.solver.Model;
// import org.chocosolver.solver.Solver;
// import org.chocosolver.solver.variables.IntVar;
// import org.junit.Test;
// import static org.junit.Assert.assertNotNull;
// import static org.junit.Assert.assertTrue;

// import java.util.Arrays;

// public class ACQ_ChocoSolverTest {

//     private void runSetSearchTest(String vrs, String vls) {
//         Model model = new Model();
//         Solver solver = model.getSolver();

//         IntVar[] chocoVars = new IntVar[] {
//             model.intVar("X", 1, 10),
//             model.intVar("Y", 1, 10),
//             model.intVar("Z", 1, 10)
//         };

//         // Debugging: Print variable domains
//         System.out.println("Variables: " + Arrays.toString(chocoVars));
//         for (IntVar var : chocoVars) {
//             System.out.println("Variable: " + var.getName() + ", Domain: " + var.getLB() + " to " + var.getUB());
//         }

//         // Add a simple constraint
//         System.out.println("Posting constraint: X + Y = Z");
//         model.arithm(chocoVars[0], "+", chocoVars[1], "=", chocoVars[2]).post();

//         ACQ_IDomain domain = new ACQ_IDomain() {
//             @Override
//             public int getMin(int var) {
//                 return 1;
//             }

//             @Override
//             public int getMax(int var) {
//                 return 10;
//             }
//         };

//         ACQ_ChocoSolver acqSolver = new ACQ_ChocoSolver(domain, vrs, vls);

//         try {
//             // Attempt to use the specified search strategy
//             System.out.println("Setting search strategy: " + vrs + " + " + vls);
//             // Mettre en public setSearch et décommenter la ligne suivante pour tester
//             //acqSolver.setSearch(solver, chocoVars, vrs, vls);
//             System.out.println("Search strategy: " + solver.getSearch());
//         } catch (Exception e) {
//             System.err.println(vrs + " + " + vls + " failed: " + e.getMessage());
//         }

//         // Assert that the search strategy is not null
//         assertNotNull("Search strategy should not be null", solver.getSearch());

//         // Solve the model
//         boolean solved = solver.solve();
//         System.out.println("Solved: " + solved);

//         // Assert that the model is solvable
//         assertTrue("The model should be solvable", solved);
//     }

    
//     @Test
//     public void testSetSearch_FirstFail_IntDomainBest() {
//         runSetSearchTest("FirstFail", "IntDomainBest");
//     }

//     @Test
//     public void testSetSearch_FirstFail_IntDomainMin() {
//         runSetSearchTest("FirstFail", "IntDomainMin");
//     }

//     @Test
//     public void testSetSearch_FirstFail_IntDomainRandom() {
//         runSetSearchTest("FirstFail", "IntDomainRandom");
//     }

//     @Test
//     public void testSetSearch_FirstFail_IntDomainImpact() {
//         runSetSearchTest("FirstFail", "IntDomainImpact");
//     }

//     @Test
//     public void testSetSearch_FirstFail_IntDomainLast() {
//         runSetSearchTest("FirstFail", "IntDomainLast");
//     }

//     @Test
//     public void testSetSearch_FirstFail_IntDomainMedian() {
//         runSetSearchTest("FirstFail", "IntDomainMedian");
//     }

//     @Test
//     public void testSetSearch_FirstFail_IntDomainMax() {
//         runSetSearchTest("FirstFail", "IntDomainMax");
//     }

//     @Test
//     public void testSetSearch_FirstFail_IntDomainRandomBound() {
//         runSetSearchTest("FirstFail", "IntDomainRandomBound");
//     }



//     @Test
//     public void testSetSearch_AntiFirstFail_IntDomainBest() {
//         runSetSearchTest("AntiFirstFail", "IntDomainBest");
//     }

//     @Test
//     public void testSetSearch_AntiFirstFail_IntDomainMin() {
//         runSetSearchTest("AntiFirstFail", "IntDomainMin");
//     }

//     @Test
//     public void testSetSearch_AntiFirstFail_IntDomainImpact() {
//         runSetSearchTest("AntiFirstFail", "IntDomainImpact");
//     }

//     @Test
//     public void testSetSearch_AntiFirstFail_IntDomainLast() {
//         runSetSearchTest("AntiFirstFail", "IntDomainLast");
//     }

//     @Test
//     public void testSetSearch_AntiFirstFail_IntDomainMedian() {
//         runSetSearchTest("AntiFirstFail", "IntDomainMedian");
//     }

//     @Test
//     public void testSetSearch_AntiFirstFail_IntDomainMax() {
//         runSetSearchTest("AntiFirstFail", "IntDomainMax");
//     }

//     @Test
//     public void testSetSearch_AntiFirstFail_IntDomainRandomBound() {
//         runSetSearchTest("AntiFirstFail", "IntDomainRandomBound");
//     }




//     @Test
//     public void testSetSearch_InputOrder_IntDomainBest() {
//         runSetSearchTest("InputOrder", "IntDomainBest");
//     }

//     @Test
//     public void testSetSearch_InputOrder_IntDomainMin() {
//         runSetSearchTest("InputOrder", "IntDomainMin");
//     }

//     @Test
//     public void testSetSearch_InputOrder_IntDomainImpact() {
//         runSetSearchTest("InputOrder", "IntDomainImpact");
//     }

//     @Test
//     public void testSetSearch_InputOrder_IntDomainLast() {
//         runSetSearchTest("InputOrder", "IntDomainLast");
//     }

//     @Test
//     public void testSetSearch_InputOrder_IntDomainMedian() {
//         runSetSearchTest("InputOrder", "IntDomainMedian");
//     }

//     @Test
//     public void testSetSearch_InputOrder_IntDomainMax() {
//         runSetSearchTest("InputOrder", "IntDomainMax");
//     }

//     @Test
//     public void testSetSearch_InputOrder_IntDomainRandomBound() {
//         runSetSearchTest("InputOrder", "IntDomainRandomBound");
//     }




//     @Test
//     public void testSetSearch_Cyclic_IntDomainBest() {
//         runSetSearchTest("Cyclic", "IntDomainBest");
//     }

//     @Test
//     public void testSetSearch_Cyclic_IntDomainMin() {
//         runSetSearchTest("Cyclic", "IntDomainMin");
//     }

//     @Test
//     public void testSetSearch_Cyclic_IntDomainImpact() {
//         runSetSearchTest("Cyclic", "IntDomainImpact");
//     }

//     @Test
//     public void testSetSearch_Cyclic_IntDomainLast() {
//         runSetSearchTest("Cyclic", "IntDomainLast");
//     }

//     @Test
//     public void testSetSearch_Cyclic_IntDomainMedian() {
//         runSetSearchTest("Cyclic", "IntDomainMedian");
//     }

//     @Test
//     public void testSetSearch_Cyclic_IntDomainMax() {
//         runSetSearchTest("Cyclic", "IntDomainMax");
//     }

//     @Test
//     public void testSetSearch_Cyclic_IntDomainRandomBound() {
//         runSetSearchTest("Cyclic", "IntDomainRandomBound");
//     }




//     @Test
//     public void testSetSearch_DomOverWDeg_IntDomainBest() {
//         runSetSearchTest("DomOverWDeg", "IntDomainBest");
//     }

//     @Test
//     public void testSetSearch_DomOverWDeg_IntDomainMin() {
//         runSetSearchTest("DomOverWDeg", "IntDomainMin");
//     }

//     @Test
//     public void testSetSearch_DomOverWDeg_IntDomainImpact() {
//         runSetSearchTest("DomOverWDeg", "IntDomainImpact");
//     }

//     @Test
//     public void testSetSearch_DomOverWDeg_IntDomainLast() {
//         runSetSearchTest("DomOverWDeg", "IntDomainLast");
//     }

//     @Test
//     public void testSetSearch_DomOverWDeg_IntDomainMedian() {
//         runSetSearchTest("DomOverWDeg", "IntDomainMedian");
//     }

//     @Test
//     public void testSetSearch_DomOverWDeg_IntDomainMax() {
//         runSetSearchTest("DomOverWDeg", "IntDomainMax");
//     }

//     @Test
//     public void testSetSearch_DomOverWDeg_IntDomainRandomBound() {
//         runSetSearchTest("DomOverWDeg", "IntDomainRandomBound");
//     }




//     @Test
//     public void testSetSearch_GeneralizedMinDomVarSelector_IntDomainBest() {
//         runSetSearchTest("GeneralizedMinDomVarSelector", "IntDomainBest");
//     }

//     @Test
//     public void testSetSearch_GeneralizedMinDomVarSelector_IntDomainMin() {
//         runSetSearchTest("GeneralizedMinDomVarSelector", "IntDomainMin");
//     }

//     @Test
//     public void testSetSearch_GeneralizedMinDomVarSelector_IntDomainImpact() {
//         runSetSearchTest("GeneralizedMinDomVarSelector", "IntDomainImpact");
//     }

//     @Test
//     public void testSetSearch_GeneralizedMinDomVarSelector_IntDomainLast() {
//         runSetSearchTest("GeneralizedMinDomVarSelector", "IntDomainLast");
//     }

//     @Test
//     public void testSetSearch_GeneralizedMinDomVarSelector_IntDomainMedian() {
//         runSetSearchTest("GeneralizedMinDomVarSelector", "IntDomainMedian");
//     }

//     @Test
//     public void testSetSearch_GeneralizedMinDomVarSelector_IntDomainMax() {
//         runSetSearchTest("GeneralizedMinDomVarSelector", "IntDomainMax");
//     }

//     @Test
//     public void testSetSearch_GeneralizedMinDomVarSelector_IntDomainRandomBound() {
//         runSetSearchTest("GeneralizedMinDomVarSelector", "IntDomainRandomBound");
//     }





//     @Test
//     public void testSetSearch_Largest_IntDomainBest() {
//         runSetSearchTest("Largest", "IntDomainBest");
//     }

//     @Test
//     public void testSetSearch_Largest_IntDomainMin() {
//         runSetSearchTest("Largest", "IntDomainMin");
//     }

//     @Test
//     public void testSetSearch_Largest_IntDomainImpact() {
//         runSetSearchTest("Largest", "IntDomainImpact");
//     }

//     @Test
//     public void testSetSearch_Largest_IntDomainLast() {
//         runSetSearchTest("Largest", "IntDomainLast");
//     }

//     @Test
//     public void testSetSearch_Largest_IntDomainMedian() {
//         runSetSearchTest("Largest", "IntDomainMedian");
//     }

//     @Test
//     public void testSetSearch_Largest_IntDomainMax() {
//         runSetSearchTest("Largest", "IntDomainMax");
//     }

//     @Test
//     public void testSetSearch_Largest_IntDomainRandomBound() {
//         runSetSearchTest("Largest", "IntDomainRandomBound");
//     }




//     @Test
//     public void testSetSearch_Smallest_IntDomainBest() {
//         runSetSearchTest("Smallest", "IntDomainBest");
//     }

//     @Test
//     public void testSetSearch_Smallest_IntDomainMin() {
//         runSetSearchTest("Smallest", "IntDomainMin");
//     }

//     @Test
//     public void testSetSearch_Smallest_IntDomainImpact() {
//         runSetSearchTest("Smallest", "IntDomainImpact");
//     }

//     @Test
//     public void testSetSearch_Smallest_IntDomainLast() {
//         runSetSearchTest("Smallest", "IntDomainLast");
//     }

//     @Test
//     public void testSetSearch_Smallest_IntDomainMedian() {
//         runSetSearchTest("Smallest", "IntDomainMedian");
//     }

//     @Test
//     public void testSetSearch_Smallest_IntDomainMax() {
//         runSetSearchTest("Smallest", "IntDomainMax");
//     }

//     @Test
//     public void testSetSearch_Smallest_IntDomainRandomBound() {
//         runSetSearchTest("Smallest", "IntDomainRandomBound");
//     }






//     @Test
//     public void testSetSearch_MaxRegret_IntDomainBest() {
//         runSetSearchTest("MaxRegret", "IntDomainBest");
//     }

//     @Test
//     public void testSetSearch_MaxRegret_IntDomainMin() {
//         runSetSearchTest("MaxRegret", "IntDomainMin");
//     }

//     @Test
//     public void testSetSearch_MaxRegret_IntDomainImpact() {
//         runSetSearchTest("MaxRegret", "IntDomainImpact");
//     }

//     @Test
//     public void testSetSearch_MaxRegret_IntDomainLast() {
//         runSetSearchTest("MaxRegret", "IntDomainLast");
//     }

//     @Test
//     public void testSetSearch_MaxRegret_IntDomainMedian() {
//         runSetSearchTest("MaxRegret", "IntDomainMedian");
//     }

//     @Test
//     public void testSetSearch_MaxRegret_IntDomainMax() {
//         runSetSearchTest("MaxRegret", "IntDomainMax");
//     }

//     @Test
//     public void testSetSearch_MaxRegret_IntDomainRandomBound() {
//         runSetSearchTest("MaxRegret", "IntDomainRandomBound");
//     }





//     @Test
//     public void testSetSearch_Occurrence_IntDomainBest() {
//         runSetSearchTest("Occurrence", "IntDomainBest");
//     }

//     @Test
//     public void testSetSearch_Occurrence_IntDomainMin() {
//         runSetSearchTest("Occurrence", "IntDomainMin");
//     }

//     @Test
//     public void testSetSearch_Occurrence_IntDomainImpact() {
//         runSetSearchTest("Occurrence", "IntDomainImpact");
//     }

//     @Test
//     public void testSetSearch_Occurrence_IntDomainLast() {
//         runSetSearchTest("Occurrence", "IntDomainLast");
//     }

//     @Test
//     public void testSetSearch_Occurrence_IntDomainMedian() {
//         runSetSearchTest("Occurrence", "IntDomainMedian");
//     }

//     @Test
//     public void testSetSearch_Occurrence_IntDomainMax() {
//         runSetSearchTest("Occurrence", "IntDomainMax");
//     }

//     @Test
//     public void testSetSearch_Occurrence_IntDomainRandomBound() {
//         runSetSearchTest("Occurrence", "IntDomainRandomBound");
//     }





//     @Test
//     public void testSetSearch_Random_IntDomainBest() {
//         runSetSearchTest("Random", "IntDomainBest");
//     }

//     @Test
//     public void testSetSearch_Random_IntDomainMin() {
//         runSetSearchTest("Random", "IntDomainMin");
//     }

//     @Test
//     public void testSetSearch_Random_IntDomainImpact() {
//         runSetSearchTest("Random", "IntDomainImpact");
//     }

//     @Test
//     public void testSetSearch_Random_IntDomainLast() {
//         runSetSearchTest("Random", "IntDomainLast");
//     }

//     @Test
//     public void testSetSearch_Random_IntDomainMedian() {
//         runSetSearchTest("Random", "IntDomainMedian");
//     }

//     @Test
//     public void testSetSearch_Random_IntDomainMax() {
//         runSetSearchTest("Random", "IntDomainMax");
//     }

//     @Test
//     public void testSetSearch_Random_IntDomainRandomBound() {
//         runSetSearchTest("Random", "IntDomainRandomBound");
//     }





//     @Test
//     public void testSetSearch_RandomVar_IntDomainBest() {
//         runSetSearchTest("RandomVar", "IntDomainBest");
//     }

//     @Test
//     public void testSetSearch_RandomVar_IntDomainMin() {
//         runSetSearchTest("RandomVar", "IntDomainMin");
//     }

//     @Test
//     public void testSetSearch_RandomVar_IntDomainImpact() {
//         runSetSearchTest("RandomVar", "IntDomainImpact");
//     }

//     @Test
//     public void testSetSearch_RandomVar_IntDomainLast() {
//         runSetSearchTest("RandomVar", "IntDomainLast");
//     }

//     @Test
//     public void testSetSearch_RandomVar_IntDomainMedian() {
//         runSetSearchTest("RandomVar", "IntDomainMedian");
//     }

//     @Test
//     public void testSetSearch_RandomVar_IntDomainMax() {
//         runSetSearchTest("RandomVar", "IntDomainMax");
//     }

//     @Test
//     public void testSetSearch_RandomVar_IntDomainRandomBound() {
//         runSetSearchTest("RandomVar", "IntDomainRandomBound");
//     }
// }