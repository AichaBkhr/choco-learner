package fr.lisn.lahdak.acquisition.core.combinatorial.mss;

import fr.lisn.lahdak.acquisition.core.acqconstraint.*;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintFactory.ConstraintSet;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_ConstraintSolver;
import fr.lisn.lahdak.acquisition.core.acqsolver.SATModel;
import fr.lisn.lahdak.acquisition.core.acqsolver.SATSolver;
import fr.lisn.lahdak.acquisition.core.combinatorial.CombinationIterator;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Bias;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;
import fr.lisn.lahdak.acquisition.core.oracle.ACQ_Oracle;
import fr.lisn.lahdak.acquisition.core.oracle.Answer;
import fr.lisn.lahdak.acquisition.core.tools.Chrono;

import java.util.*;
import java.util.concurrent.TimeoutException;

public class DQueryGen_1N extends MSSIter {

	protected ACQ_Network basenet;
	protected ACQ_Query next;
	protected ACQ_Network notSeen;
	protected ACQ_Bias bias;
	protected ConstraintMapping mapping;
	protected int distancemin;
	protected ACQ_ConstraintSolver solver;
	protected SATSolver satsolver;
	protected Deque<ACQ_Network> queueS = new LinkedList<>();
	protected Set<ACQ_Network> visited;
	protected ArrayList<ACQ_Network> muses = new ArrayList<>();
	protected Answer mainclassif;
	protected ACQ_Formula formula = new ACQ_Formula();
	protected ACQ_Network MSS;
	protected ACQ_Oracle oracle;
	protected Long timeout;
	protected Long t0;
	protected Chrono chrono;
	protected int nbUC;
	protected Set<ACQ_Network> positiveSeeds = new HashSet<>();

	public DQueryGen_1N(Deque<ACQ_Network> queueS, ACQ_Oracle oracle, Answer mainclassif,
                        ACQ_Bias bias, ACQ_Network net,
                        ACQ_ConstraintSolver solver, SATSolver satsolver,
                        ConstraintMapping mapping,
                        Long timeout, Chrono chrono) throws TimeoutException {

		this.MSS = queueS.poll();
		this.distancemin = 0;
		this.mainclassif = mainclassif;
		this.oracle = oracle;
		this.timeout = timeout;
		this.t0 = System.currentTimeMillis();
		this.solver = solver;
		this.satsolver = satsolver;
		this.chrono = chrono;
		this.basenet = net;
		this.notSeen = new ACQ_Network(net.getFactory(), bias.getVars());

		this.bias = filterbias(bias, net);
		this.mapping = mapping;
		this.formula = new ACQ_Formula();
		this.formula.addClause(blockDown(MSS));

		//if (solver.solve(concat(known.getNetwork(), net)))
		next();
	}


	public DQueryGen_1N(Deque<ACQ_Network> queueS, ACQ_Oracle oracle, ACQ_Formula form,
                        Answer mainclassif, ACQ_Bias bias, ACQ_Network net,
                        ACQ_ConstraintSolver solver, SATSolver satsolver,
                        ConstraintMapping mapping,
                        Long timeout, Chrono chrono) throws TimeoutException {

		this.formula = form;
		this.MSS = queueS.poll();
		this.distancemin = 0;
		this.mainclassif = mainclassif;
		this.oracle = oracle;
		this.timeout = timeout;
		this.t0 = System.currentTimeMillis();
		this.solver = solver;
		this.satsolver = satsolver;
		this.chrono = chrono;
		this.basenet = net;
		this.notSeen = new ACQ_Network(net.getFactory(), bias.getVars());

		this.bias = filterbias(bias, net);
		this.mapping = mapping;
		addNegMUSes(bias);
		//ContradictionSet backgroundKnowledge = createBackgroundKnowledgeAuto(bias, mapping,2);
		//this.formula.addCnf(backgroundKnowledge.toCNF());
		//if (solver.solve(concat(known.getNetwork(), net)))
		next();
	}
	public void addNegMUSes(ACQ_Bias b) {
		ConstraintFactory fact = bias.getNetwork().getFactory();
		Set<ACQ_IConstraint> seen = new HashSet<>();
		for(ACQ_IConstraint c : this.bias.getConstraints()) {
			ACQ_Network initMUSes = new ACQ_Network(fact, bias.getVars() );
			if(!seen.contains(c) && !seen.contains(c.getNegation())) {
				initMUSes.add(c, true);
				initMUSes.add(c.getNegation(), true);
				if(!formula.getClauses().contains(initMUSes)) {
					this.muses.add(initMUSes);
					this.formula.addClause(blockUp(initMUSes));
					seen.add(c);
				}
			}
		}
	}
	private boolean satisfiable(ACQ_Network net, ACQ_Bias bias) {
		return !solver.solveQ(net).isEmpty();
	}
	public ContradictionSet createBackgroundKnowledgeAuto(ACQ_Bias bias, ConstraintMapping mapping, int lmax) {
		ConstraintFactory constraintfactory = bias.getNetwork().getFactory();

		ContradictionSet res = new ContradictionSet(constraintfactory, bias.getVars(), mapping);


		int n = bias.getSize();
		for(int l=1; l <= lmax ; l++) {
			CombinationIterator iter = new CombinationIterator(n, l);

			while(iter.hasNext()) {
				int[] indices = iter.next();
				ConstraintSet constraints = constraintfactory.createSet();
				for(int i : indices) {
					constraints.add(bias.getConstraints().get_Constraint(i));
				}
				ACQ_Network C = new ACQ_Network(constraintfactory, constraints);
				if(!satisfiable(C,bias)) {
					continue;
				}
				for(ACQ_IConstraint c : bias.getConstraints()) {
					ACQ_Network ruleCorrectness = new ACQ_Network(constraintfactory, constraints);
					ruleCorrectness.add(c.getNegation(), true);
					if(!satisfiable(ruleCorrectness,bias)) {
						res.add(new Contradiction(ruleCorrectness));
					}
				}
			}

		}
		return res;
	}
	private ACQ_Bias filterbias(ACQ_Bias bias, ACQ_Network net) throws TimeoutException {
		ACQ_Bias res = bias.copy();
		for (ACQ_IConstraint c : bias.getNetwork()) {
			istimeouted();
			if (net.contains(c)) {
				res.reduce(c);
			}
		}
		return res;
	}

	protected boolean istimeouted() throws TimeoutException {
		if (this.timeout != null &&
				(this.timeout <= (System.currentTimeMillis() - t0))) {
			throw new TimeoutException();
		}
		return false;
	}

	private ACQ_Query solve(ACQ_Network net) {
		return solver.solveQ(concat(basenet, net));
	}

	private boolean satisfiable(ACQ_Network net) {
		return !solver.solveQ(concat(basenet, net)).isEmpty();
	}

	protected ACQ_Network concat(ACQ_Network net1, ACQ_Network net2) {
		ConstraintFactory fact = bias.getNetwork().getFactory();
		ACQ_Network res = new ACQ_Network(fact, bias.getVars());
		res.addAll(net1, true);
		res.addAll(net2, true);
		return res;
	}

	public boolean hasNext() {
		return next != null;
	}

	public ACQ_Formula getFormula() {
		return this.formula;
	}

	public ACQ_Query getLastQuery() {
		return this.next;
	}

	public ArrayList<ACQ_Network> getMuses() {
		return muses;
	}

	public Deque<ACQ_Network> getQueueS() {
		return queueS;
	}

	public int distance(ACQ_Network MSS1, ACQ_Network MSS2) {
		int d = 0;
		for (ACQ_IConstraint c : MSS1.getConstraints()) {
			if (!MSS2.contains(c)) d += 1;
		}
		return d;
	}

	public ACQ_Network notInMSS(ACQ_Network MSS) {
		ACQ_Network MSSnegC = new ACQ_Network(MSS.getFactory());
		for (ACQ_IConstraint c : bias.getConstraints()) {
			if (!MSS.contains(c)) MSSnegC.add(c, true);
		}
		return MSSnegC;
	}

	public ACQ_Network remNotInBias(ACQ_Network m, ACQ_Bias b) {
		ACQ_Network res = new ACQ_Network(m.getFactory());
		for (ACQ_IConstraint c : m) {
			if (b.getNetwork().contains(c)) {
				res.add(c, true);
			}
		}
		return res;
	}

	public ACQ_Formula toFormula(ACQ_Network M) {
		ACQ_Formula res = new ACQ_Formula();
		for (ACQ_IConstraint c : M.getConstraints()) {
			ACQ_Clause clause = new ACQ_Clause();
			Unit clause_unit = mapping.get(c);
			clause.add(clause_unit);
			res.addClause(clause);
		}
		return res;
	}

	public ACQ_Formula toFormula(ConstraintSet M) {
		ACQ_Formula res = new ACQ_Formula();
		for (ACQ_IConstraint c : M) {
			ACQ_Clause clause = new ACQ_Clause();
			Unit clause_unit = mapping.get(c);
			clause.add(clause_unit);
			res.addClause(clause);
		}
		return res;
	}

	public ACQ_Query next() throws TimeoutException {

		chrono.start("enum_mss");
		chrono.start("gen_query_N2");
		chrono.start("pos_query_N2");
		chrono.start("neg_query_N2");
		ACQ_Query res = next;


		while (true) {
			istimeouted();
			ACQ_Network MSSneg = notInMSS(MSS);
			//---------------------min Delta :-----------------
			//SATModel model = satsolver.maxsat(formula, toFormula(MSS), toFormula(MSSneg));
			//---------------------max Delta :----------------------
			SATModel model = satsolver.maxsat(formula, toFormula(MSSneg), toFormula(MSS));
			//----------------------- min if positive and max if negative------------------
			/*boolean isPositive = positiveSeeds.contains(MSS);

			SATModel model;
			if (isPositive) {
				// min Delta
				model = satsolver.maxsat(formula, toFormula(MSS), toFormula(MSSneg));
			} else {
				// max Delta
				model = satsolver.maxsat(formula, toFormula(MSSneg), toFormula(MSS));
			}*/
			if (satsolver.isTimeoutReached()) {
				assert false : "Sat solver timeouted";
				chrono.stop("enum_mss");
				return null;
			}

			if (model != null) {
				ACQ_Network seed = toNetwork(model);
				int dis = 0;
				for (ACQ_IConstraint c : MSS.getConstraints()) {
					if (!seed.contains(c)) {
						dis++;
					}
				}
				seed = remNotInBias(seed, bias);
				ACQ_Query query = solve(seed);
				if (!query.isEmpty()) {

					assert seed.size() == MSS.size() : "seed is not an MSS";
					formula.addClause(blockDown(seed));
					chrono.stop("gen_query_N2");
					chrono.start("gen_query_N2");

					Answer answer = oracle.ask(query);
					query.classify(answer);
					next = query;

					/*if (!queueS.contains(seed)) {
						if (answer == Answer.NO) {
							queueS.addFirst(seed);
							chrono.stop("pos_query_N2");
							chrono.start("pos_query_N2");
						} else {
							queueS.addLast(seed);
							positiveSeeds.add(seed);
							chrono.stop("neg_query_N2");
							chrono.start("neg_query_N2");
						}
					}

					int d = distance(MSS, seed);

					if (distancemin == 0) {
						distancemin = d;
					}
					if (d > distancemin) {
						if (!queueS.isEmpty()) {
							MSS = queueS.poll();
							distancemin = 0;
							break;
						} else {
							next = null;
							break;
						}
					}*/

					MSS = seed ;

					break;
				} else {
					ACQ_Network mus = shrink(seed, bias);
					muses.add(mus);
					formula.addClause(blockUp(mus));
				}
			} else {
				next = null;
				break;
			}
		}

		chrono.stop("enum_mss");
		return res;
	}

	public int getNbUC(){
		return nbUC;
	}

	protected ACQ_Network grow(ACQ_Network seed, ACQ_Bias bias) throws TimeoutException {
		ACQ_Bias diff = bias.copy();
		diff.reduce(seed.getConstraints());

		for (ACQ_IConstraint c : diff.getNetwork()) {
			istimeouted();
			ConstraintSet cset = seed.getFactory().createSet(seed.getConstraints());
			cset.add(c);
			ACQ_Network union = new ACQ_Network(seed.getFactory(), cset);
			if (satisfiable(union)) {
				seed.add(c, true);
			}
		}

		if (seed.isEmpty()) return null;
		else return seed;
	}

	protected ACQ_Network grow(ACQ_Query query, ACQ_Network seed, ACQ_Bias bias) throws TimeoutException {
		ACQ_Bias diff = bias.copy();
		diff.reduce(seed.getConstraints());

		for (ACQ_IConstraint c : diff.getNetwork()) {
			istimeouted();
			if (c.checker(c.getProjection(query))) {
				seed.add(c, true);
			}
		}

		if (seed.isEmpty()) return null;
		else return seed;
	}

	protected ACQ_Network shrink(ACQ_Network seed, ACQ_Bias C) throws TimeoutException {
		ConstraintFactory fact = bias.getNetwork().getFactory();
		ACQ_Network res = new ACQ_Network(fact, bias.getVars());
		res.addAll(seed, true);

		for (ACQ_IConstraint constr : seed) {
			istimeouted();
			ACQ_Network seedminus = new ACQ_Network(fact, bias.getVars());
			seedminus.addAll(res, true);
			seedminus.remove(constr);

			if (!satisfiable(seedminus)) {
				res.remove(constr);
			}
		}

		return res;
	}

	private ACQ_Clause blockDown(ACQ_Network mss) {
		ACQ_Clause res = new ACQ_Clause();
		for (ACQ_IConstraint constr : bias.getNetwork()) {
			if (!mss.contains(constr)) {
				res.add(mapping.get(constr));
			}
		}
		return res;
	}

	private ACQ_Clause blockUp(ACQ_Network mss) {
		ACQ_Clause res = new ACQ_Clause();
		for (ACQ_IConstraint constr : mss) {
			Unit u = mapping.get(constr).clone();
			u.setNeg();
			res.add(u);
		}
		return res;
	}

	protected ACQ_Network toNetwork(SATModel model) {
		assert (model != null);
		ACQ_Network network = new ACQ_Network(bias.getNetwork().getFactory(), bias.getVars());
		for (ACQ_IConstraint constr : model.getPositive()) {
			assert constr != null;
			network.add(constr, true);
		}
		return network;
	}
}