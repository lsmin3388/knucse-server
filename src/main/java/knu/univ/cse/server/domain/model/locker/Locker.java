package knu.univ.cse.server.domain.model.locker;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Locker {
	@Id
	@Column(name = "name", length = 10, nullable = false)
	private String lockerName;

	@Column(name = "floor", nullable = false)
	private LockerFloor floor;

	@Column(name = "height", nullable = false)
	private Integer height;

	@Column(name = "pw", length = 4, nullable = false)
	private String pw;

	@Column(name = "broken", nullable = false)
	private Boolean broken;

	@Builder
	public Locker(String lockerName, LockerFloor floor, Integer height, String pw, Boolean broken) {
		this.lockerName = lockerName;
		this.floor = floor;
		this.height = height;
		this.pw = pw;
		this.broken = broken;
	}
}
