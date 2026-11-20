package fr.lisn.lahdak.acquisition.core.acqconstraint;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;

import java.util.ArrayList;
import java.util.List;

public class BetweenMinMax extends GlobalConstraint {

	public BetweenMinMax(int var, int[] variables) {
		super("BetweenMinMax", prepend(var, variables));
	}

	@Override
	public boolean check(int... values) {
		boolean someLower = false, someGreater = false;
		for (int k = 1; k < values.length; k++) {
			if (values[0] >= values[k]) someLower = true;
			if (values[0] <= values[k]) someGreater = true;
		}
		return someLower && someGreater;
	}

	@Override
	protected Constraint buildChoco(Model model, IntVar[] vars) {
		int n = vars.length - 1;
		BoolVar[] ge = new BoolVar[n];
		BoolVar[] le = new BoolVar[n];
		for (int k = 1; k <= n; k++) {
			ge[k - 1] = model.arithm(vars[0], ">=", vars[k]).reify();
			le[k - 1] = model.arithm(vars[0], "<=", vars[k]).reify();
		}
		BoolVar someLower = model.sum(ge, ">=", 1).reify();
		BoolVar someGreater = model.sum(le, ">=", 1).reify();
		return model.arithm(someLower, "+", someGreater, "=", 2);
	}

	@Override
	public String toSmtlib() {
		List<String> ge = new ArrayList<>(), le = new ArrayList<>();
		for (int k = 1; k < getArity(); k++) {
			ge.add("(bvsge " + x(0) + " " + x(k) + ")");
			le.add("(bvsle " + x(0) + " " + x(k) + ")");
		}
		return smtAnd(List.of(smtOr(ge), smtOr(le)));
	}
}
