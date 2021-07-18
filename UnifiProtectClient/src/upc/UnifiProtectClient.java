package upc;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Optional;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;

public class UnifiProtectClient {

	private final String protocol = "https";
	
	private final String server;
	private final String username;
	private final String password;
	
	public static enum Verbosity {
		None, Verbose, VeryVerbose
	}
	
	private final Verbosity verbosity;
	
	private CloseableHttpClient httpClient;
	private BasicCookieStore cookieStore = new BasicCookieStore();
	
	public UnifiProtectClient(String server, String username, String password, Verbosity verbosity) throws KeyManagementException, ClientProtocolException, NoSuchAlgorithmException, KeyStoreException, IOException {
		this.server = server;
		this.username = username;
		this.password = password;
		this.verbosity = verbosity;
		login();
	}
	
	public void close() throws IOException {
		httpClient.close();
	}
	
	private void login() throws ClientProtocolException, IOException, KeyManagementException, NoSuchAlgorithmException, KeyStoreException {
		SSLConnectionSocketFactory selfSignedCertTrust = new SSLConnectionSocketFactory(
				new SSLContextBuilder().loadTrustMaterial(null, new TrustSelfSignedStrategy()).build(),
				NoopHostnameVerifier.INSTANCE);
		
		httpClient = HttpClients.custom()
				.setDefaultCookieStore(cookieStore)
				.setSSLSocketFactory(selfSignedCertTrust)
				.build();
		
		HttpUriRequest loginRequest = RequestBuilder.post()
				.setUri(String.format("%s://%s/api/auth/login", protocol, server))
				.addParameter("username", username)
				.addParameter("password", password)
				.build();
		
		if(verbosity.ordinal() > 2) System.out.println("Authenticating with user: " + username);
		
		HttpResponse loginResponse = httpClient.execute(loginRequest);

//		if(verbosity.ordinal() >= 1) System.out.println("Login: " + loginResponse.getStatusLine());
		if(verbosity.ordinal() >= 2) System.out.println(EntityUtils.toString(loginResponse.getEntity()));
		
		if(loginResponse.getStatusLine().getStatusCode() == 200) {
			final String TOKEN = "TOKEN";
			Optional<Cookie> tokenCookie = cookieStore.getCookies().stream()
				.filter(cookie -> cookie.getName().equals(TOKEN))
				.findFirst();
			if(!tokenCookie.isPresent()) {
				throw new RuntimeException("Could not find session token");
			}
		} else {
			throw new RuntimeException("Authentication failure");
		}
	}
	
	public void downloadVideo(Camera camera, Date start, Date end, File output) throws ClientProtocolException, IOException {
		String startTime = Long.toString(start.getTime() + camera.getClockOffset());
		String endTime = Long.toString(end.getTime() + camera.getClockOffset());
		
		HttpUriRequest exportRequest = RequestBuilder.get()
				.setUri(String.format("%s://%s/proxy/protect/api/video/export",  protocol, server))
				.addParameter("camera", camera.getId())
				.addParameter("start", startTime)
				.addParameter("end", endTime)
				.build();
		
		if(verbosity.ordinal() >= 1) System.out.println("Export Video: " + String.format("%s to %s for camera %s", start, end, camera.getName()));
		if(verbosity.ordinal() >= 2) System.out.println("Downloading: " + exportRequest);
		
		HttpResponse exportResponse = httpClient.execute(exportRequest);
		
		if(verbosity.ordinal() >= 1) System.out.println("Download: " + exportResponse.getStatusLine());
		
		if(exportResponse.getStatusLine().getStatusCode() != 200) {
			throw new RuntimeException(String.format("Video segment not available for start %s to end %s on camera %s", start, end, camera.getName()));
		}
		
		if(output.getParentFile() != null && !output.getParentFile().exists()) {
			output.getParentFile().mkdirs();
		}
		
		BufferedInputStream bis = new BufferedInputStream(exportResponse.getEntity().getContent());
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(output));
		int inByte;
		while((inByte = bis.read()) != -1) {
			bos.write(inByte);
		}
		bis.close();
		bos.close();
	}
	
	public void downloadSnapshot(String ffmpeg, Camera camera, Date timestamp, File output) throws IOException, InterruptedException {
		File tempFile = File.createTempFile(String.format("%s-%s", camera, Long.toString(timestamp.getTime())), ".mp4");
		downloadVideo(camera, timestamp, timestamp, tempFile);
		
		String[] command = new String[] {
				ffmpeg,
				"-i",  tempFile.getAbsolutePath(), // specify input file
				"-vf", "select=eq(n\\,0)", // filter to first frame
				"-q:v", "1", // quality level 1 (highest) to 31 (lowest)
				output.getAbsolutePath()
		};
		Process process = Runtime.getRuntime().exec(command);
		if(verbosity.ordinal() >= 2) {
			process.getInputStream().transferTo(System.out);
			process.getErrorStream().transferTo(System.out);
		}
		process.waitFor();
		tempFile.delete();
		
		if(!output.exists()) {
			throw new RuntimeException(String.format("Snapshot extraction failed for timestamp %s on camera %s", timestamp, camera));
		}
	}

}
