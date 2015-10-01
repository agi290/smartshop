package me.smartshop.view;


public interface Popup {
	/**
	 * @param  isPopupActive
	 * @uml.property  name="active"
	 */
	public void setActive(boolean isPopupActive);
	/**
	 * @return
	 * @uml.property  name="active"
	 */
	public boolean isActive();
}
