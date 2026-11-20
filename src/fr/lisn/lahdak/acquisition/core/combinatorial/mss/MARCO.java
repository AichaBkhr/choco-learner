package fr.lisn.lahdak.acquisition.core.combinatorial.mss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_Clause;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_Formula;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_IConstraint;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_Network;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintFactory;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintFactory.ConstraintSet;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintMapping;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ContradictionSet;
import fr.lisn.lahdak.acquisition.core.acqconstraint.Unit;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_ConstraintSolver;
import fr.lisn.lahdak.acquisition.core.acqsolver.SATModel;
import fr.lisn.lahdak.acquisition.core.acqsolver.SATSolver;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Bias;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Scope;
import fr.lisn.lahdak.acquisition.core.tools.Chrono;

public class MARCO extends MSSIter {
	
	protected ACQ_Network basenet;
	protected ACQ_Query next;
	protected ACQ_Network notSeen;
	protected ACQ_Bias bias;
	protected ConstraintMapping mapping;
	protected ACQ_ConstraintSolver solver;
	protected SATSolver satsolver;
	
	protected ACQ_Formula formula;
	
	protected Long timeout;
	protected Long t0;
	protected ArrayList<ACQ_Network> muses = new ArrayList<>();
	public MARCO(ACQ_Bias bias, ACQ_Network net, 
			ACQ_ConstraintSolver solver, SATSolver satsolver, 
			ConstraintMapping mapping,
			Long timeout, Chrono chrono) throws TimeoutException {
		this.timeout = timeout;
		this.t0 = System.currentTimeMillis();
		this.solver = solver;
		this.satsolver = satsolver;
		this.chrono = chrono;
		
		this.basenet = net;
		this.notSeen = new ACQ_Network(net.getFactory(), bias.getVars());
		
		this.bias = filterbias(bias, basenet);
		this.mapping = mapping;


		this.formula = new ACQ_Formula();

		addNegMUSes(bias);
		
		//if (solver.solve(concat(known.getNetwork(), basenet)))
		next();
		
	}
	public MARCO(ACQ_Bias bias, ACQ_Formula net,
				 ACQ_ConstraintSolver solver, SATSolver satsolver,
				 ConstraintMapping mapping,
				 Long timeout, Chrono chrono) throws TimeoutException {
		this.timeout = timeout;
		this.t0 = System.currentTimeMillis();
		this.solver = solver;
		this.satsolver = satsolver;
		this.chrono = chrono;

		this.basenet = new ACQ_Network(bias.getNetwork().getFactory(), bias.getVars());
		this.notSeen = new ACQ_Network(bias.getNetwork().getFactory(), bias.getVars());
		this.bias = filterbias(bias, basenet);
		this.mapping = mapping;

		formula = net;

		addNegMUSes(bias);

		//if (solver.solve(concat(known.getNetwork(), basenet)))
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
		addSameScopeMUSes(b, fact);
	}

	/**
	 * Blocks every pair of constraints with the same scope that cannot hold together,
	 * e.g. x<y and x>y, where neither is the negation of the other.
	 * */
	private void addSameScopeMUSes(ACQ_Bias b, ConstraintFactory fact) {
		Map<ACQ_Scope, List<ACQ_IConstraint>> byScope = new HashMap<>();
		for (ACQ_IConstraint c : this.bias.getConstraints()) {
			byScope.computeIfAbsent(c.getScope(), k -> new ArrayList<>()).add(c);
		}
		for (List<ACQ_IConstraint> group : byScope.values()) {
			if (group.size() < 2) continue;
			for (int i = 0; i < group.size(); i++) {
				for (int j = i + 1; j < group.size(); j++) {
					ACQ_IConstraint c1 = group.get(i), c2 = group.get(j);
					ACQ_Network pair = new ACQ_Network(fact, bias.getVars());
					pair.add(c1, true);
					pair.add(c2, true);
					if (formula.getClauses().contains(pair)) continue;
					if (solver.solveQ(pair).isEmpty()) {
						this.muses.add(pair);
						this.formula.addClause(blockUp(pair));
					}
				}
			}
		}
	}
	public void setMUS(ContradictionSet muses) {
		formula.addCnf(muses.toCNF());
	}

	public ACQ_Formula getFormula() {
		return this.formula;
	}

	public ACQ_Query getLastQuery() {
		return this.next;
	}

	public ArrayList<ACQ_Network> getMuses() {
		return this.muses;
	}


	
	private ACQ_Bias filterbias(ACQ_Bias bias, ACQ_Network net) throws TimeoutException {
		ACQ_Bias res = bias.copy();
		for (ACQ_IConstraint c : bias.getNetwork()) {
			istimeouted(); // throws an exception if timeout reached
			if (net.contains(c)) {
				res.reduce(c);
			}
			/*else {
				ACQ_Network all = new ACQ_Network(net.getFactory(), net, net.getVariables());
				all.addAll(net, true);
				all.add(c, true);
				if (solve(all).isEmpty()) {
					res.reduce(c);
				}
				else {
					all = new ACQ_Network(net.getFactory(), net, net.getVariables());
					all.addAll(net, true);
					all.add(c.getNegation(), true);
					if (solve(all).isEmpty()) {
						res.reduce(c);
					}
				}
			}*/
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
	
	public ACQ_Query next() throws TimeoutException {
		chrono.start("enum_mss");
		ACQ_Query res = next;
		while (true) {
			istimeouted();
			SATModel model = satsolver.solve(formula);
			if (satsolver.isTimeoutReached()) {
				assert false: "Sat solver timeouted";
				chrono.stop("enum_mss");
				return null;
			}

			if (model != null) {
				ACQ_Network seed = toNetwork(model);

				ACQ_Query query = solve(seed);
				if (!query.isEmpty()) {
					ACQ_Network mss = grow(query,seed, bias);
					next = query;
					formula.addClause(blockDown(mss));
					break;
				}
				else {
					chrono.start("number_of_muses");
					ACQ_Network mus = shrink(seed, bias);
					muses.add(mus);
					formula.addClause(blockUp(mus));
					chrono.stop("number_of_muses");
				}
			}
			else {
				next = null;
				break;
			}
		}
		
		chrono.stop("enum_mss");
		return res;
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
		
		if (seed.isEmpty()) {
			return null;
		}
		else {
			return seed;
		}
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
		//chrono.start("to_network");
		assert(model != null);
		ACQ_Network network = new ACQ_Network(bias.getNetwork().getFactory(), bias.getVars());
		
		for (ACQ_IConstraint constr : model.getPositive()) {
			assert constr != null;
			network.add(constr, true);
		}
		
		//chrono.stop("to_network");
		return network;
	}
	
}
