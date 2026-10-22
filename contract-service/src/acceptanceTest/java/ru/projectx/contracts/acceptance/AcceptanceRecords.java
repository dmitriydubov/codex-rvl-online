package ru.projectx.contracts.acceptance;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class AcceptanceRecords {

    private static final String ACCEPTANCES = """
            select contract_version, accepted_by, accepted_at, channel::text as channel, host(ip_address) as ip_address,
                   user_agent, created_at
              from contract_acceptances where contract_id = :contract
            """;

    private final JdbcClient jdbc;

    public AcceptanceRecords(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record Acceptance(int contractVersion, UUID acceptedBy, OffsetDateTime acceptedAt, String channel,
            @Nullable String ipAddress, @Nullable String userAgent, OffsetDateTime createdAt) {
    }

    List<Acceptance> of(UUID contractId) {
        return jdbc.sql(ACCEPTANCES).param("contract", contractId).query(this::acceptance).list();
    }

    private Acceptance acceptance(ResultSet row, int index) throws SQLException {
        return new Acceptance(row.getInt("contract_version"), row.getObject("accepted_by", UUID.class),
                row.getObject("accepted_at", OffsetDateTime.class), row.getString("channel"), row.getString("ip_address"),
                row.getString("user_agent"), row.getObject("created_at", OffsetDateTime.class));
    }
}
