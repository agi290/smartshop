package me.smartshop.exceptions;

public class SiteNotFoundException extends WifiCompassException {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2140169366213317953L;

	public SiteNotFoundException() {
	}

	public SiteNotFoundException(String detailMessage) {
		super(detailMessage);

	}

	public SiteNotFoundException(Throwable throwable) {
		super(throwable);

	}

	public SiteNotFoundException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);

	}

}
