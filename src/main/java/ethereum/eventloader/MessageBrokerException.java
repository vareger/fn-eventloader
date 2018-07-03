package ethereum.eventloader;

public class MessageBrokerException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public MessageBrokerException(String message, Throwable cause) {
		super(message, cause);
	}

	public MessageBrokerException(Throwable cause) {
		super(cause);
	}
}
