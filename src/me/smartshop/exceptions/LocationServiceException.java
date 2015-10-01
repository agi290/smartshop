package me.smartshop.exceptions;

public class LocationServiceException extends Exception {

	/**
	 * 
	 */
	public LocationServiceException() {
	}

	/**
	 * @param detailMessage
	 * @param throwable
	 */
	public LocationServiceException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
		
	}

	/**
	 * @param detailMessage
	 */
	public LocationServiceException(String detailMessage) {
		super(detailMessage);
		
	}

	/**
	 * @param throwable
	 */
	public LocationServiceException(Throwable throwable) {
		super(throwable);
		
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = -2922198407322703974L;

}
