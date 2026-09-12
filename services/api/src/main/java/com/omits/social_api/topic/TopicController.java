package com.omits.social_api.topic;

import com.omits.social_api.topic.dto.CreateTopicCommand;
import com.omits.social_api.topic.dto.TopicQueriesResponse;
import com.omits.social_api.topic.dto.TopicResponse;
import com.omits.social_api.topic.dto.UpdateTopicCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/topics")
@RequiredArgsConstructor
public class TopicController {

    private final TopicService topicService;

    @GetMapping
    public List<TopicResponse> list() {
        return topicService.list().stream().map(TopicResponse::from).toList();
    }

    /**
     * The Go poller's work list. Declared before {@code /{id}} for readability only — Spring
     * matches the literal path ahead of the variable one regardless, and "queries" is not a
     * UUID in any case.
     */
    @GetMapping("/queries")
    public List<TopicQueriesResponse> queries() {
        return topicService.listEnabled().stream().map(TopicQueriesResponse::from).toList();
    }

    @GetMapping("/{id}")
    public TopicResponse get(@PathVariable UUID id) {
        return TopicResponse.from(topicService.get(id));
    }

    @PostMapping
    public ResponseEntity<TopicResponse> create(@RequestBody CreateTopicCommand command) {
        Topic topic = topicService.create(command);
        return ResponseEntity.created(URI.create("/topics/" + topic.getId()))
                .body(TopicResponse.from(topic));
    }

    // A sparse patch — see UpdateTopicCommand. A null field is left unchanged.
    @PatchMapping("/{id}")
    public TopicResponse update(@PathVariable UUID id, @RequestBody UpdateTopicCommand command) {
        return TopicResponse.from(topicService.update(id, command));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        topicService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
