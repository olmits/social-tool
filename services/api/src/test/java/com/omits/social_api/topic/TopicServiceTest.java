package com.omits.social_api.topic;

import com.omits.social_api.signal.model.SignalSource;
import com.omits.social_api.topic.dto.CreateTopicCommand;
import com.omits.social_api.topic.dto.UpdateTopicCommand;
import com.omits.social_api.topic.exception.DuplicateTopicException;
import com.omits.social_api.topic.exception.TopicNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TopicServiceTest {

    private final TopicRepository topicRepository = mock(TopicRepository.class);
    private final TopicService topicService = new TopicService(topicRepository);

    /** Every topic a repository hands back is persisted, so it always has an id. */
    private static Topic topic(String name, boolean enabled) {
        Topic topic = new Topic(name, enabled, Map.of());
        setId(topic, UUID.randomUUID());
        return topic;
    }

    private void stubNameFree() {
        when(topicRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.empty());
    }

    private Topic captureSaved() {
        var captor = org.mockito.ArgumentCaptor.forClass(Topic.class);
        verify(topicRepository).save(captor.capture());
        return captor.getValue();
    }

    // --- create ---------------------------------------------------------------

    @Test
    void createsAnEnabledTopicByDefault() {
        stubNameFree();
        when(topicRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        topicService.create(new CreateTopicCommand("Local-first", null, Map.of()));

        assertThat(captureSaved().isEnabled()).isTrue();
    }

    @Test
    void trimsTheNameSoLookalikeNamesCannotBothExist() {
        stubNameFree();
        when(topicRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        topicService.create(new CreateTopicCommand("  Runtimes  ", true, Map.of()));

        assertThat(captureSaved().getName()).isEqualTo("Runtimes");
    }

    @Test
    void storesPerSourceQueries() {
        stubNameFree();
        when(topicRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        topicService.create(new CreateTopicCommand("Rust", true, Map.of(
                SignalSource.DEVTO, "rust",
                SignalSource.GITHUB_TRENDING, "language:rust")));

        assertThat(captureSaved().getQueries())
                .containsEntry(SignalSource.DEVTO, "rust")
                .containsEntry(SignalSource.GITHUB_TRENDING, "language:rust")
                .doesNotContainKey(SignalSource.HACKER_NEWS);
    }

    /** The name check has to match the {@code lower(name)} unique index, or the database
     *  rejects what the service accepted. */
    @Test
    void rejectsADuplicateNameRegardlessOfCase() {
        when(topicRepository.findByNameIgnoreCase("rust")).thenReturn(Optional.of(topic("Rust", true)));

        assertThatThrownBy(() -> topicService.create(new CreateTopicCommand("rust", true, Map.of())))
                .isInstanceOf(DuplicateTopicException.class);
        verify(topicRepository, never()).save(any());
    }

    @Test
    void rejectsABlankName() {
        assertThatThrownBy(() -> topicService.create(new CreateTopicCommand("  ", true, Map.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
        verify(topicRepository, never()).save(any());
    }

    @Test
    void rejectsANameOverTheColumnLength() {
        assertThatThrownBy(() -> topicService.create(
                new CreateTopicCommand("x".repeat(Topic.MAX_NAME_LENGTH + 1), true, Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
        verify(topicRepository, never()).save(any());
    }

    /** A blank query would be sent upstream as a real query and match everything. */
    @Test
    void rejectsABlankQuery() {
        Map<SignalSource, String> queries = new HashMap<>();
        queries.put(SignalSource.DEVTO, "  ");

        assertThatThrownBy(() -> topicService.create(new CreateTopicCommand("Rust", true, queries)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DEVTO");
        verify(topicRepository, never()).save(any());
    }

    // --- update ---------------------------------------------------------------

    @Test
    void leavesUnsetFieldsAlone() {
        UUID id = UUID.randomUUID();
        Topic existing = new Topic("Runtimes", true, Map.of(SignalSource.DEVTO, "bun"));
        when(topicRepository.findByIdWithQueries(id)).thenReturn(Optional.of(existing));
        when(topicRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // The panel's enabled toggle sends nothing but the flag.
        topicService.update(id, new UpdateTopicCommand(null, false, null));

        Topic saved = captureSaved();
        assertThat(saved.isEnabled()).isFalse();
        assertThat(saved.getName()).isEqualTo("Runtimes");
        assertThat(saved.getQueries()).containsEntry(SignalSource.DEVTO, "bun");
    }

    @Test
    void replacesQueriesRatherThanMergingThem() {
        UUID id = UUID.randomUUID();
        Topic existing = new Topic("Runtimes", true, Map.of(
                SignalSource.DEVTO, "bun", SignalSource.HACKER_NEWS, "deno"));
        when(topicRepository.findByIdWithQueries(id)).thenReturn(Optional.of(existing));
        when(topicRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        topicService.update(id, new UpdateTopicCommand(null, null, Map.of(SignalSource.DEVTO, "node")));

        // Merging would make a source's query impossible to remove.
        assertThat(captureSaved().getQueries())
                .containsExactly(Map.entry(SignalSource.DEVTO, "node"));
    }

    @Test
    void clearsQueriesOnAnEmptyMap() {
        UUID id = UUID.randomUUID();
        Topic existing = new Topic("Runtimes", true, Map.of(SignalSource.DEVTO, "bun"));
        when(topicRepository.findByIdWithQueries(id)).thenReturn(Optional.of(existing));
        when(topicRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        topicService.update(id, new UpdateTopicCommand(null, null, Map.of()));

        assertThat(captureSaved().getQueries()).isEmpty();
    }

    /** Renaming a topic to the name it already has is not a conflict with itself. */
    @Test
    void allowsATopicToKeepItsOwnName() {
        UUID id = UUID.randomUUID();
        Topic existing = topic("Runtimes", true);
        setId(existing, id);
        when(topicRepository.findByIdWithQueries(id)).thenReturn(Optional.of(existing));
        when(topicRepository.findByNameIgnoreCase("Runtimes")).thenReturn(Optional.of(existing));
        when(topicRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        topicService.update(id, new UpdateTopicCommand("Runtimes", null, null));

        assertThat(captureSaved().getName()).isEqualTo("Runtimes");
    }

    @Test
    void rejectsRenamingOntoAnotherTopicsName() {
        UUID id = UUID.randomUUID();
        Topic existing = topic("Runtimes", true);
        setId(existing, id);
        Topic other = topic("Databases", true);
        when(topicRepository.findByIdWithQueries(id)).thenReturn(Optional.of(existing));
        when(topicRepository.findByNameIgnoreCase("Databases")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> topicService.update(id, new UpdateTopicCommand("Databases", null, null)))
                .isInstanceOf(DuplicateTopicException.class);
        verify(topicRepository, never()).save(any());
    }

    @Test
    void rejectsUpdatingATopicThatDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(topicRepository.findByIdWithQueries(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> topicService.update(id, new UpdateTopicCommand("x", null, null)))
                .isInstanceOf(TopicNotFoundException.class);
    }

    // --- read / delete --------------------------------------------------------

    @Test
    void listsEnabledTopicsForThePoller() {
        when(topicRepository.findEnabledWithQueries()).thenReturn(List.of(topic("Rust", true)));

        assertThat(topicService.listEnabled()).hasSize(1);
        verify(topicRepository).findEnabledWithQueries();
    }

    @Test
    void sortsTopicsByNameCaseInsensitively() {
        when(topicRepository.findAllWithQueries()).thenReturn(List.of(
                topic("runtimes", true), topic("Databases", true), topic("architecture", true)));

        assertThat(topicService.list()).extracting(Topic::getName)
                .containsExactly("architecture", "Databases", "runtimes");
    }

    @Test
    void rejectsDeletingATopicThatDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(topicRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> topicService.delete(id)).isInstanceOf(TopicNotFoundException.class);
        verify(topicRepository, never()).deleteById(any());
    }

    /** Ids are database-generated, so a unit test has to place one by hand. */
    private static void setId(Topic topic, UUID id) {
        try {
            var field = Topic.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(topic, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
