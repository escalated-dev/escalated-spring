package dev.escalated.config;

/**
 * The bean name every transaction in this package opens against.
 *
 * <p>A bare {@code @Transactional} resolves the host application's primary
 * transaction manager. That is the right bean while Escalated shares the host's
 * database — and the wrong one the moment it does not, because the transaction
 * would be opened on a connection that has none of Escalated's tables in it.
 *
 * <p>Naming it explicitly costs nothing when Escalated is sharing: the name is
 * then an alias of the host's own manager, registered by
 * {@link EscalatedPersistenceAliasRegistrar}, so it resolves to exactly the bean
 * it always did.
 */
public final class EscalatedTransactionManagers {

    /** @see EscalatedPersistenceAliasRegistrar#TRANSACTION_MANAGER */
    public static final String ESCALATED = "escalatedTransactionManager";

    private EscalatedTransactionManagers() {
    }
}
