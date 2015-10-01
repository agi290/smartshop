package me.smartshop.wifi;

import me.smartshop.model.WifiScanResult;

public interface WifiResultCallback {
	public void onScanFinished(WifiScanResult wr);
	public void onScanFailed(Exception ex);
}
