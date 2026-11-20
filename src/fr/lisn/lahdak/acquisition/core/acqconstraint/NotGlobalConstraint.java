package fr.lisn.lahdak.acquisition.core.acqconstraint;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.variables.IntVar;

public class NotGlobalConstraint extends GlobalConstraint {

	private final GlobalConstraint positive;

	public NotGlobalConstraint(GlobalConstraint positive) {
		super("Not" + positive.getName(), positive.getVariables());
		this.positive = positive;
	}

	@Override
	public boolean check(int... values) {
		return !positive.check(values);
	}

	@Override
	protected Constraint buildChoco(Model model, IntVar[] vars) {
		return positive.buildChoco(model, vars).getOpposite();
	}

	@Override
	public ACQ_IConstraint getNegation() {
		return positive;
	}

	@Override
	public String getNegName() {
		return positive.getName();
	}

	@Override
	public String toSmtlib() {
		return "(not " + positive.toSmtlib() + ")";
	}
}
