package aqua.blatt1.broker;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/*
 * This class is not thread-safe and hence must be used in a thread-safe way, e.g. thread confined or 
 * externally synchronized. 
 */

public class ClientCollection<T> {
	private class Client {
		final String id;
		final T client;
		Date lastSeen;

		Client(String id, T client) {
			this.id = id;
			this.client = client;
			this.lastSeen = new Date();
		}
	}

	private final List<Client> clients;

	public ClientCollection() {
		clients = new ArrayList<Client>();
	}

	public ClientCollection<T> add(String id, T client) {
		clients.add(new Client(id, client));
		return this;
	}

	public ClientCollection<T> remove(int index) {
		clients.remove(index);
		return this;
	}

	public int indexOf(String id) {
		for (int i = 0; i < clients.size(); i++)
			if (clients.get(i).id.equals(id))
				return i;
		return -1;
	}

	public int indexOf(T client) {
		for (int i = 0; i < clients.size(); i++)
			if (clients.get(i).client.equals(client))
				return i;
		return -1;
	}

	public T getClient(int index) {
		return clients.get(index).client;
	}

	public int size() {
		return clients.size();
	}

	public T getLeftNeighorOf(int index) {
		return index == 0 ? clients.get(clients.size() - 1).client : clients.get(index - 1).client;
	}

	public T getRightNeighorOf(int index) {
		return index < clients.size() - 1 ? clients.get(index + 1).client : clients.get(0).client;
	}

	public Date getLastSeen(int index) {
		return clients.get(index).lastSeen;
	}

	public ClientCollection<T> updateLastSeen(int index) {
		clients.get(index).lastSeen = new Date();
		return this;
	}

	public boolean removeInactiveClients(int inactiveSince) {
		Date now = new Date();
		for (int i = clients.size() - 1; i >= 0; i--) {
			if (now.getTime() - clients.get(i).lastSeen.getTime() > inactiveSince) {
				clients.remove(i);
				return true;
			}
		}
		return false;
	}

}
