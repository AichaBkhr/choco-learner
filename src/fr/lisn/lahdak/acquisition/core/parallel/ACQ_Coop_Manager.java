package fr.lisn.lahdak.acquisition.core.parallel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_IConstraint;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_Network;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ConstraintFactory;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Bias;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Scope;
import fr.lisn.lahdak.acquisition.core.tools.FileManager;

/**
 * 
 * Class used to let threads comunicate between eachother
 *
 */

public class ACQ_Coop_Manager extends ACQ_PACQ_Manager{



	HashMap<Integer,Set<ACQ_ScopeMessage>> scopes_mailbox= new HashMap<>();

	HashMap<Integer,Set<ACQ_Scope>> Biases_scopes= new HashMap<>();
	CopyOnWriteArraySet<ACQ_Scope> visited_scopes;


	String[] threads_id;

	boolean scope_sharing=true;
	boolean query_sharing=true;


	public ACQ_Coop_Manager( CopyOnWriteArraySet<ACQ_QueryMessage> queries_mailbox) {
		super(queries_mailbox);
		this.visited_scopes= new CopyOnWriteArraySet<>();


	}

	public void setBias(ACQ_Bias bias) {
		this.bias=bias;
	}

	public ACQ_Bias getBias() {
		return this.bias;
	}



	public void set_nb_Threads(int n) {
		this.threads_id= new String[n];
	}

	public void set_thread(String name, int i) {
		this.threads_id[i]= name;
	}

	public boolean isSharing_enabled() {
		return scope_sharing;
	}


	public void setSharing_enabled(boolean sharing_enabled) {
		this.scope_sharing = sharing_enabled;
	}




	public synchronized boolean send(ACQ_Message message) {

		if(message instanceof ACQ_ScopeMessage)
			return send((ACQ_ScopeMessage) message);
		if(message instanceof ACQ_QueryMessage)
			return send((ACQ_QueryMessage) message);

		return false;
	}


	private synchronized boolean send(ACQ_ScopeMessage scope_m) {

		if(scope_sharing) 
			for(Integer receiver : Biases_scopes.keySet()) 
				if(scope_m.getSender()!=threads_id[receiver] && 
				check(Biases_scopes.get(receiver),scope_m.getScope())) {	
					if(scopes_mailbox.get(receiver)==null)
						scopes_mailbox.put(receiver, new HashSet<>());
					if(!scopes_mailbox.get(receiver).contains(scope_m))
						scopes_mailbox.get(receiver).add(scope_m);
					//	FileManager.printFile("Thread :"+Thread.currentThread().getName() +
					//			":: "+scope_m, "send_success");
					return true;
				}

		//		FileManager.printFile("Thread :"+Thread.currentThread().getName() +
		//				":: "+scope_m, "send_fails");
		return false;


	}


	public synchronized Set<ACQ_ScopeMessage> getScopeMessages(String id) {
		Set<ACQ_ScopeMessage> scopes= new HashSet<>();

		if(!scopes_mailbox.isEmpty())
			if(scopes_mailbox.get(get_index(id))!=null)
				for( ACQ_ScopeMessage scope_m: scopes_mailbox.get(get_index(id)))
				{
					scopes.add(scope_m);
				}
		return scopes;
	}




	private int get_index(String id) {		
		for(int i=0; i<threads_id.length; i++)
			if(id.equals(threads_id[i]))
				return i;

		return -1;
	}

	public boolean check(Set<ACQ_Scope>Scopes,ACQ_Scope scope) {

		for(ACQ_Scope a : Scopes) {

			if(a.containsAll(scope)||scope.containsAll(a))
				return true;
		}
		return false;
	}






	public void applyPartitioning(int Users) {
		this.bias_partitions=Bias_Partition_ScopeBased(Users);

	}

	private ConcurrentHashMap<String,ACQ_Bias> Bias_Partition_ScopeBased(int Users){

		Set<ACQ_Scope> scopes= new HashSet<>();

		ConcurrentHashMap<String,ACQ_Bias> partitions = new ConcurrentHashMap<String,ACQ_Bias>();
		List<ACQ_Network> networks = new ArrayList<>(Users);

		List<Integer> id_users=new ArrayList<>();
		for(int i = 0; i < Users; i++)
		{

			networks.add(new ACQ_Network(bias.getNetwork().getFactory(), bias.getVars()));
			id_users.add(i);
		}


		Collections.shuffle(id_users);

		int id=0, nb_scopes=0;


		for (ACQ_IConstraint cst : bias.getConstraints()) {

			scopes.add(cst.getScope());

			if(nb_scopes<scopes.size())
			{

				networks.get(id_users.get(id)).addAll(bias.getProjection(cst.getScope()), false);


				if(Biases_scopes.get(id_users.get(id))==null)
					Biases_scopes.put(id_users.get(id), new HashSet<>());
				Biases_scopes.get(id_users.get(id)).add(cst.getScope());

				nb_scopes++;
				id= (id+1)%Users;

			}

		}

		for(int i =0 ; i<Users ;i++) 
			partitions.put("pool-1-thread-"+(i+1),new ACQ_Bias(networks.get(i)));


		return partitions;


	}




	@SuppressWarnings("unused")
	private List<ACQ_Bias> Bias_Partition_MultipleAritics(int Users){

		Set<ACQ_Scope> scopes= new HashSet<>();

		List<ACQ_Bias> partitions = new ArrayList<>(Users);
		List<ACQ_Network> networks = new ArrayList<>(Users);

		List<Integer> id_users=new ArrayList<>();
		for(int i = 0; i < Users; i++)
		{
			ConstraintFactory constraintFactory=new ConstraintFactory();

			networks.add(new ACQ_Network(constraintFactory, bias.getVars()));
			id_users.add(i);
		}


		Collections.shuffle(id_users);


		for (ACQ_IConstraint cst : bias.getConstraints()) {
			if(!dominated(cst))
				scopes.add(cst.getScope());

		}

		System.out.print(scopes.size());
		int id=0, nb_scopes=0;

		for(ACQ_Scope s : scopes) {

			if(nb_scopes<scopes.size())
			{

				networks.get(id_users.get(id)).addAll(bias.getProjection(s), true);

				if(Biases_scopes.get(id_users.get(id))==null)
					Biases_scopes.put(id_users.get(id), new HashSet<>());
				Biases_scopes.get(id_users.get(id)).add(s);


				nb_scopes++;
				id= (id+1)%Users;

			}
		}


		int total=0;
		/*this method removes duplicate constraints
		 * lets say we have a bias of binary and unary constraints
		 * since get projections gets constraints of the scope and its subscopes
		 * we can have this as scopes: {3,6} in network 1 , {3,9} in network2.
		 * if we apply get projection on them the result would be as follows :
		 * network 1 : constraints on {3,9} and {3} and {9}
		 * network 2 : constraints on {3,6} and {3} and {6}
		 * */
		remove_duplicates(networks);


		for(int i =0 ; i<Users ;i++) {

			total+=networks.get(i).size();
			partitions.add(new ACQ_Bias(networks.get(i)));

		}

		System.out.print(total+"== "+bias.getNetwork().size());

		return partitions;


	}


	private boolean dominated(ACQ_IConstraint cst) {
		for(ACQ_IConstraint c : bias.getConstraints())
			if(c.getScope().size()>cst.getScope().size() && c.getScope().containsAll(cst.getScope())) {
				return true;

			}
		return false;
	}

	private void remove_duplicates(List<ACQ_Network> networks) {
		for(int i =0 ; i<networks.size()-1;i++) {
			for(int j =i+1 ; j<networks.size() ;j++) {
				for(ACQ_IConstraint c : networks.get(i).getConstraints())
					for(ACQ_IConstraint c1 : networks.get(j).getConstraints())
						if(c.equals(c1))
							networks.get(j).remove(c1);
			}			
		}
	}
	public boolean isQuery_sharing() {
		return query_sharing;
	}


	public void setQuery_sharing(boolean memory_enabled) {
		this.query_sharing = memory_enabled;
	}






	public boolean ask(ACQ_Query e) {
		// TODO Auto-generated method stub
		return false;
	}


	/**
	 * 
	 * @param example
	 */
	public synchronized void asked_query(ACQ_Query example) {

		if(query_sharing) {
			if(!queries_mailbox.isEmpty()) 	

				for(ACQ_QueryMessage query_m : queries_mailbox) 
				{
					if((example.extend(query_m.getQuery()) && query_m.getQuery().isNegative()) ||
							query_m.getQuery().extend(example) && query_m.getQuery().isPositive())
					{
						FileManager.writeLog("Thread :"+Thread.currentThread().getName() +
								"::"+example , "asked_query");

						example.classify_as(query_m.getQuery());
						break;					
					}
				}

		}
	}





	public boolean exists(Set<ACQ_Query>memory,ACQ_Query example) {
		for(ACQ_Query a : memory) {
			boolean intersect =a.getScope().intersect(example.getScope());
			if(!Arrays.equals(a.getTuple(), example.getTuple())&& !intersect)
				return true;
		}
		return false;
	}

	public Set<ACQ_Scope> get_Bias_scope(String name) {
		return Biases_scopes.get(get_index(name));
	}

	public void setLearnedNetwork(ACQ_Scope vars) {

		ConstraintFactory factory= new ConstraintFactory();
		this.setLearned_network(new ACQ_Network(factory,vars));
	}

	public void clear_scopes_mailbox(String id) {
		int index=get_index(id);
		if(index>=0){
		if(scopes_mailbox.get(index)!=null)
			scopes_mailbox.get(index).clear();
		}
	}

	synchronized public void scopeVisited(ACQ_Scope scope) {
		this.visited_scopes.add(scope);
	}

	synchronized public boolean isVisitedScope(ACQ_Scope scope) {
		return this.visited_scopes.contains(scope);
	}





}
