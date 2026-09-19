package io.vidocq.tools.lc4jcdi.mcptasks;

/**
 * The body of {@code POST /projects/{name}/rename}.
 *
 * @param newName the new project name; lower-cased, as every project name
 */
public record RenameRequest(String newName) {}
