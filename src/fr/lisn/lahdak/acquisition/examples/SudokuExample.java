package fr.lisn.lahdak.acquisition.examples;

import java.io.File;
import java.io.IOException;

import fr.lisn.lahdak.acquisition.core.acqsolver.ACQ_Heuristic;
import fr.lisn.lahdak.acquisition.core.acqsolver.ValSelector;
import fr.lisn.lahdak.acquisition.core.acqsolver.VarSelector;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Algorithm;
import fr.lisn.lahdak.acquisition.core.parallel.ACQ_Partition;
import fr.lisn.lahdak.acquisition.core.workspace.IExperience;
import fr.lisn.lahdak.acquisition.expe.ExpeBuilder;

public class SudokuExample {
    public static void main(String[] args) throws IOException {
        String exp = "sudoku";
        boolean normalizedCSP = true;
        boolean shuffle = true;
        ACQ_Algorithm mode = ACQ_Algorithm.QUACQ;
        int timeout = 50000; // number of milliseconds
        ACQ_Heuristic heuristic = ACQ_Heuristic.SOL;
        String vls = ValSelector.IntDomainRandom.toString();
        String vrs = VarSelector.Random.toString();
        ACQ_Partition partition = ACQ_Partition.RANDOM;
        boolean verbose = true;
        boolean log_queries = false;
        int nb_threads = 10;
        String instance = "10";
        String examplesfile="";
        String file="";
        int maxqueries=100;

        IExperience expe = new ExpeBuilder()
                .setExpe(exp)
                .setFile(file)
                .setAlgo(mode)
                .setMaxqueries(maxqueries)
                .setExamplesFile(examplesfile)
                .setPartition(partition)
                .setNbThreads(nb_threads)
                .setInstance(instance)
                .setNormalizedCSP(normalizedCSP)
                .setShuffle(shuffle)
                .setTimeout(timeout)
                .setHeuristic(heuristic)
                .setVarSelector(vrs)
                .setValSelector(vls)
                .setVerbose(verbose)
                .setPartition(partition)
                .setDirectory(new File("src/fr/lisn/lahdak/acquisition/examples/bench/"))
                .setQueries(log_queries)
                .build();

        expe.process();
    }
}
