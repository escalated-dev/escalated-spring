package dev.escalated.plugins;

import dev.escalated.config.EscalatedProperties;
import dev.escalated.config.EscalatedTransactionManagers;
import dev.escalated.models.AgentProfile;
import dev.escalated.models.Permission;
import dev.escalated.repositories.AgentProfileRepository;
import dev.escalated.repositories.ChatSessionRepository;
import dev.escalated.repositories.ContactRepository;
import dev.escalated.repositories.DepartmentRepository;
import dev.escalated.repositories.PluginActivationRepository;
import dev.escalated.repositories.PluginStoreRecordRepository;
import dev.escalated.repositories.ReplyRepository;
import dev.escalated.repositories.TagRepository;
import dev.escalated.repositories.TicketRepository;
import dev.escalated.security.AuthorizedTicketBroadcaster;
import dev.escalated.security.EscalatedAuthorization;
import dev.escalated.security.TicketAccess;
import dev.escalated.services.TicketService;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** The plugin registry and what it needs. */
@Configuration(proxyBeanMethods = false)
public class PluginConfiguration {

    /**
     * Admins hold every capability; anyone else, the permissions of their agent role. While roles and
     * permissions is on, only the role counts, admins included (ADR 2026-10-29-configurable-access-features).
     */
    static PluginCapabilities defaultCapabilities(
            EscalatedAuthorization authorization, AgentProfileRepository agents, TransactionTemplate readOnly,
            dev.escalated.security.AccessControl access) {
        return (user, capability) -> {
            if (access != null && access.enforced()) {
                return access.allows(user, capability);
            }
            if (authorization.isAdmin(user)) {
                return true;
            }
            Boolean granted = readOnly.execute(status -> {
                AgentProfile profile = agents.findByEmail(user.getName()).orElse(null);
                if (profile == null || !profile.isActive() || profile.getRole() == null) {
                    return false;
                }
                return profile.getRole().getPermissions().stream().map(Permission::getName)
                        .anyMatch(capability::equals);
            });
            return Boolean.TRUE.equals(granted);
        };
    }

    @Bean
    @SuppressWarnings("checkstyle:ParameterNumber")
    public PluginRegistry escalatedPluginRegistry(
            ObjectProvider<EscalatedPlugin> plugins,
            EscalatedProperties properties,
            PluginActivationRepository activations,
            PluginStoreRecordRepository records,
            TicketService tickets,
            TicketRepository ticketRows,
            ReplyRepository replies,
            ContactRepository contacts,
            TagRepository tags,
            DepartmentRepository departments,
            AgentProfileRepository agents,
            ChatSessionRepository chats,
            ObjectProvider<TicketAccess> access,
            ObjectProvider<SimpMessagingTemplate> messaging,
            ObjectProvider<AuthorizedTicketBroadcaster> broadcaster,
            ObjectProvider<PluginCapabilities> capabilities,
            EscalatedAuthorization authorization,
            ObjectProvider<dev.escalated.security.AccessControl> accessControl,
            @Qualifier(EscalatedTransactionManagers.ESCALATED) PlatformTransactionManager transactionManager) {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);
        AtomicInteger threads = new AtomicInteger();
        ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "escalated-plugin-" + threads.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        PluginDomain domain = new PluginDomain(tickets, ticketRows, replies, contacts, tags, departments, agents, chats,
                access, messaging, broadcaster, readOnly);
        PluginRegistry registry = new PluginRegistry(
                plugins.orderedStream().toList(),
                activations,
                new PluginStorage(records, transactions),
                domain,
                capabilities.orderedStream().findFirst()
                        .orElseGet(() -> defaultCapabilities(authorization, agents, readOnly, accessControl.getIfAvailable())),
                authorization::isAdmin,
                transactions,
                executor);

        EscalatedProperties.RuntimeProperties runtime = properties.getPlugins().getRuntime();
        if (runtime.isEnabled()) {
            List<String> command = new ArrayList<>();
            command.add(runtime.getCommand() == null || runtime.getCommand().isBlank() ? "node" : runtime.getCommand());
            command.addAll(runtime.getArgs().isEmpty() ? List.of(PluginRuntimeBridge.DEFAULT_SCRIPT) : runtime.getArgs());
            File directory = runtime.getDirectory().isBlank() ? null : new File(runtime.getDirectory());
            String version = PluginRegistry.class.getPackage().getImplementationVersion();
            registry.useRuntime(command, directory, runtime.getEnv(), version == null ? "dev" : version);
        }
        return registry;
    }
}
