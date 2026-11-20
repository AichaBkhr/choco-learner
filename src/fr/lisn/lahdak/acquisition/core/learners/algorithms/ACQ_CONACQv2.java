package fr.lisn.lahdak.acquisition.core.learners.algorithms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeoutException;

import fr.lisn.lahdak.acquisition.core.acqconstraint.*;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintFactory.ConstraintSet;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_ConstraintSolver;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_IDomain;
import fr.lisn.lahdak.acquisition.core.acqsolver.SATModel;
import fr.lisn.lahdak.acquisition.core.acqsolver.SATSolver;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Bias;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Scope;
import fr.lisn.lahdak.acquisition.core.oracle.ACQ_Oracle;
import fr.lisn.lahdak.acquisition.core.oracle.Answer;
import fr.lisn.lahdak.acquisition.core.tools.Chrono;
import fr.lisn.lahdak.acquisition.core.tools.FileManager;

public class ACQ_CONACQv2 {

	protected ACQ_Bias bias;
	protected ACQ_Bias bias_minus;

	protected ACQ_Oracle oracle;
	protected ACQ_ConstraintSolver constrSolver;
	protected ACQ_IDomain domain;
	protected ACQ_Query firstQuery = null;
	protected boolean firstQueryUsed = false;

	public void setFirstQuery(ACQ_Query q) {
		this.firstQuery = q;
	}
	protected ACQ_CNF T;
	protected boolean verbose = false;
	protected boolean log_queries = false;
	public ConstraintMapping mapping;
	protected SATSolver satSolver;
	protected ConstraintFactory constraintFactory;
	protected ACQ_Network learned_network;
	protected ACQ_Network learned_underapprox_network;
	protected ACQ_Network learned_overapprox_network;

	protected ACQ_Bias knownconstraints = null;
	protected ArrayList<ACQ_Network> strategy = null;
	protected ContradictionSet backgroundKnowledge = null;
	protected Chrono chrono;
	protected Long learningtimeout;
	protected Long t0;
	public boolean timeouted = false;
	protected ArrayList<ACQ_Query> asked = new ArrayList<>();
	protected ArrayList<String> asked_debug = new ArrayList<>();
	protected boolean active = true;
	protected boolean weighted = false;
	protected int max_random = 0;
	protected int n_asked = 0;
	protected int bias_size_before_preprocess;
	protected int bias_size_after_preprocess;
	protected ArrayList<String> listMS = new ArrayList<>();
	protected ArrayList<String> listMG = new ArrayList<>();

	public ACQ_CONACQv2(ACQ_Oracle oracle, ACQ_Bias bias, SATSolver sat, ACQ_ConstraintSolver solv) {
		this.bias = bias;
		this.constraintFactory = bias.network.getFactory();
		this.oracle = oracle;
		this.satSolver = sat;
		this.constrSolver = solv;
		this.domain = solv.getDomain();
		this.mapping = new ConstraintMapping();
		this.chrono = new Chrono("Conacq");

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

		assert mapping.size() >= bias.getSize() : "mapping must contain more elements than bias";
		filter_conjunctions();
		this.bias_minus = bias.copy();
		this.listMS.add(bias_minus.network.toSmtlib());

		bias_size_before_preprocess = bias.getSize();
	}


	protected void filter_conjunctions() {
		for (Unit unit : mapping.values()) {
			ACQ_IConstraint c = unit.getConstraint();
			if (c instanceof ACQ_ConjunctionConstraint) {
				bias.reduce(c);
			}
		}
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

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	public void setLog_queries(boolean logqueries) {
		this.log_queries = logqueries;
	}

	public ACQ_Bias getBias() {
		return bias;
	}

	public ACQ_Network getLearnedNetwork() {
		//return learned_network;
		return learned_overapprox_network;
	}

	public float getPreprocessDiminution() {
		return ((float) (100 * (bias_size_before_preprocess - bias_size_after_preprocess)) / bias_size_before_preprocess);
	}

	protected boolean istimeouted() throws TimeoutException {
		if (this.learningtimeout != null &&
				(this.learningtimeout <= (System.currentTimeMillis() - this.t0))) {
			throw new TimeoutException();
		}
		return false;
	}
	public void setLearningTimeout(Long tm) {
		this.learningtimeout = tm;
	}

	protected boolean istimeouted_nothrow() {
		try {
			istimeouted();
			return false;
		} catch (TimeoutException e) {
			return true;
		}
	}

	public ACQ_Query query_gen(ACQ_CNF T, ContradictionSet N) throws Exception {
		ACQ_Query q = new ACQ_Query();
		ACQ_Clause alpha = new ACQ_Clause();
		int epsilon = 0;
		int t = 0;
		boolean skip_buildformula = false;
		ACQ_Formula form = null;
		Boolean splittable = null;

		while (!istimeouted() && q.isEmpty() && !T.isMonomial()) {
			if (!skip_buildformula) {
				ACQ_Clause newalpha;

				if (alpha.isEmpty() && !(newalpha = T.getUnmarkedNonUnaryClause()).isEmpty()) {
					alpha = newalpha;
					epsilon = 0;
					t = 1;
				}
				splittable = (!alpha.isEmpty() && ((t + epsilon < alpha.getSize()) || (t - epsilon > 0)));
				chrono.start("build_formula");
				//System.out.println("here0");
				form = BuildFormula(splittable, T, N, alpha, t, epsilon);
				chrono.stop("build_formula");
				assert (form != null);
				form.addCnf(N.toCNF());
			}
			skip_buildformula = false;

			SATModel model = satSolver.solveConacq(form);
			//System.out.println("here1");

			if (satSolver.isTimeoutReached()) {
				assert (q.isEmpty());
				return q;
			}

			if (model == null) {
				assert !alpha.isEmpty() : "invariant: alpha should not be empty";

				if (splittable) {
					epsilon += 1;
				} else {
					T.remove(alpha);
					for (Unit unit : alpha) {
						assert !unit.isNeg() : "literals in alpha should not be negated";
						chrono.stop("first_constr_learned");
						ACQ_Clause fromalpha = new ACQ_Clause(unit);
						T.add(fromalpha);
						T.unitPropagate(unit, chrono);
						// KEY: also remove negation from bias_minus
						bias_minus.reduce(unit.getConstraint().getNegation());
					}
					alpha = new ACQ_Clause();
					assert alpha.isEmpty() : "The empty clause should be empty";
				}
			} else {
				ACQ_Network network = toNetwork(model);
				q = constrSolver.solveQ(network);
				//System.out.println("here3");

				if (constrSolver.isTimeoutReached()) {
					assert q.isEmpty() : "Timeout reached but q is not empty";
					return q;
				}

				if (q.isEmpty()) {
					boolean oneContradictiononly = true;
					if (oneContradictiononly) {
						network = filternet(network);
						Contradiction unsatCore = quickExplain(new ACQ_Network(constraintFactory, bias.network.getVariables()), network);
						assert (unsatCore != null && !unsatCore.isEmpty());
						N.add(unsatCore);
						/*if (!alpha.isEmpty()) {
							skip_buildformula = true;
						}*/
					}
				} else {
					if (!splittable && !alpha.isEmpty())
						alpha.mark();
				}
			}
		}

		if (q.isEmpty())
			q = irredundantQuery(T);

		return q;
	}

	protected ACQ_Network filternet(ACQ_Network net) {
		ACQ_Network res = new ACQ_Network(constraintFactory, bias.getVars());
		for (ACQ_IConstraint constr : net) {
			boolean toadd = true;

			// If disjunction: skip if one of its components is already in res
			if (constr instanceof ACQ_DisjunctionConstraint) {
				ACQ_DisjunctionConstraint disj =(ACQ_DisjunctionConstraint) constr;
				for (ACQ_IConstraint added : res) {
					if (disj.contains(added)) {
						toadd = false;
						break;
					}
				}
			}

			if (constr instanceof ACQ_ConjunctionConstraint) {
				ACQ_ConjunctionConstraint conj = (ACQ_ConjunctionConstraint) constr;
				boolean included = true;
				for (ACQ_IConstraint subconj : conj.getConstraints()) {
					if (!res.contains(subconj)) {
						included = false;
						break;
					}
				}
				toadd = !included;
			}

			if (toadd) {
				res.add(constr, true);
			}
		}
		return res;
	}


	protected ACQ_Formula BuildFormula(Boolean splittable, ACQ_CNF T, ContradictionSet N, ACQ_Clause alpha, int t, int epsilon) throws TimeoutException {
		ACQ_Formula res = new ACQ_Formula();
		if (!alpha.isEmpty()) {
			res.addCnf(T);
			for (ACQ_IConstraint c : bias_minus.getConstraints()) {
				//System.out.println("here7");
				istimeouted();
				boolean cont = alpha.contains(c);
				if (splittable && !cont && !alpha.contains(c.getNegation())) {
					res.addClause(new ACQ_Clause(mapping.get(c)));
				}
				if (cont) {
					ACQ_Clause newcl = new ACQ_Clause();
					newcl.add(mapping.get(c));
					newcl.add(mapping.get(c.getNegation()));
					res.addClause(newcl);
					//if (weighted)
						//res.addMinimization(newcl);
				}
			}

			int lower, upper;
			if (splittable && !weighted) {
				lower = Math.max(alpha.getSize() - t - epsilon, 1);
				upper = Math.min(alpha.getSize() - t + epsilon, alpha.getSize() - 1);
			} else {
				lower = 1;
				upper = alpha.getSize() - 1;
			}

			res.setAtLeastAtMost(alpha, lower, upper);
		} else {
			//System.out.println("here8");
			ACQ_CNF F = T.clone();
			ACQ_Clause toadd = new ACQ_Clause();
			for (ACQ_IConstraint constr : bias_minus.getConstraints()) {
				istimeouted();
				if (isUnset(constr, T, N)) {
					Unit toremove = mapping.get(constr.getNegation()).clone();
					toremove.setNeg();
					F.removeIfExists(new ACQ_Clause(toremove));
					toadd.add(mapping.get(constr.getNegation()));
				}
			}

			assert !toadd.isEmpty() : "toadd should not be empty";
			F.add(toadd);
			res.addCnf(F);
		}
		return res;
	}


	protected boolean isUnset(ACQ_IConstraint constr, ACQ_CNF T, ContradictionSet N) {
		Unit unit = mapping.get(constr);
		Unit neg = unit.clone();
		neg.setNeg();

		ACQ_CNF tmp1 = T.clone();
		tmp1.concat(N.toCNF());
		ACQ_CNF tmp2 = tmp1.clone();

		tmp1.add(new ACQ_Clause(unit));
		tmp2.add(new ACQ_Clause(neg));

		return satSolver.solve(tmp1) != null && satSolver.solve(tmp2) != null;
	}


	protected ACQ_Query irredundantQuery(ACQ_CNF T) {
		assert (T.isMonomial());
		ACQ_Network learned = new ACQ_Network(constraintFactory, bias.getVars(),
				constraintFactory.createSet(T.getMonomialPositive()));

		long startTime = System.currentTimeMillis();
		while (System.currentTimeMillis() - startTime < 100) {
			ACQ_Query q = constrSolver.solveQ(learned);

			ConstraintSet kappa = bias_minus.getKappa(q);
			for (ACQ_Clause clause : T) {
				Unit unit = clause.get(0);
				if (unit.isNeg())
					kappa.remove(unit.getConstraint());
			}

			if (kappa.size() > 0) {
				return q;
			}
		}

		for (ACQ_IConstraint c : bias_minus.getConstraints()) {
			if (!learned.contains(c)) {
				learned.add(c.getNegation(), true);
				ACQ_Query q = constrSolver.solveQ(learned);
				learned.remove(c.getNegation());
				if (!q.isEmpty())
					return q;
				else {
					T.add(new ACQ_Clause(mapping.get(c)));
				}
			}
		}
		return new ACQ_Query();
	}


	protected Contradiction quickExplain(ACQ_Network b, ACQ_Network network) {
		chrono.start("quick_explain");
		Contradiction result;
		if (network.size() == 0) {
			result = new Contradiction(new ACQ_Network());
		} else {
			ACQ_Network res = quick(b, b, network);
			assert !isConsistent(res) : "quickExplain must return inconsistent networks";
			result = new Contradiction(res);
		}
		chrono.stop("quick_explain");
		return result;
	}

	protected ACQ_Network quick(ACQ_Network b, ACQ_Network delta, ACQ_Network c) {
		if (delta.size() != 0 && !isConsistent(b)) {
			return new ACQ_Network(c.getFactory(), c.getFactory().createSet());
		}
		if (c.size() == 1)
			return c;

		ACQ_Network c1 = new ACQ_Network(constraintFactory, bias.network.getVariables());
		ACQ_Network c2 = new ACQ_Network(constraintFactory, bias.network.getVariables());

		int i = 0;
		for (ACQ_IConstraint constr : c.getConstraints()) {
			if (i < c.size() / 2) {
				c1.add(constr, true);
			} else {
				c2.add(constr, true);
			}
			i += 1;
		}

		ACQ_Network b_union_c1 = new ACQ_Network(constraintFactory, b, bias.network.getVariables());
		b_union_c1.addAll(c1, true);
		ACQ_Network delta2 = quick(b_union_c1, c1, c2);

		ACQ_Network b_union_delta2 = new ACQ_Network(constraintFactory, b, bias.network.getVariables());
		b_union_delta2.addAll(delta2, true);
		ACQ_Network delta1 = quick(b_union_delta2, delta2, c1);

		delta1.addAll(delta2, true);
		return delta1;
	}

	protected Boolean isConsistent(ACQ_Network network) {
		return constrSolver.solve(network);
	}


	private static final Random PREPROC_RANDOM = new Random(0); // fixed seed : reproducible runs

	protected ACQ_Query preproc_query_gen(int n_random) {
		if (this.strategy != null && this.strategy.size() > 0) {
			ACQ_Network s = this.strategy.get(0);
			this.strategy.remove(0);
			return constrSolver.solveQ(s);
		} else if (n_random < max_random) {
			ACQ_Scope scope = bias.getVars();
			Random random = PREPROC_RANDOM;
			int[] values = new int[scope.size()];
			for (int i = 0; i < values.length; i++) {
				values[i] = random.nextInt(domain.getMax(i) + 1);
			}
			return new ACQ_Query(scope, values);
		} else {
			return new ACQ_Query();
		}
	}

	protected boolean preprocess(ACQ_CNF T, ContradictionSet N, int max_queries) throws Exception {
		boolean collapse = false;
		boolean stop = false;
		int oldbias_size = bias_size_before_preprocess;
		int n_random = 0;

		while (n_asked != max_queries && !stop) {
			if (bias_minus.getConstraints().isEmpty())
				break;

			ACQ_Query membership_query = preproc_query_gen(n_random);
			assert membership_query != null : "membership query can't be null";

			if (constrSolver.isTimeoutReached() || satSolver.isTimeoutReached()) {
				collapse = true;
				break;
			}

			if (membership_query.isEmpty()) {
				stop = true;
			} else {
				if (verbose) System.out.print("[PREPROC] " + membership_query.getScope() + "::" + Arrays.toString(membership_query.values));
				Answer answer = oracle.ask(membership_query);
				n_asked++;
				ConstraintSet kappa = bias_minus.getKappa(membership_query);
				if (verbose) System.out.println("::" + membership_query.isPositive());

				if (kappa.size() > 0) {
					if (answer.equals(Answer.YES)) {
						for (ACQ_IConstraint c : kappa) {
							Unit unit = mapping.get(c).clone();
							unit.setNeg();
							T.unitPropagate(unit, chrono);
							N.unitPropagate(unit, chrono);
						}
						bias_minus.reduce(kappa);
					} else {
						if (kappa.size() == 1) {
							ACQ_IConstraint c = kappa.get_Constraint(0);
							Unit unit = mapping.get(c).clone();
							T.unitPropagate(unit, chrono);
							N.unitPropagate(unit, chrono);
							T.add(new ACQ_Clause(unit));
							// KEY: remove negation from bias_minus + propagate
							bias_minus.reduce(c.getNegation());
							Unit negunit = mapping.get(c.getNegation()).clone();
							negunit.setNeg();
							T.unitPropagate(negunit, chrono);
						} else {
							ACQ_Clause disj = new ACQ_Clause();
							for (ACQ_IConstraint c : kappa) {
								Unit unit = mapping.get(c).clone();
								disj.add(unit);
							}
							T.addChecked(disj);
						}
					}
				}

				if (oldbias_size >= bias_minus.getSize()) {
					n_random++;
				} else {
					oldbias_size = bias_minus.getSize();
					n_random = 0;
				}
			}
		}

		return collapse;
	}


	public boolean process(Chrono chronom, int max_queries) throws Exception {
		this.t0 = System.currentTimeMillis();
		chrono = chronom;
		chrono.start("pos_queries");
		chrono.start("neg_queries");
		boolean convergence = false;
		boolean collapse = false;
		T = new ACQ_CNF();



		ContradictionSet N;
		if (this.backgroundKnowledge == null) {
			N = new ContradictionSet(constraintFactory, bias.network.getVariables(), mapping);
		} else {
			N = this.backgroundKnowledge;
		}

		chrono.start("total_acq_time");
		System.out.println("Starting preprocess...");

		//collapse = preprocess(T, N, max_queries);
		//System.out.println("Preprocess completed. Collapse: " + collapse);

		bias_size_after_preprocess = bias_minus.getSize();

		ArrayList<String> asked_str = new ArrayList<>();

		while (n_asked <= max_queries && !(collapse || convergence)) {

			System.out.println("Iteration: n_asked = " + n_asked);

			if (bias_minus.getConstraints().isEmpty()) {
				System.out.println("Bias is empty. Exiting loop.");
				break;
			}

			ACQ_Query membership_query;
			try {
				if (firstQuery != null && !firstQueryUsed) {
					membership_query = firstQuery;
					firstQueryUsed = true;
				} else {
					chrono.start("gen_query");
					membership_query = query_gen(T, N);
					chrono.stop("gen_query");
				}
			} catch (TimeoutException e) {
				this.timeouted = true;
				chrono.stop("gen_query");
				break;
			}

			//System.out.println("Generated query: " + membership_query);
			assert membership_query != null : "membership query can't be null";

			if (constrSolver.isTimeoutReached() || satSolver.isTimeoutReached()) {
				System.out.println("Timeout reached. Exiting loop.");
				collapse = true;
				break;
			}

			if (membership_query.isEmpty()) {
				System.out.println("Query is empty. Convergence achieved.");
				convergence = true;
			} else {
				System.out.println("Processing query: " + membership_query);
				assert !asked_str.contains(membership_query.toString());
				asked_str.add(membership_query.toString());

				if (verbose) System.out.print(membership_query.getScope() + "::" + Arrays.toString(membership_query.values));
				Answer answer = oracle.ask(membership_query);
				if (log_queries)
					FileManager.writeLog(membership_query, "queries");
				n_asked++;

				ConstraintSet kappa = bias_minus.getKappa(membership_query);
				assert kappa.size() > 0;
				if (verbose) System.out.println("::" + membership_query.isPositive());

				if (answer.equals(Answer.YES)) {
					for (ACQ_IConstraint c : kappa) {
						Unit unit = mapping.get(c).clone();
						unit.setNeg();
						T.unitPropagate(unit, chrono);
						N.unitPropagate(unit, chrono);
					}
					bias_minus.reduce(kappa);

					chrono.stop("pos_queries");
					chrono.start("pos_queries");
					//System.out.println("bias reduced " + bias_minus.toString());
				} else {
					if (kappa.size() == 1) {
						ACQ_IConstraint c = kappa.get_Constraint(0);
						Unit unit = mapping.get(c).clone();
						T.unitPropagate(unit, chrono);
						N.unitPropagate(unit, chrono);
						T.add(new ACQ_Clause(mapping.get(c)));
						// KEY: remove negation from bias_minus + propagate
						bias_minus.reduce(c.getNegation());
						Unit negunit = mapping.get(c.getNegation()).clone();
						negunit.setNeg();
						T.unitPropagate(negunit, chrono);
						//MG ici change
					} else {
						ACQ_Clause disj = new ACQ_Clause();
						for (ACQ_IConstraint c : kappa) {
							Unit unit = mapping.get(c).clone();
							disj.add(unit);
						}
						T.add(disj);
					}
					chrono.stop("neg_queries");
					chrono.start("neg_queries");
				}
			}
			this.listMS.add(bias_minus.network.toSmtlib());
			//stocker MG à chaque itération
			ACQ_Network mg = new ACQ_Network(constraintFactory, bias.getVars());
			for (ACQ_Clause clause : T.getClauses()) {
				if (clause.getSize() == 1) {
					Unit unit = clause.get(0);
					if (!unit.isNeg()) {
						mg.add(unit.getConstraint(), true);
					}
				}
			}
			if(!mg.isEmpty()) this.listMG.add(mg.toSmtlib());
			else this.listMG.add("-");

		}

		chrono.stop("total_acq_time");
		System.out.println("Process completed. Collapse: " + collapse + ", Convergence: " + convergence);



		if (!collapse) {
			if (verbose) System.out.print("[INFO] Extract network from T: ");
			if (max_queries != n_asked) {
				SATModel model = satSolver.solve(toFormula(T));
				if (verbose) System.out.println("Done");
				learned_network = toNetwork(model);
			} else {
				learned_network = new ACQ_Network(constraintFactory, bias.getVars());
				for (ACQ_IConstraint constr : bias_minus.getConstraints()) {
					if (!isUnset(constr, T, N)) {
						learned_network.add(constr, true);
					}
				}
				if (verbose) System.out.println("Done");
			}
			learned_network.clean();
		}
		ACQ_Network overapprox = new ACQ_Network(constraintFactory, bias.getVars());
		for (ACQ_Clause clause : T.getClauses()) {
			if (clause.getSize() == 1) {
				Unit unit = clause.get(0);
				if (!unit.isNeg()) {
					overapprox.add(unit.getConstraint(), true);
				}
			}
		}
		learned_overapprox_network = overapprox;
		System.out.println("MG : " + learned_overapprox_network);
		System.out.println("Number of queries: " + n_asked);
		System.out.println("max queries: " + max_queries);
		return !collapse;
	}

	public ArrayList<String> getListMS(){
		return this.listMS;
	}
	public ArrayList<String> getListMG(){
		return this.listMG;
	}

	public ACQ_Formula toFormula(ACQ_CNF M) {
		ACQ_Formula res = new ACQ_Formula();
		for(ACQ_Clause clause : M) {
			res.addClause(clause);
		}
		return res;
	}

	protected ACQ_Network toNetwork(SATModel model) throws Exception {
		chrono.start("to_network");
		assert (model != null);
		ACQ_Network network = new ACQ_Network(constraintFactory, bias.getVars());

		for (ACQ_IConstraint constr : model.getPositive()) {
			assert constr != null;
			network.add(constr, true);
		}

		chrono.stop("to_network");
		return network;
	}
}