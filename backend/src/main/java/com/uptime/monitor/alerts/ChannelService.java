package com.uptime.monitor.alerts;

import com.uptime.monitor.common.BadRequestException;
import com.uptime.monitor.common.ConflictException;
import com.uptime.monitor.common.NotFoundException;
import com.uptime.monitor.common.TooManyRequestsException;
import com.uptime.monitor.config.UptimeProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ChannelService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern TELEGRAM_CHAT_ID = Pattern.compile("^-?\\d{1,20}$");

    private final NotificationChannelRepository channels;
    private final AlertOutbox outbox;
    private final JdbcClient jdbc;
    private final int maxPerUser;

    public ChannelService(NotificationChannelRepository channels, AlertOutbox outbox, JdbcClient jdbc, UptimeProperties props) {
        this.channels = channels;
        this.outbox = outbox;
        this.jdbc = jdbc;
        this.maxPerUser = props.alerts().maxChannelsPerUser();
    }

    @Transactional(readOnly = true)
    public List<ChannelResponse> list(long ownerId) {
        Map<Long, ChannelResponse.LastDelivery> last = lastDeliveries(ownerId);
        return channels.findByOwnerIdOrderByIdAsc(ownerId).stream()
                .map(c -> new ChannelResponse(c.getId(), c.getType(), c.getTarget(), last.get(c.getId())))
                .toList();
    }

    @Transactional
    public ChannelResponse add(long ownerId, ChannelRequest req) {
        String target = req.type() == ChannelType.EMAIL ? req.target().toLowerCase() : req.target();
        if (req.type() == ChannelType.EMAIL && !EMAIL.matcher(target).matches()) {
            throw new BadRequestException("Enter an email address like name@example.com.");
        }
        if (req.type() == ChannelType.TELEGRAM && !TELEGRAM_CHAT_ID.matcher(target).matches()) {
            throw new BadRequestException("A Telegram chat id is a number, like 123456789.");
        }
        if (channels.countByOwnerId(ownerId) >= maxPerUser) {
            throw new BadRequestException("You can have up to " + maxPerUser + " alert channels.");
        }
        if (channels.existsByOwnerIdAndTypeAndTarget(ownerId, req.type(), target)) {
            throw new ConflictException("You already get alerts there.");
        }
        NotificationChannel saved = channels.save(new NotificationChannel(ownerId, req.type(), target));
        return new ChannelResponse(saved.getId(), saved.getType(), saved.getTarget(), null);
    }

    @Transactional
    public void delete(long ownerId, long id) {
        channels.delete(find(ownerId, id));
    }

    /** One test per channel per minute: a channel can point at any address, so tests mustn't become a spam tool. */
    @Transactional
    public void sendTest(long ownerId, long id) {
        NotificationChannel channel = find(ownerId, id);
        Integer recent = jdbc.sql("""
                        SELECT COUNT(*) FROM notification_outbox
                        WHERE channel_id = :channel AND kind = 'TEST' AND created_at > :since""")
                .param("channel", channel.getId())
                .param("since", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1))
                .query(Integer.class)
                .single();
        if (recent > 0) {
            throw new TooManyRequestsException("You just sent a test to this channel. Try again in a minute.", 60);
        }
        outbox.enqueueTest(channel.getId());
    }

    private NotificationChannel find(long ownerId, long id) {
        return channels.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new NotFoundException("Alert channel " + id + " not found"));
    }

    private Map<Long, ChannelResponse.LastDelivery> lastDeliveries(long ownerId) {
        return jdbc.sql("""
                        SELECT o.channel_id, o.subject, o.status, COALESCE(o.sent_at, o.created_at) AS at, o.last_error
                        FROM notification_outbox o
                        JOIN notification_channels c ON c.id = o.channel_id
                        WHERE c.owner_id = :owner
                          AND o.id = (SELECT MAX(o2.id) FROM notification_outbox o2 WHERE o2.channel_id = o.channel_id)""")
                .param("owner", ownerId)
                .query((rs, row) -> Map.entry(rs.getLong("channel_id"), new ChannelResponse.LastDelivery(
                        rs.getString("subject"), rs.getString("status"),
                        rs.getObject("at", LocalDateTime.class).toInstant(ZoneOffset.UTC), rs.getString("last_error"))))
                .list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
