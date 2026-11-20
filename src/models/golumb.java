package models;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.variables.IntVar;

public class golumb {
public static void main(String[]args) {
	int m = 8;
	// A new model instance
	Model model = new Model("Golomb ruler");

	// VARIABLES
	// set of marks that should be put on the ruler
	IntVar[] ticks = model.intVarArray("a", m, 0, 40, false);
	// set of distances between two distinct marks

	// CONSTRAINTS

	for (int i = 0 ; i < m - 1; i++) {
	    // // the mark variables are ordered
	    model.arithm(ticks[i + 1], ">", ticks[i]).post();
	}
	
	for (int i = 0 ; i < m ; i++) {
		for (int j = 0 ; j < m ; j++) {
			for (int k = 0 ; k < m ; k++) {
	if (i > j && j  > k ) {
			model.scalar(new IntVar[]{ticks[i],ticks[j],ticks[k]}, new int[] {1,-2,1}, "!=", 0).post();
		
	}

	}}}
	
	for (int i = 0 ; i < m ; i++) {
		for (int j = 0 ; j < m ; j++) {
			for (int k = 0 ; k < m ; k++) {
				for (int l = 0 ; l < m ; l++) {

	if (i > j && k  > l && i>k ) {
			model.scalar(new IntVar[]{ticks[i],ticks[j],ticks[k],ticks[l]}, new int[] { 1, -1, -1, 1 }, "!=", 0).post();
		
	}

	}}}}
	System.out.println(model.getNbCstrs());
	System.out.println(model.getSolver().findAllSolutions());
	System.out.println(model.getSolver().getSolutionCount());

}
}
