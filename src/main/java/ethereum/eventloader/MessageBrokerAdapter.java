package ethereum.eventloader;

public interface MessageBrokerAdapter {

	void publish(Events events);

	void reconnect();

}
