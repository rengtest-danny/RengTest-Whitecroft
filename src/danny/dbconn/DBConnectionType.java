package danny.dbconn;

/**
 * DBConnectionType
 *
 * @author Danny Wong
 */
public enum DBConnectionType
{
    /**
     * direct connect to mysql
     */
    MYSQL( "MySQL" ),

    /**
     * connect to local sqlite db
     */
    SQLITE( "SQLite" ),

    /** end */
    ;

    private final String tag;
    private static int filter = 0;

    DBConnectionType () { this.tag = this.toString(); }

    DBConnectionType ( String tag ) { this.tag = tag; }

    /**
     * get the tag for this enum
     */
    public String getTag () { return tag; }

    /**
     * get all tags from this enum class
     */
    public static String[] getTags ()
    {
        String[] tags = new String[values().length];
        for ( int i = 0 ; i < tags.length ; i++ ) { tags[i] = values()[i].getTag(); }
        return tags;
    }

    /**
     * get all filtered tags from this enum class - only tags with enum index at/after <filter> will be returned
     */
    public static String[] getFilteredTags ()
    {
        String[] tags = new String[values().length - filter];
        for ( int i = 0 ; i < tags.length ; i++ ) { tags[i] = values()[i + filter].getTag(); }
        return tags;
    }

    /**
     * get the enum be tag or by enum value, return null if not found
     */
    public static DBConnectionType getEnum ( String tag )
    {
        for ( int i = 0 ; i < values().length ; i++ )
        {
            if ( values()[i].getTag().equals( tag ) ) return values()[i];
            if ( values()[i].toString().equals( tag ) ) return values()[i];
        }
        return null;
    }

    /**
     * get filtered enum values - only enums with index at/after <filter> will be returned
     */
    public static DBConnectionType[] getFilteredEnum ()
    {
        DBConnectionType[] tags = new DBConnectionType[values().length - filter];
        System.arraycopy( values(), filter, tags, 0, tags.length );
        return tags;
    }
}
