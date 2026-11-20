package fr.lisn.lahdak.acquisition.core.acqconstraint;

import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;
import fr.lisn.lahdak.acquisition.core.tools.NameService;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;

import java.util.List;

public abstract class GlobalConstraint extends ACQ_Constraint {

	public GlobalConstraint(String name, int[] variables) {
		super(name, variables);
	}

	/**
	 * Builds one choco constraint equivalent to this constraint.
	 */
	protected abstract Constraint buildChoco(Model model, IntVar[] vars);

	/** Scope [first, rest...] of the constraints on one variable and a list (BetweenMinMax, Minimum, Maximum, Domain) */
	protected static int[] prepend(int first, int[] rest) {
		int[] all = new int[rest.length + 1];
		all[0] = first;
		System.arraycopy(rest, 0, all, 1, rest.length);
		return all;
	}

	/** Model variables of this constraint's scope, in the order of getVariables()
	 * (the existing getVariables(fullVarSet) looses the scope order)
	 * */
	protected IntVar[] scopeVars(IntVar[] intVars) {
		int[] ids = this.getVariables();
		IntVar[] vars = new IntVar[ids.length];
		for (int k = 0; k < ids.length; k++) {
			String name = NameService.getVarName(ids[k]);
			for (IntVar v : intVars) {
				if (v.getName().equals(name)) {
					vars[k] = v;
					break;
				}
			}
			if (vars[k] == null)
				throw new IllegalStateException("variable " + ids[k] + " of " + this + " not in the model");
		}
		return vars;
	}

	@Override
	public Constraint[] getChocoConstraints(Model model, IntVar... intVars) {
		return new Constraint[] { buildChoco(model, scopeVars(intVars)) };
	}

	@Override
	public void toReifiedChoco(Model model, BoolVar b, IntVar... intVars) {
		buildChoco(model, scopeVars(intVars)).reifyWith(b);
	}

	@Override
	public ACQ_IConstraint getNegation() {
		return new NotGlobalConstraint(this);
	}

	@Override
	public String getNegName() {
		return "Not" + this.getName();
	}


	/**
	 * used by the oracle to clissify queries
	 */
	@Override
	public boolean check(ACQ_Query query) {
		return check(this.getProjection(query));
	}

	// ---------------- SMT-LIB helpers (same bitvector operators as BinaryArithmetic) ----------------

	/** SMT-LIB name of the k-th variable of this constraint */
	protected String x(int k) {
		return "x" + this.getVariables()[k];
	}

	protected static String smtAnd(List<String> terms) {
		return terms.size() == 1 ? terms.get(0) : "(and " + String.join(" ", terms) + ")";
	}

	protected static String smtOr(List<String> terms) {
		return terms.size() == 1 ? terms.get(0) : "(or " + String.join(" ", terms) + ")";
	}
}
