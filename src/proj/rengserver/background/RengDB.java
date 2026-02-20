package proj.rengserver.background;

import danny.dbconn.*;

import javax.swing.*;
import java.util.*;

/**
 * database connection pool
 * make new connection when required
 * use previous connections when available
 *
 * @author Danny Wong
 */
public class RengDB implements RengDBConnection
{
    private ArrayList<DBConnector> pool = new ArrayList<>();
    private HashMap<DBConnector, String> useBy = new HashMap<>();

    private static boolean dbCheck = false;

    public synchronized DBConnector initConnection ( final String forIP, final String function ) throws Exception
    {
        //search for available db connection
        for ( DBConnector dbc : pool )
        {
            if ( !useBy.containsKey(dbc) )
            {
                //available got, set using
                useBy.put(dbc, forIP + ":" + function);
                //use this db connection
                return dbc;
            }
        }

        try
        {
            final DBConnector db = new DBConnector(DBConnectionType.MYSQL, "127.0.0.1:3306/reng?user=root&password=Wh!tecr0ft&useUnicode=true&characterEncoding=utf-8&autoReconnect=true&useSSL=false");
            final int index = pool.size() + 1;
            db.setTarget("127.0.0.1");
            db.addDBConnectionListener(new DBConnectionListener()
            {
                //if this event not received check if icmp and jna library imported, or difference jna library imported
                public void dbConnectionStatusChanged ( final DBConnectionEvent dbe )
                {
                    if ( dbe.isServerReachable() )
                    {
                        print(forIP, "connected RENG-" + index);
                        if ( !dbCheck )
                        {
                            try
                            {
                                dbCheck = true;
                                RengDBAutoGen.checkDB();
                            }
                            catch ( Exception e )
                            {
                                e.printStackTrace();
                                dbCheck = false;
                            }
                        }
                    }
                    else
                    {
                        print(forIP, "disconnected RENG-" + index);
                        //remove from pool?
                        removeConnection(db);
                    }
                }
            });
            db.connect();
            pool.add(db);
            useBy.put(db, forIP + ":" + function);
            return db;
        }
        catch ( Exception e )
        {
            print(forIP, "init error : " + e.getMessage());
        }
        throw new Exception("can't connect to RENG database");
    }

    public synchronized void removeConnection ( final DBConnector db )
    {
        if ( null == db ) return;
        db.close();
        pool.remove(db);
        useBy.remove(db);
    }

    //if not return the used database connection, unlimited connection object will be made!!!
    public synchronized void returnConnection ( final DBConnector db )
    {
        if ( null == db ) return;
        useBy.remove(db);
    }

    public synchronized void flushConnection ()
    {
        final ArrayList<DBConnector> oldPool = new ArrayList<>();
        for ( int i = pool.size() - 1 ; i >= 0 ; i-- ) { oldPool.add(pool.remove(i)); }
        pool.clear();
        pool.trimToSize();
        useBy.clear();
        SwingUtilities.invokeLater(new Runnable()
        {
            public void run ()
            {
                for ( DBConnector dbc : oldPool ) { dbc.close(); }
                oldPool.clear();
                oldPool.trimToSize();
            }
        });
    }

    private static void print ( final String forIP, String message )
    {
        System.out.println("[" + forIP + "] " + message);
    }
}
