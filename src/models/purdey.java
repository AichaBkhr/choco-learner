package models;
import java.io.IOException;
import java.util.Arrays;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.variables.IntVar;

public class purdey {
	public static void main(String args[]) throws IOException {
		 String[] sfamilies = {"Family 1", "Family 2", "Family 3", "Family 4"}; // 1. five famillies
		 int SIZE = sfamilies.length;
		 int family = 0, bought = 1, paid = 2;
		
		    IntVar[][] attr;
		Model model = new Model();
        
        attr = model.intVarMatrix("attr", SIZE-1, SIZE, 1, SIZE);
        

        IntVar Boyds   = attr[family][0];
        IntVar Garveys = attr[family][1];
        IntVar Logans   = attr[family][2];
        IntVar Navarros = attr[family][3];
    
        IntVar Flour    = attr[bought][0];
        IntVar Kerosene   = attr[bought][1];
        IntVar Muslin = attr[bought][2];
        IntVar sugar  = attr[bought][3];
      
        IntVar cash = attr[paid][0];
        IntVar credit   = attr[paid][1];
        IntVar tradedham   = attr[paid][2];
        IntVar tradedpeas   = attr[paid][3];
      

      /*  Boyds.eq(4).post();
        Garveys.eq(2).post();
        Logans.eq(3).post();
        //Navarros.eq(1).post();;*/

        
        Boyds.eq(Kerosene).post(); 
        Boyds.eq(cash).post();
        Garveys.eq(Muslin).post(); 
        Garveys.eq(credit).post(); 
        Logans.eq(Flour).post(); 
        Logans.eq(tradedham).post(); 
        Navarros.eq(sugar).post(); 
        Navarros.eq(tradedpeas).post(); 
        Kerosene.eq(cash).post(); 
        Muslin.eq(credit).post(); 
        Flour.eq(tradedham).post(); 
        sugar.eq(tradedpeas).post(); 
        model.allDifferent(attr[family]).post();
        model.allDifferent(attr[bought]).post();
        model.allDifferent(attr[paid]).post();
       // model.allDifferent(new IntVar[] {Flour,Kerosene,Muslin,sugar,cash,credit,tradedham,tradedpeas}).post();
        

        
		Solver s = model.getSolver();
		
		while(s.solve()){
		System.out.println(Arrays.deepToString(attr));
		}
		System.out.print(s.getSolutionCount());
		/*s.showShortStatistics();
		while (s.solve()) {
		}

		int cp = 0;

		try {

			// s.outputSearchTreeToGraphviz(n+".dot");
		} catch (Exception e) {
			System.out.print(e);
		}
		
		 * while(s.solve()) { x=s.getDecisionPath().toString(); System.out.print(x);
		 * encode(x); String command = "python python_java_script.py"; String
		 * param="-d "+x+"-t "+6600; Process p = Runtime.getRuntime().exec(command +
		 * param ); BufferedReader in = new BufferedReader(new
		 * InputStreamReader(p.getInputStream())); String ret = in.readLine();
		 * System.out.println("value is : "+ret); System.out.println(x);
		 * 
		 * }
		 */


	
	}
        
    
}