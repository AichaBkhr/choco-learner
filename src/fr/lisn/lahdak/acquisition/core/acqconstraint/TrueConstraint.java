package fr.lisn.lahdak.acquisition.core.acqconstraint;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;

import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;

public class TrueConstraint extends ACQ_Constraint {

    public TrueConstraint() {
        super("True", new int[] {});
        // TODO Auto-generated constructor stub
    }

    @Override
    public ACQ_IConstraint getNegation() {
        return new FalseConstraint();
    }

    @Override
    public Constraint[] getChocoConstraints(Model model, IntVar... intVars) {
        return new Constraint[] {model.trueConstraint()};
    }

    @Override
    public void toReifiedChoco(Model model, BoolVar b, IntVar... intVars) {
        model.falseConstraint().reifyWith(b);

    }

    @Override
    public String getNegName() {
        return "False";
    }

    @Override
    public String toSmtlib() {
        return "true";
    }


    @Override
    public boolean check(int... value) {
        return true;
    }

    @Override
    public boolean check(ACQ_Query query) {
        return true;
    }

}
