package fr.lisn.lahdak.acquisition.core.combinatorial.mss;

import java.util.ArrayList;

import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_IConstraint;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_Network;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintFactory;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ContradictionSet;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintFactory.ConstraintSet;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_ConstraintSolver;
import fr.lisn.lahdak.acquisition.core.acqsolver.SATSolver;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Bias;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;
import fr.lisn.lahdak.acquisition.core.oracle.Answer;
import fr.lisn.lahdak.acquisition.core.oracle.ObservedOracle;
import fr.lisn.lahdak.acquisition.core.tools.Chrono;

public class MSS {

	// protected HashMap<ACQ_Query, Boolean> queries = new HashMap<>();
	protected ArrayList<ACQ_Query> queries = new ArrayList<>();
	protected ACQ_Bias atomic = null;
	protected SATSolver satSolver;
	protected ACQ_ConstraintSolver solver;
	protected ObservedOracle observedOracle;
	protected ArrayList<ACQ_Network> learned_dnf = null;
	protected int ndisj = 0;
	Chrono chrono;

	protected Long learningtimeout;
	protected Long t0;
	protected int max_random = 0;
	protected ArrayList<ACQ_Network> strategy = null;
	protected ContradictionSet backgroundKnowledge = null;
	protected boolean verbose = false;
	public boolean timeouted = false;

	public MSS(ObservedOracle obs, SATSolver sat, ACQ_ConstraintSolver solv) {
		observedOracle = obs;
		satSolver = sat;
		solver = solv;

	}

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	public void setLearningTimeout(Long tm) {
		this.learningtimeout = tm;
	}

	public void setMaxRand(int max) {
		this.max_random = max;
	}

	public void setStrat(ArrayList<ACQ_Network> strat) {
		this.strategy = strat;
	}

	public void setBackgroundKnowledge(ContradictionSet back) {
		this.backgroundKnowledge = back;
	}

	public ArrayList<ACQ_Network> getLearnedDNF() {
		return this.learned_dnf;
	}

	public Boolean process(Chrono chronom, ACQ_Bias a) throws Exception {
		t0 = System.currentTimeMillis();
		chrono = chronom;
		this.atomic = a;
		ArrayList<ACQ_Network> precond = new ArrayList<>();

		ConstraintFactory fact = this.atomic.getNetwork().getFactory();
		ACQ_Network empty = new ACQ_Network(fact, this.atomic.getNetwork().getVariables());

		MSSIterDAA iter = new MSSIterDAA(this.atomic, empty);
		while (iter.hasNext() && System.currentTimeMillis() - t0 <= learningtimeout) {
			ACQ_Network mss = iter.next();
			ACQ_Query query = solver.solveQ(mss);
			Answer answer = observedOracle.ask(query, true);
			if (verbose)
				System.out.println(query);
			if (answer == Answer.YES) {
				precond.add(mss);
			}

		}
		this.timeouted = iter.hasNext();
		this.learned_dnf = precond;
		return false;

	}

	protected boolean isMSS(ACQ_Network net, ACQ_Bias bias) {
		for (ACQ_IConstraint c : bias.getNetwork()) {
			if (net.contains(c))
				continue;
			ConstraintFactory fact = net.getFactory();
			ACQ_Network all = new ACQ_Network(fact, net, net.getVariables());
			all.add(c, true);
			if (solver.solve(all)) {
				return false;
			}
		}
		return true;
	}

	class MSSIterDAA {

		protected ArrayList<ACQ_Network> mcses = new ArrayList<>();
		protected ArrayList<ACQ_Network> muses = new ArrayList<>();
		protected ACQ_Network seed;
		protected ACQ_Network basenet;
		protected boolean haveSeed;
		protected ACQ_Bias bias;
		protected ACQ_Network next;

		public MSSIterDAA(ACQ_Bias bias, ACQ_Network net) {
			haveSeed = true;
			this.bias = bias.copy();
			this.bias.reduce(net.getConstraints());
			basenet = net;

			seed = new ACQ_Network(this.bias.getNetwork().getFactory());
			next = grow(seed, this.bias);

		}

		public boolean hasNext() {
			return next != null;
		}

		public ACQ_Network next() {
			ACQ_Network mss = getComplete(nextMSS());
			assert isMSS(mss, bias);
			return mss;
		}

		public ACQ_Network nextMSS() {

			ACQ_Bias diff = bias.copy();
			diff.reduce(next.getConstraints());
			mcses.add(diff.getNetwork());
			haveSeed = false;

			ArrayList<ACQ_Network> toiter = hittingSets(dup(mcses));
			toiter.removeAll(muses);
			for (ACQ_Network candidate : toiter) {
				boolean sat = satisfiable(candidate);
				if (sat) {
					seed = candidate;
					haveSeed = true;
					break;
				} else {
					muses.add(candidate);
				}
			}

			ACQ_Network res = next;
			next = haveSeed ? grow(seed, bias) : null;
			return res;

		}

		public ACQ_Network getComplete(ACQ_Network value) {
			ConstraintFactory fact = value.getFactory();
			ACQ_Network all = new ACQ_Network(fact, value, value.getVariables());
			all.addAll(this.basenet, true);
			return all;
		}

		public boolean satisfiable(ACQ_Network net) {
			return solver.solve(getComplete(net));
		}

		protected ACQ_Network grow(ACQ_Network seed, ACQ_Bias bias) {
			ACQ_Bias diff = bias.copy();
			diff.reduce(seed.getConstraints());

			for (ACQ_IConstraint c : diff.getNetwork()) {
				ConstraintSet cset = seed.getFactory().createSet(seed.getConstraints());
				cset.add(c);
				ACQ_Network union = new ACQ_Network(seed.getFactory(), cset);
				if (satisfiable(union)) {
					seed.add(c, true);
				}
			}

			if (seed.isEmpty()) {
				return null;
			} else {
				return seed;
			}
		}

		protected <T> ArrayList<T> dup(ArrayList<T> l) {
			ArrayList<T> res = new ArrayList<>();
			for (T e : l) {
				res.add(e);
			}
			return res;
		}

		protected ArrayList<ACQ_Network> hittingSets(ArrayList<ACQ_Network> sets) {
			assert sets.size() > 0;
			ACQ_Network elem = sets.remove(0);
			ArrayList<ACQ_Network> elemset = new ArrayList<>();

			for (ACQ_IConstraint c : elem) {
				ConstraintSet cset = elem.getFactory().createSet(c);
				ACQ_Network newnet = new ACQ_Network(elem.getFactory(), elem.getVariables(), cset);
				elemset.add(newnet);
			}

			if (sets.size() == 0) {
				return elemset;
			} else {
				return minimum(cross(hittingSets(sets), elemset));
			}
		}

		protected ArrayList<ACQ_Network> minimum(ArrayList<ACQ_Network> g) {
			ArrayList<ACQ_Network> res = new ArrayList<ACQ_Network>();
			for (ACQ_Network s : g) {
				boolean toadd = true;
				for (ACQ_Network t : g) {
					if (issubset(t, s) && !t.equals(s)) {
						toadd = false;
						break;
					}
				}
				if (toadd) {
					res.add(s);
				}
			}
			return res;
		}

		protected boolean issubset(ACQ_Network l1, ACQ_Network l2) {
			for (ACQ_IConstraint a : l1) {
				boolean in = false;
				for (ACQ_IConstraint b : l2) {
					if (a.equals(b)) {
						in = true;
						break;
					}
				}
				if (!in)
					return false;
			}
			return true;
		}

		protected ArrayList<ACQ_Network> cross(ArrayList<ACQ_Network> a, ArrayList<ACQ_Network> b) {
			ArrayList<ACQ_Network> res = new ArrayList<ACQ_Network>();
			for (ACQ_Network ai : a) {
				for (ACQ_Network bj : b) {
					ConstraintSet cset = ai.getFactory().createSet(ai.getConstraints());
					cset.addAll(bj.getConstraints());
					ACQ_Network newnet = new ACQ_Network(ai.getFactory(), cset);
					res.add(newnet);
				}
			}
			return res;
		}

	}
}
