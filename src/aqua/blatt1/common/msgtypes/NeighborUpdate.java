package aqua.blatt1.common.msgtypes;

import java.io.Serializable;
import java.net.InetSocketAddress;

import aqua.blatt1.common.Direction;

public final class NeighborUpdate implements Serializable {


	private final InetSocketAddress address;
	private final Direction direction;

	public NeighborUpdate(InetSocketAddress address, Direction direction) {
		this.address = address;
		this.direction = direction;
	}

	public InetSocketAddress getAddress() {
		return address;
	}

	public Direction getDirection() {
		return direction;
	}
}
