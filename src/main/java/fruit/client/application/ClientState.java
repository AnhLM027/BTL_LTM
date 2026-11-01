package fruit.client.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client session and server snapshots; mutated on the UI dispatcher. Contains no controls or
 * rendering.
 */
public final class ClientState {
    public String screen = "login";
    public String host = "localhost";
    public String password = "";
    public String username = "";
    public int port = 5555;
    public int direction;
    public boolean connected;
    public boolean connecting;
    public boolean registering;
    public boolean queued;
    public boolean snapshotMode;
    public boolean authPending;
    public Map<String, Object> user = new LinkedHashMap<>();
    public Map<String, Object> state = new LinkedHashMap<>();
    public Map<String, Object> result = new LinkedHashMap<>();
    public String rankingQuery = "";
    public List<Map<String, Object>> ranking = new ArrayList<>();
    public List<Map<String, Object>> history = new ArrayList<>();
    public List<Map<String, Object>> onlinePlayers = new ArrayList<>();
    public String onlineQuery = "";
    public List<Map<String, Object>> adminUsers = new ArrayList<>();
    public List<Map<String, Object>> adminMatches = new ArrayList<>();
    public Map<String, Object> adminStats = new LinkedHashMap<>();
    public int adminUserOffset;
    public int adminMatchOffset;
    public String adminUserQuery = "", adminMatchQuery = "";
    public int adminUserCount, adminMatchCount;
    public boolean moreAdminUsers;
    public boolean moreAdminMatches;
    public long queueNanos;
    public String pendingPassword = "";
}
