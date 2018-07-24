package ethereum.eventloader;

public interface CoordinatorAdapter {

	int lastProcessedBlock();

	DistributedLock obtainLock();

	void saveState(int endBlock);

	void reconnect();

}
