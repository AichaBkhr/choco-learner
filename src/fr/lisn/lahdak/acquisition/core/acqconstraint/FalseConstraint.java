package fr.lisn.lahdak.acquisition.core.acqconstraint;


import org.chocosolver.solver.Model;
import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;

import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;

public class FalseConstraint extends ACQ_Constraint {

    public FalseConstraint() {
        super("False", new int[] {});
        // TODO Auto-generated constructor stub
    }

    @Override
    public ACQ_IConstraint getNegation() {
        return new TrueConstraint();
    }

    @Override
    public Constraint[] getChocoConstraints(Model model, IntVar... intVars) {
        return new Constraint[] {model.falseConstraint()};
    }

    @Override
    public void toReifiedChoco(Model model, BoolVar b, IntVar... intVars) {
        model.falseConstraint().reifyWith(b);

    }

    @Override
    public String getNegName() {
        return "True";
    }

    @Override
    public String toSmtlib() {
        return "";
    }

    @Override
    public boolean check(int... value) {
        return false;
    }

    @Override
    public boolean check(ACQ_Query query) {
        return false;
    }

}

