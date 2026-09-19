package io.vidocq.tools.lc4jcdi.mcptasks;

/**
 * The write model of a task: what a client sends to create a task or to replace its editable fields.
 *
 * <p>Every field is a {@code String}, the priority and the due date included, so a bad value reaches
 * {@link TaskRules} and gets a message naming the field and the accepted values, instead of failing inside
 * JSON-B.
 *
 * @param title the title, required, 1 to 200 characters once trimmed
 * @param description the description, optional, at most 2000 characters
 * @param project the project, optional (default {@value TaskRules#DEFAULT_PROJECT}), lower-cased
 * @param priority {@code LOW}, {@code MEDIUM} or {@code HIGH}, any case, optional (default {@code MEDIUM})
 * @param dueDate an ISO date {@code yyyy-MM-dd}, optional
 */
public record TaskInput(String title, String description, String project, String priority, String dueDate) {}
