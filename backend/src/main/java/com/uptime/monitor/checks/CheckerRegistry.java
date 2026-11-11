package com.uptime.monitor.checks;

import com.uptime.monitor.monitors.CheckType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Picks the checker for a check type. Fails at startup if any type has no checker. */
@Component
public class CheckerRegistry {

    private final Map<CheckType, Checker> checkers = new EnumMap<>(CheckType.class);

    public CheckerRegistry(List<Checker> all) {
        for (Checker checker : all) {
            if (checkers.put(checker.type(), checker) != null) {
                throw new IllegalStateException("Two checkers registered for " + checker.type());
            }
        }
        for (CheckType type : CheckType.values()) {
            if (!checkers.containsKey(type)) {
                throw new IllegalStateException("No checker registered for " + type);
            }
        }
    }

    public Checker forType(CheckType type) {
        return checkers.get(type);
    }
}
