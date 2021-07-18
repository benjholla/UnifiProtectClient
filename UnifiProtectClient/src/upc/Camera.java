package upc;

import java.util.Optional;

public class Camera {
	
	protected final String name;
	protected final String id;
	protected final Optional<Long> clockOffset;
	
	public Camera(String name, String id) {
		this(name, id, Optional.empty());
	}
	
	public Camera(String name, String id, Optional<Long> clockOffset) {
		this.name = name;
		this.id = id;
		this.clockOffset = clockOffset;
	}

	public String getName() {
		return name;
	}

	public String getId() {
		return id;
	}
	
	public Long getClockOffset() {
		return clockOffset.isPresent() ? clockOffset.get() : 0L;
	}
	
}
