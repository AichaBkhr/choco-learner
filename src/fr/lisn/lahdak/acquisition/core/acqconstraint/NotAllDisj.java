package fr.lisn.lahdak.acquisition.core.acqconstraint;

import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Scope;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;

import java.util.Set;

public class NotAllDisj extends ACQ_Constraint {
    private final int nbTasks;
    private final String negation;

    public NotAllDisj(ACQ_Scope scope, String negation_name) {
        super("NotDisj", scope.getVariables().stream().toArray());
        this.nbTasks = scope.size() / 2;
        this.negation = negation_name;
    }

    @Override
    public ACQ_IConstraint getNegation() {
        return new AllDisj(this.getScope(), this.getName());
    }

    @Override
    public String getNegName() {
        return negation;
    }

    @Override
    public boolean check(int... values) {
        // au moins une paire se chevauche
        for (int i = 0; i < nbTasks; i++) {
            int oi = values[2 * i];
            int di = values[2 * i + 1];
            if (di <= 0) continue;
            for (int j = i + 1; j < nbTasks; j++) {
                int oj = values[2 * j];
                int dj = values[2 * j + 1];
                if (dj <= 0) continue;
                if (oi + di > oj && oj + dj > oi) return true;
            }
        }
        return false;
    }

    @Override
    public Constraint[] getChocoConstraints(Model model, IntVar... intVars) {
        int[] vars = this.getVariables();
        BoolVar[] pairBools = new BoolVar[nbTasks * (nbTasks - 1) / 2];
        int idx = 0;
        for (int i = 0; i < nbTasks; i++) {
            IntVar oi = intVars[vars[2 * i]];
            IntVar di = intVars[vars[2 * i + 1]];
            for (int j = i + 1; j < nbTasks; j++) {
                IntVar oj = intVars[vars[2 * j]];
                IntVar dj = intVars[vars[2 * j + 1]];
                // an overlap needs both durations > 0 and neither task finishing before the other starts
                BoolVar iNotEmpty = model.arithm(di, ">", 0).reify();
                BoolVar jNotEmpty = model.arithm(dj, ">", 0).reify();
                BoolVar IBeforeJ = model.arithm(oi, "+", di, "<=", oj).reify();
                BoolVar JBeforeI = model.arithm(oj, "+", dj, "<=", oi).reify();
                BoolVar noOrder = model.arithm(IBeforeJ, "+", JBeforeI, "=", 0).reify();
                pairBools[idx++] = model.sum(new BoolVar[] { iNotEmpty, jNotEmpty, noOrder }, "=", 3).reify();
            }
        }
        return new Constraint[]{ model.sum(pairBools, ">=", 1) };
    }


    // les autres méthodes abstraites
    @Override public void toReifiedChoco(Model model, BoolVar b, IntVar... intVars) {}
    @Override public String toSmtlib() { return ""; }
    @Override public boolean check(ACQ_Query query) {
        return check(this.getProjection(query));
    }
}