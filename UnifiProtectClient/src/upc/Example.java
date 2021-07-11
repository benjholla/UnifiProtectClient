package upc;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class Example {

	public static void main(String[] args) throws Exception {
		String server = "PROTECT_IP_ADDRESS";
		String username = "PROTECT_USERNAME";
		String password = "PROTECT_PASSWORD";
		
		UnifiProtectClient client = new UnifiProtectClient(server, username, password, UnifiProtectClient.Verbosity.Verbose);
		
		// this is a unique id, not the plain camera name
		// you can find this by browsing the devices and looking at the url
		// https://<SERVER>/protect/devices/<CAMERA_ID>
		String camera = "PROTECT_CAMERA";
		
		Optional<Long> clockOffset = Optional.of(TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS));
		DateFormat formatter = new SimpleDateFormat("MM/dd/yy HH:mm:ss a");
		Date start = formatter.parse("7/8/21 10:00:00 AM");
		Date end = formatter.parse("7/8/21 10:00:01 AM");
		client.downloadVideo(camera, start, end, clockOffset, new File("sample-video.mp4"));
		
		String ffmpeg = "/usr/local/bin/ffmpeg";
		Date timestamp = start;
		client.downloadSnapshot(ffmpeg, camera, timestamp, clockOffset, new File("sample-snapshot.jpg"));
	}

}
