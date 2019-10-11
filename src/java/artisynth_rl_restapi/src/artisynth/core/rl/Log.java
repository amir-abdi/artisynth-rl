package artisynth.core.rl;

public class Log {
	public static boolean DEBUG = false;

	public static void debug(String message) {
		if (DEBUG)
			System.out.println(message);
	}

	public static void debug(Object obj) {
		if (DEBUG)
			System.out.println(obj);
	}

	public static void info(String message) {

		System.out.println(message);
	}

	public static void info(Object obj) {

		System.out.println(obj);
	}

}
