package fr.lisn.lahdak.acquisition.core.acqsolver;

import java.util.ArrayList;

import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_IConstraint;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintMapping;
import fr.lisn.lahdak.acquisition.core.acqconstraint.RevConstraintMapping;
import fr.lisn.lahdak.acquisition.core.acqconstraint.Unit;

public class MiniSatModel extends SATModel {

	ArrayList<Integer> model;
	RevConstraintMapping revmapping;

	public MiniSatModel(ArrayList<Integer> model, RevConstraintMapping revmap) {
		this.model = model;
		this.revmapping = revmap;
	}

	public MiniSatModel(ArrayList<Integer> model) {
		this(model, null);
	}

	@Override
	public Boolean get(Unit unit) {
		assert !unit.isNeg() : "unit cannot be negative";
		int constrid = unit.getMiniSatVar();
		for (int i = 0; i < model.size(); i++) {
			if (model.get(i) == constrid || model.get(i) == -constrid) {
				assert model.get(i) != 0;
				if (model.get(i) < 0) {
					assert false;
					return false;
				} else {
					return true;
				}
			}
		}
		return false; // By default if the literal is not in the model it is set to false
	}

	@Override
	public String toString() {
		String res = "(";
		for (Unit unit : revmapping.values()) {
			if (this.get(unit))
				res += unit.toString() + "= 1, ";
			else
				res += unit.toString() + "= 0, ";
		}
		res += ")";

		return res;
	}

	@Override
	public ACQ_IConstraint[] getPositive() {
		ACQ_IConstraint[] res = new ACQ_IConstraint[model.size()];
		for (int i = 0; i < model.size(); i++) {
			Unit u = revmapping.get(model.get(i));
			res[i] = u.getConstraint();
		}
		return res;
	}
}