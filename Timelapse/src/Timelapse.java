

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import upc.UnifiProtectClient;

public class Timelapse {
	
	private static final String FFMPEG = "/usr/local/bin/ffmpeg";

	public static void main(String[] args) throws Exception {
		String server = "SERVER_IP";
		String username = "PROTECT_USERNAME";
		String password = "PROTECT_PASSWORD";
		File outputDirectory = new File("protect-archive");
		
		Optional<Long> clockOffset = Optional.of(TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS));
		DateFormat formatter = new SimpleDateFormat("MM/dd/yy HH:mm:ss a");
		Date current = new Date();
		
		List<Camera> cameras = List.of(
			new Camera("Camera1", "CAMERA1_ID", formatter.parse("4/25/21 8:00:00 AM"), current),
			new Camera("Camera2", "CAMERA2_ID", formatter.parse("3/16/21 8:00:00 AM"), current)
		);

		cameras.forEach(camera -> {
			camera.getDirectory(outputDirectory).mkdirs();
		});
		
		
		UnifiProtectClient client = null;
		long interval = TimeUnit.MILLISECONDS.convert(1, TimeUnit.MINUTES);
		Date timestamp = cameras.stream().map(camera -> camera.getStart()).sorted().findFirst().get();
		while(timestamp.getTime() < current.getTime()) {
			for(Camera camera : cameras) {
				if(timestamp.getTime() >= camera.getStart().getTime() && timestamp.getTime() <= camera.getEnd().getTime()) {
					File cameraDirectory = camera.getDirectory(outputDirectory);
					
					File snapshot = new File(cameraDirectory.getAbsolutePath() + File.separator + String.format("%s_%s.jpg", camera.getName(), Long.toString(timestamp.getTime())));
					if(snapshot.exists()) {
						System.out.println(String.format("Timestamp %s for camera %s already exists", Long.toString(timestamp.getTime()), camera.getName()));
					} else {
						try {
							if(client == null) {
								client = new UnifiProtectClient(server, username, password, UnifiProtectClient.Verbosity.Verbose);
							}
							client.downloadSnapshot(FFMPEG, camera.getId(), timestamp, clockOffset, snapshot);
						} catch (Exception e) {
							System.out.println(e.getMessage());
							if(client != null) client.close();
							client = null;
						}
					}
					timestamp = new Date(timestamp.getTime() + interval);
				}
			};
		}
		
	}
	
	private static class Camera {
		private final String name;
		private final String id;
		private final Date start;
		private final Date end;
		
		public Camera(String name, String id, Date start, Date end) {
			this.name = name;
			this.id = id;
			this.start = start;
			this.end = end;
		}

		public String getName() {
			return name;
		}

		public String getId() {
			return id;
		}
		
		public Date getStart() {
			return start;
		}
		
		public Date getEnd() {
			return end;
		}

		public File getDirectory(File outputDirectory) {
			return new File(outputDirectory.getAbsolutePath() + File.separator + name);
		}
	}

}
