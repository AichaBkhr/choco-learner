package fr.lisn.lahdak.acquisition.core.acqconstraint;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;

import java.util.ArrayList;
import java.util.List;

public class Maximum extends GlobalConstraint {

	/** variables[0] = MAX, variables[1..n] = VARIABLES */
	public Maximum(int max, int[] variables) {
		super("Maximum", prepend(max, variables));
	}

	@Override
	public boolean check(int... values) {
		boolean reached = false;
		for (int k = 1; k < values.length; k++) {
			if (values[0] < values[k]) return false;
			if (values[0] == values[k]) reached = true;
		}
		return reached;
	}

	@Override
	protected Constraint buildChoco(Model model, IntVar[] vars) {
		int n = vars.length - 1;
		BoolVar[] ge = new BoolVar[n];
		BoolVar[] eq = new BoolVar[n];
		for (int k = 1; k <= n; k++) {
			ge[k - 1] = model.arithm(vars[0], ">=", vars[k]).reify();
			eq[k - 1] = model.arithm(vars[0], "=", vars[k]).reify();
		}
		BoolVar allLower = model.sum(ge, "=", n).reify();
		BoolVar reached = model.sum(eq, ">=", 1).reify();
		return model.arithm(allLower, "+", reached, "=", 2);
	}

	@Override
	public String toSmtlib() {
		List<String> terms = new ArrayList<>(), eq = new ArrayList<>();
		for (int k = 1; k < getArity(); k++) {
			terms.add("(bvsge " + x(0) + " " + x(k) + ")");
			eq.add("(= " + x(0) + " " + x(k) + ")");
		}
		terms.add(smtOr(eq));
		return smtAnd(terms);
	}
}
