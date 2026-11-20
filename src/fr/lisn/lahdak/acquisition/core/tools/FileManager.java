package fr.lisn.lahdak.acquisition.core.tools;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;
import fr.lisn.lahdak.acquisition.core.oracle.ACQ_Oracle;
import fr.lisn.lahdak.acquisition.core.oracle.Answer;

public class FileManager {

	/**
	 * Regex pattern for variables
	 */
	public static final Pattern VALID_VAR_REGEX = Pattern.compile("^[1-9][0-9]*, *[0-9]+, *[0-9]+;$",
			Pattern.CASE_INSENSITIVE);
	/**
	 * Regex pattern for binary constraints
	 */
	public static final Pattern VALID_BINARY_REGEX = Pattern
			.compile("(Diff||Equal||Greater||Less||GreaterOrEqual||LessOrEqual)XY;$", Pattern.CASE_INSENSITIVE);
	/**
	 * Regex pattern for unary constraints
	 */
	public static final Pattern VALID_UNARY_REGEX = Pattern.compile(
			"(Diff||Equal||Greater||Less||GreaterOrEqual||LessOrEqual)X - [-0-9]+;$", Pattern.CASE_INSENSITIVE);
	/**
	 * Stream factory
	 */
	private Supplier<Stream<String>> supplier;

	@SuppressWarnings("unused")
	private String filePath;

	/**
	 * Directory that will contain the log files
	 */
	private static final String logDirectoryPath = getExpDir() + "/logs/";

	/**
	 * Constructor
	 * 
	 * @param filePath path of the .acq file
	 * @throws IOException
	 */
	public FileManager(String filePath) throws IOException {
		this.filePath = filePath;
		List<String> allLines = Files.readAllLines(Paths.get(filePath));
		supplier = () -> allLines.stream();
	}

	/**
	 * Empty constructor
	 */
	public FileManager() {}

	public void display() {
		supplier.get().forEach(e -> System.out.println(e.toString()));
	}

	public List<String> getVariables() {
		List<String> vars = new ArrayList<String>();
		for (String s : toLineList()) {
			Matcher matcher = VALID_VAR_REGEX.matcher(s);
			if (matcher.find()) {
				vars.add(s);
			}
		}
		return vars;
	}

	public int getNbVar() {
		return getVariables().size();
	}

	public int[] getDomain() throws IOException {
		List<String> varList = getVariables();
		List<Integer> mins = new ArrayList<Integer>();
		List<Integer> maxs = new ArrayList<Integer>();
		for (String s : varList) {
			String[] split = s.substring(0, s.length() - 1).replaceAll("\\s+", "").split(",");
			mins.add(Integer.parseInt(split[1]));
			maxs.add(Integer.parseInt(split[2]));
		}
		int[] domain = new int[2];
		domain[0] = Collections.min(mins);
		domain[1] = Collections.min(maxs);
		return domain;
	}

	public List<String> getBinaryConstraints() {
		List<String> csts = new ArrayList<String>();
		for (String s : toLineList()) {
			Matcher matcher = VALID_BINARY_REGEX.matcher(s);
			if (matcher.find())
				csts.add(s.substring(0, s.length() - 1));
		}
		return csts;
	}

	public List<String> getUnaryConstraints() {
		List<String> csts = new ArrayList<String>();
		for (String s : toLineList()) {
			Matcher matcher = VALID_UNARY_REGEX.matcher(s);
			if (matcher.find())
				csts.add(s.substring(0, s.length() - 1));
		}
		return csts;
	}

	public List<String> getConstraints() {
		List<String> constraints = new ArrayList<String>();
		constraints.addAll(getBinaryConstraints());
		constraints.addAll(getUnaryConstraints());
		return constraints;
	}

	public List<String> toLineList() {
		return supplier.get().collect(Collectors.toList());
	}

	public static void saveTextToFile(String content, File file) {
		try {
			PrintWriter writer;
			writer = new PrintWriter(file);
			writer.println(content);
			writer.close();
		} catch (IOException ex) {
//			Logger.getLogger(class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	public static void writeTest(Object something, String file_name) {
		String directoryName = getExpDir() + "/benchmarks/PTHG/";
	    File directory = new File(directoryName);
		File file = new File(directoryName + file_name + ".classified");

		writeObjToFileInDir(something, directory, file);
	}

	/**
	 * Write the content of the given object to a file in the log directory.
	 * If the file already exists, append the new content at the end.
	 * @param obj The object to log
	 * @param file_name The name of the log file to write into
	 */
	public static void writeLog(Object obj, String file_name) {
	    File directory = new File(logDirectoryPath);
		File file = new File(logDirectoryPath + file_name + ".log");

		writeObjToFileInDir(obj, directory, file);
	}

	/**
	 * Write the content of the given object to a file in the results directory.
	 * If the file already exists, append the new content at the end.
	 * If the file does not exist and its name includes ".results", a special header will be included.
	 * @param obj The object to log
	 * @param file_name The name of the log file to write into
	 */
	public static void writeResults(Object obj, String file_name) {
		String directoryName = getExpDir() + "/results/";
		
	    File directory = new File(directoryName);
		File file = new File(directoryName + file_name);

		writeObjToFileInDir(obj, directory, file, true);
	}

	/**
	 * Write the content of a given object to a given file in a given directory.
	 * If the file already exists, append the new content at the end.
	 * @param obj The object
	 * @param directory The directory
	 * @param file The file
	 */
	private static void writeObjToFileInDir(Object obj, File directory, File file) {
		writeObjToFileInDir(obj, directory, file, false);
	}

	/**
	 * Write the content of a given object to a given file in a given directory.
	 * If the file already exists, append the new content at the end.
	 * @param obj The object
	 * @param directory The directory
	 * @param file The file
	 * @param addResultsHeader Whether a special content should be added to the file (if its name contains ".results") before the object's data
	 */
	private static void writeObjToFileInDir(Object obj, File directory, File file, boolean addResultsHeader) {
		if (! directory.exists()){
			directory.mkdirs();
		}

		try {
			BufferedWriter writer = null;

			if (!file.exists()) {
				file.createNewFile();

				if (addResultsHeader && file.getName().contains(".results")) {
					writer = new BufferedWriter(new FileWriter(file));
					writer.append("Date \t - \t CL size \t RelativeAcquisitionRate \t AbsoluteAcquisitionRate  \t ConvergenceRate \t #Queries \t (#Queries/CLsize) \t #MembershipQueries \t Query size \t Acquisition time \t Running Time \t "
							+ "Max Waiting Time \t - \t #NonAskedQueries \t BiasInit Size \t Bias Final Size \t VRS Heuristic \t QueryGeneration Heuristic" + "\n");
				}
			}

			if (writer == null) {
				writer = new BufferedWriter(new FileWriter(file, true));
			}

			writer.append(obj.toString()).append("\n");
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static String getExpDir() {
		return System.getProperty("user.dir");
	}

	/**
	 * Remove all files from the log directory.
	 */
	public static void deleteLogFiles() {
		File[] files = new File(logDirectoryPath).listFiles();
		if (files != null) { 
			for (File f : files) {
				f.delete();
			}
		}
	}

	public static ACQ_Oracle oracleFromJar(String jarPath, StatManager stats) {
		return new ACQ_Oracle() {
			@Override
			public Answer ask(ACQ_Query e) {
				try {
					final Process p = Runtime.getRuntime().exec("java -jar " + jarPath + " " + e.oracleAskingFormat());
					int res = p.waitFor();
					if (res == 2) {
						System.out.println("true");
						e.classify(Answer.YES);
						stats.update(e);
						return Answer.YES;
					} else if (res == 3) {
						System.out.println("false");
						e.classify(Answer.NO);
						stats.update(e);
						return Answer.NO;
					} else {
						System.err.println("Incorrect exit value from jar oracle");
					}
				} catch (IOException | InterruptedException excep) {
					System.err.println("Incorrect exit value from jar oracle");
				}
				return (Answer) null;
			}
		};
	}
}