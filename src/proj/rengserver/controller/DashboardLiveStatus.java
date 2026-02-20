package proj.rengserver.controller;


import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import danny.dbconn.DBConnectionType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import proj.rengserver.background.PoolDBConnection;

import java.sql.ResultSet;
import java.util.Date;

import static proj.rengserver.controller.Dashboard.fullDateFormat;

/**
 * @author Danny Wong
 */
@CrossOrigin ( origins = "*" )
@RestController
public class DashboardLiveStatus
{
    @RequestMapping ( value = "/api/reng/andon", method = RequestMethod.POST )
    public String postAndon ( @RequestBody ( required = false ) String jsonBody )
    {
        try
        {
            JsonObject json = new JsonParser().parse(jsonBody).getAsJsonObject();
            json.addProperty("switch_time", fullDateFormat.format(new Date()));

            System.out.println(jsonBody);

            String values = "";
            String field = "";

            for ( String key : json.keySet() )
            {
                field += key + ",";
                switch ( key )
                {
                    case "station":
                    case "switch_time":
                    case "color":
                        values += "'" + json.get(key).getAsString() + "',";
                        break;

                    default:
                        values += json.get(key).getAsString() + ",";
                }
            }

            if ( field.length() == 0 || values.length() == 0 ) throw new Exception("no data in request body");
            values = values.substring(0, values.length() - 1);
            field = field.substring(0, field.length() - 1);

            try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "PostAndon") )
            {
                String sql = "INSERT INTO andondata (" + field + ") VALUES (" + values + ")";
                pdb.getDb().updateSQL(sql);
            }
            return "OK";
        }
        catch ( Exception e )
        {
            return "ERROR:" + e.getMessage();
        }
    }

    @RequestMapping ( value = "/dashboard/andon", method = RequestMethod.GET )
    public String getAndon ()
    {
        String sql = "SELECT station,color,switch_time,CONCAT('',TIMEDIFF(CURRENT_TIMESTAMP,switch_time))duration FROM andondata INNER JOIN (SELECT MAX(id)ida FROM andondata GROUP BY station)a ON id=a.ida ORDER BY station";
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Dashboard") ; ResultSet rs = pdb.getDb().querySQL(sql) )
        {
            JsonArray stations = new JsonArray();
            while ( rs.next() )
            {
                JsonObject station = new JsonObject();
                //station, color, switch_time, duration
                station.addProperty("label", rs.getString("station"));
                station.addProperty("color", rs.getString("color"));
                station.addProperty("since", fullDateFormat.format(rs.getTimestamp("switch_time")));
                station.addProperty("duration", rs.getString("duration"));
                stations.add(station);
            }
            JsonObject result = new JsonObject();
            result.add("station", stations);
            return result.toString();
        }
        catch ( Exception e )
        {
            e.printStackTrace();
            return "{}";
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
