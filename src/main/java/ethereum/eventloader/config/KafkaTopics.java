package ethereum.eventloader.config;

import ethereum.eventloader.messages.EventMessage;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;
import java.util.Set;

@Configuration
@ConfigurationProperties("event-loader.topics")
public class KafkaTopics {

    private String all;
    private String blocks;
    private Set<EventTopicMap> events;

    public String getAll() {
        return all;
    }

    public void setAll(String all) {
        this.all = all;
    }

    public Set<EventTopicMap> getEvents() {
        return events;
    }

    public void setEvents(Set<EventTopicMap> events) {
        this.events = events;
    }

    public String getBlocks() {
        return blocks;
    }

    public void setBlocks(String blocks) {
        this.blocks = blocks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KafkaTopics)) return false;
        KafkaTopics that = (KafkaTopics) o;
        return Objects.equals(getAll(), that.getAll()) &&
                Objects.equals(getEvents(), that.getEvents());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getAll(), getEvents());
    }

    public static class EventTopicMap {
        private String event;
        private String topic;
        private String name;

        public String getEvent() {
            return event;
        }

        public void setEvent(String event) {
            this.event = event;
        }

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean equalsEvent(EventMessage eventMessage) {
            if (!eventMessage.getTopics().isEmpty()) {
                return event.equalsIgnoreCase(eventMessage.getTopics().get(0));
            }
            else {
                return false;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof EventTopicMap)) return false;
            EventTopicMap that = (EventTopicMap) o;
            return Objects.equals(getEvent(), that.getEvent()) &&
                    Objects.equals(getTopic(), that.getTopic()) &&
                    Objects.equals(getName(), that.getName());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getEvent(), getTopic(), getName());
        }
    }

}
