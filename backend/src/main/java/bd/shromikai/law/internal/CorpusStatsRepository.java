package bd.shromikai.law.internal;

import bd.shromikai.law.CorpusStats;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public final class CorpusStatsRepository {

  private final JdbcClient jdbc;

  public CorpusStatsRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public CorpusStats load() {
    return jdbc.sql(
            """
            select (select count(*) from law_act) as acts,
                   count(*) as sections,
                   count(*) filter (where is_current) as current_sections,
                   count(*) filter (where embedding is not null) as embedded_sections
              from law_section
            """)
        .query(
            (rs, row) ->
                new CorpusStats(
                    null,
                    rs.getLong("acts"),
                    rs.getLong("sections"),
                    rs.getLong("current_sections"),
                    rs.getLong("embedded_sections"),
                    null))
        .single();
  }
}
