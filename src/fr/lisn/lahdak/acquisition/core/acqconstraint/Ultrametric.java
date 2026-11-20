package fr.lisn.lahdak.acquisition.core.acqconstraint;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;

import java.util.ArrayList;
import java.util.List;

public class Ultrametric extends GlobalConstraint {

	public Ultrametric(int[] variables) {
		super("Ultrametric", variables);
	}

	@Override
	public boolean check(int... values) {
		int n = values.length;
		for (int a = 0; a < n; a++)
			for (int b = a + 1; b < n; b++)
				for (int c = b + 1; c < n; c++) {
					int x = values[a], y = values[b], z = values[c];
					if (!((y == z && x >= y) || (x == z && y >= x) || (x == y && z >= x))) return false;
				}
		return true;
	}

	private BoolVar eqGe(Model model, IntVar u, IntVar v, IntVar w) {
		BoolVar eq = model.arithm(u, "=", v).reify();
		BoolVar ge = model.arithm(w, ">=", u).reify();
		return model.arithm(eq, "+", ge, "=", 2).reify();
	}

	@Override
	protected Constraint buildChoco(Model model, IntVar[] vars) {
		int n = vars.length;
		List<BoolVar> ok = new ArrayList<>();
		for (int a = 0; a < n; a++)
			for (int b = a + 1; b < n; b++)
				for (int c = b + 1; c < n; c++) {
					BoolVar[] cases = { eqGe(model, vars[b], vars[c], vars[a]), eqGe(model, vars[a], vars[c], vars[b]),
							eqGe(model, vars[a], vars[b], vars[c]) };
					ok.add(model.sum(cases, ">=", 1).reify());
				}
		return model.sum(ok.toArray(new BoolVar[0]), "=", ok.size());
	}

	@Override
	public String toSmtlib() {
		List<String> terms = new ArrayList<>();
		int n = getArity();
		for (int a = 0; a < n; a++)
			for (int b = a + 1; b < n; b++)
				for (int c = b + 1; c < n; c++)
					terms.add("(or (and (= " + x(b) + " " + x(c) + ") (bvsge " + x(a) + " " + x(b) + ")) (and (= " + x(a) + " "
							+ x(c) + ") (bvsge " + x(b) + " " + x(a) + ")) (and (= " + x(a) + " " + x(b) + ") (bvsge " + x(c)
							+ " " + x(a) + ")))");
		return smtAnd(terms);
	}
}
