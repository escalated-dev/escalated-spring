package dev.escalated.demo;

import dev.escalated.models.AgentProfile;
import dev.escalated.models.Department;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.DepartmentRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.view.RedirectView;

import java.util.List;
import java.util.stream.Collectors;

@Controller
public class DemoController {

    private final AgentProfileRepository agents;
    private final DepartmentRepository departments;

    public DemoController(AgentProfileRepository agents, DepartmentRepository departments) {
        this.agents = agents;
        this.departments = departments;
    }

    @PostConstruct
    void seed() {
        if (agents.count() > 0) return;
        Department support = new Department();
        support.setName("Support");
        support.setActive(true);
        departments.save(support);
        Department billing = new Department();
        billing.setName("Billing");
        billing.setActive(true);
        departments.save(billing);

        for (Object[] row : new Object[][]{
                {"Alice (Admin)", "alice@demo.test", 1L},
                {"Bob (Agent)", "bob@demo.test", 2L},
                {"Carol (Agent)", "carol@demo.test", 3L},
        }) {
            AgentProfile a = new AgentProfile();
            a.setName((String) row[0]);
            a.setEmail((String) row[1]);
            a.setUserId((Long) row[2]);
            a.setActive(true);
            a.setAvailable(true);
            agents.save(a);
        }
    }

    @GetMapping("/")
    public RedirectView root() {
        return new RedirectView("/demo");
    }

    @GetMapping("/demo")
    @ResponseBody
    public String picker() {
        List<AgentProfile> all = agents.findAll();
        String rows = all.stream().map(a -> String.format(
                "<form method='POST' action='/demo/login/%d'>" +
                "<button type='submit' class='user'>" +
                "<span>%s</span><span class='meta'>%s · UserId %s</span>" +
                "</button></form>",
                a.getId(), escape(a.getName()), escape(a.getEmail()), a.getUserId())).collect(Collectors.joining());
        return picker(rows);
    }

    @PostMapping("/demo/login/{id}")
    public RedirectView login(@PathVariable Long id) {
        return new RedirectView("/demo/agent/" + id);
    }

    @GetMapping("/demo/agent/{id}")
    @ResponseBody
    public String agentPage(@PathVariable Long id) {
        return agents.findById(id).map(a -> String.format(
                "<!DOCTYPE html><html><head><meta charset='utf-8'><title>Agent %s</title>" +
                "<style>%s</style></head><body><div class='wrap'>" +
                "<h1>Logged in as %s</h1>" +
                "<p class='meta'>Email: %s · UserId: %s · Active: %s · Available: %s</p>" +
                "<p>Spring Boot host + Postgres + JPA round-trip verified end-to-end. " +
                "Department count: %d. Click <a href='/demo'>back to picker</a>.</p>" +
                "</div></body></html>",
                escape(a.getName()), styles(),
                escape(a.getName()), escape(a.getEmail()), a.getUserId(), a.isActive(), a.isAvailable(),
                departments.count())).orElse("Agent not found");
    }

    private static String picker(String rows) {
        return "<!DOCTYPE html><html><head><meta charset='utf-8'><title>Escalated · Spring Demo</title>" +
                "<style>" + styles() + "</style></head><body><div class='wrap'>" +
                "<h1>Escalated Spring Demo</h1>" +
                "<p class='lede'>Click an agent to load their profile. Database seeds on first boot.</p>" +
                rows +
                "</div></body></html>";
    }

    private static String styles() {
        return "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:#0f172a;color:#e2e8f0;margin:0;padding:2rem}" +
                ".wrap{max-width:720px;margin:0 auto}" +
                "h1{font-size:1.5rem;margin:0 0 .25rem}" +
                "p.lede{color:#94a3b8;margin:0 0 2rem}" +
                "p.meta{color:#94a3b8;font-size:.85rem;margin-bottom:1rem}" +
                "form{display:block;margin:0}" +
                "button.user{display:flex;width:100%;align-items:center;justify-content:space-between;padding:.75rem 1rem;background:#1e293b;border:1px solid #334155;border-radius:8px;color:#f1f5f9;font-size:.95rem;cursor:pointer;margin-bottom:.5rem;text-align:left}" +
                "button.user:hover{background:#273549;border-color:#475569}" +
                ".meta{color:#94a3b8;font-size:.8rem}" +
                "a{color:#60a5fa}";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
