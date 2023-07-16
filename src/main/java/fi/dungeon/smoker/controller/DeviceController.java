package fi.dungeon.smoker.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;

import fi.dungeon.smoker.service.InfluxService;
import fi.dungeon.smoker.service.IoTService;
import fi.dungeon.smoker.entity.Device;
import fi.dungeon.smoker.entity.DeviceUpdate;

@RestController
public class DeviceController {
	Logger logger = LoggerFactory.getLogger(DeviceController.class);

	@Autowired
	@Qualifier("iotService")
	private IoTService iot;

	@Autowired
	private InfluxService influx;

	@RequestMapping("/device")
	public List<Device> index() throws Exception {
		List<Device> devices = iot.listDevices();
		return devices;
	}

	@RequestMapping(value = "/device/{id}", method = RequestMethod.GET)
	public Device getDevice(@PathVariable("id") String id) throws Exception {
		Device device = iot.getDevice(id);
		device.put("series", influx.getDeviceSeries(id));
		return device;
	}

	// ET Config orig: {mode=-2, min=70, offMins=10, cloudUpdateTime=2023-05-01T12:47:21.105654Z, max=90, maxMins=0, onMins=3, deviceAckTime=null}

	@RequestMapping(value = "/device/{id}", method = RequestMethod.PUT)
	//public void modifyDeviceConfig(@PathVariable("id") String id, @RequestBody Map<String, String> config)
	public void modifyDeviceConfig(@PathVariable("id") String id, @RequestBody DeviceUpdate config)
			throws Exception {
		Device device = iot.getDevice(id);
		logger.info("SET Config orig: " + device.get("config"));
		validateDeviceConfig(config);
		String json = config.toString();
		logger.info("SET Config new: " + json);
		iot.setDeviceConfiguration(id, json, 0);
	}

	private void validateDeviceConfig(DeviceUpdate config) throws ResponseStatusException {
		// FIXME validate input properly
		if (config.mode == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing mode parameter");
		}
		if (-2 == config.mode) {
			if (config.min == null || config.max == null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing parameters");
			}
		} else if (-1 == config.mode) {
			if (config.onMins == null || config.offMins == null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing parameters");
			}
		} else if (0 == config.mode) {
		} else if (100 == config.mode) {
		} else {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Illegal parameters");
		}
	}

}
