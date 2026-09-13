package hostroutes;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A route of the host's own, outside {@code /escalated/**}. Registered only by
 * {@link HostRoutes}; nothing scans this package.
 */
@RestController
public class HostPingController {

    @GetMapping("/host/ping")
    public Map<String, String> ping() {
        return Map.of("pong", "ok");
    }
}
