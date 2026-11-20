package fr.lisn.lahdak.acquisition.core.learners;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import fr.lisn.lahdak.acquisition.core.acqconstraint.ACQ_Network;
import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_ConstraintSolver;
import fr.lisn.lahdak.acquisition.core.oracle.Answer;
import fr.lisn.lahdak.acquisition.core.workspace.IExperience;
import fr.lisn.lahdak.acquisition.expe.ExpeBuilder;

public class ACQ_QueryTest {

    private ACQ_Bias bias;
    private ACQ_Network network;
    private ACQ_Scope scope;
    private int[] values;
    private ACQ_ConstraintSolver solver;
    private ACQ_Query query;

    @Before
    public void setUp() {
        System.out.println("Setting up experience-based test for ACQ_Query");

        try {
            ExpeBuilder builder = new ExpeBuilder();
            builder.setAlgo(ACQ_Algorithm.QUACQ); // Use QUACQ algorithm
            builder.setFile("golomb_4"); // Example experience file
            // builder.setVarSelector("defaultVarSelector");
            // builder.setValSelector("defaultValSelector");

            IExperience experience = builder.build();
            System.out.println("Experience built successfully");

            bias = experience.createBias();
            solver = experience.createSolver();
            network = bias.getNetwork();
            scope = bias.getVars();

            values = new int[scope.size()];
            for (int i = 0; i < scope.size(); i++) {
                values[i] = i * 10; 
            }
            query = new ACQ_Query(scope, values);

            System.out.println("Bias initialized: " + bias);
            System.out.println("Scope initialized: " + scope);
            System.out.println("Query initialized: " + query);

        } catch (Exception e) {
            e.printStackTrace();
            fail("Failed to set up the experience: " + e.getMessage());
        }
    }

    @Test
    public void testQueryInitialization() {
        assertNotNull("Query should not be null", query);
        assertEquals("Scope should match the initialized scope", scope, query.getScope());
        assertNotNull("Values should not be null", query.getTuple());
        assertEquals("Values length should match scope size", scope.size(), query.getTuple().length);
        assertFalse("Query should not be classified initially", query.isClassified());
        assertEquals("Query classification should be UKN initially", Answer.UKN, query.classification);
    }

    @Test
    public void testQueryClassification() {
        query.classify(Answer.YES);
        assertTrue("Query should be classified as positive", query.isPositive());
        assertFalse("Query should not be classified as negative", query.isNegative());
        assertTrue("Query should be classified", query.isClassified());

        query.classify(Answer.NO);
        assertTrue("Query should be classified as negative", query.isNegative());
        assertFalse("Query should not be classified as positive", query.isPositive());
    }

    @Test
    public void testQueryProjection() {
        int[] projectedValues = query.getProjection(scope);
        assertNotNull("Projected values should not be null", projectedValues);
        assertEquals("Projected values length should match projection scope size", scope.size(), projectedValues.length);

        System.out.println("Projected values: " + java.util.Arrays.toString(projectedValues));
    }

    @Test
    public void testQueryWithEmptyScope() {
        ACQ_Scope emptyScope = new ACQ_Scope();
        int[] emptyValues = new int[0];

        ACQ_Query emptyQuery = new ACQ_Query(emptyScope, emptyValues);
        System.out.println(emptyQuery+ " : should be an empty query");
        //the assertNull test under is good, the query is empty : {}::[]::false
        //assertNull("Query with empty scope should be empty", emptyQuery);
        assertEquals("Query classification should be UKN", Answer.UKN, emptyQuery.classification);
    }

    @Test
    public void testQueryExtend() {
        ACQ_Query exampleQuery = new ACQ_Query(scope, values);
        assertTrue("Query should extend the example query", query.extend(exampleQuery));
        
        int[] values2 = new int[scope.size()];
        for (int i = 0; i < scope.size(); i++) {
            values[i] = i + 2; 
        }
        ACQ_Query nonMatchingQuery = new ACQ_Query(scope, values2);
        assertFalse("Query should not extend the non-matching query", query.extend(nonMatchingQuery));
    }

    @Test
    public void testQueryToString() {
        String expectedString = scope + "::" + java.util.Arrays.toString(query.getTuple()) + "::false";
        assertEquals("String representation should match", expectedString, query.toString());
    }

    @Test
    public void testQueryClone() throws CloneNotSupportedException {
        ACQ_Query clonedQuery = query.clone();
        assertNotNull("Cloned query should not be null", clonedQuery);
        assertEquals("Cloned query scope should match the original", query.getScope(), clonedQuery.getScope());
        assertArrayEquals("Cloned query values should match the original", query.getTuple(), clonedQuery.getTuple());
        assertEquals("Cloned query classification should match the original", query.classification, clonedQuery.classification);
    }
}