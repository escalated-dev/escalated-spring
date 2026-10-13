package dev.escalated.security;

import java.util.Map;

/** Fixed SQL shared by ORM filters, collection loads and preloaded write guards. */
public final class TicketAccessSql {
    private TicketAccessSql() {}

    public static final String TICKET_STANDARD =
            "((:departmentRestricted = false and :accountRestricted = false) or"
                + " ((:departmentRestricted = false or exists (select 1 from"
                + " escalated_agent_profiles va where va.id = :agentId and va.is_active = true and"
                + " (va.department_id = {row}.department_id or exists (select 1 from"
                + " escalated_department_agent vm where vm.agent_id = va.id and vm.department_id ="
                + " {row}.department_id)))) and (:accountRestricted = false or (:accountTypeLength"
                + " > 0 and :accountIdLength > 0 and {row}.account_type = :accountType and"
                + " {row}.account_id = :accountId and char_length({row}.account_type) ="
                + " :accountTypeLength and char_length({row}.account_id) = :accountIdLength))))";
    public static final String REPLY_STANDARD =
            "((:departmentRestricted = false and :accountRestricted = false) or (exists (select 1"
                + " from escalated_tickets vt where vt.id = {row}.ticket_id and"
                + " (:departmentRestricted = false or exists (select 1 from"
                + " escalated_agent_profiles va where va.id = :agentId and va.is_active = true and"
                + " (va.department_id = vt.department_id or exists (select 1 from"
                + " escalated_department_agent vm where vm.agent_id = va.id and vm.department_id ="
                + " vt.department_id)))) and (:accountRestricted = false or (:accountTypeLength > 0"
                + " and :accountIdLength > 0 and vt.account_type = :accountType and vt.account_id ="
                + " :accountId and char_length(vt.account_type) = :accountTypeLength and"
                + " char_length(vt.account_id) = :accountIdLength)))))";
    public static final String TICKET_ACTIVITY_STANDARD =
            "((:departmentRestricted = false and :accountRestricted = false) or (exists (select 1"
                + " from escalated_tickets vt where vt.id = {row}.ticket_id and"
                + " (:departmentRestricted = false or exists (select 1 from"
                + " escalated_agent_profiles va where va.id = :agentId and va.is_active = true and"
                + " (va.department_id = vt.department_id or exists (select 1 from"
                + " escalated_department_agent vm where vm.agent_id = va.id and vm.department_id ="
                + " vt.department_id)))) and (:accountRestricted = false or (:accountTypeLength > 0"
                + " and :accountIdLength > 0 and vt.account_type = :accountType and vt.account_id ="
                + " :accountId and char_length(vt.account_type) = :accountTypeLength and"
                + " char_length(vt.account_id) = :accountIdLength)))))";
    public static final String CUSTOM_FIELD_VALUE_STANDARD =
            "((:departmentRestricted = false and :accountRestricted = false) or (exists (select 1"
                + " from escalated_tickets vt where vt.id = {row}.ticket_id and"
                + " (:departmentRestricted = false or exists (select 1 from"
                + " escalated_agent_profiles va where va.id = :agentId and va.is_active = true and"
                + " (va.department_id = vt.department_id or exists (select 1 from"
                + " escalated_department_agent vm where vm.agent_id = va.id and vm.department_id ="
                + " vt.department_id)))) and (:accountRestricted = false or (:accountTypeLength > 0"
                + " and :accountIdLength > 0 and vt.account_type = :accountType and vt.account_id ="
                + " :accountId and char_length(vt.account_type) = :accountTypeLength and"
                + " char_length(vt.account_id) = :accountIdLength)))))";
    public static final String SIDE_CONVERSATION_STANDARD =
            "((:departmentRestricted = false and :accountRestricted = false) or (exists (select 1"
                + " from escalated_tickets vt where vt.id = {row}.ticket_id and"
                + " (:departmentRestricted = false or exists (select 1 from"
                + " escalated_agent_profiles va where va.id = :agentId and va.is_active = true and"
                + " (va.department_id = vt.department_id or exists (select 1 from"
                + " escalated_department_agent vm where vm.agent_id = va.id and vm.department_id ="
                + " vt.department_id)))) and (:accountRestricted = false or (:accountTypeLength > 0"
                + " and :accountIdLength > 0 and vt.account_type = :accountType and vt.account_id ="
                + " :accountId and char_length(vt.account_type) = :accountTypeLength and"
                + " char_length(vt.account_id) = :accountIdLength)))))";
    public static final String TICKET_SUBJECT_LINK_STANDARD =
            "((:departmentRestricted = false and :accountRestricted = false) or (exists (select 1"
                + " from escalated_tickets vt where vt.id = {row}.ticket_id and"
                + " (:departmentRestricted = false or exists (select 1 from"
                + " escalated_agent_profiles va where va.id = :agentId and va.is_active = true and"
                + " (va.department_id = vt.department_id or exists (select 1 from"
                + " escalated_department_agent vm where vm.agent_id = va.id and vm.department_id ="
                + " vt.department_id)))) and (:accountRestricted = false or (:accountTypeLength > 0"
                + " and :accountIdLength > 0 and vt.account_type = :accountType and vt.account_id ="
                + " :accountId and char_length(vt.account_type) = :accountTypeLength and"
                + " char_length(vt.account_id) = :accountIdLength)))))";
    public static final String SATISFACTION_RATING_STANDARD =
            "((:departmentRestricted = false and :accountRestricted = false) or (exists (select 1"
                + " from escalated_tickets vt where vt.id = {row}.ticket_id and"
                + " (:departmentRestricted = false or exists (select 1 from"
                + " escalated_agent_profiles va where va.id = :agentId and va.is_active = true and"
                + " (va.department_id = vt.department_id or exists (select 1 from"
                + " escalated_department_agent vm where vm.agent_id = va.id and vm.department_id ="
                + " vt.department_id)))) and (:accountRestricted = false or (:accountTypeLength > 0"
                + " and :accountIdLength > 0 and vt.account_type = :accountType and vt.account_id ="
                + " :accountId and char_length(vt.account_type) = :accountTypeLength and"
                + " char_length(vt.account_id) = :accountIdLength)))))";
    public static final String CHAT_SESSION_STANDARD =
            "((:departmentRestricted = false and :accountRestricted = false) or (exists (select 1"
                + " from escalated_tickets vt where vt.id = {row}.ticket_id and"
                + " (:departmentRestricted = false or exists (select 1 from"
                + " escalated_agent_profiles va where va.id = :agentId and va.is_active = true and"
                + " (va.department_id = vt.department_id or exists (select 1 from"
                + " escalated_department_agent vm where vm.agent_id = va.id and vm.department_id ="
                + " vt.department_id)))) and (:accountRestricted = false or (:accountTypeLength > 0"
                + " and :accountIdLength > 0 and vt.account_type = :accountType and vt.account_id ="
                + " :accountId and char_length(vt.account_type) = :accountTypeLength and"
                + " char_length(vt.account_id) = :accountIdLength)))))";
    public static final String TICKET_FOLLOWER_STANDARD =
            "((:departmentRestricted = false and :accountRestricted = false) or (exists (select 1"
                + " from escalated_tickets vt where vt.id = {row}.ticket_id and"
                + " (:departmentRestricted = false or exists (select 1 from"
                + " escalated_agent_profiles va where va.id = :agentId and va.is_active = true and"
                + " (va.department_id = vt.department_id or exists (select 1 from"
                + " escalated_department_agent vm where vm.agent_id = va.id and vm.department_id ="
                + " vt.department_id)))) and (:accountRestricted = false or (:accountTypeLength > 0"
                + " and :accountIdLength > 0 and vt.account_type = :accountType and vt.account_id ="
                + " :accountId and char_length(vt.account_type) = :accountTypeLength and"
                + " char_length(vt.account_id) = :accountIdLength)))))";
    public static final String WORKFLOW_LOG_STANDARD =
            "((:departmentRestricted = false and :accountRestricted = false) or (exists (select 1"
                + " from escalated_tickets vt where vt.id = {row}.ticket_id and"
                + " (:departmentRestricted = false or exists (select 1 from"
                + " escalated_agent_profiles va where va.id = :agentId and va.is_active = true and"
                + " (va.department_id = vt.department_id or exists (select 1 from"
                + " escalated_department_agent vm where vm.agent_id = va.id and vm.department_id ="
                + " vt.department_id)))) and (:accountRestricted = false or (:accountTypeLength > 0"
                + " and :accountIdLength > 0 and vt.account_type = :accountType and vt.account_id ="
                + " :accountId and char_length(vt.account_type) = :accountTypeLength and"
                + " char_length(vt.account_id) = :accountIdLength)))))";
    public static final String TICKET_LINK_STANDARD =
            "((:departmentRestricted = false and :accountRestricted = false) or (exists (select 1"
                + " from escalated_tickets vt where vt.id = {row}.source_ticket_id and"
                + " (:departmentRestricted = false or exists (select 1 from"
                + " escalated_agent_profiles va where va.id = :agentId and va.is_active = true and"
                + " (va.department_id = vt.department_id or exists (select 1 from"
                + " escalated_department_agent vm where vm.agent_id = va.id and vm.department_id ="
                + " vt.department_id)))) and (:accountRestricted = false or (:accountTypeLength > 0"
                + " and :accountIdLength > 0 and vt.account_type = :accountType and vt.account_id ="
                + " :accountId and char_length(vt.account_type) = :accountTypeLength and"
                + " char_length(vt.account_id) = :accountIdLength))) and exists (select 1 from"
                + " escalated_tickets vt where vt.id = {row}.target_ticket_id and"
                + " (:departmentRestricted = false or exists (select 1 from"
                + " escalated_agent_profiles va where va.id = :agentId and va.is_active = true and"
                + " (va.department_id = vt.department_id or exists (select 1 from"
                + " escalated_department_agent vm where vm.agent_id = va.id and vm.department_id ="
                + " vt.department_id)))) and (:accountRestricted = false or (:accountTypeLength > 0"
                + " and :accountIdLength > 0 and vt.account_type = :accountType and vt.account_id ="
                + " :accountId and char_length(vt.account_type) = :accountTypeLength and"
                + " char_length(vt.account_id) = :accountIdLength)))))";
    public static final String SIDE_CONVERSATION_REPLY_STANDARD =
            "((:departmentRestricted = false and :accountRestricted = false) or (exists (select 1"
                + " from escalated_side_conversations vc where vc.id = {row}.side_conversation_id"
                + " and exists (select 1 from escalated_tickets vt where vt.id = vc.ticket_id and"
                + " (:departmentRestricted = false or exists (select 1 from"
                + " escalated_agent_profiles va where va.id = :agentId and va.is_active = true and"
                + " (va.department_id = vt.department_id or exists (select 1 from"
                + " escalated_department_agent vm where vm.agent_id = va.id and vm.department_id ="
                + " vt.department_id)))) and (:accountRestricted = false or (:accountTypeLength > 0"
                + " and :accountIdLength > 0 and vt.account_type = :accountType and vt.account_id ="
                + " :accountId and char_length(vt.account_type) = :accountTypeLength and"
                + " char_length(vt.account_id) = :accountIdLength))))))";
    public static final String ATTACHMENT_STANDARD =
            "((:departmentRestricted = false and :accountRestricted = false) or (({row}.ticket_id"
                + " is not null or {row}.reply_id is not null) and ({row}.ticket_id is null or"
                + " exists (select 1 from escalated_tickets vt where vt.id = {row}.ticket_id and"
                + " (:departmentRestricted = false or exists (select 1 from"
                + " escalated_agent_profiles va where va.id = :agentId and va.is_active = true and"
                + " (va.department_id = vt.department_id or exists (select 1 from"
                + " escalated_department_agent vm where vm.agent_id = va.id and vm.department_id ="
                + " vt.department_id)))) and (:accountRestricted = false or (:accountTypeLength > 0"
                + " and :accountIdLength > 0 and vt.account_type = :accountType and vt.account_id ="
                + " :accountId and char_length(vt.account_type) = :accountTypeLength and"
                + " char_length(vt.account_id) = :accountIdLength)))) and ({row}.reply_id is null"
                + " or exists (select 1 from escalated_replies vr where vr.id = {row}.reply_id and"
                + " exists (select 1 from escalated_tickets vt where vt.id = vr.ticket_id and"
                + " (:departmentRestricted = false or exists (select 1 from"
                + " escalated_agent_profiles va where va.id = :agentId and va.is_active = true and"
                + " (va.department_id = vt.department_id or exists (select 1 from"
                + " escalated_department_agent vm where vm.agent_id = va.id and vm.department_id ="
                + " vt.department_id)))) and (:accountRestricted = false or (:accountTypeLength > 0"
                + " and :accountIdLength > 0 and vt.account_type = :accountType and vt.account_id ="
                + " :accountId and char_length(vt.account_type) = :accountTypeLength and"
                + " char_length(vt.account_id) = :accountIdLength)))))))";
    public static final String DEFERRED_WORKFLOW_JOB_STANDARD =
            "((:departmentRestricted = false and :accountRestricted = false) or (exists (select 1"
                + " from escalated_tickets vt where vt.id = {row}.ticket_id and"
                + " (:departmentRestricted = false or exists (select 1 from"
                + " escalated_agent_profiles va where va.id = :agentId and va.is_active = true and"
                + " (va.department_id = vt.department_id or exists (select 1 from"
                + " escalated_department_agent vm where vm.agent_id = va.id and vm.department_id ="
                + " vt.department_id)))) and (:accountRestricted = false or (:accountTypeLength > 0"
                + " and :accountIdLength > 0 and vt.account_type = :accountType and vt.account_id ="
                + " :accountId and char_length(vt.account_type) = :accountTypeLength and"
                + " char_length(vt.account_id) = :accountIdLength)))))";
    public static final String AUDIT_LOG_STANDARD =
            "((:departmentRestricted = false and :accountRestricted = false) or (({row}.entity_type"
                + " <> 'Ticket' or exists (select 1 from escalated_tickets vt where vt.id ="
                + " {row}.entity_id and (:departmentRestricted = false or exists (select 1 from"
                + " escalated_agent_profiles va where va.id = :agentId and va.is_active = true and"
                + " (va.department_id = vt.department_id or exists (select 1 from"
                + " escalated_department_agent vm where vm.agent_id = va.id and vm.department_id ="
                + " vt.department_id)))) and (:accountRestricted = false or (:accountTypeLength > 0"
                + " and :accountIdLength > 0 and vt.account_type = :accountType and vt.account_id ="
                + " :accountId and char_length(vt.account_type) = :accountTypeLength and"
                + " char_length(vt.account_id) = :accountIdLength))))))";
    static final Map<String, String> STANDARD =
            Map.ofEntries(
                    Map.entry("Ticket", TICKET_STANDARD),
                    Map.entry("Reply", REPLY_STANDARD),
                    Map.entry("TicketActivity", TICKET_ACTIVITY_STANDARD),
                    Map.entry("CustomFieldValue", CUSTOM_FIELD_VALUE_STANDARD),
                    Map.entry("SideConversation", SIDE_CONVERSATION_STANDARD),
                    Map.entry("TicketSubjectLink", TICKET_SUBJECT_LINK_STANDARD),
                    Map.entry("SatisfactionRating", SATISFACTION_RATING_STANDARD),
                    Map.entry("ChatSession", CHAT_SESSION_STANDARD),
                    Map.entry("TicketFollower", TICKET_FOLLOWER_STANDARD),
                    Map.entry("WorkflowLog", WORKFLOW_LOG_STANDARD),
                    Map.entry("TicketLink", TICKET_LINK_STANDARD),
                    Map.entry("SideConversationReply", SIDE_CONVERSATION_REPLY_STANDARD),
                    Map.entry("Attachment", ATTACHMENT_STANDARD),
                    Map.entry("DeferredWorkflowJob", DEFERRED_WORKFLOW_JOB_STANDARD),
                    Map.entry("AuditLog", AUDIT_LOG_STANDARD));
    public static final String TICKET_MYSQL =
            "((:departmentRestricted = false and :accountRestricted = false) or"
                + " ((:departmentRestricted = false or exists (select 1 from"
                + " escalated_agent_profiles va where va.id = :agentId and va.is_active = true and"
                + " (va.department_id = {row}.department_id or exists (select 1 from"
                + " escalated_department_agent vm where vm.agent_id = va.id and vm.department_id ="
                + " {row}.department_id)))) and (:accountRestricted = false or (:accountTypeLength"
                + " > 0 and :accountIdLength > 0 and cast({row}.account_type as binary) ="
                + " cast(:accountType as binary) and cast({row}.account_id as binary) ="
                + " cast(:accountId as binary)))))";
    public static final String REPLY_MYSQL =
            "((:departmentRestricted = false and :accountRestricted = false) or (exists (select 1"
                + " from escalated_tickets vt where vt.id = {row}.ticket_id and"
                + " (:departmentRestricted = false or exists (select 1 from"
                + " escalated_agent_profiles va where va.id = :agentId and va.is_active = true and"
                + " (va.department_id = vt.department_id or exists (select 1 from"
                + " escalated_department_agent vm where vm.agent_id = va.id and vm.department_id ="
                + " vt.department_id)))) and (:accountRestricted = false or (:accountTypeLength > 0"
                + " and :accountIdLength > 0 and cast(vt.account_type as binary) ="
                + " cast(:accountType as binary) and cast(vt.account_id as binary) ="
                + " cast(:accountId as binary))))))";
    public static final String TICKET_ACTIVITY_MYSQL =
            "((:departmentRestricted = false and :accountRestricted = false) or (exists (select 1"
                + " from escalated_tickets vt where vt.id = {row}.ticket_id and"
                + " (:departmentRestricted = false or exists (select 1 from"
                + " escalated_agent_profiles va where va.id = :agentId and va.is_active = true and"
                + " (va.department_id = vt.department_id or exists (select 1 from"
                + " escalated_department_agent vm where vm.agent_id = va.id and vm.department_id ="
                + " vt.department_id)))) and (:accountRestricted = false or (:accountTypeLength > 0"
                + " and :accountIdLength > 0 and cast(vt.account_type as binary) ="
                + " cast(:accountType as binary) and cast(vt.account_id as binary) ="
                + " cast(:accountId as binary))))))";
    public static final String CUSTOM_FIELD_VALUE_MYSQL =
            "((:departmentRestricted = false and :accountRestricted = false) or (exists (select 1"
                + " from escalated_tickets vt where vt.id = {row}.ticket_id and"
                + " (:departmentRestricted = false or exists (select 1 from"
                + " escalated_agent_profiles va where va.id = :agentId and va.is_active = true and"
                + " (va.department_id = vt.department_id or exists (select 1 from"
                + " escalated_department_agent vm where vm.agent_id = va.id and vm.department_id ="
                + " vt.department_id)))) and (:accountRestricted = false or (:accountTypeLength > 0"
                + " and :accountIdLength > 0 and cast(vt.account_type as binary) ="
                + " cast(:accountType as binary) and cast(vt.account_id as binary) ="
                + " cast(:accountId as binary))))))";
    public static final String SIDE_CONVERSATION_MYSQL =
            "((:departmentRestricted = false and :accountRestricted = false) or (exists (select 1"
                + " from escalated_tickets vt where vt.id = {row}.ticket_id and"
                + " (:departmentRestricted = false or exists (select 1 from"
                + " escalated_agent_profiles va where va.id = :agentId and va.is_active = true and"
                + " (va.department_id = vt.department_id or exists (select 1 from"
                + " escalated_department_agent vm where vm.agent_id = va.id and vm.department_id ="
                + " vt.department_id)))) and (:accountRestricted = false or (:accountTypeLength > 0"
                + " and :accountIdLength > 0 and cast(vt.account_type as binary) ="
                + " cast(:accountType as binary) and cast(vt.account_id as binary) ="
                + " cast(:accountId as binary))))))";
    public static final String TICKET_SUBJECT_LINK_MYSQL =
            "((:departmentRestricted = false and :accountRestricted = false) or (exists (select 1"
                + " from escalated_tickets vt where vt.id = {row}.ticket_id and"
                + " (:departmentRestricted = false or exists (select 1 from"
                + " escalated_agent_profiles va where va.id = :agentId and va.is_active = true and"
                + " (va.department_id = vt.department_id or exists (select 1 from"
                + " escalated_department_agent vm where vm.agent_id = va.id and vm.department_id ="
                + " vt.department_id)))) and (:accountRestricted = false or (:accountTypeLength > 0"
                + " and :accountIdLength > 0 and cast(vt.account_type as binary) ="
                + " cast(:accountType as binary) and cast(vt.account_id as binary) ="
                + " cast(:accountId as binary))))))";
    public static final String SATISFACTION_RATING_MYSQL =
            "((:departmentRestricted = false and :accountRestricted = false) or (exists (select 1"
                + " from escalated_tickets vt where vt.id = {row}.ticket_id and"
                + " (:departmentRestricted = false or exists (select 1 from"
                + " escalated_agent_profiles va where va.id = :agentId and va.is_active = true and"
                + " (va.department_id = vt.department_id or exists (select 1 from"
                + " escalated_department_agent vm where vm.agent_id = va.id and vm.department_id ="
                + " vt.department_id)))) and (:accountRestricted = false or (:accountTypeLength > 0"
                + " and :accountIdLength > 0 and cast(vt.account_type as binary) ="
                + " cast(:accountType as binary) and cast(vt.account_id as binary) ="
                + " cast(:accountId as binary))))))";
    public static final String CHAT_SESSION_MYSQL =
            "((:departmentRestricted = false and :accountRestricted = false) or (exists (select 1"
                + " from escalated_tickets vt where vt.id = {row}.ticket_id and"
                + " (:departmentRestricted = false or exists (select 1 from"
                + " escalated_agent_profiles va where va.id = :agentId and va.is_active = true and"
                + " (va.department_id = vt.department_id or exists (select 1 from"
                + " escalated_department_agent vm where vm.agent_id = va.id and vm.department_id ="
                + " vt.department_id)))) and (:accountRestricted = false or (:accountTypeLength > 0"
                + " and :accountIdLength > 0 and cast(vt.account_type as binary) ="
                + " cast(:accountType as binary) and cast(vt.account_id as binary) ="
                + " cast(:accountId as binary))))))";
    public static final String TICKET_FOLLOWER_MYSQL =
            "((:departmentRestricted = false and :accountRestricted = false) or (exists (select 1"
                + " from escalated_tickets vt where vt.id = {row}.ticket_id and"
                + " (:departmentRestricted = false or exists (select 1 from"
                + " escalated_agent_profiles va where va.id = :agentId and va.is_active = true and"
                + " (va.department_id = vt.department_id or exists (select 1 from"
                + " escalated_department_agent vm where vm.agent_id = va.id and vm.department_id ="
                + " vt.department_id)))) and (:accountRestricted = false or (:accountTypeLength > 0"
                + " and :accountIdLength > 0 and cast(vt.account_type as binary) ="
                + " cast(:accountType as binary) and cast(vt.account_id as binary) ="
                + " cast(:accountId as binary))))))";
    public static final String WORKFLOW_LOG_MYSQL =
            "((:departmentRestricted = false and :accountRestricted = false) or (exists (select 1"
                + " from escalated_tickets vt where vt.id = {row}.ticket_id and"
                + " (:departmentRestricted = false or exists (select 1 from"
                + " escalated_agent_profiles va where va.id = :agentId and va.is_active = true and"
                + " (va.department_id = vt.department_id or exists (select 1 from"
                + " escalated_department_agent vm where vm.agent_id = va.id and vm.department_id ="
                + " vt.department_id)))) and (:accountRestricted = false or (:accountTypeLength > 0"
                + " and :accountIdLength > 0 and cast(vt.account_type as binary) ="
                + " cast(:accountType as binary) and cast(vt.account_id as binary) ="
                + " cast(:accountId as binary))))))";
    public static final String TICKET_LINK_MYSQL =
            "((:departmentRestricted = false and :accountRestricted = false) or (exists (select 1"
                + " from escalated_tickets vt where vt.id = {row}.source_ticket_id and"
                + " (:departmentRestricted = false or exists (select 1 from"
                + " escalated_agent_profiles va where va.id = :agentId and va.is_active = true and"
                + " (va.department_id = vt.department_id or exists (select 1 from"
                + " escalated_department_agent vm where vm.agent_id = va.id and vm.department_id ="
                + " vt.department_id)))) and (:accountRestricted = false or (:accountTypeLength > 0"
                + " and :accountIdLength > 0 and cast(vt.account_type as binary) ="
                + " cast(:accountType as binary) and cast(vt.account_id as binary) ="
                + " cast(:accountId as binary)))) and exists (select 1 from escalated_tickets vt"
                + " where vt.id = {row}.target_ticket_id and (:departmentRestricted = false or"
                + " exists (select 1 from escalated_agent_profiles va where va.id = :agentId and"
                + " va.is_active = true and (va.department_id = vt.department_id or exists (select"
                + " 1 from escalated_department_agent vm where vm.agent_id = va.id and"
                + " vm.department_id = vt.department_id)))) and (:accountRestricted = false or"
                + " (:accountTypeLength > 0 and :accountIdLength > 0 and cast(vt.account_type as"
                + " binary) = cast(:accountType as binary) and cast(vt.account_id as binary) ="
                + " cast(:accountId as binary))))))";
    public static final String SIDE_CONVERSATION_REPLY_MYSQL =
            "((:departmentRestricted = false and :accountRestricted = false) or (exists (select 1"
                + " from escalated_side_conversations vc where vc.id = {row}.side_conversation_id"
                + " and exists (select 1 from escalated_tickets vt where vt.id = vc.ticket_id and"
                + " (:departmentRestricted = false or exists (select 1 from"
                + " escalated_agent_profiles va where va.id = :agentId and va.is_active = true and"
                + " (va.department_id = vt.department_id or exists (select 1 from"
                + " escalated_department_agent vm where vm.agent_id = va.id and vm.department_id ="
                + " vt.department_id)))) and (:accountRestricted = false or (:accountTypeLength > 0"
                + " and :accountIdLength > 0 and cast(vt.account_type as binary) ="
                + " cast(:accountType as binary) and cast(vt.account_id as binary) ="
                + " cast(:accountId as binary)))))))";
    public static final String ATTACHMENT_MYSQL =
            "((:departmentRestricted = false and :accountRestricted = false) or (({row}.ticket_id"
                + " is not null or {row}.reply_id is not null) and ({row}.ticket_id is null or"
                + " exists (select 1 from escalated_tickets vt where vt.id = {row}.ticket_id and"
                + " (:departmentRestricted = false or exists (select 1 from"
                + " escalated_agent_profiles va where va.id = :agentId and va.is_active = true and"
                + " (va.department_id = vt.department_id or exists (select 1 from"
                + " escalated_department_agent vm where vm.agent_id = va.id and vm.department_id ="
                + " vt.department_id)))) and (:accountRestricted = false or (:accountTypeLength > 0"
                + " and :accountIdLength > 0 and cast(vt.account_type as binary) ="
                + " cast(:accountType as binary) and cast(vt.account_id as binary) ="
                + " cast(:accountId as binary))))) and ({row}.reply_id is null or exists (select 1"
                + " from escalated_replies vr where vr.id = {row}.reply_id and exists (select 1"
                + " from escalated_tickets vt where vt.id = vr.ticket_id and (:departmentRestricted"
                + " = false or exists (select 1 from escalated_agent_profiles va where va.id ="
                + " :agentId and va.is_active = true and (va.department_id = vt.department_id or"
                + " exists (select 1 from escalated_department_agent vm where vm.agent_id = va.id"
                + " and vm.department_id = vt.department_id)))) and (:accountRestricted = false or"
                + " (:accountTypeLength > 0 and :accountIdLength > 0 and cast(vt.account_type as"
                + " binary) = cast(:accountType as binary) and cast(vt.account_id as binary) ="
                + " cast(:accountId as binary))))))))";
    public static final String DEFERRED_WORKFLOW_JOB_MYSQL =
            "((:departmentRestricted = false and :accountRestricted = false) or (exists (select 1"
                + " from escalated_tickets vt where vt.id = {row}.ticket_id and"
                + " (:departmentRestricted = false or exists (select 1 from"
                + " escalated_agent_profiles va where va.id = :agentId and va.is_active = true and"
                + " (va.department_id = vt.department_id or exists (select 1 from"
                + " escalated_department_agent vm where vm.agent_id = va.id and vm.department_id ="
                + " vt.department_id)))) and (:accountRestricted = false or (:accountTypeLength > 0"
                + " and :accountIdLength > 0 and cast(vt.account_type as binary) ="
                + " cast(:accountType as binary) and cast(vt.account_id as binary) ="
                + " cast(:accountId as binary))))))";
    public static final String AUDIT_LOG_MYSQL =
            "((:departmentRestricted = false and :accountRestricted = false) or (({row}.entity_type"
                + " <> 'Ticket' or exists (select 1 from escalated_tickets vt where vt.id ="
                + " {row}.entity_id and (:departmentRestricted = false or exists (select 1 from"
                + " escalated_agent_profiles va where va.id = :agentId and va.is_active = true and"
                + " (va.department_id = vt.department_id or exists (select 1 from"
                + " escalated_department_agent vm where vm.agent_id = va.id and vm.department_id ="
                + " vt.department_id)))) and (:accountRestricted = false or (:accountTypeLength > 0"
                + " and :accountIdLength > 0 and cast(vt.account_type as binary) ="
                + " cast(:accountType as binary) and cast(vt.account_id as binary) ="
                + " cast(:accountId as binary)))))))";
    static final Map<String, String> MYSQL =
            Map.ofEntries(
                    Map.entry("Ticket", TICKET_MYSQL),
                    Map.entry("Reply", REPLY_MYSQL),
                    Map.entry("TicketActivity", TICKET_ACTIVITY_MYSQL),
                    Map.entry("CustomFieldValue", CUSTOM_FIELD_VALUE_MYSQL),
                    Map.entry("SideConversation", SIDE_CONVERSATION_MYSQL),
                    Map.entry("TicketSubjectLink", TICKET_SUBJECT_LINK_MYSQL),
                    Map.entry("SatisfactionRating", SATISFACTION_RATING_MYSQL),
                    Map.entry("ChatSession", CHAT_SESSION_MYSQL),
                    Map.entry("TicketFollower", TICKET_FOLLOWER_MYSQL),
                    Map.entry("WorkflowLog", WORKFLOW_LOG_MYSQL),
                    Map.entry("TicketLink", TICKET_LINK_MYSQL),
                    Map.entry("SideConversationReply", SIDE_CONVERSATION_REPLY_MYSQL),
                    Map.entry("Attachment", ATTACHMENT_MYSQL),
                    Map.entry("DeferredWorkflowJob", DEFERRED_WORKFLOW_JOB_MYSQL),
                    Map.entry("AuditLog", AUDIT_LOG_MYSQL));
    static final Map<String, String> TABLES =
            Map.ofEntries(
                    Map.entry("Ticket", "escalated_tickets"),
                    Map.entry("Reply", "escalated_replies"),
                    Map.entry("TicketActivity", "escalated_ticket_activities"),
                    Map.entry("CustomFieldValue", "escalated_custom_field_values"),
                    Map.entry("SideConversation", "escalated_side_conversations"),
                    Map.entry("TicketSubjectLink", "escalated_ticket_subjects"),
                    Map.entry("SatisfactionRating", "escalated_satisfaction_ratings"),
                    Map.entry("ChatSession", "escalated_chat_sessions"),
                    Map.entry("TicketFollower", "escalated_ticket_followers"),
                    Map.entry("WorkflowLog", "escalated_workflow_logs"),
                    Map.entry("TicketLink", "escalated_ticket_links"),
                    Map.entry("SideConversationReply", "escalated_side_conversation_replies"),
                    Map.entry("Attachment", "escalated_attachments"),
                    Map.entry("DeferredWorkflowJob", "escalated_deferred_workflow_jobs"),
                    Map.entry("AuditLog", "escalated_audit_logs"));
}
