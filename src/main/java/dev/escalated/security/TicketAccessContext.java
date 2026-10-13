package dev.escalated.security;

import java.util.function.Supplier;

/** Non-inheritable: background jobs are trusted unless they explicitly runAs an actor. */
public final class TicketAccessContext {
    private TicketAccessContext() {}

    static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    record State(
            TicketAccess access,
            boolean department,
            boolean account,
            long agentId,
            TicketAccount identity) {}

    static boolean restricted() {
        return new DepartmentRestricted().get() || new AccountRestricted().get();
    }

    public static class DepartmentRestricted implements Supplier<Boolean> {
        public Boolean get() {
            State s = CURRENT.get();
            return s != null && s.department();
        }
    }

    public static class AccountRestricted implements Supplier<Boolean> {
        public Boolean get() {
            State s = CURRENT.get();
            return s != null && s.account();
        }
    }

    public static class AgentId implements Supplier<Long> {
        public Long get() {
            State s = CURRENT.get();
            return s == null ? -1L : s.agentId();
        }
    }

    public static class AccountType implements Supplier<String> {
        public String get() {
            State s = CURRENT.get();
            return s == null || s.identity() == null ? "" : s.identity().type();
        }
    }

    public static class AccountId implements Supplier<String> {
        public String get() {
            State s = CURRENT.get();
            return s == null || s.identity() == null ? "" : s.identity().id();
        }
    }

    public static class AccountTypeLength implements Supplier<Integer> {
        public Integer get() {
            String s = new AccountType().get();
            return s.codePointCount(0, s.length());
        }
    }

    public static class AccountIdLength implements Supplier<Integer> {
        public Integer get() {
            String s = new AccountId().get();
            return s.codePointCount(0, s.length());
        }
    }

    static Object parameter(String name) {
        return switch (name) {
            case "departmentRestricted" -> new DepartmentRestricted().get();
            case "accountRestricted" -> new AccountRestricted().get();
            case "agentId" -> new AgentId().get();
            case "accountType" -> new AccountType().get();
            case "accountId" -> new AccountId().get();
            case "accountTypeLength" -> new AccountTypeLength().get();
            case "accountIdLength" -> new AccountIdLength().get();
            default -> throw new IllegalArgumentException("Unknown access parameter");
        };
    }
}
