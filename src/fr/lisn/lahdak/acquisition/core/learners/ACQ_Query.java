package fr.lisn.lahdak.acquisition.core.learners;

import java.util.Arrays;
import java.util.Iterator;

import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_IConstraint;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_Network;
import fr.lisn.lahdak.acquisition.core.oracle.Answer;

/**
 * A query is a potential solution that is submitted to the solver. It can be a positive or a negative example.
 * It contains a scope (the variables involved) and a set of values for these variables.
 * 
 *
 */
public class ACQ_Query implements Cloneable {
	/**
	 * Defines if this query is positive or negative
	 */
	protected Answer classification;			// as positive or negative
	/**
	 * Defines if the classification of this query has been determined or not
	 */
	protected boolean isClassified;		// classified or not yet classified
	/**
	 * Variables involved in this query
	 */
	public final ACQ_Scope scope;
	/**
	 * Values for these variables
	 */
	public final int[] values;

	/**
	 * Constructor for a query from a scope and a set of values
	 * 
	 * @param scope Variables of this query
	 * @param values Values of these variables
	 */
	public ACQ_Query(ACQ_Scope scope, int[] values) {
		assert scope.size() == values.length;
		this.isClassified = false;
		this.scope = scope;
		this.values = values;
		this.classification= Answer.UKN;
	}

	/**
	 * Empty constructor
	 */
	public ACQ_Query() {
		this.scope=ACQ_Scope.EMPTY;
		this.values=null;
		this.classification= Answer.UKN;
	}
	/**
	 * Returns the set of values of this query
	 * 
	 * @return Values of this query
	 */
	public int[] getTuple() {
		return values;
	}

	/**
	 * Checks if the scope of this query is empty
	 * 
	 * @return true if the scope of this query is empty
	 */
	public boolean isEmpty() {
		return scope == ACQ_Scope.EMPTY;
	}

	/**
	 * Set the classification of this query to the same value of the specified query
	 * 
	 * @param asked_query Query used to set the classification of this query
	 */
	public void classify_as(ACQ_Query asked_query) {
		this.classification = (asked_query.isPositive())? Answer.YES:Answer.NO;
	}
	/**
	 * Returns the classification of this query
	 * 
	 * @return true if this query is positive
	 */
	public boolean isPositive() {
		return this.classification.equals(Answer.YES);
	}

	/**
	 * Returns the classification of this query
	 * 
	 * @return true if this query is positive
	 */
	public boolean isNegative() {
		return this.classification.equals(Answer.NO);
	}
	/**
	 * Returns the value of the specified variable in this query
	 * 
	 * @param numvar Identify the variable
	 * @return Value of the variable in this query
	 */
	public int getValue(int numvar){
		int index=0;
		for(Iterator<Integer> it=scope.iterator();it.hasNext();index++)
		{
			if(numvar==it.next()) return values[index];
		}
		return -1;
	}


	public int[] getProjection(ACQ_Scope bgd) {
		int[] proj = new int[bgd.size()];
		int i = 0,j=0;
		for (int numvar : scope) {
			if (bgd.contains(numvar)) {
				proj[i++] = values[j];
			}
			j++;
		}
		return proj;
	}

	/**
	 * Checks if this query has been classified or not
	 * 
	 * @return true if this query has been classified
	 */
	public boolean isClassified() {
		return this.isClassified;
	}

	/**
	 * Set if this query is positive or not
	 * 
	 * @param classification true if this query is positive, else false
	 */
	public void classify(Answer classification) {
		this.isClassified = true;
		this.classification = classification;
	}

	public String toString() {
		String output=scope +"::"+ Arrays.toString(values) ;// + "::"+this.isPositive();

		return output;
	}

	/**
	 * Returns the scope of this query
	 * 
	 * @return scope of this query
	 */
	public ACQ_Scope getScope(){
		return scope;
	}

	public String oracleAskingFormat() {
		return ("var"+scope.getVariables()+"="+"val"+Arrays.toString(values)).replaceAll("\\s+","");
	}

	public Answer getClassification() {
		return this.classification;
	}

	public ACQ_Network getM(ACQ_Bias bias) {
		ACQ_Network res = new ACQ_Network(
			bias.getNetwork().getFactory(), 
			bias.getVars()
		);
		for (ACQ_IConstraint c : bias.getConstraints()) {
			if (c.checker(c.getProjection(this))) {  
				res.add(c, true);
			}
		}
		return res;
	}

	public boolean extend(ACQ_Query example) {

		if(this.getScope().containsAll(example.getScope())) {
			int[] projection= this.getProjection(example.getScope());
			for(int i=0; i<projection.length; i++)
				if(projection[i]!=example.getTuple()[i])	
					return false;
			return true;
		}

		return false;
	}
	 @Override
	    protected ACQ_Query clone() throws CloneNotSupportedException {
	        return (ACQ_Query) super.clone();  
	    }
}
