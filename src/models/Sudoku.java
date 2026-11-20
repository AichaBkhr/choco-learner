package models;

import java.text.MessageFormat;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.util.tools.StringUtils;

public class Sudoku {
	static IntVar[][] rows, cols, shapes;
	static int s;

public static void main(String[]args) {
	int n = 9;
	s=3;
	// A new model instance
	Model model = new Model();

	rows = new IntVar[n][n];
	cols = new IntVar[n][n];
	shapes = new IntVar[n][n];
	for (int i = 0; i < n; i++) {
		for (int j = 0; j < n; j++) {
			rows[i][j] = model.intVar("c_" + i + "_" + j, 1, n, false);
		}
	}

	

	for (int r = 0; r < 9; r++)
		for (int c = 0; c < 8; c++)
			for (int c2 = c + 1; c2 < 9; c2++)
				model.arithm(rows[r][c] ,"!=", rows[r][c2]).post();
					

	// column checker
	for (int c = 0; c < 9; c++)
		for (int r = 0; r < 8; r++)
			for (int r2 = r + 1; r2 < 9; r2++)
				model.arithm (rows[r][c] ,"!=", rows[r2][c]).post();
	
	for (int r = 0; r < 9; r += 3)
	for (int c = 0; c < 9; c += 3)
		// row, col is start of the 3 by 3 grid
		for (int pos = 0; pos < 8; pos++)
			for (int pos2 = pos + 1; pos2 < 9; pos2++)
					model.arithm(rows[r + pos % 3][c + pos / 3] ,"!=",rows[r + pos2 % 3][c + pos2 / 3]).post();
					
				
	

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
