package fr.lisn.lahdak.acquisition.core.acqconstraint;

import java.util.HashMap;

public class RevConstraintMapping {
    protected HashMap<Integer, Unit> mapping;

    public RevConstraintMapping() {
        this.mapping = new HashMap<Integer, Unit>();
    }

    public void add(Integer i, Unit unit) {
        assert i > 0;
        assert(!unit.isNeg());
        this.mapping.put(i, unit);
    }

    public Unit get(Integer i) {
        assert i > 0;
        Unit res = this.mapping.get(i);
        assert(!res.isNeg());
        return res;
    }

    public Iterable<Unit> values(){
        return this.mapping.values();
    }

    public boolean containsKey(int n) {
        return mapping.containsKey(n);
    }

    public int size() {
        return this.mapping.size();
    }
}