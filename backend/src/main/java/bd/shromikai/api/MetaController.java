package bd.shromikai.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Basic system info for the frontend: confirms the backend and database are up
 * and shows how much law is loaded in the knowledge base.
 */
@RestController
@RequestMapping("/api/v1")
public class MetaController {

    private final JdbcTemplate jdbc;

    public MetaController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record CorpusStats(long acts, long sections, long currentSections, long embeddedSections) {}

    public record Meta(String name, String version, CorpusStats corpus) {}

    @GetMapping("/meta")
    public Meta meta() {
        Long acts = jdbc.queryForObject("select count(*) from law_act", Long.class);
        Long sections = jdbc.queryForObject("select count(*) from law_section", Long.class);
        Long current = jdbc.queryForObject("select count(*) from law_section where is_current", Long.class);
        Long embedded = jdbc.queryForObject("select count(*) from law_section where embedding is not null", Long.class);
        return new Meta("ShromikAI", "0.1.0", new CorpusStats(acts, sections, current, embedded));
    }
}
