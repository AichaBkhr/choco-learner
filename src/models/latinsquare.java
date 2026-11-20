package models;

import java.text.MessageFormat;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.util.tools.StringUtils;

public class latinsquare {
	static IntVar[][] rows, cols, shapes;
	static int s;

public static void main(String[]args) {
	int n = 10;
	s=3;
	// A new model instance
	Model model = new Model();

	rows = new IntVar[n][n];

	for (int i = 0; i < n; i++) {
		for (int j = 0; j < n; j++) {
			rows[i][j] = model.intVar("c_" + i + "_" + j, 0, n-1, false);
		}
	}

	

	for (int r = 0; r < n; r++)
		for (int c = 0; c < n-1; c++)
			for (int c2 = c + 1; c2 < n; c2++)
				model.arithm(rows[r][c] ,"!=", rows[r][c2]).post();
					

	// column checker
	for (int c = 0; c < n; c++)
		for (int r = 0; r < n-1; r++)
			for (int r2 = r + 1; r2 < n; r2++)
				model.arithm (rows[r][c] ,"!=", rows[r2][c]).post();
	

	

	//symmetry-breaking constraints
	System.out.println(model.getNbCstrs());
	System.out.println(model.getSolver().findSolution());
	System.out.println(model.getSolver().getSolutionCount());
	StringBuilder st = new StringBuilder();
    String line = "+";
    for (int i = 0; i < n; i++) {
        line += "----+";
    }
    line += "\n";
    st.append(line);
    for (int i = 0; i < n; i++) {
        st.append("|");
        for (int j = 0; j < n; j++) {
            st.append(StringUtils.pad((rows[i][j].getValue()) + "", -3, " ")).append(" |");
        }
        st.append(MessageFormat.format("\n{0}", line));
    }
    st.append("\n\n\n");
    System.out.println(st.toString());

}
}
