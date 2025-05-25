package aqua.blatt1.common.msgtypes;

import java.io.Serializable;
import java.net.InetSocketAddress;

public final class RegisterResponse implements Serializable {
	private final String id;
	private final InetSocketAddress leftNeighbor;
	private final InetSocketAddress rightNeighbor;
	private final int leaseDuration;

	public RegisterResponse(String id, InetSocketAddress leftNeighbor, InetSocketAddress rightNeighbor, int leaseDuration) {
		this.id = id;
		this.leftNeighbor = leftNeighbor;
		this.rightNeighbor = rightNeighbor;
		this.leaseDuration = leaseDuration;
	}

	public String getId() {
		return id;
	}

	public InetSocketAddress getLeftNeighbor() {
		return leftNeighbor;
	}

	public InetSocketAddress getRightNeighbor() {
		return rightNeighbor;
	}

	public int getLeaseDuration() {
		return leaseDuration;
	}
}
