package proj.rengserver.controller;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import proj.rengserver.background.PoolDBConnection;
import danny.dbconn.DBConnectionType;

/**
 * handle submitted test record and insert to database
 *
 * @author Danny Wong
 */
@CrossOrigin ( origins = "*" )
@RestController
public class RecordReceiver
{
    static RecordReceiver instance;

    public RecordReceiver ()
    {
        instance = this;
    }

    public static RecordReceiver getInstance ()
    {
        return instance;
    }

    //insert test record
    @RequestMapping ( value = "/api/reng/record", method = RequestMethod.POST )
    public String postRecord ( @RequestBody ( required = false ) String jsonBody )
    {
        try
        {
            JsonObject json = new JsonParser().parse(jsonBody).getAsJsonObject();

            //append to raw data
//            if ( !json.has( STATION.getTag() ) ) throw new Exception( "key missing: " + STATION.getTag() );
//            if ( !json.has( DATE_TIME.getTag() ) ) throw new Exception( "key missing: " + DATE_TIME.getTag() );
//            File output = new File( "public/testdata/", json.get( STATION.getTag() ).getAsString() + "/" + json.get( DATE_TIME.getTag() ).getAsString().substring( 0, 10 ) + ".html" );
//            if ( !output.exists() )
//            {
//                output.getParentFile().mkdirs();
//                output.createNewFile();
//                try ( RandomAccessFile raf = new RandomAccessFile( output, "rw" ) )
//                {
//                    raf.writeBytes( "<html><body><table border=1>\r\n" );
//                    raf.writeBytes( "<tr>" );
//                    for ( RengRecordItem item : RengRecordItem.values() )
//                    {
//                        raf.writeBytes( "<th style=\"white-space:nowrap;\">" );
//                        raf.writeBytes( item.toString().replace( "_", " " ) );
//                        raf.writeBytes( "</th>" );
//                    }
//                    raf.writeBytes( "</tr>\r\n" );
//                }
//            }
//            if ( output.exists() )
//            {
//                try ( RandomAccessFile raf = new RandomAccessFile( output, "rw" ) )
//                {
//                    raf.seek( raf.length() );
//                    raf.writeBytes( "<tr>" );
//                    for ( RengRecordItem item : RengRecordItem.values() )
//                    {
//                        raf.writeBytes( "<td style=\"white-space:nowrap;\">" );
//                        if ( json.has( item.getTag() ) ) raf.writeBytes( json.get( item.getTag() ).getAsString() );
//                        else raf.writeBytes( "-" );
//                        raf.writeBytes( "</td>" );
//                    }
//                    raf.writeBytes( "</tr>\r\n" );
//                }
//            }

            //proceed to insert to database
            String values = "";
            String field = "";

            for ( String key : json.keySet() )
            {
                field += key + ",";
                switch ( key )
                {
                    case "test_datetime":
                    case "test_station":
                    case "test_operator":
                    case "test_wire_1":
                    case "test_wire_2":
                    case "test_order":
                    case "test_type":
                    case "test_class":
                    case "test_fail_at":
                    case "test_result":
                    case "test_duration":
                    case "test_lastdatetime":
                        values += "'" + json.get(key).getAsString() + "',";
                        break;

                    default:
                        values += json.get(key).getAsString() + ",";
                }
            }

            if ( field.length() == 0 || values.length() == 0 ) throw new Exception("no data in request body");
            values = values.substring(0, values.length() - 1);
            field = field.substring(0, field.length() - 1);

            try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "PostRecord") )
            {
                String sql = "INSERT INTO testdata (" + field + ") VALUES (" + values + ")";
                pdb.getDb().updateSQL(sql);
            }
            return "OK";
        }
        catch ( Exception e )
        {
            return "ERROR:" + e.getMessage();
        }
    }

    //insert issue report
    @RequestMapping ( value = "/api/reng/issue", method = RequestMethod.POST )
    public String postIssue ( @RequestBody ( required = false ) String jsonBody )
    {
        try
        {
            JsonObject json = new JsonParser().parse(jsonBody).getAsJsonObject();

            //proceed to insert to database
            String values = "";
            String field = "";

            for ( String key : json.keySet() )
            {
                field += key + ",";
                switch ( key )
                {
                    case "report_datetime":
                    case "report_station":
                    case "report_operator":
                    case "report_issue":
                    case "report_program":
                    case "report_reason":
                    case "report_screenshot":
                        values += "'" + json.get(key).getAsString() + "',";
                        break;

                    default:
                        values += json.get(key).getAsString() + ",";
                }
            }

            if ( field.length() == 0 || values.length() == 0 ) throw new Exception("no data in request body");
            values = values.substring(0, values.length() - 1);
            field = field.substring(0, field.length() - 1);

            try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "PostIssue") )
            {
                String sql = "INSERT INTO issuedata (" + field + ") VALUES (" + values + ")";
                pdb.getDb().updateSQL(sql);
            }
            return "OK";
        }
        catch ( Exception e )
        {
            return "ERROR:" + e.getMessage();
        }
    }

    protected static String getIP ()
    {
        try
        {
            return ( (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes() ).getRequest().getRemoteAddr();
        }
        catch ( Exception e ) { return "Unknown"; }
    }
}
