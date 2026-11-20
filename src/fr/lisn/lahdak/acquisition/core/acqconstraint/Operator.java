package fr.lisn.lahdak.acquisition.core.acqconstraint;

import java.util.HashMap;

public enum Operator {

	NONE(), EQ(), LT(), GT(), NEQ(), LE(), GE(), PL(), MN(), Dist();

	private static HashMap<String, Operator> operators = new HashMap<>();

	static {
		operators.put("@", Operator.NONE);
		operators.put("=", Operator.EQ);
		operators.put(">", Operator.GT);
		operators.put(">=", Operator.GE);
		operators.put("<", Operator.LT);
		operators.put("<=", Operator.LE);
		operators.put("!=", Operator.NEQ);
		operators.put("+", Operator.PL);
		operators.put("-", Operator.MN);
		operators.put("abs", Operator.Dist);
	}

	@Override
	public String toString() {
        return switch (this) {
            case LT -> "<";
            case GT -> ">";
            case LE -> "<=";
            case GE -> ">=";
            case NEQ -> "!=";
            case EQ -> "=";
            case PL -> "+";
            case MN -> "-";
            case Dist -> "dist";
            default -> throw new UnsupportedOperationException();
        };
	}

	/**
	 * Flips the direction of an inequality
	 * 
	 * @param operator op to flip
	 */
	public static String getFlip(String operator) {
        return switch (getOperator(operator)) {
            case LT -> ">";
            case GT -> "<";
            case LE -> ">=";
            case GE -> "<=";
            default -> operator;
        };
	}

	public static Operator getOpposite(Operator operator) {
        return switch (operator) {
            case LT -> GE;
            case GT -> LE;
            case LE -> GT;
            case GE -> LT;
            case NEQ -> EQ;
            case EQ -> NEQ;
            case PL -> PL; // NL: neutral negation on PL and MN
            case MN -> MN;
            default -> throw new UnsupportedOperationException();
        };
	}

	/**
	 * Get the enum value corresponding to a given string
	 * 
	 * @param s string describing an operator
	 * @return the corresponding enum value
	 */
	public static Operator getOperator(String s) {
		System.out.println(s);
        return switch (s) {
            case ("EqualXY"), ("EqualX") -> Operator.EQ;
            case ("DiffXY"), ("DiffX") -> Operator.NEQ;
            case ("GreaterOrEqualXY"), ("GreaterOrEqualX") -> Operator.GE;
            case ("GreaterXY"), ("GreaterX") -> Operator.GT;
            case ("LessOrEqualXY"), ("LessOrEqualX") -> Operator.LE;
            case ("LessXY"), ("LessX") -> Operator.LT;
            default -> getOperatorUnary(s);
        };
	}

	/*  TODO review : Discuss the expected values for the s parameter.
	*   Depending on the possible values for the wildcards, regexes may be replaced with a switch expression. */
	public static Operator getOperatorUnary(String s) {
		if (s.matches("^DiffX.*"))
			return Operator.NEQ;
		else if (s.matches("^EqualX.*"))
			return Operator.EQ;
		else if (s.matches("^GreaterOrEqualX.*"))
			return Operator.GE;
		else if (s.matches("^GreaterX.*"))
			return Operator.GT;
		else if (s.matches("^LessOrEqualX.*"))
			return Operator.LE;
		else if (s.matches("^LessX.*"))
			return Operator.LT;
		throw new UnsupportedOperationException();
	}
}
