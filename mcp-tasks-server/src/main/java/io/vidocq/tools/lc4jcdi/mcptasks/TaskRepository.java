package io.vidocq.tools.lc4jcdi.mcptasks;

import jakarta.data.repository.BasicRepository;
import jakarta.data.repository.Param;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import java.time.Instant;
import java.util.List;

/**
 * The {@code tasks} table, as a Jakarta Data repository. Mansart's annotation processor generates
 * {@code TaskRepositoryImpl} at compile time, and its CDI extension registers it as a bean.
 *
 * <p>Deliberately <em>not</em> {@code @Transactional}, unlike the repositories of Vidocq's Mansart H2 example:
 * {@code TaskService} owns the transaction boundary, so that a task row and its event row commit or roll back
 * together. Outside a transaction (the reads of {@code TaskQueries}), each call autocommits on its own.
 *
 * <p>{@code findAll}, {@code findById}, {@code save} and {@code deleteById} are inherited. JDQL parameters are
 * bound by the Java parameter name, which {@link Param} repeats; never name one {@code from} or {@code to}, which
 * are JDQL keywords.
 */
@Repository
public interface TaskRepository extends BasicRepository<Task, Long> {

    /**
     * Tasks with the given status.
     *
     * @param status the status
     * @return the tasks, by due date (the caller re-sorts them by urgency)
     */
    List<Task> findByStatusOrderByDueDateAsc(TaskStatus status);

    /**
     * Tasks of a project with the given status.
     *
     * @param project the project name
     * @param status the status
     * @return the tasks, by due date
     */
    List<Task> findByProjectAndStatusOrderByDueDateAsc(String project, TaskStatus status);

    /**
     * Every task of a project.
     *
     * @param project the project name
     * @return the tasks, by id
     */
    List<Task> findByProjectOrderByIdAsc(String project);

    /**
     * Counts the tasks of a project.
     *
     * @param project the project name
     * @return the number of tasks, whatever their status
     */
    long countByProject(String project);

    /**
     * Case-insensitive text search in titles and descriptions.
     *
     * @param pattern a lower-case SQL {@code LIKE} pattern, such as {@code %redaction%} (see
     *     {@link TaskRules#likePattern(String)})
     * @return the matching tasks, by id
     */
    @Query("FROM Task WHERE LOWER(title) LIKE :pattern OR LOWER(description) LIKE :pattern ORDER BY id ASC")
    List<Task> searchText(@Param("pattern") String pattern);

    /**
     * Moves every task of a project to another project, in one set-based {@code UPDATE}.
     *
     * @param oldName the current project name
     * @param newName the new project name
     * @param now the new {@code updatedAt} of the moved tasks
     * @return the number of tasks moved
     */
    @Query("UPDATE Task SET project = :newName, updatedAt = :now WHERE project = :oldName")
    long renameProject(
            @Param("oldName") String oldName, @Param("newName") String newName, @Param("now") Instant now);
}
