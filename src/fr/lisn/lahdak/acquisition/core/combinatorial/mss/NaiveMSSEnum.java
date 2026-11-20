package fr.lisn.lahdak.acquisition.core.combinatorial.mss;

import java.util.concurrent.TimeoutException;

import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_IConstraint;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_Network;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintFactory;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_ConstraintSolver;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Bias;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;

public class NaiveMSSEnum extends MSSIter {

	protected ACQ_Network net;
	protected ACQ_Query next;
	protected ACQ_Network notSeen;
	protected ACQ_Bias bias;
	protected ACQ_Bias known; // known relations
	protected ACQ_ConstraintSolver solver;
	
	protected Long timeout;
	protected Long t0;
	
	public NaiveMSSEnum(ACQ_Bias bias, ACQ_Network net, ACQ_ConstraintSolver solver, ACQ_Bias known, Long timeout) throws TimeoutException {
		this.timeout = timeout;
		t0 = System.currentTimeMillis();
		this.solver = solver;
		
		this.net = net;
		notSeen = new ACQ_Network(net.getFactory(), bias.getVars());
		this.known = known;
		
		this.bias = filterbias(bias, net);
		
		next();
		
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
	
	public boolean hasNext() {
		return next != null;
	}
	
	public ACQ_Query solve(ACQ_Network net) {
		return solver.solveQ(concat(net, known.getNetwork()));
	}
	
	public ACQ_Query next() {
		ACQ_Query res = next;
		
		ACQ_Query query = solve(concat(notSeen, net));
		if (query.isEmpty()) {
			next = null;
		}
		else {
			notSeen.add(getMSS(query).getNegation(), true);
			next = query;
		}
		
		return res;
	}
	
	protected ACQ_Network concat(ACQ_Network net1, ACQ_Network net2) {
		ConstraintFactory fact = bias.getNetwork().getFactory();
		ACQ_Network res = new ACQ_Network(fact, bias.getVars());
		res.addAll(net1, true);
		res.addAll(net2, true);
		return res;
	}
	
	protected ACQ_Network getMSS(ACQ_Query e) {
		ConstraintFactory fact = bias.getNetwork().getFactory();
		ACQ_Network res = new ACQ_Network(fact, e.getScope());
		for (ACQ_IConstraint constr: bias.getConstraints()) {
			if (constr.checker(constr.getProjection(e))) {
				res.add(constr, true);
			}
		}
		return res;
	}
}
