package fr.lisn.lahdak.acquisition.core.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_IConstraint;
import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_Network;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Scope;

public class Test_Network {
	
	ACQ_Network learned;
	
	String test_file;
	ArrayList<ACQ_Query> examples;

	public Test_Network(ACQ_Network learned,String test_file) {
		this.learned=learned;
		this.test_file=test_file;
		this.examples=parseTest();
		
		
	}

	
	
	private ArrayList<ACQ_Query> parseTest()  {
		
		ArrayList<ACQ_Query> examples=new ArrayList<ACQ_Query>();
	    File file = new File("benchmarks/PTHG/"+test_file+".test");
	    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
	        String line;
	        while ((line = br.readLine()) != null) {
	        	String[] cols =line.split(" ");
	        	int[] vars = new int[cols.length];
	        	int[] values= new int[cols.length];
	        	int i =0;
	        	for(String s: cols) {
	        		values[i]=Integer.parseInt(s);
	        		vars[i]=i;
	        		i++;
	        	}
	        	ACQ_Scope scope = new ACQ_Scope(vars);
	            ACQ_Query q = new ACQ_Query(scope,values);
	            examples.add(q);
	        }
	    } catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}		
		
		return examples;
	}

	public void check() {
		for(ACQ_Query e: examples) {
			boolean clas=true;
		for(ACQ_IConstraint c : this.learned) {
			
			if(c.checker(e.getProjection(c.getScope()))==false) {
				clas=false;
			
			}
			
		}
		
		
		FileManager.writeTest(Arrays.toString(e.values)+" "+clas, test_file);
		
		}
		
	}

	
	

	
	


}
