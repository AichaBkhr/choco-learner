package fr.lisn.lahdak.acquisition.core.oracle;

public enum Answer {
	YES,
	NO,
	UKN,
    ;

    public Answer getNegation() {
        switch (this){
            case YES: return NO;
            case NO: return YES;
            default: return UKN;
        }
    }
}
