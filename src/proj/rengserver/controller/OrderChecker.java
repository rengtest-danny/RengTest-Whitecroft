package proj.rengserver.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import danny.dbconn.DBConnectionType;
import danny.dbconn.DBResultSet;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import proj.rengserver.background.PoolDBConnection;

import java.sql.ResultSet;

/**
 * @author Danny Wong
 */
@CrossOrigin ( origins = "*" )
@RestController
public class OrderChecker
{
    @RequestMapping ( value = "/api/reng/order", method = RequestMethod.GET )
    public String checkOrder ( @RequestParam ( required = true ) String order )
    {
        try
        {
            String sql = "SELECT order_number,order_testtype,order_class_two,order_quantity,order_profile,order_created,SUM(pass)pass,order_quantity-SUM(pass)AS remain FROM (SELECT o.*,t.test_quantity,MAX(t.test_pass)pass FROM orderdata o LEFT JOIN testdata t ON t.test_order=o.order_number WHERE o.order_number='" + order + "' GROUP BY t.test_quantity) a";
            try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Dashboard") ; ResultSet rs = pdb.getDb().querySQL(sql) )
            {
                String result = "ERROR:order not found";
                if ( rs.next() )
                {
                    if ( null != rs.getString("order_number") )
                    {
                        //reply as type,order,class,-passed(=to do)
                        result = rs.getString("order_testtype") + ",";
                        result += rs.getString("order_number") + ",";
                        result += rs.getString("order_class_two") + ",";
                        if ( null != rs.getString("remain") ) result += rs.getString("remain") + ",";
                        else result += rs.getString("order_quantity") + ",";
                        result += rs.getString("order_profile") + ",";
                        result += rs.getString("order_created");
                        return result;
                    }
                }
                return result;
            }
            catch ( Exception e )
            {
                e.printStackTrace();
            }
        }
        catch ( Exception e )
        {
            return "ERROR:" + e.getMessage();
        }
        return "ERROR:unknown";
    }

    @RequestMapping ( value = "/api/reng/order", method = RequestMethod.POST )
    public String createOrder ( @RequestBody ( required = true ) String body )
    {
        JsonObject result = new JsonObject();
        try
        {
            JsonObject request = new JsonParser().parse(body).getAsJsonObject();
            //get details from request and insert to database
            String values = "";
            String field = "";
            for ( String key : request.keySet() )
            {
                field += key + ",";
                switch ( key )
                {
                    case "order_number":
                    case "order_profile":
                    case "order_created":
                        values += "'" + request.get(key).getAsString() + "',";
                        break;

                    default:
                        values += request.get(key).getAsString() + ",";
                }
            }
            //
            if ( field.length() == 0 || values.length() == 0 ) throw new Exception("no data in request body");
            values = values.substring(0, values.length() - 1);
            field = field.substring(0, field.length() - 1);
            try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "CreateOrder") )
            {
                String sql = "INSERT INTO orderdata (" + field + ") VALUES (" + values + ")";
                pdb.getDb().updateSQL(sql);
            }
            result.addProperty("result", "ok");
        }
        catch ( Exception e )
        {
            result.addProperty("result", "fail");
            result.addProperty("error", e.getMessage());
        }
        return result.toString();
    }

    @RequestMapping ( value = "/api/reng/order", method = RequestMethod.DELETE )
    public String deleteOrder ( @RequestBody ( required = true ) String body )
    {
        JsonObject result = new JsonObject();
        try
        {
            JsonObject request = new JsonParser().parse(body).getAsJsonObject();
            //get order id from request and delete from database
            if ( !request.keySet().contains("order_id") ) throw new Exception("no order id");
            try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "DeleteOrder") )
            {
                String sql = "DELETE FROM orderdata WHERE id=" + request.get("order_id");
                pdb.getDb().updateSQL(sql);
            }
            result.addProperty("result", "ok");
        }
        catch ( Exception e )
        {
            result.addProperty("error", e.getMessage());
        }
        return result.toString();
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
