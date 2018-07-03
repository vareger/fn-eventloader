package ethereum.eventloader;

public class BlockchainException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public BlockchainException(String message, Throwable cause) {
		super(message, cause);
	}

	public BlockchainException(Throwable cause) {
		super(cause);
	}
}
