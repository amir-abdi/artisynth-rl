package artisynth.core.rl;

public class Log {
	public static boolean logging = true;
	
	public static void log(String message) {
		if (logging)
			System.out.println(message);
	}
	
	public static void log(Object obj) {
		if (logging)
			System.out.println(obj);
	}

}
