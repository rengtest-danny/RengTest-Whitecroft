package danny.dbconn;

import danny.util.TimerThread;

/**
 * DBConnectionChecker
 *
 * @author Danny Wong
 */
public abstract class DBConnectionChecker extends TimerThread
{
    protected final DBConnector dbc;
    protected boolean lastResult = false;
    protected boolean firstCheck = true;
    protected final String nameInit;

    public DBConnectionChecker ( DBConnector dbc )
    {
        super( "DBConnectivityCheck : " + dbc.type.getTag(), Thread.MIN_PRIORITY + 1 );
        this.dbc = dbc;
        this.nameInit = "DBConnectivityCheck : " + dbc.type.getTag() + " - ";
    }
}











