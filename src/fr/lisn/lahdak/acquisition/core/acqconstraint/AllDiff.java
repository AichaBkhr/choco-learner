package fr.lisn.lahdak.acquisition.core.acqconstraint;

import java.util.Set;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;

import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;


public  class AllDiff extends ACQ_Constraint{

	public AllDiff(Set<Integer> clique) {

		super("AllDiff",clique);
	}

	/**
	 * @author Mess. SarahM
	 * 
	 * check if all the given values are different 
	 * 
	 * @param values a set of given values
	 * @return a boolean, true if ALL the given values are different, false otherwise
	 */
	@Override
	public boolean check(int... values) {
		if (values.length == 0 || values.length == 1) {
			return true;
		}
		else
		{
			for (int i = 0; i < values.length; i++) {
				for (int j = i + 1; j < values.length; j++) {
					if (values[i] == values[j]) {
						return false;
					}
				}
			}
				return true;
		}
	}

	@Override
	public ACQ_IConstraint getNegation() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Constraint[] getChocoConstraints(Model model, IntVar... intVars) {
		
		int[] alldiff_vars = this.getVariables();
		IntVar[] vars=new IntVar[alldiff_vars.length];
	
		
		for(int i=0; i<alldiff_vars.length; i++) {
				vars[i]=intVars[alldiff_vars[i]];
			
			 }
		return new Constraint[]{model.allDifferent(vars)};

	}

	@Override
	public void toReifiedChoco(Model model, BoolVar b, IntVar... intVars) {

		model.allDifferent(intVars).reifyWith(b);

	}

	@Override
	public String getNegName() {
		String newname = this.getName();
		if (this.getName().startsWith("Not")) {
			newname = newname.substring(3);
		} else {
			newname = "Not" + newname;
		}

		return newname;
	}

	@Override
	public String toSmtlib() {
		return "";
	}

	@Override
	public boolean check(ACQ_Query query) {
		int value[] = this.getProjection(query);
		return check(value);
	}

}
