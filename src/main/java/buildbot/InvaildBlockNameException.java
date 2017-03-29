package main.java.buildbot;

public class InvaildBlockNameException extends RuntimeException {

	public final String invaildName;

	public InvaildBlockNameException(String str) {
		super("Block name \"" + str + "\" is invaild");
		invaildName = str;
	}

}
