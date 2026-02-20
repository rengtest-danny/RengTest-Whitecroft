package danny.dbconn;

/**
 * Created by Danny Wong on 27/1/2015
 */
public class DBConnectionEvent
{
    /**
     *
     */
    private final DBConnector connector;
    private final boolean firstConnect, serverReachable;

    public DBConnectionEvent ( DBConnector connector, boolean firstConnect, boolean serverReachable )
    {
        this.connector = connector;
        this.firstConnect = firstConnect;
        this.serverReachable = serverReachable;
    }

    public DBConnector getConnector ()
    {
        return connector;
    }

    public boolean isFirstConnect ()
    {
        return firstConnect;
    }

    public boolean isServerReachable ()
    {
        return serverReachable;
    }
}
