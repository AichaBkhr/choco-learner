package fr.lisn.lahdak.acquisition.core.learners.algorithms;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Random;

import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_CNF;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_Clause;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_ConjunctionConstraint;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_Formula;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_IConstraint;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_Network;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintFactory;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintMapping;
import fr.lisn.lahdak.acquisition.core.acqconstraint.Contradiction;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ContradictionSet;
import fr.lisn.lahdak.acquisition.core.acqconstraint.Unit;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintFactory.ConstraintSet;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_ConstraintSolver;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_IDomain;
import fr.lisn.lahdak.acquisition.core.acqsolver.SATModel;
import fr.lisn.lahdak.acquisition.core.acqsolver.SATSolver;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Bias;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Scope;
import fr.lisn.lahdak.acquisition.core.oracle.*;
import fr.lisn.lahdak.acquisition.core.tools.Chrono;
import fr.lisn.lahdak.acquisition.core.tools.FileManager;

/**
 * Implementation of the CONACQv1 algorithm for constraint acquisition.
 * <p>
 * This class handles the initialization of the bias, SAT solver, constraint solver, and oracle.
 * It also manages the query generation, preprocessing, and learning process.
 * </p>
 */
public class ACQ_CONACQv1 {
	protected ACQ_Bias bias;
	protected ACQ_Oracle oracle;
	protected ACQ_ConstraintSolver constrSolver;
	protected ACQ_IDomain domain;
	protected boolean verbose = false;
	protected boolean log_queries = false;
	public ConstraintMapping mapping;
	protected SATSolver satSolver;
	protected ConstraintFactory constraintFactory;
	protected ACQ_Network learned_network;
	protected ACQ_Network init_network;
	protected ArrayList<ACQ_Network> strategy = null;
	protected ContradictionSet backgroundKnowledge = null;
	protected Chrono chrono;
	protected int bias_size_before_preprocess;
	protected int bias_size_after_preprocess;
	protected int max_random = 0;
	protected int n_asked = 0;
	protected ArrayList<ACQ_Query> queries;
	
	
	/**
     * Constructs an instance of the CONACQv1 algorithm.
     *
     * @param oracle  
     * @param bias    
     * @param sat     
     * @param solv    
     * @param queries 
     */
	public ACQ_CONACQv1(ACQ_Oracle oracle, ACQ_Bias bias, SATSolver sat, ACQ_ConstraintSolver solv, String queries) {
		this.bias = bias;
		this.constraintFactory = bias.network.getFactory();
		this.oracle = oracle;
		this.satSolver = sat;
		this.constrSolver = solv;
		this.domain = solv.getDomain();
		this.mapping = new ConstraintMapping();
		this.queries=getQueries(queries);
		this.init_network= new ACQ_Network(constraintFactory, bias.getVars());
		
		for (ACQ_IConstraint c : bias.getConstraints()) {
			String newvarname = c.getName() + c.getVariables();
			Unit unit = this.satSolver.addVar(c, newvarname);
			this.mapping.add(c, unit);
		}
		assert mapping.size() == bias.getSize(): "mapping and bias must contain the same number of elements";
		filter_conjunctions();
		bias_size_before_preprocess = bias.getSize();
		
	}

	/**
     * Reads queries from a file and returns them as a list.
     *
     * @param queries2
     * @return A list of queries.
     */
	private ArrayList<ACQ_Query> getQueries(String queries2) {
		ArrayList<ACQ_Query> queries = new ArrayList<ACQ_Query>();
		BufferedReader reader;//
		try {
			reader = new BufferedReader(new FileReader("benchmarks/queries/"+queries2+".queries"));
			String line;
			while ((line = reader.readLine()) != null) {
				String[] lineSplited = line.split(" ");
				int [] values = new int[lineSplited.length-1];
				
				int label =  Integer.parseInt(lineSplited[lineSplited.length-1]);
				int i=0;
				for(String s : lineSplited) {
					if(i==lineSplited.length-1)
						break;
					values[i]=Integer.parseInt(s);
					i++;
				}
				BitSet bs = new BitSet();
				bs.set(0, i);
				ACQ_Scope scope = new ACQ_Scope(bs);
				ACQ_Query q= new ACQ_Query(scope,values);
				if(label == 1)
				q.classify(Answer.YES);
				else
					q.classify(Answer.NO);

				queries.add(q);
				
			}
			reader.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return queries;
	}

	/**
     * Filters conjunction constraints from the bias.
     */
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

	public void setInitN(ACQ_Network InitNetwork) {
		init_network=InitNetwork;
	}
	
	public float getPreprocessDiminution() {
		return ((float) (100*(bias_size_before_preprocess - bias_size_after_preprocess)) / bias_size_before_preprocess);
	}
	
	public ACQ_Bias getBias() {
		return bias;
	}

	public ACQ_Network getLearnedNetwork() {
		return learned_network;
	}
	
	public void setVerbose(boolean verbose) {

		this.verbose = verbose;
	}
	
	public void setLog_queries(boolean logqueries) {

		this.log_queries = logqueries;
	}
	
	/**
     * Generates a query based on the given CNF formula and contradiction set.
     * <p>
     * This method iteratively builds a formula, solves it using the SAT solver, and generates a query.
     * If the SAT solver finds a model, the query is generated based on the learned network.
     * If no model is found, the method updates the CNF formula and contradiction set accordingly.
     * </p>
     *
     * @param T The CNF formula representing the current state of the problem.
     * @param N The contradiction set containing known contradictions.
     * @return The generated query. If no query can be generated, an empty query is returned.
     * @throws Exception If an error occurs during query generation.
     */
	public ACQ_Query query_gen(ACQ_CNF T, ContradictionSet N) throws Exception {
		ACQ_Query q = new ACQ_Query();
		ACQ_Clause alpha = new ACQ_Clause();
		int epsilon = 0;
		int t = 0;
		boolean skip_buildformula = false;
		ACQ_Formula form = null;
		Boolean splittable = null;
		
		while (q.isEmpty() && !T.isMonomial()) {
			if (!skip_buildformula) {
				ACQ_Clause newalpha;
				
				if (alpha.isEmpty() && !(newalpha = T.getUnmarkedNonUnaryClause()).isEmpty()) {
					alpha = newalpha;
					epsilon = 0;
					//TODO value depending on strategy
					t = 1; // Optimal in expectation
					//t = Math.max(alpha.getSize() -1, 1); // Optimistic
				}
				splittable = (!alpha.isEmpty() && ((t+epsilon < alpha.getSize()) || (t - epsilon > 0)));
				chrono.start("build_formula");
				form = BuildFormula(splittable, T, N, alpha, t, epsilon);
				chrono.stop("build_formula");
				assert(form != null);
				form.addCnf(N.toCNF());
			}
			skip_buildformula = false;
			SATModel model = satSolver.solve(form);
			
			if (satSolver.isTimeoutReached()) {
				assert(q.isEmpty());
				return q; // Collapse
			}
			
			if (model == null) {
				assert !alpha.isEmpty() : "invariant: alpha should not be empty";
				
				if (splittable) {
					epsilon += 1;
				}
				
				else {
					T.remove(alpha);
					for (Unit unit : alpha) {
						assert !unit.isNeg() : "literals in alpha should not be negated";
						T.add(new ACQ_Clause(unit));
					}
					T.unitPropagate(chrono);
					alpha = new ACQ_Clause(); //Empty clause 
					assert alpha.isEmpty() : "The empty clause should be empty";
				}
			}
			else {
				ACQ_Network network = toNetwork(model);
				q = constrSolver.solveQ(network);
				
				if (constrSolver.isTimeoutReached()) {
					assert q.isEmpty() : "Timeout reached but q is not empty";
					return q;
				}
				
				if (q.isEmpty()) {
					boolean oneContradictiononly = true;
					if (oneContradictiononly) {
						Contradiction unsatCore = quickExplain(new ACQ_Network(constraintFactory, bias.network.getVariables()), network);
						assert (unsatCore != null && !unsatCore.isEmpty());
						N.add(unsatCore);
						if (!alpha.isEmpty()) {
							// Here splittalbe, alpha (!= empty) and T does not change (only N change) 
							// so BuildFormula will return the same formula 
							skip_buildformula = true; // Here splittalbe, alpha (!= empty) and T does not change (only N change) so BuildFormula 
						}
					}
					else {
						int i = 0;
						while (!isConsistent(network)) {
							Contradiction unsatCore = quickExplain(new ACQ_Network(constraintFactory, bias.network.getVariables()), network);
							if (unsatCore == null || unsatCore.isEmpty())
								break;
							N.add(unsatCore);
							i += 1;
							for (ACQ_IConstraint constr : unsatCore.toNetwork()) {
								network.remove(constr);
							}
						}
						assert i > 0 : "No contradictions added";
					}
				}
				else {
					if (!splittable && !alpha.isEmpty())
						alpha.mark();
				}
				
			}
			
		}
		if (q.isEmpty())
			q = irredundantQuery(T);
		return q;
	}
	
	protected ACQ_Formula BuildFormula(Boolean splittable, ACQ_CNF T, ContradictionSet N, ACQ_Clause alpha, int t, int epsilon) {
		ACQ_Formula res =  new ACQ_Formula();
		if (!alpha.isEmpty()) {
			res.addCnf(T);
			// No need to remove unary negative as it is never added to T
			for(ACQ_IConstraint c : bias.getConstraints()) {	
				// No need to check if T contains unary negative as it is never added to T 
				boolean cont = alpha.contains(c);
				if (splittable && !cont && !alpha.contains(c.getNegation())) {
					res.addClause(new ACQ_Clause(mapping.get(c)));
				}
				if (cont) {
					ACQ_Clause newcl = new ACQ_Clause();
					newcl.add(mapping.get(c));
					newcl.add(mapping.get(c.getNegation()));
					res.addClause(newcl);
				}
			}
			
			int lower, upper;
			if (splittable) {
				lower = Math.max(alpha.getSize() - t - epsilon, 1);
				upper = Math.min(alpha.getSize() - t + epsilon, alpha.getSize() -1);
			}
			else {
				lower = 1;
				upper = alpha.getSize() - 1;
			}
			
			res.setAtLeastAtMost(alpha, lower, upper); // atLeast and atMost are left symbolic in order to let the solver encode it at will
		}
		else {
			ACQ_CNF F = T.clone();
			ACQ_Clause toadd = new ACQ_Clause();
			for (ACQ_IConstraint constr : bias.getConstraints()) {
				if (isUnset(constr, T, N)) {
					//constr is unset
					Unit toremove = mapping.get(constr.getNegation()).clone();
					toremove.setNeg();
					
					F.removeIfExists(new ACQ_Clause(toremove));
					//F.remove(new Clause(toremove));
					
					toadd.add(mapping.get(constr.getNegation()));	
				}
			}
			
			assert !toadd.isEmpty() : "toadd should not be empty";
			F.add(toadd);
			res.addCnf(F);
		}
		return res;
	}
	
	/**
     * Checks if a constraint is unset in the given CNF and contradiction set.
     *
     * @param constr The constraint to check.
     * @param T      The CNF formula.
     * @param N      The contradiction set.
     * @return True if the constraint is unset, false otherwise.
     */
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
	
	/**
     * Generates an irredundant query from the given CNF formula.
     *
     * @param T The CNF formula.
     * @return An irredundant query.
     */
	protected ACQ_Query irredundantQuery(ACQ_CNF T) {
		assert(T.isMonomial());
		ACQ_Network learned = new ACQ_Network(constraintFactory, bias.getVars(), constraintFactory.createSet(T.getMonomialPositive()));
		
		long startTime = System.currentTimeMillis();
		while(System.currentTimeMillis() - startTime < 100) { // Until 0.1 second timeout
			ACQ_Query q = constrSolver.solveQ(learned);
			
			ConstraintSet kappa = bias.getKappa(q);
			for(ACQ_Clause clause : T) {
				Unit unit = clause.get(0);
				if (unit.isNeg())
					kappa.remove(unit.getConstraint());
			}
			
			if(kappa.size() > 0) {
				return q;
			}
		}
		
		for(ACQ_IConstraint c : bias.getConstraints()) {
			if (!learned.contains(c)) {
				learned.add(c.getNegation(), true);
				ACQ_Query q = constrSolver.solveQ(learned);
				learned.remove(c.getNegation());
				if(!q.isEmpty())
					return q;
				else
					T.add(new ACQ_Clause(mapping.get(c)));
			}
		}
		return new ACQ_Query();
	}
	
	/**
     * 
     *
     * @param b       The base network.
     * @param network The network to check for contradictions.
     * @return A contradiction set 
     */
	protected Contradiction quickExplain(ACQ_Network b, ACQ_Network network) {
		chrono.start("quick_explain");
		Contradiction result;
		if (network.size() == 0) {
			result = new Contradiction(new ACQ_Network());
		}
		else {
			ACQ_Network res = quick(b, b, network);
			assert !isConsistent(res) : "quickExplain must return inconsistent networks";
			result = new Contradiction(res);
		}
		chrono.stop("quick_explain");
		return result;
	}
	
	protected ACQ_Network quick(ACQ_Network b, ACQ_Network delta, ACQ_Network c) {
		//System.out.println("In");
		if(delta.size() != 0 && !isConsistent(b)) {
			return new ACQ_Network(c.getFactory(), c.getFactory().createSet());
		}
		if(c.size() == 1) 
			return c;
		
		ACQ_Network c1 = new ACQ_Network(constraintFactory, bias.network.getVariables()); 
		ACQ_Network c2 = new ACQ_Network(constraintFactory, bias.network.getVariables());
		
		int i=0;
		for (ACQ_IConstraint constr : c.getConstraints()) {
			if(i < c.size()/2) {
				c1.add(constr, true);
			}
			else {
				c2.add(constr, true);
			}
			i+=1;
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

	/**
     * Checks if the given network is consistent.
     *
     * @param network The network to check.
     * @return True if the network is consistent, false otherwise.
     */
	protected Boolean isConsistent(ACQ_Network network) {
		return constrSolver.solve(network);
	}
	
	 /**
     * Generates a preprocessing query.
     *
     * @param n_random 
     * @return A preprocessing query.
     */
	protected ACQ_Query preproc_query_gen(int n_random) {
		if (this.strategy != null && this.strategy.size() > 0) {
			ACQ_Network s = this.strategy.get(0);
			this.strategy.remove(0);
			return constrSolver.solveQ(s);
		} else if (n_random < max_random){
			ACQ_Scope scope = bias.getVars();
			Random random = new Random();
			int values[] = new int [scope.size()]; 
			for (int i = 0; i < values.length; i++) {
				values[i] = random.nextInt(domain.getMax(i)+1);
			}
			return new ACQ_Query(scope, values);
		}
		else {
			return new ACQ_Query();
		}
	}
	
	@SuppressWarnings("unused")
	protected boolean preprocess(ACQ_CNF T, ContradictionSet N, int max_queries) throws Exception {
		boolean collapse = false;
		boolean stop = false;
		int oldbias_size = bias_size_before_preprocess;
		
		int n_random = 0;
		
		for(ACQ_Query membership_query : queries){

			if (bias.getConstraints().isEmpty())
				break;
			
			assert membership_query != null : "membership query can't be null";
			
			
			if(constrSolver.isTimeoutReached() || satSolver.isTimeoutReached()) {
				collapse = true;
				break;
			}
		
			if (membership_query.isEmpty()) {
				stop = true;
			}
			else {
				if (verbose) System.out.print("[PREPROC] " + membership_query.getScope() +"::"+ Arrays.toString(membership_query.values));
				boolean answer = membership_query.isPositive();
				n_asked++;
				ConstraintSet kappa = bias.getKappa(membership_query);
				if (verbose) System.out.println("::" + membership_query.isPositive());
				if(kappa.size() > 0) {
					if (answer) {
						for (ACQ_IConstraint c : kappa) {
							Unit unit = mapping.get(c).clone();
							unit.setNeg();
							T.unitPropagate(unit, chrono);
							N.unitPropagate(unit, chrono);
						}
						bias.reduce(kappa);
					}
					else {
						if (kappa.size() == 1) {
							ACQ_IConstraint c = kappa.get_Constraint(0);
							Unit unit = mapping.get(c).clone();
							T.unitPropagate(unit, chrono);
							N.unitPropagate(unit, chrono);
							bias.reduce(c.getNegation());
						}
						else {
							ACQ_Clause disj = new ACQ_Clause();
							for (ACQ_IConstraint c: kappa) {
								Unit unit = mapping.get(c).clone();
								disj.add(unit);
							}
							T.addChecked(disj);
						}
					}
				}
				
				if (oldbias_size >= bias.getSize()) {
					n_random++;
				}
				else {
					oldbias_size = bias.getSize();
					n_random = 0;
				}
			}
		}
		
		return collapse;
	}

	@SuppressWarnings("unused")
	public boolean process(Chrono chronom, int max_queries) throws Exception {
		chrono = chronom;
		boolean convergence = false;
		boolean collapse = false;
		ACQ_CNF T = new ACQ_CNF();
		ContradictionSet N;
		if (this.backgroundKnowledge == null) {
			N = new ContradictionSet(constraintFactory, bias.network.getVariables(), mapping);
		} else {
			N = this.backgroundKnowledge;
		}
		
		// assert(learned_network.size()==0);
		chrono.start("total_acq_time");
		
		//collapse = preprocess(T, N, max_queries);
		bias_size_after_preprocess = bias.getSize();
		
		ArrayList<String> asked = new ArrayList<>();
		
		if(!init_network.isEmpty()) {
			
			for (ACQ_IConstraint c : init_network.getConstraints()) {
				Unit unit = mapping.get(c).clone();
				T.unitPropagate(unit, chrono);
				N.unitPropagate(unit, chrono);
				T.add(new ACQ_Clause(mapping.get(c)));

			}
			bias.reduce(init_network.getConstraints());
		}
		
		for(ACQ_Query membership_query : queries){

			if (bias.getConstraints().isEmpty())
				break;

			assert membership_query != null : "membership query can't be null";
			
			
			if(constrSolver.isTimeoutReached() || satSolver.isTimeoutReached()) {
				collapse = true;
				break;
			}
		
			if (membership_query.isEmpty()) {
				convergence = true;
			}
			else {
				assert !asked.contains(membership_query.toString());
				asked.add(membership_query.toString());
				if (verbose) System.out.print(membership_query.getScope() +"::"+ Arrays.toString(membership_query.values));
				boolean answer = membership_query.isPositive();
				if(log_queries)
					 FileManager.writeLog(membership_query, "queries");
				n_asked++;
				ConstraintSet kappa = bias.getKappa(membership_query);
				assert kappa.size() > 0;
				if (verbose) System.out.println("::" + membership_query.isPositive());
				if(answer) {
					for (ACQ_IConstraint c : kappa) {
						Unit unit = mapping.get(c).clone();
						unit.setNeg();
						T.unitPropagate(unit, chrono);
						N.unitPropagate(unit, chrono);
					}
					bias.reduce(kappa);
				}
				else {
					if (kappa.size() == 1) {
						ACQ_IConstraint c = kappa.get_Constraint(0);
						Unit unit = mapping.get(c).clone();
						T.unitPropagate(unit, chrono);
						N.unitPropagate(unit, chrono);
						T.add(new ACQ_Clause(mapping.get(c)));
						bias.reduce(c.getNegation());
					}
					else {
						ACQ_Clause disj = new ACQ_Clause();
						for (ACQ_IConstraint c: kappa) {
							Unit unit = mapping.get(c).clone();
							disj.add(unit);
						}
						T.add(disj);
						//T.unitPropagate(chrono);
					}
				}
			}
		}
		chrono.stop("total_acq_time");
		if (!collapse) {
			if (verbose) System.out.print("[INFO] Extract network from T: ");
			if (max_queries != n_asked) {
				SATModel model = satSolver.solve(T);
				if (verbose) System.out.println("Done");
				learned_network = toNetwork(model);
				System.out.println(learned_network);
			}
			else {
				learned_network = new ACQ_Network(constraintFactory, bias.getVars());
				for (ACQ_IConstraint constr: bias.getConstraints()) {
					if (!isUnset(constr, T, N)) {
						learned_network.add(constr, true);
					}
					
				}
				if (verbose) System.out.println("Done");
			}
			learned_network.clean();
		}

		return !collapse;
	}
	
	protected ACQ_Network toNetwork(SATModel model) throws Exception {
		chrono.start("to_network");
		assert(model != null);
		ACQ_Network network = new ACQ_Network(constraintFactory, bias.getVars());
		
		for (Unit unit : mapping.values()) {
			if(model.get(unit)) {
				network.add(unit.getConstraint(), true);
			}
		}
		chrono.stop("to_network");
		return network;
	}

	
}
