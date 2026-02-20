package proj.rengserver.background;

import danny.dbconn.*;

import java.util.*;

/**
 * database connection pool
 * make new connection when required
 * use previous connections when available
 *
 * @author Danny Wong
 */
public class PoolDBConnection implements AutoCloseable
{
    private static final HashMap<DBConnectionType, RengDBConnection> types = new HashMap<>();

    public static synchronized void flushConnections ()
    {
        for ( RengDBConnection dbConnection : types.values() )
        {
            dbConnection.flushConnection();
        }
    }

    final DBConnectionType type;
    final DBConnector db;

    public PoolDBConnection ( final DBConnectionType type, final String ip, final String function ) throws Exception
    {
        this.type = type;
        checkDatabase( this.type );
        db = types.get( type ).initConnection( ip, function );
    }

    private static synchronized void checkDatabase ( final DBConnectionType type ) throws Exception
    {
        switch ( type )
        {
            case MYSQL:
                if ( !types.containsKey( type ) ) types.put( type, new RengDB() );
                break;
            default:
                throw new Exception( "database type not supported" );
        }
    }

    public DBConnector getDb ()
    {
        return db;
    }

    public void close ()
    {
        types.get( type ).returnConnection( db );
    }
}
