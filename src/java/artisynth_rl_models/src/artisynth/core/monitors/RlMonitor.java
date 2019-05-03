package artisynth.core.monitors;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

import artisynth.core.modelbase.MonitorBase;

public abstract class RlMonitor extends MonitorBase {
	protected PrintStream out;
	
	// The frequency of printing to file in seconds (e.g. 2 --> write only even seconds of simulation)
	protected int printFrequency;
	
	public RlMonitor() {
		printFrequency = 1;
	}

	public void openFile(File f) throws FileNotFoundException {
		out = new PrintStream(f);
		writeHeader();
	}

	public void closeFile() {
		writeFooter();
		out.close();
	}

	protected void writeHeader() {
	}

	protected void writeFooter() {
	}
	
	protected boolean isWrite(int t0) {
		if (t0 % printFrequency == 0 && out != null)
			return true;
		return false;
	}
}
