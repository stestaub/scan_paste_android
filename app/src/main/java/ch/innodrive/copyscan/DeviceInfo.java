package ch.innodrive.copyscan;

public class DeviceInfo {
    public String device_id;
    public String device_name;
    public String user_id;

    public DeviceInfo(String deviceId, String deviceName, String user_id) {
        this.device_id = deviceId;
        this.device_name = deviceName;
        this.user_id = user_id;
    }
}
