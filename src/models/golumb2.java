package models;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.variables.IntVar;

// import fr.lisn.lahdak.acquisition.core.acqconstraint.Operator;
// import fr.lisn.lahdak.acquisition.core.acqconstraint.ScalarArithmetic;

public class golumb2 {
public static void main(String[]args) {
	int m = 8;
	// A new model instance
	Model model = new Model("Golomb ruler");

	// VARIABLES
	// set of marks that should be put on the ruler
	IntVar[] ticks = model.intVarArray("a", m, 0, 40, false);
	// set of distances between two distinct marks
	IntVar[] diffs = model.intVarArray("d", (m * (m - 1)) / 2, 0, 999, false);

	// CONSTRAINTS
	for (int i = 0, k = 0 ; i < m - 1; i++) {
	    // // the mark variables are ordered
	    model.arithm(ticks[i + 1], ">", ticks[i]).post();
	    for (int j = i + 1; j < m; j++, k++) {
	        // declare the distance constraint between two distinct marks
	        model.scalar(new IntVar[]{ticks[j], ticks[i]}, new int[]{1, -1}, "=", diffs[k]).post();
	      }
	}
	// all distances must be distinct
	model.allDifferent(diffs, "BC").post();
	model.arithm(ticks[0], "=", 0).post();
	model.arithm(ticks[1], "=", 1).post();
	model.arithm(ticks[2], "=", 3).post();
	model.arithm(ticks[3], "=", 7).post();
	model.arithm(ticks[4], "=", 15).post();
	model.arithm(ticks[5], "=", 24).post();
	model.arithm(ticks[6], "=", 35).post();
	model.arithm(ticks[7], "=", 40).post();

	//symmetry-breaking constraints
	System.out.println(model.getNbCstrs());
	System.out.println(model.getSolver().findAllSolutions());
	System.out.println(model.getSolver().getSolutionCount());

}
}
