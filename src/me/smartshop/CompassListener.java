package me.smartshop;

public interface CompassListener {
	/**
	 * This method will be called, if the azimuth of the compass changes. The values are lowpass filtered, to get smother results. 
	 * @param azimuth the current direction of the device towards north in degrees
	 * @param angle the current direction of the device towards north in radiant
	 * @param direction a String describing the the compass direction, i.e. N, SO, NW
	 */
	public void onCompassChanged(float azimuth,float angle,String direction);
}
