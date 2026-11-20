package fr.lisn.lahdak.acquisition.core.learners.algorithms;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_Clause;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_DisjunctionConstraint;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_Formula;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_IConstraint;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_Network;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintFactory;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintMapping;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ContradictionSet;
import fr.lisn.lahdak.acquisition.core.acqconstraint.Unit;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintFactory.ConstraintSet;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_ConstraintSolver;
import fr.lisn.lahdak.acquisition.core.acqsolver.SATSolver;
import fr.lisn.lahdak.acquisition.core.combinatorial.mss.MARCO;
import fr.lisn.lahdak.acquisition.core.combinatorial.mss.MSSIter;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Bias;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Scope;
import fr.lisn.lahdak.acquisition.core.oracle.ACQ_Oracle;
import fr.lisn.lahdak.acquisition.core.oracle.Answer;
import fr.lisn.lahdak.acquisition.core.tools.Chrono;

public class ACQ_DCA {

    protected ACQ_Bias atomic = null;
    public ConstraintMapping mapping = null;
    protected SATSolver satSolver;
    protected ACQ_ConstraintSolver solver;
    protected ACQ_Oracle oracle;
    protected ACQ_Network learned_dnf = null;
    protected Long learningtimeout;
    protected Long t0;
    protected ArrayList<ACQ_Network> strategy = null;
    protected boolean verbose = false;
    public boolean timeouted = false;
    Chrono chrono;
    protected ArrayList<ACQ_Network> muses = null;
    protected ACQ_Network learned_dnf_MG = null;
    protected ArrayList<String> learnedNegMSSes = new ArrayList<>();
    protected ArrayList<String> learnedPosMSSes = new ArrayList<>();
    protected ACQ_Formula seedFormula = null;
    protected ACQ_Query firstQuery = null;

    public void setFirstQuery(ACQ_Query q) {
        this.firstQuery = q;
    }

    public void seedKnownConfigs(List<ACQ_Network> knownPositiveFull, List<ACQ_Network> knownNegative) {
        ACQ_Formula f = new ACQ_Formula();
        if (knownPositiveFull != null) {
            for (ACQ_Network pos : knownPositiveFull) f.addClause(blockDownFor(pos));
        }
        if (knownNegative != null) {
            for (ACQ_Network neg : knownNegative) f.addClause(blockUpFor(neg));
        }
        this.seedFormula = f;
    }

    private ACQ_Clause blockDownFor(ACQ_Network fullConfig) {
        ACQ_Clause res = new ACQ_Clause();
        for (ACQ_IConstraint c : atomic.getConstraints()) {
            if (!fullConfig.contains(c)) res.add(mapping.get(c));
        }
        return res;
    }

    private ACQ_Clause blockUpFor(ACQ_Network pattern) {
        ACQ_Clause res = new ACQ_Clause();
        for (ACQ_IConstraint c : pattern) {
            Unit u = mapping.get(c).clone();
            u.setNeg();
            res.add(u);
        }
        return res;
    }

    public ACQ_DCA(ACQ_Bias bias, ACQ_Oracle oracle, SATSolver sat, ACQ_ConstraintSolver solv) {
        this.oracle = oracle;
        satSolver = sat;
        solver = solv;
        atomic = bias;
        mapping = new ConstraintMapping();

        for (ACQ_IConstraint c : bias.getConstraints()) {
            String newvarname = c.getName() + c.getVariables();
            Unit unit = this.satSolver.addVar(c, newvarname);
            this.mapping.add(c, unit);

            ACQ_IConstraint neg = c.getNegation();
            if (!bias.getNetwork().contains(neg)) {
                newvarname = neg.getName() + neg.getVariables();
                unit = this.satSolver.addVar(neg, newvarname);
                this.mapping.add(neg, unit);
            }
        }
    }

    public ACQ_Bias getBias() {
        return this.atomic;
    }

    public ACQ_Network getLearnedNetwork() {
        return this.learned_dnf;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setLearningTimeout(Long tm) {
        this.learningtimeout = tm;
    }

    public void setStrat(ArrayList<ACQ_Network> strat) {
        this.strategy = strat;
    }

    public void setBackgroundKnowledge(ContradictionSet back) {
        // pas utilisé dans DCA projet 2
    }

    public void setLog_queries(boolean log) {
        // pas utilisé
    }

    public void setMaxRand(int maxRand) {
        // pas utilisé
    }

    public double getPreprocessDiminution() {
        return 0.0;
    }

    public Boolean process(Chrono chrono, int maxQueries) throws Exception {
        this.learningtimeout = (long) maxQueries * 1000;
        return process(chrono);
    }

    public Boolean process(Chrono chronom) throws Exception {
        t0 = System.currentTimeMillis();
        chrono = chronom;
        chrono.start("pos_queries");
        chrono.start("neg_queries");


        ConstraintFactory fact = this.atomic.getNetwork().getFactory();
        ACQ_Network res = new ACQ_Network(fact, this.atomic.getNetwork().getVariables());
        ACQ_Network empty = new ACQ_Network(fact, this.atomic.getNetwork().getVariables());
        if (firstQuery != null) {
            Answer answer = oracle.ask(firstQuery);
            ACQ_Network firstMSS = firstQuery.getM(atomic);
            if (answer != Answer.YES) {
                res.add(removeMSS(firstQuery, atomic), true);
                learnedNegMSSes.add(firstMSS.toSmtlib());
                chrono.stop("neg_queries");
                chrono.start("neg_queries");
            } else {
                learnedPosMSSes.add(firstMSS.toSmtlib());
                chrono.stop("pos_queries");
                chrono.start("pos_queries");
            }
            if (seedFormula == null) seedFormula = new ACQ_Formula();
            seedFormula.addClause(blockDownFor(firstMSS));
        }
        MSSIter iter;
        try {
            if (seedFormula != null) {
                iter = new MARCO(atomic, seedFormula, solver, satSolver, mapping, learningtimeout, chrono);
            } else {
                iter = new MARCO(atomic, empty, solver, satSolver, mapping, learningtimeout, chrono);
            }
        } catch (TimeoutException e) {
            timeouted = true;
            return false;
        }

        while (iter.hasNext()) {
            if (System.currentTimeMillis() - t0 > learningtimeout) {
                timeouted = true;
                break;
            }

            chrono.start("gen_query");
            ACQ_Query query;
            try {
                query = iter.next();
            } catch (TimeoutException e) {
                timeouted = true;
                break;
            }
            chrono.stop("gen_query");

            if (verbose)
                System.out.println(query.toString());

            Answer answer = oracle.ask(query);

            if (verbose)
                System.out.println("::" + answer);


            if (answer != Answer.YES) {
                ACQ_IConstraint toadd = removeMSS(query, atomic);
                res.add(toadd, true);
                ACQ_Network MSSneg = query.getM(atomic);
                learnedNegMSSes.add(MSSneg.toSmtlib());
                chrono.stop("neg_queries");
                chrono.start("neg_queries");
            } else {
                learnedPosMSSes.add(query.getM(atomic).toSmtlib());
                chrono.stop("pos_queries");
                chrono.start("pos_queries");
            }
        }

        this.learned_dnf = res;
        this.muses = iter.getMuses();
        return timeouted;
    }
    public ArrayList<ACQ_Network> getMuses() {
        return this.muses;
    }
    public ArrayList<String> getLearnedNegMSSes() {
        return this.learnedNegMSSes;
    }
    public ArrayList<String> getLearnedPosMSSes() {
        return this.learnedPosMSSes;
    }

    protected ACQ_DisjunctionConstraint removeMSS(ACQ_Query e, ACQ_Bias bias) {
        ConstraintFactory fact = bias.getNetwork().getFactory();
        ConstraintSet set = fact.createSet();
        for (ACQ_IConstraint c : bias.getConstraints()) {
            if (c.checker(c.getProjection(e))) {
                set.add(c.getNegation());
            }
        }
        return new ACQ_DisjunctionConstraint(fact, set);
    }
}