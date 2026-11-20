package fr.lisn.lahdak.acquisition.core.acqsolver;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.io.File;

import fr.lisn.lahdak.acquisition.core.acqconstraint.*;
import fr.lisn.lahdak.acquisition.core.acqsolver.MiniSatSolver.AtLeastAtMostResult;
import org.chocosolver.sat.Clause;
import org.sat4j.core.VecInt;
import org.sat4j.maxsat.WeightedMaxSatDecorator;
import org.sat4j.pb.OptToPBSATAdapter;
import org.sat4j.pb.PseudoOptDecorator;
import org.sat4j.minisat.SolverFactory;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.IProblem;
import org.sat4j.specs.ISolver;
import org.sat4j.specs.TimeoutException;

public class Sat4jMaxSATSolver extends SATSolver {

    RevConstraintMapping revmapping;
    int timeout;
    int nvars = 0;
    boolean timeoutReached = false;
    Runtime runtime;
    String torun;

    public Sat4jMaxSATSolver() {
        revmapping = new RevConstraintMapping();
        runtime = Runtime.getRuntime();
        torun = EXTERNAL ? locateJar() : null;
    }


    /** -Dsat4j.external=true : previous mechanism, the external jar on a .cnf/.wcnf file (one JVM per call).
     *  Default : the same Sat4j 2.3.6 MaxSAT library, called in-process (about 0.1 s saved per call). */
    static final boolean EXTERNAL = Boolean.getBoolean("sat4j.external");
    static final String JAR_PROPERTY = "sat4j.maxsat.jar";
    static final String JAR_RELATIVE = "src/lib/sat4j-maxsat.jar";

    static String locateJar() {
        List<File> tried = new ArrayList<>();
        String prop = System.getProperty(JAR_PROPERTY);
        if (prop != null) {
            File f = new File(prop);
            if (f.isFile()) return f.getAbsolutePath();
            tried.add(f);
        }
        File cwd = new File(JAR_RELATIVE);
        if (cwd.isFile()) return cwd.getAbsolutePath();
        tried.add(cwd.getAbsoluteFile());
        try {
            File dir = new File(Sat4jMaxSATSolver.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            for (; dir != null; dir = dir.getParentFile()) {
                File f = new File(dir, JAR_RELATIVE);
                if (f.isFile()) return f.getAbsolutePath();
                tried.add(f);
            }
        } catch (Exception e) {
            // no code source (unusual class loader) : only the paths above are tried
        }
        throw new IllegalStateException("Sat4jMaxSATSolver: MaxSAT solver jar not found. Tried:\n  "
                + String.join("\n  ", tried.stream().map(File::getPath).toArray(String[]::new))
                + "\nRun from the choco-learner root, or pass -D" + JAR_PROPERTY + "=/path/to/sat4j-maxsat.jar");
    }

    /** Runs the external solver on a (w)cnf file and returns its output.*/
    private List<String> runSolver(String filename) {
        for (int attempt = 1; ; attempt++) {
            List<String> out = runSolverOnce(filename);
            if (out.stream().noneMatch(l -> l.startsWith("s UNKNOWN"))) return out;
            if (attempt == 3) {
                throw new IllegalStateException("Sat4jMaxSATSolver: the MaxSAT solver answered \"s UNKNOWN\" 3 times on "
                        + filename + " (no model: the result would be wrong). Machine overloaded ? The in-process solver"
                        + " (default, without -Dsat4j.external=true) does not have this problem.");
            }
        }
    }

    private List<String> runSolverOnce(String filename) {
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        List<String> cmd = Arrays.asList(java, "-jar", torun, filename);
        List<String> out = new ArrayList<>();
        int exit;
        try {
            Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) out.add(line);
            }
            exit = proc.waitFor();
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Sat4jMaxSATSolver: cannot run the MaxSAT solver: " + String.join(" ", cmd), e);
        }
        boolean answered = out.stream().anyMatch(l -> l.startsWith("s "));
        if (exit != 0 || !answered) {
            throw new IllegalStateException("Sat4jMaxSATSolver: the MaxSAT solver gave no answer (exit code " + exit
                    + ", no \"s\" line).\n  command: " + String.join(" ", cmd)
                    + "\n  output: " + String.join("\n          ", out.subList(0, Math.min(out.size(), 10))));
        }
        return out;
    }

    /** null when UNSAT : hard clauses unsatisfiable ("s UNSATISFIABLE", no objective line) or objective above maxObjective */
    private SATModel readModel(List<String> out, long maxObjective) {
        ArrayList<Integer> values = new ArrayList<>();
        for (String line : out) {
            if (line.startsWith("s UNSATISFIABLE")) return null;
            if (line.startsWith("c objective function")) {
                long objInt = Long.parseLong(line.split("=")[1].trim());
                if (objInt > maxObjective) return null;
            }
            if (line.startsWith("v")) values.addAll(convert(line));
        }
        return new MiniSatModel(values, revmapping);
    }


    /** the learning budget given by ACQ_WS through setLimit(), so that a single call cannot outlive the run
     *  (without it, one MaxSAT call can run for hours : the budget is only checked between two queries) */
    private void applyLimit(ISolver solver) {
        if (timeout > 0) solver.setTimeoutMs(timeout);
        else solver.setTimeout(Integer.MAX_VALUE);
    }

    SATModel solveInProcess(String formula, long hardWeight) {
        String[] lines = formula.split("\n");
        String[] header = lines[0].trim().split("\\s+");
        boolean weighted = header[1].equals("wcnf");
        int nv = Integer.parseInt(header[2]);
        try {
            WeightedMaxSatDecorator maxsat = null;
            ISolver sat = null;
            IProblem problem;
            if (weighted) {
                maxsat = new WeightedMaxSatDecorator(org.sat4j.pb.SolverFactory.newDefault(), false);
                maxsat.newVar(nv);
                applyLimit(maxsat);
                problem = new OptToPBSATAdapter(new PseudoOptDecorator(maxsat));
            } else {
                sat = SolverFactory.newDefault();
                sat.newVar(nv);
                applyLimit(sat);
                problem = sat;
            }
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;
                String[] tok = line.split("\\s+");
                int first = weighted ? 1 : 0;
                int[] lits = new int[tok.length - first - 1]; // the last token is the terminating 0
                for (int j = first; j < tok.length - 1; j++) lits[j - first] = Integer.parseInt(tok[j]);
                VecInt clause = new VecInt(lits);
                if (!weighted) sat.addClause(clause);
                else if (Long.parseLong(tok[0]) >= hardWeight) maxsat.addHardClause(clause);
                else maxsat.addSoftClause(Integer.parseInt(tok[0]), clause);
            }
            if (!problem.isSatisfiable()) return null;
            // an optimization stopped by the timeout returns a SUBOPTIMAL model without saying so : the min/max
            // distance of DQueryGen would no longer be the right one, so it is treated as a timeout
            if (weighted && !((OptToPBSATAdapter) problem).isOptimal()) {
                this.timeoutReached = true;
                return null;
            }
            ArrayList<Integer> values = new ArrayList<>();
            for (int lit : problem.model()) {
                // only the variables of the formula (the decorator adds selector variables above nv)
                if (lit > 0 && lit <= nv && revmapping.containsKey(lit)) values.add(lit);
            }
            return new MiniSatModel(values, revmapping);
        } catch (ContradictionException e) {
            return null; // trivially unsatisfiable hard clauses
        } catch (TimeoutException e) {
            this.timeoutReached = true;
            return null;
        }
    }

    @Override
    public SATModel solve(ACQ_CNF T) {
        assert false : "Not supported in Sat4jMaxSATSolver";
        return null;
    }

    @Override
    public void setVars() {
        // not needed
    }

    @Override
    public void setLimit(Long timeout) {
        this.timeout = timeout.intValue();
    }

    @Override
    public Unit addVar(ACQ_IConstraint constr, String name) {
        this.nvars++;
        Unit unit = new Unit(constr, nvars, false);
        return unit;
    }

    @Override
    public Boolean isTimeoutReached() {

        return this.timeoutReached;
    }


    public String toWCNF(ACQ_Formula Fhard, ACQ_Formula Fsoft, ACQ_Formula Fsofter) {

        int n = Fsofter.getClauses().size();
        int k = Fsoft.getClauses().size();
        int h = Fhard.getClauses().size();
        int nbClauses = h + k + n;

        //System.out.println("total in wcnf: " + nbClauses);
        StringBuilder clauses = new StringBuilder("p wcnf " + nvars + " " + nbClauses + "\n");
        // Hard clauses, very high weight
        for (ACQ_CNF T : Fhard.getCnfs()) {
            for (ACQ_Clause cl : T) {
                assert cl.getSize() > 0 : "empty clause";
                clauses.append(k * (n + 1) + n + 1);
                int l = k * (n + 1) + n + 1;
                for (Unit u : cl) {
                    if (u.isNeg()) {
                        Unit tmp = u.clone();
                        tmp.unsetNeg();
                        revmapping.add(tmp.getMiniSatVar(), tmp.clone());
                        clauses.append(" ").append(-u.getMiniSatVar());
                    } else {
                        revmapping.add(u.getMiniSatVar(), u.clone());
                        clauses.append(" ").append(u.getMiniSatVar());
                    }
                }
                clauses.append(" 0\n");
            }
        }

        // Soft clauses (weight n+1)
        for (ACQ_CNF T : Fsoft.getCnfs()) {
            for (ACQ_Clause cl : T) {
                assert cl.getSize() == 1 : "soft clause must be size 1";
                Unit u = cl.get(0);
                if(u.isNeg()) {
                    Unit tmp = u.clone();
                    tmp.unsetNeg();
                    revmapping.add(tmp.getMiniSatVar(), tmp.clone());
                    clauses.append(n + 1).append(" ").append(-u.getMiniSatVar()).append(" 0\n");
                }
                else {
                    revmapping.add(u.getMiniSatVar(), u.clone());
                    clauses.append(n + 1).append(" ").append(u.getMiniSatVar()).append(" 0\n");
                }
            }
        }

        // Soft clauses (weight n+1)
        for (ACQ_CNF T : Fsofter.getCnfs()) {
            for (ACQ_Clause cl : T) {
                assert cl.getSize() == 1 : "softer clause must be size 1";
                Unit u = cl.get(0);
                if(u.isNeg()) {
                    Unit tmp = u.clone();
                    tmp.unsetNeg();
                    revmapping.add(tmp.getMiniSatVar(), tmp.clone());
                    clauses.append(1).append(" ").append(-u.getMiniSatVar()).append(" 0\n");
                }
                else {
                    revmapping.add(u.getMiniSatVar(), u.clone());
                    clauses.append(1).append(" ").append(u.getMiniSatVar()).append(" 0\n");
                }
            }
        }

        return clauses.toString();
    }

    public String toWCNF(ACQ_Formula Fhard, ACQ_Formula Fsoft) {

        int n = 0;//Fsofter.getClauses().size();
        int k = Fsoft.getClauses().size();
        int h = Fhard.getClauses().size();

        int nbClauses = h + k + n;
        StringBuilder clauses = new StringBuilder("p wcnf " + nvars + " " + nbClauses + "\n");

        // Hard clauses, very high weight
        for (ACQ_CNF T : Fhard.getCnfs()) {
            for (ACQ_Clause cl : T) {
                assert cl.getSize() > 0 : "empty clause";
                clauses.append(k * (n + 1) + n + 1);
                for (Unit u : cl) {
                    if (u.isNeg()) {
                        Unit tmp = u.clone();
                        tmp.unsetNeg();
                        revmapping.add(tmp.getMiniSatVar(), tmp.clone());
                        clauses.append(" ").append(-u.getMiniSatVar());
                    } else {
                        revmapping.add(u.getMiniSatVar(), u.clone());
                        clauses.append(" ").append(u.getMiniSatVar());
                    }
                }
                clauses.append(" 0\n");
            }
        }

        // Soft clauses (weight n+1)
        for (ACQ_CNF T : Fsoft.getCnfs()) {
            for (ACQ_Clause cl : T) {
                assert cl.getSize() == 1 : "soft clause must be size 1";
                Unit u = cl.get(0);
                if(u.isNeg()) {
                    Unit tmp = u.clone();
                    tmp.unsetNeg();
                    revmapping.add(tmp.getMiniSatVar(), tmp.clone());
                    clauses.append(n + 1).append(" ").append(-u.getMiniSatVar()).append(" 0\n");
                }
                else {
                    revmapping.add(u.getMiniSatVar(), u.clone());
                    clauses.append(n + 1).append(" ").append(u.getMiniSatVar()).append(" 0\n");
                }
            }
        }
        return clauses.toString();
    }

    public SATModel maxsat(ACQ_Formula Fhard, ACQ_Formula Fsoft, ACQ_Formula Fsofter) {
        fireSolverEvent("BEG_maxsatsolve", false, true);

        int n = Fsofter.getClauses().size();
        int k = Fsoft.getClauses().size();
        int h = Fhard.getClauses().size();


        String wcnf = toWCNF(Fhard, Fsoft, Fsofter);
        //System.out.println(wcnf.toString());
        SATModel res;
        if (!EXTERNAL) {
            res = solveInProcess(wcnf, k * (n + 1) + n + 1);
        } else {
            String filename = "/tmp/sat4jmaxsat_" + ProcessHandle.current().pid() + ".wcnf";
            try {
                FileWriter myWriter = new FileWriter(filename);
                myWriter.write(wcnf);
                myWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            res = readModel(runSolver(filename), k * (n + 1) + n);
            try {
                Files.deleteIfExists(Paths.get(filename));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        fireSolverEvent("END_maxsatsolve", true, false);
        return res;
    }

    public SATModel maxsat(ACQ_Formula Fhard, ACQ_Formula Fsoft) {
        fireSolverEvent("BEG_maxsatsolve", false, true);

        int n = 0;//Fsofter.getClauses().size();
        int k = Fsoft.getClauses().size();


        String wcnf = toWCNF(Fhard, Fsoft);
        //System.out.println(wcnf.toString());
        SATModel res;
        if (!EXTERNAL) {
            res = solveInProcess(wcnf, k * (n + 1) + n + 1);
        } else {
            String filename = "/tmp/sat4jmaxsat_" + ProcessHandle.current().pid() + ".wcnf";
            try {
                FileWriter myWriter = new FileWriter(filename);
                myWriter.write(wcnf);
                myWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            res = readModel(runSolver(filename), k * (n + 1) + n);
            try {
                Files.deleteIfExists(Paths.get(filename));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        fireSolverEvent("END_maxsatsolve", true, false);
        return res;
    }

    protected ArrayList<Integer> convert(String resline) {
        ArrayList<Integer> res = new ArrayList<>();
        if (resline.equals("v")) return res;
        for (String val : resline.substring(2).split(" ")) {
            if ((!val.startsWith("-")) && (!val.equals("0"))) {
                res.add(Integer.parseInt(val));
            }
        }
        return res;
    }

    public String toCNF(ACQ_Formula F) {
        int n = F.getClauses().size();
        StringBuilder clauses = new StringBuilder("p cnf " + nvars + " " + n + "\n");
        for (ACQ_CNF T : F.getCnfs()) {
            for (ACQ_Clause cl : T) {
                assert cl.getSize() > 0 : "empty clause";
                for (Unit u : cl) {
                    if (u.isNeg()) {
                        Unit tmp = u.clone();
                        tmp.unsetNeg();
                        revmapping.add(tmp.getMiniSatVar(), tmp.clone());
                        clauses.append(-u.getMiniSatVar()).append(" ");
                    } else {
                        revmapping.add(u.getMiniSatVar(), u.clone());
                        clauses.append(u.getMiniSatVar()).append(" ");
                    }
                }
                clauses.append("0\n");
            }
        }
        return clauses.toString();
    }

    @Override
    public SATModel solve(ACQ_Formula F) {
        fireSolverEvent("BEG_satsolve", false, true);

        String cnf = toCNF(F);
        SATModel res;
        if (!EXTERNAL) {
            res = solveInProcess(cnf, Long.MAX_VALUE);
        } else {
            String filename = "/tmp/sat4jsat_" + ProcessHandle.current().pid() + ".cnf";
            try {
                FileWriter myWriter = new FileWriter(filename);
                myWriter.write(cnf);
                myWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            res = readModel(runSolver(filename), 0);
            try {
                Files.deleteIfExists(Paths.get(filename));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        fireSolverEvent("END_satsolve", true, false);
        return res;
    }

    protected VecInt toMiniSatClause(ACQ_Clause cl) {
        int[] minisatcl = new int[cl.getSize()];
        for (int i = 0; i < cl.getSize(); i++) {
            Unit unit = cl.get(i);
            minisatcl[i] = unit.toMiniSat();
            if (!unit.isNeg()) {
                revmapping.add(minisatcl[i], unit.clone());
            }
            else {
                assert minisatcl[i] < 0;
                Unit tmp = unit.clone();
                tmp.unsetNeg();
                revmapping.add(-minisatcl[i], tmp);

            }
        }
        return new VecInt(minisatcl);
    }
    public SATModel solveConacq(ACQ_Formula F) {
        fireSolverEvent("BEG_satsolve", false, true);
        SATModel res = null;

        ISolver solv = SolverFactory.newDefault();
        // applyLimit uses setTimeoutMs.
        applyLimit(solv);


        try {
            for (ACQ_CNF T : F.getCnfs()) {
                for (ACQ_Clause cl : T) {
                    solv.addClause(toMiniSatClause(cl));
                }
            }
            if (F.hasAtLeastAtMost()) {
                VecInt minisatclause = toMiniSatClause(F.getAtLeastAtMost());
                solv.addAtLeast(minisatclause, F.atLeastLower());
                solv.addAtMost(minisatclause, F.atMostUpper());
            }

            IProblem problem = solv;
            fireSolverEvent("BEG_TIMECOUNT", false, true);
            boolean sat = problem.isSatisfiable();
            fireSolverEvent("END_TIMECOUNT", true, false);
            if (sat) {
                ArrayList<Integer> model = convertConacq(problem.model());
                res = new MiniSatModel(model, revmapping);
            }
        }
        catch (ContradictionException e) {/* the constraint network is not satisfiable */}
        catch (TimeoutException e) {
            this.timeoutReached = true;
        }

        fireSolverEvent("END_satsolve", true, false);
        return res;
    }

    protected ArrayList<Integer> convertConacq(int[] l) {
        // returns the list of all positive literal (corresponding to a real constraint)
        ArrayList<Integer> res = new ArrayList<>();
        for (int i : l) {
            if (i > 0)	res.add(i);
        }
        return res;
    }
}