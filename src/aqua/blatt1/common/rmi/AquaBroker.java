package aqua.blatt1.common.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

import aqua.blatt1.common.FishModel;

public interface AquaBroker extends Remote {
	String register(AquaClient client) throws RemoteException;

	void deregister(String id) throws RemoteException;

	void handoffFish(String id, FishModel fish) throws RemoteException;
}
