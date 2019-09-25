package artisynth.core.utils;

import java.util.HashMap;
import java.util.Map;

import artisynth.core.rl.Log;

public class Utils {
	public static Map<String, String> parseArgs(String[] args) {
		Map<String, String> dictionary = new HashMap<String, String>();
		try {
			for (int i = 0; i < args.length; i += 2) {
				dictionary.put(args[i], args[i + 1]);
				Log.debug("Arg: " + args[i] + " : " + args[i + 1]);
			}
		} catch (Exception e) {
			Log.debug("Args not properly parsed");
		}
		return dictionary;
	}
}
