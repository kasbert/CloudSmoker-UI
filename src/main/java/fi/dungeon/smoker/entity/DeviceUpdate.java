package fi.dungeon.smoker.entity;

import com.fasterxml.jackson.databind.ObjectMapper;

public class DeviceUpdate {
        public Integer mode;
        public int maxMins;
        public Float min;
        public Float max;
        public Integer onMins;
        public Integer offMins;

        public String toString() {
                try {
                        ObjectMapper mapper = new ObjectMapper();
                        return mapper.writeValueAsString(this);
                } catch (Exception e) {
                        return "{mode:-100}";
                }
        }
}
