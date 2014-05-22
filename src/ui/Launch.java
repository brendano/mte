package ui;

public class Launch {
	public static void main(String[] args) throws Exception {
		ClassLoader.getSystemClassLoader().setPackageAssertionStatus("ui", true);
		ClassLoader.getSystemClassLoader().setPackageAssertionStatus("d", true);
		Main.myMain(args);
	}
}
