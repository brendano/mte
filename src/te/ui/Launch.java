package te.ui;

public class Launch {
	public static void main(String[] args) throws Exception {
		// Enable asserts for everything. Main::main() is too late to set these; it has to be done in a different class before Main is class-loaded.
		ClassLoader.getSystemClassLoader().setPackageAssertionStatus("te", true);
//		// these don't seem to work
//		System.setProperty("apple.laf.useScreenMenuBar", "true"); 
//		System.setProperty("com.apple.mrj.application.apple.menu.about.name", "@AppName");
		Main.myMain(args);
	}
}
