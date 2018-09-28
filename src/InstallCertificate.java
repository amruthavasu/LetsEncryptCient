import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class InstallCertificate {
	private String apacheConfigFilePath;
	private String apacheConfigDirectory;
	private String domain;
	private String newCertFile;
	private String newKeyFile;
	private String newIcaFile;
	public InstallCertificate(String domain, String certPath, String keyPath, String icaPath, String certDir) {
		this.domain = domain;
		this.newCertFile = certPath;
		this.newKeyFile = keyPath;
		this.newIcaFile = icaPath;
		install();
		//validateCert();
	}

	public void install() {
		getConfigFilePath();
		getExistingCertPath();

		// Restart Apache Httpd
		System.out.println("Config file updated.");
		System.out.println("Restart your apache httpd server to use the new certificate");
	}

	public void getExistingCertPath() {
		try {
			BufferedReader br = new BufferedReader(new FileReader(apacheConfigFilePath));
			String tempFilePath = apacheConfigDirectory + File.separator + "temp";
			BufferedWriter bw = new BufferedWriter(new FileWriter(tempFilePath));
			String line = null;
			while ((line = br.readLine()) != null) {
				if (line.contains("<VirtualHost") && line.contains(domain)) {
					bw.write(line + "\n"); 
					while ((line = br.readLine()) != null && !line.contains("</VirtualHost")) {
						if (line.contains("SSLCertificateFile") && !line.startsWith("#")) {
							if(newCertFile != null) {
								line = "SSLCertificateFile " + newCertFile;
							}
						} else if (line.contains("SSLCertificateKeyFile") && !line.startsWith("#")) {
							if (newKeyFile != null) {
								line = "SSLCertificateKeyFile " + newKeyFile;
							}
						} else if (line.contains("SSLCertificateChainFile") && !line.startsWith("#")) {
							if (newIcaFile != null) {
								line = "SSLCertificateChainFile " + newIcaFile;
							}
						}
						bw.write(line + "\n");
					}
				}
				bw.write(line + "\n");
			}
			br.close();
			bw.close();
			
			Utils.copyFiles(tempFilePath, apacheConfigFilePath);
			try {
				new File(tempFilePath).delete();
			} catch (Exception e) {
				e.printStackTrace();
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void getConfigFilePath() {
		ClassLoader classLoader = getClass().getClassLoader();
		File script = new File(classLoader.getResource("getConfigFile.sh").getFile());
		String pathToFile = script.getAbsolutePath();

		try {
			ProcessBuilder pb = new ProcessBuilder("/bin/bash", pathToFile, domain);
			final Process process = pb.start();
			InputStream is = process.getInputStream();
			InputStreamReader isr = new InputStreamReader(is);
			BufferedReader br = new BufferedReader(isr);
			String line = br.readLine();
			if (line != null) {
				String buf[] = line.split("\\(");
				buf = buf[1].split(":");
				apacheConfigFilePath = buf[0];
				File file = new File(apacheConfigFilePath);
				apacheConfigDirectory = file.getParent();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void validateCert() {
		String certFP = null;
		String nmapFP = null;
		
		// get installed cert fingerprint using openssl
		try {
			Process p = Runtime.getRuntime().exec("openssl x509 -fingerprint -noout -in " + newCertFile);
			p.waitFor();

			InputStream is = p.getInputStream();
			InputStreamReader isr = new InputStreamReader(is);
			BufferedReader br = new BufferedReader(isr);
			String line = br.readLine();
			if (line != null) {
				String buf[] = line.split("=");
				certFP = buf[1].replace(":", "");
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// get fingerprint using nmap
		try {
			Process p = Runtime.getRuntime().exec("nmap --script ssl-cert -p 443 " + domain);
			p.waitFor();

			InputStream is = p.getInputStream();
			InputStreamReader isr = new InputStreamReader(is);
			BufferedReader br = new BufferedReader(isr);
			String line = null;
			
			while ((line = br.readLine()) != null) {
				if (line.contains("SHA-1:")) {
				String buf[] = line.split(": ");
				nmapFP = buf[1].replace(" ", "");
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if (certFP.equalsIgnoreCase(nmapFP)) {
			System.out.println("SUCCESS: Certificate validation successful");
		} else {
			System.out.println("ERROR: Certificate validation failed");
		}
	}
	
	/*public static void main(String args[]) {
		String domain = "testcert.serc.iisc.ernet.in";
		String certPath = "/root/.getssl/testcert.serc.iisc.ernet.in/testcert.serc.iisc.ernet.in.crt";
		String keyPath = "/root/.getssl/testcert.serc.iisc.ernet.in/testcert.serc.iisc.ernet.in.key";
		String icaPath = "/root/.getssl/testcert.serc.iisc.ernet.in/chain.crt";
		String certDir = "testcert.serc.iisc.ernet.in";
		new InstallCertificate(domain, certPath, keyPath, icaPath, certDir);
		
	}*/
}
