package fr.lisn.lahdak.acquisition.core.learners.algorithms;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import fr.lisn.lahdak.acquisition.core.acqconstraint.*;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintFactory.ConstraintSet;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_ConstraintSolver;
import fr.lisn.lahdak.acquisition.core.acqsolver.SATSolver;
import fr.lisn.lahdak.acquisition.core.combinatorial.mss.DQueryGen;
import fr.lisn.lahdak.acquisition.core.combinatorial.mss.MARCO;
import fr.lisn.lahdak.acquisition.core.combinatorial.mss.MSSIter;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Bias;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Scope;
import fr.lisn.lahdak.acquisition.core.oracle.ACQ_Oracle;
import fr.lisn.lahdak.acquisition.core.oracle.Answer;
import fr.lisn.lahdak.acquisition.core.tools.Chrono;

public class ACQ_CANDY {

    protected ArrayList<ACQ_Query> queries = new ArrayList<>();
    protected ACQ_Bias atomic = null;
    protected SATSolver satSolver;
    protected ACQ_ConstraintSolver solver;
    protected ACQ_Oracle oracle;
    protected ACQ_Network learned_dnf_MS = null;
    protected ACQ_Network learned_dnf_MG = null;
    public ConstraintMapping mapping = null;
    protected int ndisj = 0;
    protected Chrono chrono;

    protected ArrayList<ACQ_Network> muses = null;
    protected Long learningtimeout;
    protected Long t0;
    protected ArrayList<ACQ_Network> strategy = null;
    protected boolean verbose = false;
    public boolean timeouted = false;
    protected ACQ_Query firstQuery = null;
    protected Answer mainClassif = null;

    /** Bound to prioritize, set with -Dcandy.priority=neg (default, MG) or -Dcandy.priority=pos (MS). */
    public static final Answer PRIORITY =
            "pos".equalsIgnoreCase(System.getProperty("candy.priority", "neg"))
                    ? Answer.YES : Answer.NO;
    protected ACQ_Network firstMSS = null;
    protected ArrayList<String> learnedPosMSSes = new ArrayList<>();
    protected ArrayList<String> learnedNegMSSes = new ArrayList<>();
    protected Boolean bascule = false;
    protected ACQ_Formula seedFormula = null;

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

    public ACQ_CANDY(ACQ_Query q, ACQ_Bias bias,
                     ACQ_Oracle oracle, SATSolver sat, ACQ_ConstraintSolver solv) {

        this.oracle = oracle;
        if (q != null) {
            firstQuery = q;
            mainClassif = oracle.ask(firstQuery);
            firstMSS = firstQuery.getM(bias);
        } else {
            mainClassif = Answer.YES;
        }

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

    public ACQ_Network getLearnedNetworkMG() { //was getLearnedNetwork()
        return this.learned_dnf_MG;
    }
    public ACQ_Network getLearnedNetworkMS() {
        return this.learned_dnf_MS;
    }

    public double getPreprocessDiminution() {
        return 0.0;
    }

    public Boolean process(Chrono chrono, int maxQueries) throws Exception {
        this.learningtimeout = (long) maxQueries * 1000;
        return process(chrono);
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

    public ACQ_Network getLearnedDNFMS() {
        return this.learned_dnf_MS;
    }

    public ACQ_Network getLearnedDNFMG() {
        return this.learned_dnf_MG;
    }

    public Boolean process(Chrono chronom) throws Exception {

        t0 = System.currentTimeMillis();
        chrono = chronom;
        chrono.start("pos_queries");
        chrono.start("neg_queries");

        ConstraintFactory fact = this.atomic.getNetwork().getFactory();
        ACQ_Network resMG = new ACQ_Network(fact, this.atomic.getNetwork().getVariables());
        //resMG.add(new TrueConstraint(),true);
        ACQ_Network resMS = new ACQ_Network(fact, this.atomic.getNetwork().getVariables());
        ACQ_Network empty = new ACQ_Network(fact, this.atomic.getNetwork().getVariables());
        ConstraintSet conjMSS = fact.createSet();

        // init MS, MG
        if (firstQuery != null) {

            if (mainClassif == Answer.YES) {
                ACQ_IConstraint toaddFirstPosMSS = addMSS(firstQuery, atomic);
                conjMSS.add(toaddFirstPosMSS);
                learnedPosMSSes.add(firstQuery.getM(atomic).toSmtlib());
                chrono.stop("pos_queries");
                chrono.start("pos_queries");
            } else {
                ACQ_IConstraint toaddFirstNegMSS = removeMSS(firstQuery, atomic);
                resMG.add(toaddFirstNegMSS,true);
                ACQ_Network MG0 = firstQuery.getM(atomic);
                learnedNegMSSes.add(MG0.toSmtlib());
                chrono.stop("neg_queries");
                chrono.start("neg_queries");
            }
            if (verbose)
                System.out.println(firstQuery.toString() + "::" + mainClassif);
        }

        Deque<ACQ_Network> queueS = new LinkedList<>();
        MSSIter iter = null;
        this.timeouted = false;

        try {
            if (firstQuery == null) {
                if (seedFormula != null) {
                    iter = new MARCO(atomic, seedFormula, solver, satSolver, mapping, learningtimeout, chrono);
                } else {
                    iter = new MARCO(atomic, empty, solver, satSolver, mapping, learningtimeout, chrono);
                }
            } else {
                bascule = true;
                queueS.add(firstMSS);
                iter = new DQueryGen(queueS, oracle, mainClassif, atomic, empty, solver, satSolver, mapping, learningtimeout, chrono);
            }
        } catch (TimeoutException e) {
            this.timeouted = true;
            conjMSS.add(new FalseConstraint());
            resMS.add(new ACQ_DisjunctionConstraint(fact, conjMSS), true);
            this.learned_dnf_MS = resMS;
            this.learned_dnf_MG = resMG;
            this.muses = new ArrayList<>();
            return timeouted;
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
                System.out.print(query.toString());

            Answer answer;
            if (query.getClassification() == Answer.UKN) {
                answer = oracle.ask(query);
            } else {
                answer = query.getClassification();
            }

            if (verbose)
                System.out.println("::" + answer);




            if (PRIORITY == Answer.YES) {
                if (answer == Answer.YES) {
    
                    ACQ_IConstraint toadd = addMSS(query, atomic);
                    conjMSS.add(toadd);
                    learnedPosMSSes.add(query.getM(atomic).toSmtlib());
    
                    queueS.addFirst(query.getM(atomic));
                    chrono.stop("pos_queries");
                    chrono.start("pos_queries");
    
                    if (!bascule) {
                        ACQ_Formula formulaMARCO = iter.getFormula();
                        ACQ_Query lastQuery = iter.getLastQuery();
                        if (lastQuery != null && !lastQuery.isEmpty()) {
                            if (verbose)
                                System.out.print(lastQuery.toString());
    
                            Answer answer2;
                            if (lastQuery.getClassification() == Answer.UKN) {
                                answer2 = oracle.ask(lastQuery);
                            } else {
                                answer2 = lastQuery.getClassification();
                            }
    
                            if (verbose)
                                System.out.println("::" + answer2);
    
                            if (answer2 == Answer.YES) {
                                ACQ_IConstraint toadd2 = addMSS(lastQuery, atomic);
                                conjMSS.add(toadd2);
                                //queueS.addFirst(lastQuery.getM(atomic));
                                queueS.addFirst(lastQuery.getM(atomic));
                                learnedPosMSSes.add(lastQuery.getM(atomic).toSmtlib());
                                chrono.stop("pos_queries");
                                chrono.start("pos_queries");
                            } else {
                                ACQ_IConstraint toadd2 = removeMSS(lastQuery, atomic);
                                resMG.add(toadd2, true);
                                queueS.addLast(lastQuery.getM(atomic));
                                ACQ_Network MG2 = lastQuery.getM(atomic);
                                learnedNegMSSes.add(MG2.toSmtlib());
                                //queueS.addLast(lastQuery.getM(atomic));
                                chrono.stop("neg_queries");
                                chrono.start("neg_queries");
                            }
                        }
                        try{
                            ArrayList<ACQ_Network> previousMuses = iter.getMuses();
                            iter = new DQueryGen(queueS, oracle, formulaMARCO, PRIORITY,
                                    atomic, empty, solver, satSolver, mapping, learningtimeout, chrono);
                            iter.getMuses().addAll(previousMuses);
                        }
                        catch (TimeoutException e) {
                            this.timeouted = true;
                            break;
                        }
                        bascule = true;
                    }
    
                } else {
                    ACQ_IConstraint toadd = removeMSS(query, atomic);
                    //System.out.println("toadd : " + toadd);
                    resMG.add(toadd, true);
    
                    ACQ_Network M_marco = query.getM(atomic);
    
                    queueS.addLast(M_marco);
                    ACQ_Network MG = query.getM(atomic);
                    learnedNegMSSes.add(MG.toSmtlib());
                    chrono.stop("neg_queries");
                    chrono.start("neg_queries");
    
                }
            } else {
            if (answer != Answer.YES) {
                    ACQ_IConstraint toadd = removeMSS(query, atomic);
                    //System.out.println("toadd : " + toadd);
                    resMG.add(toadd, true);
    
                    ACQ_Network M_marco = query.getM(atomic);
                    queueS.addFirst(M_marco);
                    ACQ_Network MG = query.getM(atomic);
                    learnedNegMSSes.add(MG.toSmtlib());
                    chrono.stop("neg_queries");
                    chrono.start("neg_queries");
    
                    if (!bascule) {
                        ACQ_Formula formulaMARCO = iter.getFormula();
                        ACQ_Query lastQuery = iter.getLastQuery();
                        // MARCO has already computed (and blocked) its next query, ask it before switching,
                        // otherwise that MSS is lost (DQueryGen starts from formulaMARCO, where it is blocked)
                        if (lastQuery != null && !lastQuery.isEmpty()) {
                            if (verbose)
                                System.out.print(lastQuery.toString());
    
                            Answer answer2;
                            if (lastQuery.getClassification() == Answer.UKN) {
                                answer2 = oracle.ask(lastQuery);
                            } else {
                                answer2 = lastQuery.getClassification();
                            }
    
                            if (verbose)
                                System.out.println("::" + answer2);
    
                            if (answer2 == Answer.YES) {
                                ACQ_IConstraint toadd2 = addMSS(lastQuery, atomic);
                                conjMSS.add(toadd2);
                                //queueS.addFirst(lastQuery.getM(atomic));
                                queueS.addLast(lastQuery.getM(atomic));
                                learnedPosMSSes.add(lastQuery.getM(atomic).toSmtlib());
                                chrono.stop("pos_queries");
                                chrono.start("pos_queries");
                            } else {
                                ACQ_IConstraint toadd2 = removeMSS(lastQuery, atomic);
                                resMG.add(toadd2, true);
                                queueS.addFirst(lastQuery.getM(atomic));
                                ACQ_Network MG2 = lastQuery.getM(atomic);
                                learnedNegMSSes.add(MG2.toSmtlib());
                                //queueS.addLast(lastQuery.getM(atomic));
                                chrono.stop("neg_queries");
                                chrono.start("neg_queries");
                            }
                        }
                        try{
                            ArrayList<ACQ_Network> previousMuses = iter.getMuses();
                            iter = new DQueryGen(queueS, oracle, formulaMARCO, PRIORITY,
                                    atomic, empty, solver, satSolver, mapping, learningtimeout, chrono);
                            iter.getMuses().addAll(previousMuses);
                        }
                        catch (TimeoutException e) {
                            this.timeouted = true;
                            break;
                        }
                        bascule = true;
                    }
    
                } else {
                    ACQ_IConstraint toadd = addMSS(query, atomic);
                    //System.out.println("toadd addMSS " + toadd);
                    conjMSS.add(toadd);
                    learnedPosMSSes.add(query.getM(atomic).toSmtlib());
    
                    queueS.addLast(query.getM(atomic));
                    chrono.stop("pos_queries");
                    chrono.start("pos_queries");
                }
            }
        }


        ACQ_IConstraint FalseConstraint = new FalseConstraint(); //create false constraint to add it in MS (init MS)
        conjMSS.add(FalseConstraint);
        ACQ_DisjunctionConstraint disjMSS = new ACQ_DisjunctionConstraint(fact, conjMSS); // create a disj of pos MSSes and false constraint
        ACQ_IConstraint oneclause = disjMSS;
        resMS.add(oneclause, true);
        //System.out.println(iter.getMuses().size());
        this.learned_dnf_MS = resMS;
        this.learned_dnf_MG = resMG;
        this.muses = iter.getMuses();
        return timeouted;
    }
    public ArrayList<ACQ_Network> getMuses() {
        return this.muses;
    }

    public ArrayList<String> getLearnedPosMSSes() {
        return this.learnedPosMSSes;
    }

    public ArrayList<String> getLearnedNegMSSes() {
        return this.learnedNegMSSes;
    }

    protected ACQ_ConjunctionConstraint addMSS(ACQ_Query e, ACQ_Bias bias) {
        ConstraintFactory fact = bias.getNetwork().getFactory();
        ConstraintSet set = fact.createSet();
        for (ACQ_IConstraint c : bias.getConstraints()) {
            if (c.checker(c.getProjection(e))) {
                set.add(c);
            }
        }
        return new ACQ_ConjunctionConstraint(fact, set);
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