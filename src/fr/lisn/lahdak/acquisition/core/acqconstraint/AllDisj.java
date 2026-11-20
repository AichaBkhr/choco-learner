package fr.lisn.lahdak.acquisition.core.acqconstraint;

import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Scope;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public  class AllDisj extends ACQ_Constraint{
	private final int nbTasks;
	private final String negation;
	public AllDisj(ACQ_Scope scope, String negation_name) {
		super("Disj", scope.getVariables().stream().toArray());
		assert scope.size() % 2 == 0: "";
		this.nbTasks = scope.size() / 2;
		this.negation = negation_name;
	}

	/**
	 * @author Aicha
	 * 
	 * All the tasks that have a duration strictly greater than 0 should not overlap.
	 * 
	 * @param values set of 2*n vars (alternate between origin and duration)
	 * @return a boolean, true if ALL the tasks do not overlap, false otherwise
	 */
	@Override
	public boolean check(int... values) {
		for (int i = 0; i < nbTasks; i++) {
			int oi = values[2 * i];
			int di = values[2 * i + 1];
			if (di <= 0) continue;

			for (int j = i + 1; j < nbTasks; j++) {
				int oj = values[2 * j];
				int dj = values[2 * j + 1];
				if (dj <= 0) continue;

				boolean IBeforeJ = (oi + di <= oj);
				boolean JBeforeI = (oj + dj <= oi);

				if (!IBeforeJ && !JBeforeI) {
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public ACQ_IConstraint getNegation() {
		return new NotAllDisj(this.getScope(), this.getName());
	}

	@Override
	public Constraint[] getChocoConstraints(Model model, IntVar... intVars) {

		int[] alldisj_vars = this.getVariables();
		int nbPairs = nbTasks * (nbTasks - 1) / 2;
		Constraint[] constraints = new Constraint[nbPairs];
		int idx = 0;

		for (int i = 0; i < nbTasks; i++) {
			IntVar oi = intVars[alldisj_vars[2 * i]];
			IntVar di = intVars[alldisj_vars[2 * i + 1]];
			for (int j = i + 1; j < nbTasks; j++) {
				IntVar oj = intVars[alldisj_vars[2 * j]];
				IntVar dj = intVars[alldisj_vars[2 * j + 1]];
				// same rule as check() : a task of duration <= 0 never overlaps anything
				BoolVar iEmpty = model.arithm(di, "<=", 0).reify();
				BoolVar jEmpty = model.arithm(dj, "<=", 0).reify();
				BoolVar IBeforeJ = model.arithm(oi, "+", di, "<=", oj).reify();
				BoolVar JBeforeI = model.arithm(oj, "+", dj, "<=", oi).reify();
				constraints[idx++] = model.sum(new BoolVar[] { iEmpty, jEmpty, IBeforeJ, JBeforeI }, ">=", 1);
			}
		}

		return constraints;

	}

	@Override
	public void toReifiedChoco(Model model, BoolVar b, IntVar... intVars) {

		int[] vars = this.getVariables();
		BoolVar[] pairBools = new BoolVar[nbTasks * (nbTasks - 1) / 2];
		int idx = 0;

		for (int i = 0; i < nbTasks; i++) {
			IntVar oi = intVars[vars[2 * i]];
			IntVar di = intVars[vars[2 * i + 1]];
			for (int j = i + 1; j < nbTasks; j++) {
				IntVar oj = intVars[vars[2 * j]];
				IntVar dj = intVars[vars[2 * j + 1]];
				BoolVar iEmpty = model.arithm(di, "<=", 0).reify();
				BoolVar jEmpty = model.arithm(dj, "<=", 0).reify();
				BoolVar IBeforeJ = model.arithm(oi, "+", di, "<=", oj).reify();
				BoolVar JBeforeI = model.arithm(oj, "+", dj, "<=", oi).reify();
				pairBools[idx++] = model.sum(new BoolVar[] { iEmpty, jEmpty, IBeforeJ, JBeforeI }, ">=", 1).reify();
			}
		}
		model.and(pairBools).reifyWith(b);
	}

	@Override
	public String getNegName() {
		String newname = this.getName();
		if (this.getName().startsWith("Not")) {
			newname = newname.substring(3);
		} else {
			newname = "Not" + newname;
		}

		return newname;
	}

	@Override
	public String toSmtlib() {
		int[] vars = this.getVariables();
		StringBuilder sb = new StringBuilder();
		List<String> pairs = new ArrayList<>();

		for (int i = 0; i < nbTasks; i++) {
			String oi = "x" + vars[2 * i];
			String di = "x" + vars[2 * i + 1];

			for (int j = i + 1; j < nbTasks; j++) {
				String oj = "x" + vars[2 * j];
				String dj = "x" + vars[2 * j + 1];

				// IBeforeJ : oi + di <= oj  (tâche i finit avant que j commence)
				String IBeforeJ = "(bvsle (bvadd " + oi + " " + di + ") " + oj + ")";
				// JBeforeI : oj + dj <= oi  (tâche j finit avant que i commence)
				String JBeforeI = "(bvsle (bvadd " + oj + " " + dj + ") " + oi + ")";

				// au moins l'un des deux doit être vrai
				String iEmpty = "(bvsle " + di + " (_ bv0 32))";
				String jEmpty = "(bvsle " + dj + " (_ bv0 32))";
				pairs.add("(or " + iEmpty + " " + jEmpty + " " + IBeforeJ + " " + JBeforeI + ")");
			}
		}

		if (pairs.size() == 1) return pairs.get(0);

		sb.append("(and");
		for (String p : pairs) sb.append(" ").append(p);
		sb.append(")");
		return sb.toString();
	}

	@Override
	public boolean check(ACQ_Query query) {
		int value[] = this.getProjection(query);
		return check(value);
	}

}
