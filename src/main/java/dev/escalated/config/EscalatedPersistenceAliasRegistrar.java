package dev.escalated.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

/**
 * Points {@code escalatedEntityManagerFactory} and
 * {@code escalatedTransactionManager} at the host's own beans.
 *
 * <p>Escalated's repositories and services name those two beans unconditionally,
 * so that the same code works whether or not the host gave Escalated a database
 * of its own. When it did not — the default — this registers the names as
 * <em>aliases</em> rather than defining beans.
 *
 * <p>The distinction matters. A second bean of type {@code EntityManagerFactory}
 * would make every {@code @Autowired EntityManagerFactory} in the host
 * application ambiguous, breaking applications that have nothing to do with
 * support tickets. An alias is the same bean under a second name.
 */
public class EscalatedPersistenceAliasRegistrar implements BeanFactoryPostProcessor {

    static final String ENTITY_MANAGER_FACTORY = "escalatedEntityManagerFactory";
    static final String TRANSACTION_MANAGER = EscalatedTransactionManagers.ESCALATED;

    private static final String HOST_ENTITY_MANAGER_FACTORY = "entityManagerFactory";
    private static final String HOST_TRANSACTION_MANAGER = "transactionManager";

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (!(beanFactory instanceof BeanDefinitionRegistry registry)) {
            return;
        }

        alias(registry, HOST_ENTITY_MANAGER_FACTORY, ENTITY_MANAGER_FACTORY);
        alias(registry, HOST_TRANSACTION_MANAGER, TRANSACTION_MANAGER);
    }

    private void alias(BeanDefinitionRegistry registry, String target, String alias) {
        // The host defines these; a host without JPA at all has no Escalated to
        // configure either, so a missing target means something else is wrong
        // and Spring's own error is clearer than one invented here.
        if (!registry.isBeanNameInUse(alias) && registry.containsBeanDefinition(target)) {
            registry.registerAlias(target, alias);
        }
    }
}
