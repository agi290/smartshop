package me.smartshop.exceptions;

public class WifiException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6706707383357492753L;

	public WifiException() {
	}

	public WifiException(String detailMessage) {
		super(detailMessage);

	}

	public WifiException(Throwable throwable) {
		super(throwable);

	}

	public WifiException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);

	}

}
