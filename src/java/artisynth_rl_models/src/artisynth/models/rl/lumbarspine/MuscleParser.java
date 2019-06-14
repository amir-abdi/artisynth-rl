package artisynth.models.rl.lumbarspine;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import maspack.matrix.Point3d;
import maspack.util.ReaderTokenizer;

public class MuscleParser {

	// holds info for muscle points
	public static class MusclePoint {
		public Point3d pnt;
		public String type;
		public String body;
	}

	public static class WrapObject {
		public String name;
		public String type;
		public int val1;
		public int val2;

	}

	// holds info for muscle
	public static class MuscleInfo {
		public String name;
		public String group;
		public ArrayList<MusclePoint> points = new ArrayList<MusclePoint>();
		public WrapObject wrap;
		public double maxForce;
		public double optFiberLen;
		public double tendonSlackLen;
		public double pennationAngle;
		public double actTimeConst;
		public double deactTimeConst;
		public double vMax;
		public double vMax0;
		public double fMaxTendonStrain;
		public double fmaxMuscleStrain;
		public double kShapeActive;
		public double kShapePassive;
		public double damping;
		public double Af;
		public double fLen;
		public int muscle_model;
		public double fiberRatio;

		public MuscleInfo() {
		}

	}

	public static ArrayList<MuscleInfo> parse(String filename) throws IOException {
		ArrayList<MuscleInfo> info = new ArrayList<MuscleInfo>();

		// start tokenizer
		ReaderTokenizer rtok = null;
		try {
			rtok = new ReaderTokenizer(new FileReader(filename));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		}

		// loop until we read end-of-file
		while (rtok.ttype != ReaderTokenizer.TT_EOF) {

			// read next token and check if string
			if (rtok.nextToken() == ReaderTokenizer.TT_WORD) {
				if ("beginmuscle".equals(rtok.sval)) {
					// we are in a muscle section
					MuscleInfo muscleInfo = parseMuscle(rtok);
					if (muscleInfo != null) {
						info.add(muscleInfo);
					}
				}
			}

		}

		return info;

	}

	private static MuscleInfo parseMuscle(ReaderTokenizer rtok) throws IOException {
		MuscleInfo info = new MuscleInfo();

		// safety check to make sure we are at the beginning of the muscle
		if (!"beginmuscle".equals(rtok.sval)) {
			return null;
		}

		// name seems to be able to contain "-"
		rtok.parseNumbers(false);
		int dashSetting = rtok.getCharSetting('-');
		int periodSetting = rtok.getCharSetting('.');
		rtok.wordChar('-');
		rtok.wordChar('.');

		// Masoud, 19 July 2013, start
		// set name
		rtok.nextToken(); // next token
		info.name = rtok.sval;
		info.name = info.name.replace("-", "_");
		info.name = info.name.replace(".", "_");

		// set group
		String name = info.name;
		String[] chars = name.split("");
		name = chars[0] + chars[1];
		if ("LD".equals(name)) {
			if ("l".equals(chars[chars.length - 1]))
				info.group = "Latissimus_Dorsi_l";
			else
				info.group = "Latissimus_Dorsi_r";
		} else if ("re".equals(name)) {
			if ("l".equals(chars[chars.length - 1]))
				info.group = "Rectus_Abdominis_l";
			else
				info.group = "Rectus_Abdominis_r";

		} else if ("IO".equals(name)) {
			if ("l".equals(chars[chars.length - 1]))
				info.group = "Int_Oblique_l";
			else
				info.group = "Int_Oblique_r";
		} else if ("EO".equals(name)) {
			if ("l".equals(chars[chars.length - 1]))
				info.group = "Ext_Oblique_l";
			else
				info.group = "Ext_Oblique_r";
		} else if ("MF".equals(name)) {
			if ("l".equals(chars[chars.length - 1]))
				info.group = "Multifidus_l";
			else
				info.group = "Multifidus_r";
		} else if ("Ps".equals(name)) {
			if ("l".equals(chars[chars.length - 1]))
				info.group = "Psoas_major_l";
			else
				info.group = "Psoas_major_r";
		} else if ("QL".equals(name)) {
			if ("l".equals(chars[chars.length - 1]))
				info.group = "quadratus_lumborum_l";
			else
				info.group = "quadratus_lumborum_r";
		} else if ("LTpL".equals(name + chars[2] + chars[3])) {
			if ("l".equals(chars[chars.length - 1]))
				info.group = "longissimus_thoracis_pars_lumborum_l";
			else
				info.group = "longissimus_thoracis_pars_lumborum_r";
		} else if ("LTpT".equals(name + chars[2] + chars[3])) {
			if ("l".equals(chars[chars.length - 1]))
				info.group = "longissimus_thoracis_pars_thoracis_l";
			else
				info.group = "longissimus_thoracis_pars_thoracis_r";
		} else if ("IL_L".equals(name + chars[2] + chars[3])) {
			if ("l".equals(chars[chars.length - 1]))
				info.group = "iliocostalis_lumborum_pars_lumborum_l";
			else
				info.group = "iliocostalis_lumborum_pars_lumborum_r";
		} else if ("IL_R".equals(name + chars[2] + chars[3])) {
			if ("l".equals(chars[chars.length - 1]))
				info.group = "iliocostalis_lumborum_pars_thoracis_l";
			else
				info.group = "iliocostalis_lumborum_pars_thoracis_r";
		} else
			System.out.println("Something is wrong");

		// revert character setting
		rtok.setCharSetting('-', dashSetting);
		rtok.setCharSetting('.', periodSetting);
		rtok.parseNumbers(true);

		// loop until end of file (but hopefully encounter "endmuscle" first
		while (rtok.ttype != ReaderTokenizer.TT_EOF) {

			if (rtok.nextToken() == ReaderTokenizer.TT_WORD) {
				if ("beginpoints".equals(rtok.sval)) {
					// parse points
					rtok.nextToken();

					// loop until we hit endpoints
					while (!"endpoints".equals(rtok.sval)) {
						if (rtok.ttype == ReaderTokenizer.TT_NUMBER) {

							// parse line
							MusclePoint mp = new MusclePoint();
							double x = rtok.nval;
							rtok.nextToken();
							double y = rtok.nval;
							rtok.nextToken();
							double z = rtok.nval;
							mp.pnt = new Point3d(x, y, z);

							rtok.nextToken();
							mp.type = rtok.sval;
							rtok.nextToken();
							mp.body = rtok.sval;
							info.points.add(mp);
						}
						rtok.nextToken();
					}

				} else if ("begingroups".equals(rtok.sval)) {
					// parse group
					rtok.nextToken();
					while (!"endgroups".equals(rtok.sval)) {
						// parse groups
						// if it's a string, add it to the list of groups
						if (rtok.ttype == ReaderTokenizer.TT_WORD) {
							// info.group = rtok.sval;
						}
						rtok.nextToken();
					}
				} else if ("wrapobject".equals(rtok.sval)) {
					WrapObject wrap = new WrapObject();
					rtok.nextToken();
					wrap.name = rtok.sval;
					rtok.nextToken();
					wrap.type = rtok.sval;
					rtok.nextToken();
					wrap.val1 = (int) rtok.nval;
					rtok.nextToken();
					wrap.val2 = (int) rtok.nval;
					info.wrap = wrap;

				} else if ("max_force".equals(rtok.sval)) {
					// get next double value
					rtok.nextToken();
					info.maxForce = rtok.nval;
				} else if ("optimal_fiber_length".equals(rtok.sval)) {
					rtok.nextToken();
					info.optFiberLen = rtok.nval;
				} else if ("tendon_slack_length".equals(rtok.sval)) {
					rtok.nextToken();
					info.tendonSlackLen = rtok.nval;
				} else if ("pennation_angle".equals(rtok.sval)) {
					rtok.nextToken();
					info.pennationAngle = rtok.nval;
				} else if ("activation_time_constant".equals(rtok.sval)) {
					rtok.nextToken();
					info.actTimeConst = rtok.nval;
				} else if ("deactivation_time_constant".equals(rtok.sval)) {
					rtok.nextToken();
					info.deactTimeConst = rtok.nval;
				} else if ("Vmax".equals(rtok.sval)) {
					rtok.nextToken();
					info.vMax = rtok.nval;
				} else if ("Vmax0".equals(rtok.sval)) {
					rtok.nextToken();
					info.vMax0 = rtok.nval;
				} else if ("FmaxTendonStrain".equals(rtok.sval)) {
					rtok.nextToken();
					info.fMaxTendonStrain = rtok.nval;
				} else if ("FmaxMuscleStrain".equals(rtok.sval)) {
					rtok.nextToken();
					info.fmaxMuscleStrain = rtok.nval;
				} else if ("KshapeActive".equals(rtok.sval)) {
					rtok.nextToken();
					info.kShapeActive = rtok.nval;
				} else if ("KshapePassive".equals(rtok.sval)) {
					rtok.nextToken();
					info.kShapePassive = rtok.nval;
				} else if ("damping".equals(rtok.sval)) {
					rtok.nextToken();
					info.damping = rtok.nval;
				} else if ("Af".equals(rtok.sval)) {
					rtok.nextToken();
					info.Af = rtok.nval;
				} else if ("Flen".equals(rtok.sval)) {
					rtok.nextToken();
					info.fLen = rtok.nval;
				} else if ("muscle_model".equals(rtok.sval)) {
					rtok.nextToken();
					info.muscle_model = (int) rtok.nval;
				} else if ("fiberRatio".equals(rtok.sval)) {
					rtok.nextToken();
					info.fiberRatio = rtok.nval;
				} else if ("endmuscle".equals(rtok.sval)) {
					// end of muscle definition
					return info;
				} else {
					System.out.println("Unrecognized token: " + rtok.sval);
				}

			}
		}

		return info;
	}

}
