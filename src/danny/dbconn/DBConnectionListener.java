package danny.dbconn;

/**
 * listenr the status of database connectivity
 *
 * @author Danny Wong
 */
public interface DBConnectionListener
{
    void dbConnectionStatusChanged ( DBConnectionEvent dbe );
}
