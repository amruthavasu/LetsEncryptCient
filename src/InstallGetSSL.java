import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

public class InstallGetSSL {

	private static final String /* STAGING_URL */ ACTUAL_URL = "CA=\"https://acme-staging.api.letsencrypt.org\"";
	private static final String /* ACTUAL_URL */ STAGING_URL = "CA=\"https://acme-v01.api.letsencrypt.org\"";
	public static String domain;
	public static String certDirectory;
	public static String certPath;
	public static String icaPath;
	public static String keyPath;
	public String domainConfig;

	public void installClient() {

		ClassLoader classLoader = getClass().getClassLoader();
		File script = new File(classLoader.getResource("getssl_install.sh").getFile());
		String pathToFile = script.getAbsolutePath();

		try {
			File file = new File("/opt/getssl");
			if (!file.exists()) {
				Process p = Runtime.getRuntime().exec("sh " + pathToFile);
				p.waitFor();
			}
			file.setExecutable(true);

			Process p = Runtime.getRuntime().exec("/bin/bash /opt/getssl -c " + domain);
			p.waitFor();

			BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String line = "";
			while ((line = br.readLine()) != null) {
				if (line.contains("creating domain config file")) {
					String[] buf = line.split(" in ");
					domainConfig = buf[1];
					file = new File(domainConfig);
					certDirectory = file.getParent();
				} else if (line.contains("domain config already exists")) {
					String[] buf = line.split(" exists ");
					domainConfig = buf[1];
					file = new File(domainConfig);
					certDirectory = file.getParent();
				}
				System.out.println(line);
			}

			// Uncomment ACL in getssl.cfg and set the server
			File configFile = new File(domainConfig);
			File tmpConfig = new File(certDirectory + File.separator + "temp");
			br = new BufferedReader(new FileReader(configFile));
			BufferedWriter bw = new BufferedWriter(new FileWriter(tmpConfig));
			while ((line = br.readLine()) != null) {
				if (line.contains(STAGING_URL)) {
					if (line.startsWith("#")) {
						// do nothing
					} else {
						line = "#" + line;
					}
				} else if (line.contains(ACTUAL_URL)) {
					if (line.startsWith("#")) {
						line = line.substring(1, line.length());
					}
				} else if (line.contains("ACL=(")) {
					while (!(line.endsWith(")"))) {
						if (line.startsWith("#")) {
							line = line.substring(1, line.length());
						}
						bw.write(line + "\n");
						line = br.readLine();
					}
					if (line.startsWith("#")) {
						line = line.substring(1, line.length());
					}
				}
				bw.write(line + "\n");
			}
			br.close();
			bw.close();

			// Copy temp file to domain config
			Utils.copyFiles(tmpConfig.getAbsolutePath(), configFile.getAbsolutePath());
			try {
				tmpConfig.delete();
			} catch (Exception e) {
				e.printStackTrace();
			}

			// set the server in the main config file.
			File mainConfigDir = new File(new File(certDirectory).getParent());
			File mainConfigFile = new File(mainConfigDir.getAbsolutePath() + File.separator + "getssl.cfg");
			File tempConfigFile = new File(mainConfigDir.getAbsolutePath() + File.separator + "temp");
			br = new BufferedReader(new FileReader(mainConfigFile));
			bw = new BufferedWriter(new FileWriter(tempConfigFile));
			line = null;
			while ((line = br.readLine()) != null) {
				if (line.contains(STAGING_URL)) {
					if (line.startsWith("#")) {
						// do nothing
					} else {
						line = "#" + line;
					}
				} else if (line.contains(ACTUAL_URL)) {
					if (line.startsWith("#")) {
						line = line.substring(1, line.length());
					}
				}
				bw.write(line + "\n");
			}
			br.close();
			bw.close();
			Utils.copyFiles(tempConfigFile.getAbsolutePath(), mainConfigFile.getAbsolutePath());
			try {
				tempConfigFile.delete();
			} catch (Exception e) {
				e.printStackTrace();
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void getCert(String passwordFile) {
		installClient();
		setSubjectDetails();
		setAccountPassphrase(passwordFile);
		try {
			Process p = Runtime.getRuntime().exec("/bin/bash /opt/getssl -f " + domain);
			p.waitFor();
			BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String line = null;
			while ((line = br.readLine()) != null) {
				System.out.println(line);
				if (line.contains("no certificate obtained from host")) {
					System.out.println("ERROR: Could not obtain certificate");
					System.exit(0);
				} else if (line.contains("certificate for " + domain + " is still valid for more than 30 days")) {
					System.out.println("ERROR: Existing cert is still valid for more than 30 days");
					System.exit(0);
				} else if (line.contains("Verification completed, obtaining certificate")) {
					line = br.readLine();
					if (line.contains("Certificate saved")) {
						String[] buf = line.split(" in ");
						certPath = buf[1];
						// System.out.println("SUCCESS:Certificate saved in " +
						// certPath);
					}
					line = br.readLine();
					if (line.contains("The intermediate CA cert is")) {
						String[] buf = line.split(" in ");
						icaPath = buf[1];
					}
				} else if (line.contains("creating domain key")) {
					String buf[] = line.split(" - ");
					keyPath = buf[1];
				}
			}

			if (certPath == null || keyPath == null) {
				System.out.println("ERROR: Could not obtain certificate");
				System.exit(0);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void setSubjectDetails() {
		ClassLoader classLoader = getClass().getClassLoader();
		File script = new File(classLoader.getResource("cert.properties").getFile());
		String pathToFile = script.getAbsolutePath();
		try {
			Process p = Runtime.getRuntime().exec("openssl version -d");
			p.waitFor();
			BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String line = br.readLine();
			if (line != null) {
				String[] buf = line.split(" ");
				line = buf[1].replace("\"", "");
			}
			br.close();

			Properties prop = new Properties();
			InputStream input = null;
			input = new FileInputStream(pathToFile);
			prop.load(input);

			File opensslConfig = new File(line + File.separator + "openssl.cnf");
			File tmpConf = new File(line + File.separator + "temp");
			br = new BufferedReader(new FileReader(opensslConfig));
			BufferedWriter bw = new BufferedWriter(new FileWriter(tmpConf));
			line = null;
			while ((line = br.readLine()) != null) {
				if (line.contains("countryName_default")) {
					line = "countryName_default = " + prop.getProperty("countryName");
				} else if (line.contains("stateOrProvinceName_default")) {
					line = "stateOrProvinceName_default = " + prop.getProperty("stateOrProvinceName");
				} else if (line.contains("localityName_default")) {
					line = "localityName_default = " + prop.getProperty("localityName");
				} else if (line.contains("0.organizationName_default")) {
					line = "0.organizationName_default = " + prop.getProperty("organizationName");
				} else if (line.contains("organizationalUnitName_default")) {
					line = "organizationalUnitName_default = " + prop.getProperty("organizationalUnitName");
				}
				bw.write(line + "\n");
			}
			br.close();
			bw.close();

			Utils.copyFiles(tmpConf.getAbsolutePath(), opensslConfig.getAbsolutePath());
			try {
				tmpConf.delete();
			} catch (Exception e) {
				e.printStackTrace();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private void setAccountPassphrase(String passwordFile) {
		File file = new File(passwordFile);
		if (file.exists()) {
			File getssl = new File("/opt/getssl");
			File tmpFile = new File("/opt/tmp");
			try {
				BufferedReader br = new BufferedReader(new FileReader(getssl));
				BufferedWriter bw = new BufferedWriter(new FileWriter(tmpFile));
				String line = null;
				while ((line = br.readLine()) != null) {
					if (line.contains("openssl rsa -in ") && line.contains("ACCOUNT_KEY")) {
						if (!line.contains("-passin")) {
							String[] buf = line.split("ACCOUNT_KEY");
							line = buf[0] + "ACCOUNT_KEY -passin file:\"" + passwordFile + "\" " + buf[1];
						}
					} else if ((line.contains("openssl genrsa") && line.contains("$ACCOUNT_KEY_LENGTH"))) {
						if (!line.contains("-passout")) {
							String[] buf = line.split("ACCOUNT_KEY_LENGTH");
							line = buf[0] + "ACCOUNT_KEY_LENGTH -passout file:\"" + passwordFile + "\" " + buf[1];
						}
					}
					bw.write(line + "\n");
				}
				br.close();
				bw.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			Utils.copyFiles(tmpFile.getAbsolutePath(), getssl.getAbsolutePath());
			try {
				tmpFile.delete();
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			System.out.println("ERROR: The passphrase file could not be found");
		}
	}

	public static void main(String args[]) {
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		try {
			System.out.println("Enter domain name: ");
			domain = br.readLine();
			System.out.println("Enter path to password file: ");
			String passPath = br.readLine();
			InstallGetSSL getssl = new InstallGetSSL();
			getssl.getCert(passPath);
			new InstallCertificate(domain, certPath, keyPath, icaPath, certDirectory);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
