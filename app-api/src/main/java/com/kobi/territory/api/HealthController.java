package com.kobi.territory.api;

import com.kobi.territory.config.TerritoryProperties;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final TerritoryProperties props;

    public HealthController(TerritoryProperties props) { this.props = props; }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "service", "territory",
            "dailyCap", props.checkIn().dailyCap(),
            "leaveGraceDays", props.map().leaveGraceDays()
        );
    }
}
