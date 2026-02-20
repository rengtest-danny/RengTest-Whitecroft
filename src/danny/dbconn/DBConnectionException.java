package danny.dbconn;

/**
 * @author Danny Wong
 */
public class DBConnectionException extends Exception
{
    public DBConnectionException ( String message )
    {
        super( message );
    }

    public DBConnectionException ( Exception e )
    {
        super( e.getLocalizedMessage(), e );
    }
}
