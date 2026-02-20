package danny.dbconn;

import danny.util.IcmpPing;

import java.net.*;
import java.sql.*;

/**
 * MySQLConnectionChecker
 *
 * @author Danny Wong
 */
public class MySQLConnectionChecker extends DBConnectionChecker
{
    public MySQLConnectionChecker ( DBConnector dbc )
    {
        super( dbc );
    }

    public void runTask ()
    {
        while ( dbc.started )
        {
            try
            {
                Thread.currentThread().setName( nameInit + "test ping" );
                if ( null != dbc.connectTarget )
                {
                    boolean pingResult = false;
                    try
                    {
                        pingResult = IcmpPing.ping( dbc.connectTarget, 10000 );
                    }
                    catch ( Exception e )
                    {
                        pingResult = InetAddress.getByName( dbc.connectTarget ).isReachable( 10000 );
                    }

                    if ( pingResult )
                    {
                        Thread.currentThread().setName( nameInit + "reachable" );
                        if ( !lastResult )
                        {
                            if ( !firstCheck )
                            {
                                //try to connect if last time failed
                                dbc.connect( true );
                                //connectable
                                Thread.currentThread().setName( nameInit + "connectable" );
                                dbc.notifyDBConnectionStatusChanged( firstCheck, lastResult );
                            }
                            else
                            {
                                //first check do nothing
                                dbc.notifyDBConnectionStatusChanged( firstCheck, lastResult );
                                firstCheck = false;
                            }
                        }
                        else
                        {
                            //try to create statement
                            try ( Connection testConn = DriverManager.getConnection( dbc.connectionString ) )
                            {
                                //ok - cut the connection test
                                testConn.close();
                            }

                            //notice
                            if ( firstCheck )
                            {
                                dbc.notifyDBConnectionStatusChanged( firstCheck, lastResult );
                                firstCheck = false;
                            }

                            //do something to maintain the connection
                            try ( ResultSet rs = dbc.querySQL( "SELECT CURRENT_TIMESTAMP() AS serverTime", false, false, false ) )
                            {
                                rs.next();
                                dbc.closeResultSet( rs );
                            }
                            Thread.currentThread().setName( nameInit + "select OK" );
                        }
                        //sleep for a while if reachable
                        Thread.sleep( 9000 );
                    }
                    else
                    {
                        //report server not reachable
                        throw new DBConnectionException( "ping timeout" );
                    }
                }
                else
                {
                    //sleep for a while if no target
                    Thread.sleep( 9000 );
                }
            }
            catch ( Exception e )
            {
                Thread.currentThread().setName( nameInit + e.getLocalizedMessage() );
                if ( lastResult || firstCheck )
                {
                    try { dbc.disconnect(); }
                    catch ( Exception ee ) {}
                    lastResult = false;
                    dbc.notifyDBConnectionStatusChanged( firstCheck, false );
                    firstCheck = false;
                }

                if ( !"ping timeout".equals( e.getLocalizedMessage() ) )
                {
                    try { Thread.sleep( 5000 ); }
                    catch ( InterruptedException ex ) {}
                }
            }
        }
    }
}












