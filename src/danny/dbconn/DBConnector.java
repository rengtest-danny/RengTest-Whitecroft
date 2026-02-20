package danny.dbconn;

import danny.util.TaskScheduler;

import java.io.*;
import java.net.*;
import java.util.*;
import java.sql.*;

/**
 * redirect sql commands and resultset
 *
 * @author Danny Wong
 */
public class DBConnector
{
    protected final DBConnectionType type;

    protected String connectTarget;

    private Connection conn;
    boolean started = false;
    protected transient String connectionString = "";
    protected transient String connectionUsername = null;
    protected transient String connectionPassword = null;

    //for watches
    private String lastQuerySql = "";
    private String lastUpdateSql = "";
    protected final ArrayList<PreparedStatementHistory> psHist = new ArrayList<PreparedStatementHistory>();

    /**
     * listeners
     */
    protected final ArrayList<DBConnectionListener> listeners = new ArrayList<DBConnectionListener>();

    protected DBConnectionChecker thread;

    /**
     * Constructor for objects of class DBConnector
     * <p>
     * MYSQL connectionString : 192.168.200.93:3306/defaultSchema?user=username&password=password&useUnicode=true&characterEncoding=utf-8
     * SQLite connectionString : <filepath>.db
     * SQLServer connectionString : localhost:1433;databaseName=AdventureWorks;integratedSecurity=true;
     */
    public DBConnector ( DBConnectionType type, String connectionString )
    {
        this.type = type;
        switch ( type )
        {
            case MYSQL:
                this.connectionString = "jdbc:mysql://" + connectionString;
                break;

            case SQLITE:
                this.connectionString = "jdbc:sqlite:" + connectionString;
                break;
        }
    }

    /**
     * Constructor for objects of class DBConnector
     *
     * @param dbConfigFileName the name of DBConnectorConfig file, name only e.g. "mysql.xml" = File("config/mysql.xml")
     */
//    public DBConnector ( String dbConfigFileName ) throws DBConnectionException, IOException
//    {
//        this( new File( "config", dbConfigFileName ) );
//    }

    /**
     * Constructor for objects of class DBConnector
     */
//    public DBConnector ( File configFile ) throws DBConnectionException, IOException
//    {
//        XmlConfig xmlConfig = new XmlConfig( configFile , "DBConnectorConfig" );
//        this.type = DBConnectionType.getEnum( xmlConfig.get( "Type" ) );
//        if ( null == this.type ) throw new DBConnectionException( "Connection type not defined." );
//        switch ( type )
//        {
//            case MYSQL:
//                connectTarget = xmlConfig.get( "Param" , "Host" );
//                int remotePort;
//                String mysqlUsername = "root";
//                String mysqlPassword = "";
//                String mysqlSchema = "";
//                String mysqlExtend = "";
//                try { remotePort = Integer.parseInt( xmlConfig.get( "Param" , "Port" ) ); } catch ( Exception e ) { throw new DBConnectionException( "MySQL connection config using a invalid port" ); }
//                mysqlSchema = xmlConfig.get( "Param" , "Schema" );
//                mysqlUsername = xmlConfig.get( "Param" , "User" );
//                mysqlPassword = xmlConfig.get( "Param" , "Password" );
//                //accept old config file
//                try { mysqlExtend = xmlConfig.get( "Param" , "Extend" ); }
//                catch ( Exception e ) { mysqlExtend = "&useUnicode=true&characterEncoding=utf-8"; }
//                connectionString = "jdbc:mysql://" + connectTarget + ":" + remotePort + "/" + mysqlSchema + "?user=" + mysqlUsername + "&password=" + mysqlPassword + mysqlExtend;
//                debug = new Debug( DebugItem.DATABASE , type.getTag() + "@" + connectTarget );
//                debug.log( 2 , "Host:" + connectTarget + " port:" + remotePort + " schema:" + mysqlSchema + " user:" + mysqlUsername + ( "".equals( mysqlPassword ) ? "" : " password:" + StringTool.getMaskedString( mysqlPassword ) ) + " ext:" + mysqlExtend );
//                break;
//
//            case SQL_SERVER:
//                connectTarget = xmlConfig.get( "Param" , "Host" );
//                int sqlServerPort;
//                String sqlServerUsername = "root";
//                String sqlServerPassword = "";
//                String sqlServerSchema = "";
//                String sqlServerExtend = "";
//                try { sqlServerPort = Integer.parseInt( xmlConfig.get( "Param" , "Port" ) ); } catch ( Exception e ) { throw new DBConnectionException( "SQL Server connection config using a invalid port" ); }
//                sqlServerSchema = xmlConfig.get( "Param" , "DatabaseName" );
//                sqlServerUsername = xmlConfig.get( "Param" , "User" );
//                sqlServerPassword = xmlConfig.get( "Param" , "Password" );
//                //accept old config file
//                try { sqlServerExtend = xmlConfig.get( "Param" , "Extend" ); }
//                catch ( Exception e ) { }
//                connectionString = "jdbc:sqlserver://" + connectTarget + ":" + sqlServerPort + ";databaseName=" + sqlServerSchema + ";user=" + sqlServerUsername + ";password=" + sqlServerPassword + ";" + sqlServerExtend;
//                debug = new Debug( DebugItem.DATABASE , type.getTag() + "@" + connectTarget );
//                debug.log( 2 , "Host:" + connectTarget + " port:" + sqlServerPort + " schema:" + sqlServerSchema + " user:" + sqlServerUsername + ( "".equals( sqlServerPassword ) ? "" : " password:" + StringTool.getMaskedString( sqlServerPassword ) ) + " ext:" + sqlServerExtend );
//                break;
//
//            case SQLITE:
//                connectTarget = xmlConfig.get( "Param" , "DBFile" );
//                if ( null == connectTarget || "".equals( connectTarget ) || !connectTarget.toLowerCase().endsWith( ".db" ) ) throw new DBConnectionException( "Invalid Sqlite db path." );
//                connectionString = "jdbc:sqlite:" + connectTarget;
//                debug = new Debug( DebugItem.DATABASE , type.getTag() );
//                debug.log( 2 , "File : " + connectTarget );
//                break;
//
//            case ODBC:
//                connectTarget = xmlConfig.get( "Param" , "DataSource" );
//                String odbcSchema = "";
//                odbcSchema = xmlConfig.get( "Param" , "Schema" );
//                connectionUsername = xmlConfig.get( "Param" , "User" );
//                connectionPassword = xmlConfig.get( "Param" , "Password" );
//                connectionString = "jdbc:odbc:" + connectTarget;
//                debug = new Debug( DebugItem.DATABASE , type.getTag() + "@" + connectTarget );
//                debug.log( 2 , "DataSource:" + connectTarget + " schema:" + odbcSchema + ( null == connectionUsername ? "" : " user:" + connectionUsername ) + ( null == connectionPassword ? "" : " password:" + StringTool.getMaskedString( connectionPassword ) ) );
//                break;
//
//            default:
//                throw new DBConnectionException( "Connection type not defined." );
//        }
//    }
    public DBConnectionType getType () { return type; }


    public void setConnectionStatus ( boolean connectable )
    {
        if ( null == thread ) return;
        thread.lastResult = connectable;
    }

    public boolean getConnectionStatus ()
    {
        if ( null == thread ) return false;
        return thread.lastResult;
    }

    public boolean isStarted () { return started; }

    /**
     * add a listener to the connection
     */
    public void addDBConnectionListener ( DBConnectionListener listener )
    {
        if ( !listeners.contains( listener ) ) listeners.add( listener );
    }

    /**
     * remove a listener from the connection
     */
    public void removeDBConnectionListener ( DBConnectionListener listener )
    {
        if ( listeners.contains( listener ) )
        {
            listeners.remove( listener );
            listeners.trimToSize();
        }
    }

    /**
     * fire a db connection change event
     */
    protected void notifyDBConnectionStatusChanged ( final boolean firstConnect, final boolean connectable )
    {
        final DBConnectionEvent event = new DBConnectionEvent( this, firstConnect, connectable );
        for ( final DBConnectionListener listener : listeners )
        {
            new Thread( "DBConn Listener Thread" )
            {
                public void run ()
                {
                    listener.dbConnectionStatusChanged( event );
                }
            }.start();
        }
    }

    /**
     *
     */
    public void close ()
    {

        try
        {
            started = false;

            disconnect();
            //close checking thread
            if ( null != thread )
            {
                thread.cancel();
                thread = null;
            }
        }
        catch ( DBConnectionException dbce ) { dbce.printStackTrace(); }
    }

    /**
     * disconnecion the current conneciton if any
     */
    public void disconnect () throws DBConnectionException
    {
        try
        {
            if ( null != conn && !conn.isClosed() )
            {
                conn.close();
                conn = null;
            }
        }
        catch ( Exception e )
        {
            throw new DBConnectionException( e );
        }
    }

    /**
     * check if the database target exists
     */
    public boolean exists ()
    {
        switch ( type )
        {
            case MYSQL:
                try
                {
                    if ( null == connectTarget ) return false;
                    else return InetAddress.getByName( connectTarget ).isReachable( 10000 );
                }
                catch ( Exception e ) { return false; }

            case SQLITE:
                return new File( connectTarget ).exists();

            default:
                return false;
        }
    }

    public void setTarget ( String connectTarget )
    {
        this.connectTarget = connectTarget;
    }

    /**
     * get the target
     */
    public String getTarget ()
    {
        switch ( type )
        {
            case MYSQL:
            case SQLITE:
                return connectTarget;
            default:
                return null;
        }
    }

    public boolean connect () throws DBConnectionException
    {
        return connect( false );
    }

    protected boolean connect ( boolean reconnect ) throws DBConnectionException
    {
        //close previous connection
        if ( reconnect ) disconnect();
        try
        {
            switch ( type )
            {
                case MYSQL:
                    if ( null == thread )
                    {
                        //make connection
                        Class.forName( "com.mysql.jdbc.Driver" ).newInstance();
                        conn = DriverManager.getConnection( connectionString );
                        started = true;
                        thread = new MySQLConnectionChecker( this );
                        thread.lastResult = true;
                        TaskScheduler.commonScheduler().schedule( thread, 1000 );
                    }
                    else
                    {
                        conn = DriverManager.getConnection( connectionString );
                        thread.lastResult = true;
                    }
                    return true;

                case SQLITE:
                    Class.forName( "org.sqlite.JDBC" ).newInstance();
                    conn = DriverManager.getConnection( connectionString );
                    started = true;
                    notifyDBConnectionStatusChanged( true, true );
                    return true;
            }
        }
        catch ( Exception e )
        {
            switch ( type )
            {
                case MYSQL:
                    if ( null == thread )
                    {
                        started = true;
                        thread = new MySQLConnectionChecker( this );
                        TaskScheduler.commonScheduler().schedule( thread, 1000 );
                    }
                    break;
            }
            throw new DBConnectionException( e );
        }
        return false;
    }

    public int updateSQL ( String sql ) throws DBConnectionException
    {
        if ( null == conn ) throw new DBConnectionException( "connection not ready" );
        lastUpdateSql = sql;
        try ( Statement st = conn.createStatement() )
        {
            int rowUpdated = st.executeUpdate( sql );
            return rowUpdated;
        }
        catch ( com.mysql.jdbc.CommunicationsException comme )
        {
            connect( true );

            try ( Statement st = conn.createStatement() )
            {
                int rowUpdated = st.executeUpdate( sql );
                return rowUpdated;
            }
            catch ( Exception e )
            {
                logFail( sql, e );
                throw new DBConnectionException( e );
            }
        }
        catch ( Exception e )
        {
            logFail( sql, e );
            throw new DBConnectionException( e );
        }
        //use auto closeable 20200912
//        finally
//        {
//            if ( null != st )
//            {
//                try { st.close(); }
//                catch ( Exception e ) { debug.log ( 4 , "Update" , e.getMessage() ); }
//            }
//        }
    }

    public int updateSQL ( PreparedStatement ps ) throws DBConnectionException
    {
        if ( null == conn ) throw new DBConnectionException( "connection not ready" );
        final String sql = getPreparedStatementHistory( ps );
        lastUpdateSql = sql;
        try
        {
            int rowUpdated = ps.executeUpdate();
            return rowUpdated;
        }
        catch ( com.mysql.jdbc.CommunicationsException comme )
        {
            try
            {
                connect( true );
                int rowUpdated = ps.executeUpdate();
                return rowUpdated;
            }
            catch ( Exception e )
            {
                logFail( sql, e );
                throw new DBConnectionException( e );
            }
        }
        catch ( Exception e )
        {
            logFail( sql, e );
            throw new DBConnectionException( e );
        }
        finally
        {
            if ( null != ps )
            {
                try { ps.close(); }
                catch ( Exception e ) {}
            }
        }
    }

    private void logFail ( String sql, Exception e )
    {
        try
        {
//            FileOperation.writeFile( new File( "db", "DBFail.stack" ), "Update SQL : " + sql + "\r\nException : " + e.getMessage() + "\r\n\r\n", true );
        }
        catch ( Exception ex )
        {

        }
    }

    public ResultSet querySQL ( String sql ) throws DBConnectionException
    {
        return querySQL( sql, true, true, true );
    }

    public ResultSet querySQL ( String sql, boolean logSql ) throws DBConnectionException
    {
        return querySQL( sql, logSql, true, true );
    }

    public ResultSet querySQL ( String sql, boolean logSql, boolean showWatch ) throws DBConnectionException
    {
        return querySQL( sql, logSql, showWatch, true );
    }

    ResultSet querySQL ( String sql, boolean logSql, boolean showWatch, boolean incCount ) throws DBConnectionException
    {
        if ( null == conn ) throw new DBConnectionException( "connection not ready" );
        if ( showWatch ) lastQuerySql = sql;
        Statement st = null;
        try
        {
            st = conn.createStatement();
            return new DBResultSet( st.executeQuery( sql ) );
        }
        catch ( com.mysql.jdbc.CommunicationsException comme )
        {
            try
            {
                connect( true );
                st = conn.createStatement();
                return new DBResultSet( st.executeQuery( sql ) );
            }
            catch ( Exception e )
            {
                if ( null != st )
                {
                    try { st.close(); }
                    catch ( Exception ee ) {}
                }
                throw new DBConnectionException( e );
            }
        }
        catch ( Exception e )
        {
            if ( null != st )
            {
                try { st.close(); }
                catch ( Exception ee ) {}
            }
            throw new DBConnectionException( e );
        }
    }

    public ResultSet querySQL ( PreparedStatement ps ) throws DBConnectionException
    {
        return querySQL( ps, true );
    }

    public ResultSet querySQL ( PreparedStatement ps, boolean showWatch ) throws DBConnectionException
    {
        final String sql = getPreparedStatementHistory( ps );
        if ( showWatch ) lastQuerySql = sql;
        try
        {
            return new DBResultSet( ps.executeQuery() );
        }
        catch ( com.mysql.jdbc.CommunicationsException comme )
        {
            try
            {
                connect( true );
                return new DBResultSet( ps.executeQuery() );
            }
            catch ( Exception e )
            {
                throw new DBConnectionException( e );
            }
        }
        catch ( Exception e )
        {
            throw new DBConnectionException( e );
        }
    }


    public PreparedStatement prepareStatement ( String sql ) throws Exception
    {
        PreparedStatement ps = conn.prepareStatement( sql );
        if ( null == ps )
        {
            throw new SQLException();
        }
        addPreparedStatementHistory( ps, sql );
        return ps;
    }

    public int createTable ( String tableName, ArrayList<String> columnDefinition ) throws Exception, DBConnectionException
    {
        return createTable( tableName, columnDefinition, "" );
    }

    public int createTable ( String tableName, ArrayList<String> columnDefinition, String additional ) throws Exception, DBConnectionException
    {
        String sql = "CREATE TABLE " + tableName + " ( ";
        for ( String column : columnDefinition ) { sql += column + ", "; }
        sql = sql.substring( 0, sql.length() - 2 );
        sql += " ) " + additional + ";";
        return updateSQL( sql );
    }

    public static void closeResultSet ( ResultSet rs )
    {
        if ( null == rs ) return;
        try { rs.getStatement().close(); }
        catch ( Exception e ) {}
        try { rs.close(); }
        catch ( Exception e ) {}
        rs = null;
    }

    private void addPreparedStatementHistory ( PreparedStatement ps, String sql )
    {
        synchronized ( psHist )
        {
            while ( psHist.size() > 20 ) { psHist.remove( 0 ); }
            psHist.add( new PreparedStatementHistory( ps, sql ) );
        }
    }

    private String getPreparedStatementHistory ( PreparedStatement ps )
    {
        try
        {
            if ( ps.toString().equals( ps.getClass().getName() + "@" + Integer.toHexString( ps.hashCode() ) ) )
                return ps.toString().substring( ps.toString().indexOf( ": " ) + 2 );
        }
        catch ( Exception e ) {}
        synchronized ( psHist )
        {
            for ( PreparedStatementHistory hist : psHist ) { if ( hist.ps == ps ) return hist.sql; }
        }
        return "Not available";
    }

    private class PreparedStatementHistory
    {
        final PreparedStatement ps;
        final String sql;

        public PreparedStatementHistory ( PreparedStatement ps, String sql )
        {
            this.ps = ps;
            this.sql = sql;
        }
    }


//    public static void createMySQLConfig ( File file, String ip, int port, String schema, String username, String password ) throws IOException
//    {
//        String config = "";
//        config += Xml.add( DBConnectionType.MYSQL.toString(), "Type", 1, 1 );
//        String param = "";
//        param += Xml.add( ip, "Host", 1, 2 );
//        param += Xml.add( "" + port, "Port", 1, 2 );
//        param += Xml.add( schema, "Schema", 1, 2 );
//        param += Xml.add( username, "User", 1, 2 );
//        param += Xml.add( password, "Password", 1, 2 );
//        param += Xml.add( "&useUnicode=true&characterEncoding=utf-8", "Extend", 1, 2, 1, 1 );
//        config += Xml.add( param, "Param", 1, 1, 1, 0 );
//        config = Xml.add( config, "DBConnectorConfig" );
//        FileOperation.writeFile( file, config );
//    }
//
//    public static void createMySQLConfig ( String filename, String ip, int port, String schema, String username, String password ) throws IOException
//    {
//        String config = "";
//        config += Xml.add( DBConnectionType.MYSQL.toString(), "Type", 1, 1 );
//        String param = "";
//        param += Xml.add( ip, "Host", 1, 2 );
//        param += Xml.add( "" + port, "Port", 1, 2 );
//        param += Xml.add( schema, "Schema", 1, 2 );
//        param += Xml.add( username, "User", 1, 2 );
//        param += Xml.add( password, "Password", 1, 2 );
//        param += Xml.add( "&useUnicode=true&characterEncoding=utf-8", "Extend", 1, 2, 1, 1 );
//        config += Xml.add( param, "Param", 1, 1, 1, 0 );
//        config = Xml.add( config, "DBConnectorConfig" );
//        FileOperation.writeFile( new File( "config/" + filename ), config );
//    }
//
//    public static void createOdbcConfig ( String filename, String dataSource, String schema ) throws IOException
//    {
//        String config = "";
//        config += Xml.add( DBConnectionType.ODBC.toString(), "Type", 1, 1 );
//        String param = "";
//        param += Xml.add( dataSource, "DataSource", 1, 2 );
//        param += Xml.add( schema, "Schema", 1, 2, 1, 1 );
//        config += Xml.add( param, "Param", 1, 1, 1, 0 );
//        config = Xml.add( config, "DBConnectorConfig" );
//        FileOperation.writeFile( new File( "config/" + filename ), config );
//    }
//
//    public static void createOdbcConfig ( String filename, String dataSource, String schema, String username, String password ) throws IOException
//    {
//        String config = "";
//        config += Xml.add( DBConnectionType.ODBC.toString(), "Type", 1, 1 );
//        String param = "";
//        param += Xml.add( dataSource, "DataSource", 1, 2 );
//        param += Xml.add( schema, "Schema", 1, 2 );
//        param += Xml.add( username, "User", 1, 2 );
//        param += Xml.add( password, "Password", 1, 2, 1, 1 );
//        config += Xml.add( param, "Param", 1, 1, 1, 0 );
//        config = Xml.add( config, "DBConnectorConfig" );
//        FileOperation.writeFile( new File( "config/" + filename ), config );
//    }
//
//    public static void createSQLServerConfig ( String filename, String ip, int port, String schema, String username, String password ) throws IOException
//    {
//        //default port 1433
//        String config = "";
//        config += Xml.add( DBConnectionType.SQL_SERVER.toString(), "Type", 1, 1 );
//        String param = "";
//        param += Xml.add( ip, "Host", 1, 2 );
//        param += Xml.add( "" + port, "Port", 1, 2 );
//        param += Xml.add( schema, "DatabaseName", 1, 2 );
//        param += Xml.add( username, "User", 1, 2 );
//        param += Xml.add( password, "Password", 1, 2 );
//        param += Xml.add( "&useUnicode=true&characterEncoding=utf-8", "Extend", 1, 2, 1, 1 );
//        config += Xml.add( param, "Param", 1, 1, 1, 0 );
//        config = Xml.add( config, "DBConnectorConfig" );
//        FileOperation.writeFile( new File( "config/" + filename ), config );
//    }
//
//    public static void createSqliteConfig ( String filename, File dbFile ) throws IOException
//    {
//        String config = "";
//        config += Xml.add( DBConnectionType.SQLITE.toString(), "Type", 1, 1 );
//        String param = "";
//        param += Xml.add( dbFile.getAbsolutePath(), "DBFile", 1, 2, 1, 1 );
//        config += Xml.add( param, "Param", 1, 1, 1, 0 );
//        config = Xml.add( config, "DBConnectorConfig" );
//        FileOperation.writeFile( new File( "config/" + filename ), config );
//    }
}
