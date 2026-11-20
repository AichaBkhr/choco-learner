package fr.lisn.lahdak.acquisition.core.learners.algorithms;

import java.util.concurrent.CopyOnWriteArraySet;

import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_ConjunctionConstraint;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_IConstraint;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_Network;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintFactory.ConstraintSet;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_ConstraintSolver;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_Heuristic;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Bias;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Scope;
import fr.lisn.lahdak.acquisition.core.learners.Query_type;
import fr.lisn.lahdak.acquisition.core.oracle.ACQ_Oracle;
import fr.lisn.lahdak.acquisition.core.oracle.Answer;
import fr.lisn.lahdak.acquisition.core.parallel.ACQ_Ducq_Manager;
import fr.lisn.lahdak.acquisition.core.parallel.ACQ_QueryMessage;
import fr.lisn.lahdak.acquisition.core.tools.Chrono;

public class ACQ_DUCQ extends ACQ_QUACQ {

	private ACQ_Ducq_Manager coop;
	private String thread_id;
	private ACQ_Scope session;

	public ACQ_DUCQ(ACQ_ConstraintSolver solver, ACQ_Bias bias, ACQ_Oracle oracle, ACQ_Ducq_Manager coop,
					ACQ_Heuristic heuristic, CopyOnWriteArraySet<ACQ_QueryMessage> queries_mailbox) {

		super(solver, bias, oracle, heuristic);
		this.coop = coop;

	}

	@Override
	public boolean process() {

		boolean convergence = true;

		while (coop.bias.getSize() > 0) {

			coop.semaphore.acquireUninterruptibly();
			if (coop.noSession()) {
				coop.increase_index();
				coop.set_sessions(coop.getNb_threads());
			}
			session = coop.getSession();
			coop.semaphore.release();

			if (session.size() != 0) {
				this.bias = new ACQ_Bias(coop.getBias().getProjection(session));
				if (this.bias.getSize() != 0)
					convergence = (convergence & process(null));
				coop.closeSession(session);
			}

		}

		return convergence;

	}

	public boolean process(Chrono chrono) {

		boolean convergence = false;
		boolean collapse = false;

		// assert(learned_network.size()==0);
		while (!convergence && !collapse) {

			if (with_collapse_state && !solver.solve(learned_network) && !solver.timeout_reached()) {
				collapse = true;

			}

			if (bias.getConstraints().isEmpty())
				break;

			ACQ_Query membership_query = query_gen(learned_network.getProjection(session), bias.network, session,
					Query_type.MQ, heuristic);
			// System.out.print(solver.solveA(learned_network)+"\n");

			if (membership_query.isEmpty()) {
				if (solver.isTimeoutReached()) {
					collapse = true;
				} else {
					convergence = true;

					coop.bias.reduce(bias.getConstraints());
					bias.empty();
				}
			}

			else {
				oracle.ask_query(membership_query);
				boolean answer = membership_query.isPositive();
				if (answer)

				{
					bias.reduce(membership_query);
					coop.bias.reduce(membership_query);

				} else {

					ACQ_IConstraint cst;
					ACQ_Scope s;
					if (bias.getSize() == bias.computeMaxArity())
						s = membership_query.scope;
					else
						s = findScope(membership_query, bias.getVars(), new ACQ_Scope());

					cst = findC(s, membership_query, normalizedCSP);

					if (cst == null)
						collapse = true;
					else {
						if (cst instanceof ACQ_ConjunctionConstraint) {
							for (ACQ_IConstraint c : ((ACQ_ConjunctionConstraint) cst).constraintSet) {
								learned_network.add(c, true);
								coop.getLearned_network().add(c, true);
								bias.reduce(c);
								bias.reduce(c.getNegation());
								coop.bias.reduce(c);
								coop.bias.reduce(c.getNegation());

							}

						} else {
							coop.getLearned_network().add(cst, true);
							learned_network.add(cst, true);
							bias.reduce(cst.getNegation());
							coop.bias.reduce(cst.getNegation());

						}

						System.out.println("Thread n° :: " + Thread.currentThread().getName() + " :: learned_network::"
								+ coop.getLearned_network().size() + "\t::bias::" + coop.bias.network.size()
								+ "\t::==> " + cst + "\t::kappa::" + bias.getKappa(membership_query).size());

						// FileManager.printFile(learned_network.size()+ " ==> "+ cst + "
						// bias:"+bias.network.size(), "sudoku");
					}
				}
			}

			// NL diff cliques detection and reformulation using alldiff global constraints
			if (collapse && allDiff_detection
			// && (bias.getConstraints().size()*100/bias.getInitial_size()<bias_threshold)
			) {
				// learned_network.allDiffCliques();
				allDiff_detection = false;
				collapse = false;
			}

		}

		// System.out.println(solver.allSolutions(learned_network));

		return !collapse;
	}

	public ACQ_IConstraint findC(ACQ_Scope scope, ACQ_Query e, boolean normalizedCSP) {

		if (normalizedCSP)
			return findC_one(scope, e);
		else
			return findC_two(scope, e);

	}

	/**
	 * **************************************************************** findC
	 * (classic findC of IJCAI13)
	 *
	 * TODO : see if we keep the following invalid tag : @date 03-10-17
	 * TODO : see if we keep the following invalid tag : @update 17-11-18
	 *         *****************************************************************
	 */
	protected ACQ_IConstraint findC_one(ACQ_Scope scope, ACQ_Query e) {

		// ACQ_Network learned_network_y = new ACQ_Network(learned_network, scope);
		ACQ_Network learned_network_y = coop.getLearned_network().getProjection(scope);

		ACQ_Bias bias_y = new ACQ_Bias(bias.getExactProjection(scope));

		ConstraintSet temp_kappa;

		assert scope.size() >= 1;

		ConstraintSet candidates = constraintFactory.createSet(bias_y.getConstraints()); // NL: candidates = delta in
																							// IJCAI13

		assert (candidates.size() == bias_y.getConstraints().size());

		if (e != null)
			candidates.retainAll(bias_y.getKappa(e));

		while (true) {

			if (candidates.isEmpty()) {
				return null;
			}
			// TODO check if returning null or empty constraint (NL: 03-10-17)

			int temp = candidates.size();

			ACQ_Query partial_findC_query = query_gen(learned_network_y,
					new ACQ_Network(constraintFactory, scope, candidates), scope, Query_type.findc1, ACQ_Heuristic.SOL);
			assert (candidates.size() == temp);

			if (partial_findC_query.isEmpty()) {

				bias.reduce(candidates);
				coop.bias.reduce(candidates);

				return candidates.iterator().next();

			}

			temp_kappa = bias_y.getKappa(partial_findC_query);

			if (temp_kappa.isEmpty()) {
				throw new RuntimeException("Collapse state");
			}

			oracle.ask_query(partial_findC_query);

			if (!partial_findC_query.isClassified()) {
				Answer b = oracle.ask(partial_findC_query);
				partial_findC_query.classify(b.equals(Answer.YES)?Answer.YES:Answer.NO);

			}

			if (partial_findC_query.isPositive()) {
				bias.reduce(temp_kappa); // NL: kappa of partial_findC_query
				coop.bias.reduce(temp_kappa);

				candidates.removeAll(temp_kappa);
			} else {
				candidates.retainAll(temp_kappa);
			}
		}

	}

	/**
	 * **************************************************************** findC (new
	 * findC presented in AIJ on non-normalized CSPs (new implementation)
	 *
	 * TODO : see if we keep the following invalid tag : @date 19-02-20
	 *         *****************************************************************
	 */

	protected ACQ_IConstraint findC_two(ACQ_Scope scope, ACQ_Query e) {

		ACQ_Network learned_network_y = coop.getLearned_network().getProjection(scope);

		// if(scope.size()>3)
		// System.out.println("test");

		ACQ_Bias bias_y = new ACQ_Bias(bias.getExactProjection(scope));

		ConstraintSet temp_kappa;

		assert scope.size() >= 1;

		ConstraintSet all_candidates = join(constraintFactory.createSet(bias_y.getConstraints()), bias_y.getKappa(e)); // NL:
																														// join
																														// operator
																														// between
																														// delta
																														// and
																														// kappa_delta
		// System.out.print("all condidates::"+ all_candidates+"\n");

		while (true) {

			ACQ_Query partial_findC_query = query_gen(learned_network_y,
					new ACQ_Network(constraintFactory, scope, all_candidates), scope, Query_type.findc2, ACQ_Heuristic.SOL);

			if (partial_findC_query.isEmpty()) {

				ACQ_IConstraint cst = pick(all_candidates);
				if (cst != null) {
					bias.reduce(cst); // NL: to check if all_candidates can be removed from B?
					coop.bias.reduce(cst);
				}

				return cst;
			}

			temp_kappa = all_candidates.getKappa(partial_findC_query); // NL: level or all candidates?

			oracle.ask_query(partial_findC_query);

			if (!partial_findC_query.isClassified()) {
				Answer b = oracle.ask(partial_findC_query);

				partial_findC_query.classify(b.equals(Answer.YES)?Answer.YES:Answer.NO);


			}

			if (partial_findC_query.isPositive()) {

				all_candidates.removeAll(temp_kappa);

			} else {

				ACQ_Scope S = findScope(partial_findC_query, scope, new ACQ_Scope());

				if (scope.containsAll(S) && scope.size() < S.size()) {

					ACQ_IConstraint cst = findC(S, partial_findC_query, normalizedCSP);

					if (cst == null)
						throw new RuntimeException("Collapse state");

					else {
						learned_network.add(cst, false);
						coop.getLearned_network().add(cst, false);
						bias.reduce(cst.getNegation());
						coop.bias.reduce(cst.getNegation());

					}

				} else

					all_candidates = join(all_candidates, temp_kappa);

			}
		}

	}

	@Override
	public ACQ_Scope findScope(ACQ_Query negative_example, ACQ_Scope X, ACQ_Scope Bgd) {
		if (new_findscope)
			return findScope_two(negative_example, X, Bgd);

		return findScope_one(negative_example, X, Bgd, false);

	}

	/**
	 * ************************************************************** FindScope
	 * procedure
	 *
	 * @param negative_example : a complete negative example
	 * @param X                : problem variables (and/or) foreground variables
	 * @param Bgd              : background variables
	 * @return variable scope
	 *
	 * TODO : see if we keep the following invalid tag : @update 31/05/19
 **************************************************************
	 */

	protected ACQ_Scope findScope_two(ACQ_Query negative_example, ACQ_Scope X, ACQ_Scope Bgd) {

		if (Bgd.size() >= bias.computeMinArity()) { // TESTME if minArity has the good value !!

			ACQ_Query query_bgd = new ACQ_Query(Bgd, Bgd.getProjection(negative_example)); // projection e|Bgd

			ConstraintSet temp_kappa = coop.bias.getKappa(query_bgd);
			if (!temp_kappa.isEmpty()) {

				oracle.ask_query(query_bgd);

				if (query_bgd.isNegative())
					return ACQ_Scope.EMPTY; // NL: return emptyset

				else {
					bias.reduce(temp_kappa);
					coop.bias.reduce(temp_kappa);

				}

			}
		}

		if (X.size() == 1)
			return X;

		// NL: different splitting manners can be defined here!

		ACQ_Scope[] splitedX;
		if (shuffle_split)
			splitedX = X.shuffleSplit();
		else
			splitedX = X.split();

		ACQ_Scope X1 = splitedX[0];
		ACQ_Scope X2 = splitedX[1];

		ACQ_Query query_BXone = new ACQ_Query(Bgd.union(X1), Bgd.union(X1).getProjection(negative_example)); // projection
																												// e|Bgd

		ConstraintSet kappa_BXone;

		kappa_BXone = bias.getKappa(query_BXone);

		ACQ_Query query_BX = new ACQ_Query(Bgd.union(X), Bgd.union(X).getProjection(negative_example)); // projection
																										// e|Bgd

		ConstraintSet kappa_BX = bias.getKappa(query_BX);

		ACQ_Scope S1;

		if (kappa_BXone.equals(kappa_BX))
			S1 = ACQ_Scope.EMPTY;
		else
			S1 = findScope_two(negative_example, X2, Bgd.union(X1)); // NL: First recursive call of findScope

		ACQ_Query query_BSone = new ACQ_Query(Bgd.union(S1), Bgd.union(S1).getProjection(negative_example)); // projection
																												// e|Bgd

		ConstraintSet kappa_BSone = bias.getKappa(query_BSone);

		ACQ_Scope S2;

		if (kappa_BSone.equals(kappa_BX))
			S2 = ACQ_Scope.EMPTY;

		else
			S2 = findScope_two(negative_example, X1, Bgd.union(S1)); // NL: Second recursive call of findScope

		return S1.union(S2);
	}

	protected ACQ_Scope findScope_one(ACQ_Query negative_example, ACQ_Scope X, ACQ_Scope Bgd, boolean mutex) {

		if (mutex && Bgd.size() >= bias.computeMinArity()) { // TESTME if minArity has the good value !!

			ACQ_Query query_bgd = new ACQ_Query(Bgd, Bgd.getProjection(negative_example)); // projection e|Bgd
			ConstraintSet temp_kappa = bias.getKappa(query_bgd);

			oracle.ask_query(query_bgd);

			if (!query_bgd.isPositive()) // NL: negative
			{
				return ACQ_Scope.EMPTY; // NL: return emptyset
			} else {
				bias.reduce(temp_kappa);
				coop.bias.reduce(temp_kappa);

			}
		}

		if (X.size() == 1) {
			return X;
		}

		// NL: different splitting manners can be defined here!
		ACQ_Scope[] splitedX;

		if (shuffle_split)
			splitedX = X.shuffleSplit();
		else
			splitedX = X.split();

		ACQ_Scope X1 = splitedX[0];
		ACQ_Scope X2 = splitedX[1];

		ACQ_Scope S1 = findScope_one(negative_example, X2, Bgd.union(X1), true); // NL: First recursive call of
																					// findScope
		mutex = (S1 != ACQ_Scope.EMPTY);
		ACQ_Scope S2 = findScope_one(negative_example, X1, Bgd.union(S1), mutex); // NL: Second recursive call of
																					// findScope

		return S1.union(S2);
	}

	public String getThread_id() {
		return thread_id;
	}

	public void setThread_id(String thread_id) {
		this.thread_id = thread_id;
	}

}
