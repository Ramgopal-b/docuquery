package com.sedin.docuquery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ready-made queries shown under Favorites > Administration / Audittrail / Data Dictionary /
 * DocApp / Documents / Workflows and in the navigator's "Standard Favorites".
 * Queries containing &lt;placeholders&gt; are only loaded into the editor, not executed.
 */
final class StandardFavorites {

    static final class Fav {
        final String name;
        final String dql;

        Fav(String name, String dql) {
            this.name = name;
            this.dql = dql;
        }

        boolean runnable() {
            return !dql.contains("<");
        }
    }

    static final Map<String, List<Fav>> ALL = new LinkedHashMap<>();

    static {
        cat("Administration",
                "Server sessions", "EXECUTE show_sessions",
                "Docbase config", "select * from dm_docbase_config",
                "Server config", "select * from dm_server_config",
                "Jobs", "select r_object_id, object_name, is_inactive, a_last_invocation, a_last_completion, a_current_status, "
                        + "a_next_invocation from dm_job order by object_name",
                "Failed jobs", "select r_object_id, object_name, a_last_completion, a_current_status from dm_job "
                        + "where upper(a_current_status) like '%FAIL%' order by object_name",
                "Methods", "select r_object_id, object_name, method_verb, method_type, timeout_default from dm_method order by object_name",
                "Locked objects", "select r_object_id, object_name, r_object_type, r_lock_owner, r_lock_date, r_lock_machine "
                        + "from dm_sysobject where r_lock_owner <> ' ' order by r_lock_date desc",
                "Inactive users", "select r_object_id, user_name, user_os_name, user_address, user_state from dm_user "
                        + "where user_state <> 0 order by user_name",
                "Superusers / sysadmins", "select r_object_id, user_name, user_privileges from dm_user where user_privileges >= 8 "
                        + "order by user_name",
                "ACLs", "select r_object_id, object_name, owner_name, description from dm_acl order by object_name",
                "Storage areas", "select r_object_id, name, r_object_type from dm_store order by name",
                "Content size by format", "select a_content_type, count(*) as docs, sum(r_full_content_size) as bytes "
                        + "from dm_document group by a_content_type order by 3 desc",
                "Registered tables", "select r_object_id, object_name, table_name, table_owner from dm_registered order by object_name",
                "Formats", "select r_object_id, name, dos_extension, mime_type, description from dm_format order by name");
        cat("Audittrail",
                "Audit events – last 7 days", "select r_object_id, event_name, user_name, time_stamp, audited_obj_id, object_name, string_1 "
                        + "from dm_audittrail where time_stamp > DATEADD(day, -7, DATE(TODAY)) order by time_stamp desc "
                        + "ENABLE (RETURN_TOP 1000)",
                "Logins today", "select user_name, time_stamp, host_name from dm_audittrail where event_name = 'dm_connect' "
                        + "and time_stamp >= DATE(TODAY) order by time_stamp desc",
                "Deleted objects – last 7 days", "select r_object_id, user_name, time_stamp, audited_obj_id, object_name "
                        + "from dm_audittrail where event_name = 'dm_destroy' and time_stamp > DATEADD(day, -7, DATE(TODAY)) "
                        + "order by time_stamp desc",
                "Audit trail of an object", "select r_object_id, event_name, user_name, time_stamp, string_1, string_2 "
                        + "from dm_audittrail where audited_obj_id = '<object id>' order by time_stamp desc",
                "Audit registrations", "select r_object_id, registered_id, event, user_name, is_audittrail from dmi_registry "
                        + "where is_audittrail = 1");
        cat("Data Dictionary",
                "Types and supertypes", "select name, super_name, attr_count from dm_type order by name",
                "Custom types", "select name, super_name, attr_count from dm_type where name not like 'dm%' "
                        + "and name not like 'dmi%' order by name",
                "Attribute labels", "select type_name, attr_name, label_text from dmi_dd_attr_info where nls_key = 'en' "
                        + "order by type_name, attr_name",
                "Type labels", "select type_name, label_text from dmi_dd_type_info where nls_key = 'en' order by type_name",
                "Attributes of a type", "select attr_name, attr_type, attr_length, attr_repeating from dm_type "
                        + "where name = '<type name>'");
        cat("DocApp",
                "DocApps", "select r_object_id, object_name, r_modify_date from dm_application order by object_name",
                "Modules (BOF)", "select r_object_id, object_name, r_object_type, r_modify_date from dmc_module order by object_name");
        cat("Documents",
                "Last 100 modified", "select r_object_id, object_name, r_object_type, r_modify_date, r_modifier "
                        + "from dm_document order by r_modify_date desc ENABLE (RETURN_TOP 100)",
                "Created today", "select r_object_id, object_name, r_object_type, owner_name, r_creation_date "
                        + "from dm_document where r_creation_date >= DATE(TODAY) order by r_creation_date desc",
                "Checked out", "select r_object_id, object_name, r_lock_owner, r_lock_date from dm_document "
                        + "where r_lock_owner <> ' ' order by r_lock_date desc",
                "Without content", "select r_object_id, object_name, r_object_type from dm_document where r_page_cnt = 0",
                "All versions of a document", "select r_object_id, object_name, r_version_label, r_modify_date "
                        + "from dm_document (all) where i_chronicle_id = '<chronicle id>' order by r_modify_date",
                "Folder path of an object", "select r_folder_path from dm_folder where r_object_id in "
                        + "(select i_folder_id from dm_sysobject where r_object_id = '<object id>')",
                "Find by name", "select r_object_id, object_name, r_object_type, r_modify_date from dm_sysobject "
                        + "where upper(object_name) like upper('%<name>%')");
        cat("Workflows",
                "Running workflows", "select r_object_id, object_name, process_id, r_creator_name, r_start_date "
                        + "from dm_workflow where r_runtime_state = 1 order by r_start_date desc",
                "Halted / paused workflows", "select r_object_id, object_name, r_creator_name, r_start_date, r_runtime_state "
                        + "from dm_workflow where r_runtime_state in (2, 3) order by r_start_date desc",
                "Open work items", "select r_object_id, r_workflow_id, r_act_seqno, r_performer_name, r_runtime_state, r_creation_date "
                        + "from dmi_workitem where r_runtime_state in (0, 1, 3) order by r_creation_date desc",
                "Inbox items", "select r_object_id, name, item_id, task_name, sent_by, date_sent from dmi_queue_item "
                        + "where delete_flag = 0 order by date_sent desc",
                "Inbox of a user", "select r_object_id, item_id, task_name, sent_by, date_sent from dmi_queue_item "
                        + "where name = '<user name>' and delete_flag = 0 order by date_sent desc",
                "Process definitions", "select r_object_id, object_name, r_definition_state, r_modify_date from dm_process "
                        + "order by object_name");
    }

    private StandardFavorites() {}

    private static void cat(String category, String... pairs) {
        List<Fav> l = new ArrayList<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) l.add(new Fav(pairs[i], pairs[i + 1]));
        ALL.put(category, l);
    }
}
