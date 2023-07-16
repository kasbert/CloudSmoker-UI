
package fi.dungeon.smoker.service;

import java.util.List;

import fi.dungeon.smoker.entity.Device;

public interface IoTService {
	public List<Device> listDevices() throws Exception;
	public Device getDevice(String deviceId) throws Exception;
	public void setDeviceConfiguration(String deviceId, String data, long version) throws Exception;
}
