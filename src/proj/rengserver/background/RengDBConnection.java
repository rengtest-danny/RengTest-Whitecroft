package proj.rengserver.background;

import danny.dbconn.*;

interface RengDBConnection
{
    DBConnector initConnection ( final String forIP, final String function ) throws Exception;

    void returnConnection ( final DBConnector db );

    void flushConnection ();
}
