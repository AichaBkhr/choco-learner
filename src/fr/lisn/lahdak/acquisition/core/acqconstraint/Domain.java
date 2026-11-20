package fr.lisn.lahdak.acquisition.core.acqconstraint;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;

import java.util.ArrayList;
import java.util.List;

public class Domain extends GlobalConstraint {

	public Domain(int var, int[] variables) {
		super("Domain", prepend(var, variables));
	}

	@Override
	public boolean check(int... values) {
		for (int k = 1; k < values.length; k++)
			if ((values[0] == k) != (values[k] == 1)) return false;
		return true;
	}

	@Override
	protected Constraint buildChoco(Model model, IntVar[] vars) {
		int n = vars.length - 1;
		BoolVar[] ok = new BoolVar[n];
		for (int k = 1; k <= n; k++) {
			BoolVar is = model.arithm(vars[0], "=", k).reify();
			BoolVar one = model.arithm(vars[k], "=", 1).reify();
			ok[k - 1] = model.arithm(is, "=", one).reify();
		}
		return model.sum(ok, "=", n);
	}

	@Override
	public String toSmtlib() {
		List<String> terms = new ArrayList<>();
		for (int k = 1; k < getArity(); k++) {
			String is = "(= " + x(0) + " (_ bv" + k + " 32))", one = "(= " + x(k) + " (_ bv1 32))";
			terms.add("(or (not " + is + ") " + one + ")");
			terms.add("(or " + is + " (not " + one + "))");
		}
		return smtAnd(terms);
	}
}
