package aqua.blatt1.common.msgtypes;

import java.io.Serializable;
import java.net.InetSocketAddress;

public final class RegisterResponse implements Serializable {
	private final String id;
	private final InetSocketAddress leftNeighbor;
	private final InetSocketAddress rightNeighbor;

	public RegisterResponse(String id, InetSocketAddress leftNeighbor, InetSocketAddress rightNeighbor) {
		this.id = id;
		this.leftNeighbor = leftNeighbor;
		this.rightNeighbor = rightNeighbor;
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

}
