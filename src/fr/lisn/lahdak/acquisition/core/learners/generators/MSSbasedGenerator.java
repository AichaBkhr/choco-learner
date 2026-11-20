package fr.lisn.lahdak.acquisition.core.learners.generators;

import java.util.concurrent.TimeoutException;

import fr.lisn.lahdak.acquisition.core.combinatorial.mss.MSSIter;
import fr.lisn.lahdak.acquisition.core.learners.ACQ_Query;

public class MSSbasedGenerator implements IQGenerator {
    MSSIter mssGenerator;

    public MSSbasedGenerator(MSSIter m) {
        this.mssGenerator = m;
    }

    @Override
    public ACQ_Query queryGen() {


        try {
            return mssGenerator.next();
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

}
