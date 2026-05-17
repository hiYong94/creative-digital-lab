package com.creatived.chat.domain.snapshot;

import com.creatived.chat.domain.event.EventType;
import com.creatived.chat.domain.event.UnsupportedEventTypeException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class EventProjectorRegistry {

    private final Map<EventType, EventProjector> projectors;

    public EventProjectorRegistry(List<EventProjector> projectorList) {
        this.projectors = projectorList.stream()
                .collect(Collectors.toMap(EventProjector::supportedType, Function.identity()));
    }

    public EventProjector get(EventType type) {
        EventProjector projector = projectors.get(type);
        if (projector == null) {
            throw new UnsupportedEventTypeException(type);
        }
        return projector;
    }
}
