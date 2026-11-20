package fr.lisn.lahdak.acquisition.core.acqconstraint;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;

public class LexLessEq extends GlobalConstraint {

	private final int n;

	public LexLessEq(int[] vector1, int[] vector2) {
		super("LexLessEq", concat(vector1, vector2));
		assert vector1.length == vector2.length : "vectors of different lengths";
		this.n = vector1.length;
	}

	private static int[] concat(int[] a, int[] b) {
		int[] all = new int[a.length + b.length];
		System.arraycopy(a, 0, all, 0, a.length);
		System.arraycopy(b, 0, all, a.length, b.length);
		return all;
	}

	@Override
	public boolean check(int... values) {
		for (int i = 0; i < n; i++) {
			if (values[i] < values[n + i]) return true;
			if (values[i] > values[n + i]) return false;
		}
		return true; // equal vectors
	}

	@Override
	protected Constraint buildChoco(Model model, IntVar[] vars) {
		// lex(i) = Xi < Yi or (Xi = Yi and lex(i+1)), with lex(n-1) = X(n-1) <= Y(n-1)
		BoolVar rest = model.arithm(vars[n - 1], "<=", vars[2 * n - 1]).reify();
		for (int i = n - 2; i >= 0; i--) {
			BoolVar lt = model.arithm(vars[i], "<", vars[n + i]).reify();
			BoolVar eq = model.arithm(vars[i], "=", vars[n + i]).reify();
			BoolVar eqAndRest = model.arithm(eq, "+", rest, "=", 2).reify();
			rest = model.arithm(lt, "+", eqAndRest, ">=", 1).reify();
		}
		return model.arithm(rest, "=", 1);
	}

	@Override
	public String toSmtlib() {
		String rest = "(bvsle " + x(n - 1) + " " + x(2 * n - 1) + ")";
		for (int i = n - 2; i >= 0; i--)
			rest = "(or (bvslt " + x(i) + " " + x(n + i) + ") (and (= " + x(i) + " " + x(n + i) + ") " + rest + "))";
		return rest;
	}
}
