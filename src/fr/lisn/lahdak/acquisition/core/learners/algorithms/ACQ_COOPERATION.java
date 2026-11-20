package fr.lisn.lahdak.acquisition.core.learners.algorithms;


import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
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
import fr.lisn.lahdak.acquisition.core.parallel.ACQ_Coop_Manager;
import fr.lisn.lahdak.acquisition.core.parallel.ACQ_QueryMessage;
import fr.lisn.lahdak.acquisition.core.parallel.ACQ_ScopeMessage;
import fr.lisn.lahdak.acquisition.core.tools.Chrono;
import fr.lisn.lahdak.acquisition.core.tools.FileManager;


public class ACQ_COOPERATION extends ACQ_QUACQ{






	private ACQ_Coop_Manager coop;
	private String thread_id ;
	private ACQ_Bias bias_i;


	public ACQ_COOPERATION(ACQ_ConstraintSolver solver, ACQ_Oracle oracle, ACQ_Coop_Manager coop,
						   ACQ_Heuristic heuristic, CopyOnWriteArraySet<ACQ_QueryMessage> queries_mailbox, int nb_thread) {

		super(solver, coop.bias, oracle,heuristic);
		this.coop=coop;
		this.coop.set_thread(Thread.currentThread().getName(), nb_thread);

	}


	public ACQ_IConstraint findC(ACQ_Scope scope, ACQ_Query e, boolean normalizedCSP) {

		if(normalizedCSP)
			return findC_one(scope,e);
		else
			return findC_two(scope,e);

	}

	/**
	 * ****************************************************************
	 * findC (classic findC of IJCAI13 adapted for multithreading)
	 *
	 * TODO : see if we keep the following invalid tag : @date 24-03-20
	 * *****************************************************************
	 */
	protected ACQ_IConstraint findC_one(ACQ_Scope scope, ACQ_Query e) {

		//		ACQ_Network learned_network_y = new ACQ_Network(learned_network, scope);
		ACQ_Network learned_network_y =coop.getLearned_network().getProjection(scope);

		ACQ_Bias bias_y = new ACQ_Bias(bias_i.getExactProjection(scope));

		ConstraintSet temp_kappa;

		assert scope.size() >= 1;

		ConstraintSet candidates = bias_i.network.getFactory().createSet(bias_y.getConstraints());  //NL:  candidates = delta in IJCAI13

		assert (candidates.size() == bias_y.getConstraints().size());

		if(e!=null)
			candidates.retainAll(bias_y.getKappa(e));

		while (true) {

			if (candidates.isEmpty()) {
				return null;
			}

			int temp = candidates.size();

			ACQ_Query partial_findC_query = query_gen(learned_network_y, new ACQ_Network(constraintFactory,scope, candidates), scope, Query_type.findc1, ACQ_Heuristic.SOL);
			assert (candidates.size() == temp);

			if (partial_findC_query.isEmpty()) {

				coop.Reduce(candidates);
				bias_i.reduce(candidates);
				return candidates.iterator().next();

			}

			temp_kappa = bias_y.getKappa(partial_findC_query);

			if (temp_kappa.isEmpty()) {
				throw new RuntimeException("Collapse state");
			}


			coop.ask_query(partial_findC_query);



			if (!partial_findC_query.isClassified()) {
				Answer b = oracle.ask(partial_findC_query);
				partial_findC_query.classify(b.equals(Answer.YES)?Answer.YES:Answer.NO);
				coop.send(new ACQ_QueryMessage(Thread.currentThread().getName(),
						partial_findC_query)); 
			}

			if (partial_findC_query.isPositive()) {
				// NL: kappa of partial_findC_query

				coop.Reduce(temp_kappa);
				bias_i.reduce(temp_kappa);

				candidates.removeAll(temp_kappa);
			} else {
				candidates.retainAll(temp_kappa);
			}
		}

	}

	/**
	 * ****************************************************************
	 * findC (new findC presented in AIJ on non-normalized CSPs (new implementation)
	 * (adapted to MT)
	 *
	 * TODO : see if we keep the following invalid tag : @date 24-03-20
	 * *****************************************************************
	 */

	protected ACQ_IConstraint findC_two(ACQ_Scope scope, ACQ_Query e) {


		ACQ_Network learned_network_y =coop.getLearned_network().getProjection(scope);


		ACQ_Bias bias_y = new ACQ_Bias(bias_i.getExactProjection(scope));

		ConstraintSet temp_kappa;

		assert scope.size() >= 1;


		ConstraintSet all_candidates= join(bias_i.network.getFactory().createSet(bias_y.getConstraints()),bias_y.getKappa(e));		//NL: join operator between delta and kappa_delta
		//System.out.print("all condidates::"+ all_candidates+"\n");

		while (true) {

			ACQ_Query partial_findC_query=query_gen(learned_network_y, 
					new ACQ_Network(constraintFactory,scope, all_candidates), scope, Query_type.findc2, ACQ_Heuristic.SOL);

			if (partial_findC_query.isEmpty()) {

				ACQ_IConstraint cst = pick(all_candidates);
				if(cst!=null)
				{
					//NL: to check if all_candidates can be removed from B?
					coop.Reduce(cst);
					bias_i.reduce(cst);
				}

				return cst;
			}


			temp_kappa = all_candidates.getKappa(partial_findC_query);			// NL: level or all candidates?


			coop.ask_query(partial_findC_query);

			if (!partial_findC_query.isClassified()) {
				Answer b = oracle.ask(partial_findC_query);

				partial_findC_query.classify(b.equals(Answer.YES)?Answer.YES:Answer.NO);

				coop.send(new ACQ_QueryMessage(Thread.currentThread().getName(),
						partial_findC_query)); 

			}

			if (partial_findC_query.isPositive()) {


				all_candidates.removeAll(temp_kappa);

			} else {

				ACQ_Scope S = findScope(partial_findC_query,scope,  new ACQ_Scope());

				if(scope.containsAll(S) && scope.size()<S.size()) {

					ACQ_IConstraint cst= findC(S,partial_findC_query, normalizedCSP);	

					if(cst==null) 	throw new RuntimeException("Collapse state");

					else 
					{
						coop.getLearned_network().add(cst,false);
						learned_network.add(cst,false);

						coop.Reduce(cst.getNegation());
						bias_i.reduce(cst.getNegation());

					}

				}
				else 

					all_candidates= join(all_candidates,temp_kappa);	



			}
		}


	}





	/**
	 * **************************************************************
	 * FindScope procedure (adapted to MT)
	 *
	 * @param negative_example : a complete negative example
	 * @param X : problem variables (and/or) foreground variables
	 * @param Bgd : background variables
	 * @return variable scope
	 *
	 * TODO : see if we keep the following invalid tag : @update 24/03/20
	 * **************************************************************
	 */

	protected ACQ_Scope findScope_two(ACQ_Query negative_example,
			ACQ_Scope X, ACQ_Scope Bgd) {

		if (Bgd.size() >= coop.bias.computeMinArity()) {	//TESTME if minArity has the good value !!

			ACQ_Query query_bgd = new ACQ_Query(Bgd, Bgd.getProjection(negative_example));      // projection e|Bgd

			ConstraintSet temp_kappa = coop.bias.getKappa(query_bgd);
			if(!temp_kappa.isEmpty())
			{

				coop.ask_query(query_bgd);


				if (!query_bgd.isClassified()) {
					oracle.ask(query_bgd);
					coop.send(new ACQ_QueryMessage(Thread.currentThread().getName(),
							query_bgd)); 					

				}

				if (query_bgd.isNegative()) 
					return ACQ_Scope.EMPTY;	//NL: return emptyset

				else 
					coop.Reduce(temp_kappa);
				bias_i.reduce(temp_kappa);


			}
		}


		if (X.size() == 1) 
			return X;


		//NL: different splitting manners can be defined here!


		ACQ_Scope[] splitedX;
		if(shuffle_split) 
			splitedX = X.shuffleSplit();
		else 
			splitedX = X.split();

		ACQ_Scope X1 = splitedX[0];
		ACQ_Scope X2 = splitedX[1];


		ACQ_Query query_BXone = new ACQ_Query(Bgd.union(X1), Bgd.union(X1).getProjection(negative_example));      // projection e|Bgd

		ConstraintSet kappa_BXone = coop.bias.getKappa(query_BXone);


		ACQ_Query query_BX = new ACQ_Query(Bgd.union(X), Bgd.union(X).getProjection(negative_example));      // projection e|Bgd


		ConstraintSet kappa_BX = coop.bias.getKappa(query_BX);

		ACQ_Scope S1;

		if(kappa_BXone.equals(kappa_BX))
			S1= ACQ_Scope.EMPTY;
		else
			S1 = findScope_two(negative_example, X2, Bgd.union(X1));    // NL: First recursive call of findScope

		ACQ_Query query_BSone = new ACQ_Query(Bgd.union(S1), Bgd.union(S1).getProjection(negative_example));      // projection e|Bgd


		ConstraintSet kappa_BSone = coop.bias.getKappa(query_BSone);

		ACQ_Scope S2;

		if(kappa_BSone.equals(kappa_BX))
			S2=ACQ_Scope.EMPTY;

		else
			S2 = findScope_two(negative_example, X1, Bgd.union(S1));    // NL: Second recursive call of findScope

		return S1.union(S2);
	}



	@Override
	public boolean process() {
		return process(null);
	}

	synchronized public boolean process(Chrono chrono) {
		boolean collapse=false;


		ACQ_IConstraint cst;


		bias_i=coop.getPartition(thread_id);

		Set<ACQ_ScopeMessage> mailbox_scopes= new HashSet<>();
		while(!bias.getConstraints().isEmpty()){ 


			if( with_collapse_state && !solver.solve(learned_network) && !solver.timeout_reached()){
				collapse=true;
				return !collapse;
			}


			//////////////////////////////////////////////////:

			// Receive Scopes
			if(coop.getScopeMessages(Thread.currentThread().getName())!=null)
			{
				mailbox_scopes.addAll(coop.getScopeMessages(Thread.currentThread().getName()));
				FileManager.writeLog("Thread :"+Thread.currentThread().getName() +
						"::"+mailbox_scopes.size(), "mailbox_scopes");

				coop.clear_scopes_mailbox(Thread.currentThread().getName());
			}

			Iterator<ACQ_ScopeMessage> iter = mailbox_scopes.iterator();

			while(iter.hasNext()){

				ACQ_ScopeMessage scope_m=iter.next();


				cst= findC(scope_m.getScope(),scope_m.getQuery(), normalizedCSP);		// not the same membership_query;

				coop.scopeVisited(scope_m.getScope());

				if(cst==null ) 
				{
					FileManager.writeLog(Thread.currentThread().getName(), "fails");
					continue;	
				}
				else 
				{
					iter.remove();

					if(cst instanceof ACQ_ConjunctionConstraint)
					{
						for(ACQ_IConstraint c: ((ACQ_ConjunctionConstraint) cst).constraintSet)
						{
							learned_network.add(c,true);
							coop.getLearned_network().add(c,true);

							bias_i.reduce(c);
							bias_i.reduce(c.getNegation());
							coop.Reduce(c);
							coop.Reduce(c.getNegation());
						}

					}else
					{
						learned_network.add(cst,true);
						coop.getLearned_network().add(cst,true);
						bias_i.reduce(cst);
						bias_i.reduce(cst.getNegation());
						coop.Reduce(cst);
						coop.Reduce(cst.getNegation());
					}

					FileManager.writeLog("Thread :"+Thread.currentThread().getName() +
							"::"+cst, "sent_constraints");


					System.out.println("Thread n° "+Thread.currentThread().getName()+" :: learned_network::"+coop.getLearned_network().size()+
							"\t::bias::"+bias.network.size()+"\t::==> "+cst);




					//						FileManager.printFile(learned_network.size()+ " ==> "+ cst + " bias:"+bias.network.size(), "sudoku");
				}
			}

			//////////////////////////////////////////////////:

			
			ACQ_Query membership_query=query_gen(coop.getLearned_network(), bias_i.network, bias_i.getVars(), Query_type.MQ, heuristic );



			if (membership_query.isEmpty()) {

				if(!solver.isTimeoutReached()) {
					coop.Reduce(bias_i.getConstraints());
					bias_i.empty();
					return true;

				}else {
					collapse=true;
					return !collapse;
				}
			}

			Answer answer = oracle.ask(membership_query);

			if(answer.equals(Answer.YES))

			{
				coop.Reduce(membership_query);
				bias_i.reduce(membership_query);


			}
			else{




				//find scope
				ACQ_Scope scope = findScope(membership_query, bias_i.getVars(), 
						new ACQ_Scope());


				// check if scope exists in B_i
				if(coop.check(coop.get_Bias_scope(Thread.currentThread().getName()), scope)) {


					cst= findC(scope,membership_query, normalizedCSP);		

					coop.scopeVisited(scope);

					if(cst==null ) 
					{
						FileManager.writeLog(Thread.currentThread().getName()+scope, "fails");
						continue;	
					}
					else 
					{
						if(cst instanceof ACQ_ConjunctionConstraint)
						{
							for(ACQ_IConstraint c: ((ACQ_ConjunctionConstraint) cst).constraintSet)
							{
								learned_network.add(c,true);
								coop.getLearned_network().add(c, true);
								coop.Reduce(c);
								coop.Reduce(c.getNegation());

								bias_i.reduce(c);
								bias_i.reduce(c.getNegation());

							}

						}else
						{
							learned_network.add(cst,true);
							coop.getLearned_network().add(cst, true);
							coop.Reduce(cst);
							coop.Reduce(cst.getNegation());

							bias_i.reduce(cst);
							bias_i.reduce(cst.getNegation());

						}


						System.out.println("Thread n° "+Thread.currentThread().getName()+" :: learned_network::"+coop.getLearned_network().size()+
								"\t::bias::"+bias.network.size()+"\t::==> "+cst);


					}

				} 
				else {// send Scope to Appropriate agent
					if(!coop.isVisitedScope(scope))
					{
						coop.send(new ACQ_ScopeMessage(Thread.currentThread().getName(),membership_query,scope));
						FileManager.writeLog("Thread :"+Thread.currentThread().getId() +
								"\t"+scope, "sent_scopes");
					}

				}



			}


		
			//NL diff cliques detection and reformulation using alldiff global constraints
			if(collapse && allDiff_detection 
					//&&  (bias.getConstraints().size()*100/bias.getInitial_size()<bias_threshold)
					) {
				//		learned_network.allDiffCliques();
				allDiff_detection=false;
				collapse=false;
			}

		}
		return collapse;

		//System.out.println(solver.allSolutions(learned_network));


	}

	public ACQ_Scope findScope(ACQ_Query negative_example, ACQ_Scope X, ACQ_Scope Bgd) {
		if(new_findscope )
			return findScope_two( negative_example,  X,  Bgd);

		return findScope_one( negative_example,  X,  Bgd, false);

	}

	public String getThread_id() {
		return thread_id;
	}



	public void setThread_id(String thread_id) {
		this.thread_id = thread_id;
	}
}
