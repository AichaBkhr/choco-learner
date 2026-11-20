package fr.lisn.lahdak.acquisition.core.acqconstraint;

import java.util.HashMap;

public class ConstraintMapping {
	protected HashMap<ACQ_IConstraint, Unit> mapping;

	public ConstraintMapping() {
		mapping = new HashMap<ACQ_IConstraint, Unit>();
	}

	public void add(ACQ_IConstraint constr, Unit unit) {
		assert (!unit.isNeg());
		Unit u = mapping.put(constr, unit);
		assert u == null : "mapping where already containing an entry for constr";
	}

	public Unit get(ACQ_IConstraint constr) {
		Unit res = mapping.get(constr);
		assert (!res.isNeg());
		return res;
	}

	public Iterable<Unit> values() {
		return mapping.values();
	}

	// to test conacq2
	public Boolean contains(ACQ_IConstraint constr) {
		return mapping.containsKey(constr);
	}

	public int size() {
		return mapping.size();
	}
}
