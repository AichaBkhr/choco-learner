package models;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadLocalRandom;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.search.strategy.Search;
import org.chocosolver.solver.search.strategy.selectors.values.IntDomainRandom;
import org.chocosolver.solver.search.strategy.selectors.variables.DomOverWDeg;
import org.chocosolver.solver.variables.IntVar;

public class RCPSP {

	@SuppressWarnings("resource")
	public static void main(String[] args) throws IOException, CompletionException, ContradictionException {

		

		//Parameters
		String instance = "00";
		int nResource = 0;
		int nTasks = 0;
		int[] durations = null;
		int[] capacities = null;
		int[][] requirements = null;
		int UB = 0;
		
		//Get Instance
		HashMap<Integer, ArrayList<Integer>> precedencies = new HashMap<Integer, ArrayList<Integer>>();
		BufferedReader reader = new BufferedReader(new FileReader(instance + ".dzn"));
		String line;
		String str;

		while ((line = reader.readLine()) != null) {

			if (line.isEmpty() == true) {
				continue;
			}

			// split the line according to spaces
			String[] lineSplited = line.split(" ");

			if (line.startsWith("n_res")) {
				nResource = Integer.parseInt(lineSplited[2].replace(";", ""));
				continue;
			}
			if (line.startsWith("rc")) {
				capacities = new int[nResource];
				str = line;
				str = str.replaceAll("\\D", " ");
				String[] strSplited = str.split(" +");
				for (int i = 1; i <= nResource; i++) {
					capacities[i - 1] = Integer.parseInt(strSplited[i]);

				}
			}

			if (line.startsWith("n_tasks")) {
				nTasks = Integer.parseInt(lineSplited[2].replace(";", ""));
				continue;
			}

			if (line.startsWith("d")) {
				durations = new int[nTasks];
				str = line;
				str = str.replaceAll("\\D", " ");
				String[] strSplited = str.split(" +");
				for (int i = 1; i <= nTasks; i++) {
					durations[i - 1] = Integer.parseInt(strSplited[i]);
					UB = UB  + durations[i - 1];

				}
				continue;
			}

			if (line.startsWith("rr")) {
				requirements = new int[nResource][nTasks];
				str = line;
				str = str.replaceAll("\\D", " ");
				String[] strSplited = str.split(" +");
				
				for (int i = 1; i <= nTasks; i++) {
					requirements[0][i - 1] = Integer.parseInt(strSplited[i]);
					
				}

			}
			int i = 1;
			while (!line.startsWith("rr") && line.contains("|")) {
				str = line;
				str = str.replaceAll("\\D", " ");
				String[] strSplited = str.split(" +");
				for (int j = 1; j <= nTasks; j++) {
					requirements[i][j - 1] = Integer.parseInt(strSplited[j]);
					

				}
				line = reader.readLine();
				i++;
			}

			if (line.startsWith("suc")) {
				str = line;
				str = str.replaceAll("\\D", " ");
				ArrayList<Integer> task = new ArrayList<Integer>();
				String[] strSplited = str.split(" +");
				for (int j = 1; j < strSplited.length; j++) {
					task.add(Integer.parseInt(strSplited[j]) - 1);
				}
				precedencies.put(0, task);
			}
			i = 1;
			while (line != null && line.contains("{") && !line.startsWith("suc")) {
				str = line;
				str = str.replaceAll("\\D", " ");
				String[] strSplited = str.split(" +");
				ArrayList<Integer> task = new ArrayList<Integer>();
				for (int j = 1; j < strSplited.length; j++) {
					task.add(Integer.parseInt(strSplited[j]) - 1);
				}
				line = reader.readLine();
				precedencies.put(i, task);
				i++;
			}

		}
		
		
		
		// The CP model
		Model model = new Model("RCPSP");

		// Variables & Domains
		// Starting time variables
		IntVar[] S = model.intVarArray("Start", nTasks, 1, UB);
		IntVar obj = model.intVar("OBJ", 1, UB);
		
		// Constraints
		// Cumulative constraints
		for (int i = 0; i < nResource; i++) {
			model.cumulative(S, durations, requirements[i], capacities[i]);
		}
		
		// Precedence constraints
		for (int i = 0; i < nTasks; i++) {
			ArrayList<Integer> prec = precedencies.get(i);
			
			for (int a : prec) {
				model.arithm(S[i], "-", S[a], "<=", -1 * durations[i]).post();
			}
		}
		
		// Objective value
		for (int i = 0; i < nTasks; i++) {
			model.arithm(S[i], "-", obj, "<=", -1 * durations[i]).post();
		}
		
		// Set deadline 
		int deadline = 85;
		model.arithm(obj, "<=", deadline).post();
		
		//Or minimize "obj" (makespan)
		
		//model.setObjective(Model.MINIMIZE, obj);
		Solver solver = model.getSolver();
		// v0 code 
		// solver.setSearch(new DomOverWDeg<IntVar>(model.retrieveIntVars(false), System.currentTimeMillis(), 
		//  				 new IntDomainRandom(System.currentTimeMillis())));

		// potential alt but still has a pb
		// VariableSelector<IntVar> variableSelector = new DomOverWDeg<IntVar>(model.retrieveIntVars(false), System.currentTimeMillis());
		// solver.setSearch(new AbstractStrategy<IntVar>(Search.intVarSearch(variableSelector, null, S)));

		//alt to test but still has a pb
		// solver.setSearch(domOverWDegSearch(model.retrieveIntVars(false), new IntDomainRandom(System.currentTimeMillis())));

		//actual found alt to test
		solver.setSearch(Search.intVarSearch(new DomOverWDeg<>(model.retrieveIntVars(false), System.currentTimeMillis()), 
			new IntDomainRandom(System.currentTimeMillis()), model.retrieveIntVars(false)));
								   
		int nbS = 10;
		//model.getSolver().limitTime(600000);
		solver.limitSolution(nbS);
		FileWriter writer = new FileWriter("scheduling" + instance+"_"+ nbS+ ".queries");
		

		//Print the nbS first soltuions
		while(solver.solve()) {
			for (int i = 0; i < nTasks; i++) {
				writer.write(S[i].getValue() + " ");
			}
			writer.write("1");
			writer.write("\n");
			int[] fail =GenerateNegative(S,precedencies);
			for (int i = 0; i < nTasks; i++) {
				writer.write(fail[i]+ " ");
			}
			writer.write("0");
			writer.write("\n");
			solver.restart();
			

		}
		writer.close();
	
		
		
		model.getSolver().printStatistics();

		//Print details of the optimal solution
		//Solution s = model.getSolver().findOptimalSolution(obj, false, null).copySolution();
		//System.out.println("Solution found: " + s);

	}

	@SuppressWarnings("unused")
	private static int[] GenerateNegative(IntVar[] solution, HashMap<Integer, ArrayList<Integer>> precedencies) throws ContradictionException {
		int nprecedences = 0;

		// count precedences
		int[] S = new int[solution.length];
		for (int i = 0; i < S.length; i++) {
			S[i]=solution[i].getValue();
			ArrayList<Integer> prec = precedencies.get(i);


			for (int a : prec) {

				nprecedences++;

			}

		}

		System.out.println(nprecedences);
		int violated = ThreadLocalRandom.current().nextInt(1, nprecedences + 1);

		System.out.println(violated);
		// Precedence constraints

		int k =0;

		
		for (int i = 0; i < S.length; i++) {
			int v = 0; 

			ArrayList<Integer> prec = precedencies.get(i);

			

			for (int a : prec) {
				k++;

				if(k<=violated) {
					v=S[a];
					S[a]=S[i];
					S[i]=v;

				}
			}

		}
		return S;
		
	}

}
