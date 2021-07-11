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

public class UnifiProtectClient {

	private final String protocol = "https";
	
	private final String server;
	private final String username;
	private final String password;
	private final boolean verbose;
	
	private CloseableHttpClient httpClient;
	private BasicCookieStore cookieStore = new BasicCookieStore();
	
	public UnifiProtectClient(String server, String username, String password, boolean verbose) throws KeyManagementException, ClientProtocolException, NoSuchAlgorithmException, KeyStoreException, IOException {
		this.server = server;
		this.username = username;
		this.password = password;
		this.verbose = verbose;
		login();
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
		
		HttpResponse loginResponse = httpClient.execute(loginRequest);

//		System.out.println(EntityUtils.toString(loginResponse.getEntity()));
		if(verbose) System.out.println(loginResponse.getStatusLine());
		
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
	
	public void downloadVideo(String camera, Date start, Date end, File output) throws ClientProtocolException, IOException {
		downloadVideo(camera, start, end, Optional.empty(), output);
	}
	
	public void downloadVideo(String camera, Date start, Date end, Optional<Long> clockOffset, File output) throws ClientProtocolException, IOException {
		String startTime = clockOffset.isPresent() ? Long.toString(start.getTime() + clockOffset.get()) : Long.toString(start.getTime());
		String endTime = clockOffset.isPresent() ? Long.toString(end.getTime() + clockOffset.get()) : Long.toString(end.getTime());
		
		HttpUriRequest exportRequest = RequestBuilder.get()
				.setUri(String.format("%s://%s/proxy/protect/api/video/export",  protocol, server))
				.addParameter("camera", camera)
				.addParameter("start", startTime)
				.addParameter("end", endTime)
				.build();
		
		if(verbose) System.out.println("Downloading: " + exportRequest);
		
		HttpResponse exportResponse = httpClient.execute(exportRequest);
		
		if(verbose) System.out.println(exportResponse.getStatusLine());
		
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
	
	public void downloadSnapshot(String ffmpeg, String camera, Date timestamp, File output) throws IOException, InterruptedException {
		downloadSnapshot(ffmpeg, camera, timestamp, Optional.empty(), output);
	}
	
	public void downloadSnapshot(String ffmpeg, String camera, Date timestamp, Optional<Long> clockOffset, File output) throws IOException, InterruptedException {
		File tempFile = File.createTempFile(String.format("%s-%s", camera, Long.toString(timestamp.getTime())), "mp4");
		downloadVideo(camera, timestamp, timestamp, clockOffset, tempFile);
		
		String[] command = new String[] {
				ffmpeg,
				"-i",  tempFile.getAbsolutePath(), // specify input file
				"-vf", "select=eq(n\\,0)", // filter to first frame
				"-q:v", "1", // quality level 1 (highest) to 31 (lowest)
				output.getAbsolutePath()
		};
		Process process = Runtime.getRuntime().exec(command);
		if(verbose) {
			process.getInputStream().transferTo(System.out);
			process.getErrorStream().transferTo(System.out);
		}
		process.waitFor();
		
		tempFile.delete();
	}

}
