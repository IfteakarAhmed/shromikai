package bd.shromikai.auth;

import bd.shromikai.shared.Hashing;
import bd.shromikai.shared.config.ShromikaiProperties;
import bd.shromikai.shared.problem.ProblemException;
import bd.shromikai.shared.problem.ProblemType;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Guest quotas (FR-09): analyses per device and per IP per day (days in {@code Asia/Dhaka}), and
 * concurrent analyses per device. Counters live in memory for P2 and move to {@code usage_counter}
 * with V6 in P7; signed-in users are not limited here. {@link #reserve} is what the analyses
 * endpoint calls before it starts work.
 */
@Service
public class GuestQuotaService {

  private final int perDevice;
  private final int perIp;
  private final int concurrency;
  private final ZoneId zone;
  private final Clock clock;

  private final Object lock = new Object();
  private LocalDate day;
  private final Map<String, Integer> deviceCounts = new HashMap<>();
  private final Map<String, Integer> ipCounts = new HashMap<>();
  private final Map<String, Integer> running = new HashMap<>();

  @Autowired
  GuestQuotaService(ShromikaiProperties properties, Clock clock) {
    this(
        properties.quotas().guestPerDevicePerDay(),
        properties.quotas().guestPerIpPerDay(),
        properties.quotas().guestConcurrency(),
        properties.quotas().timezone(),
        clock);
  }

  public GuestQuotaService(int perDevice, int perIp, int concurrency, ZoneId zone, Clock clock) {
    this.perDevice = perDevice;
    this.perIp = perIp;
    this.concurrency = concurrency;
    this.zone = zone;
    this.clock = clock;
  }

  /** An analysis slot; close it when the analysis ends. Closing twice is harmless. */
  public final class Reservation implements AutoCloseable {
    private final String device;
    private final AtomicBoolean open = new AtomicBoolean(true);

    private Reservation(String device) {
      this.device = device;
    }

    @Override
    public void close() {
      if (device != null && open.compareAndSet(true, false)) {
        synchronized (lock) {
          running.computeIfPresent(device, (key, n) -> n <= 1 ? null : n - 1);
        }
      }
    }
  }

  /**
   * Counts one analysis for a guest, or throws {@code quota-exceeded} (with the seconds until the
   * next midnight in Dhaka) or {@code concurrency-limit}. Does nothing for signed-in users.
   */
  public Reservation reserve(AuthenticatedSubject subject, String clientIp) {
    return reserve(subject, clientIp, 1);
  }

  /**
   * As {@link #reserve(AuthenticatedSubject, String)}, counting {@code weight} analyses against the
   * daily limits (a compare counts 2, FR-59) but occupying one concurrent slot.
   */
  public Reservation reserve(AuthenticatedSubject subject, String clientIp, int weight) {
    if (!subject.guest() || subject.deviceHash() == null) {
      return new Reservation(null);
    }
    String device = subject.deviceHash();
    String ip = Hashing.sha256Hex(clientIp);
    synchronized (lock) {
      ZonedDateTime now = ZonedDateTime.ofInstant(clock.instant(), zone);
      if (!now.toLocalDate().equals(day)) {
        day = now.toLocalDate();
        deviceCounts.clear();
        ipCounts.clear();
      }
      if (deviceCounts.getOrDefault(device, 0) + weight > perDevice
          || ipCounts.getOrDefault(ip, 0) + weight > perIp) {
        long retry =
            Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(zone)).toSeconds();
        throw new ProblemException(ProblemType.QUOTA_EXCEEDED, null, (int) Math.max(1, retry));
      }
      if (running.getOrDefault(device, 0) >= concurrency) {
        throw new ProblemException(ProblemType.CONCURRENCY_LIMIT, null, 5);
      }
      deviceCounts.merge(device, weight, Integer::sum);
      ipCounts.merge(ip, weight, Integer::sum);
      running.merge(device, 1, Integer::sum);
    }
    return new Reservation(device);
  }
}
