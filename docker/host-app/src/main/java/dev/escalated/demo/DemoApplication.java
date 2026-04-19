package dev.escalated.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication(scanBasePackages = {"dev.escalated"})
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}

@RestController
class DemoController {
    @GetMapping("/")
    public String home() {
        return "Escalated Spring Boot demo host. Set APP_ENV=demo for /demo routes.";
    }

    @GetMapping("/demo")
    public String demo() {
        return "<html><body><h1>Escalated Spring Demo</h1>" +
            "<p>Host project bootstrapped. The /demo picker, click-to-login, " +
            "and seed work is the remaining punch list — see PR body.</p></body></html>";
    }
}
