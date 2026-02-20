package proj.rengserver.controller;


import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import danny.dbconn.DBConnectionType;
import danny.dbconn.DBResultSet;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import proj.rengserver.background.PoolDBConnection;

import java.sql.ResultSet;

/**
 * responding details to query for single station
 *
 * @author Danny Wong
 */
@CrossOrigin ( origins = "*" )
@RestController
public class DashboardStationCheck
{
    @RequestMapping ( value = "/station/1", method = RequestMethod.GET )
    public String station1 ( @RequestParam ( required = true ) String station, @RequestParam ( required = true ) String result )
    {
        String sql = "SELECT r.order,HOUR(MIN(r.datetime))+TRUNCATE((MINUTE(MIN(r.datetime))/60)+(SECOND(MIN(r.datetime))/3600),6)AS start,HOUR(MAX(r.datetime))+ROUND((MINUTE(MAX(r.datetime))/60)+(SECOND(MAX(r.datetime))/3600),6)AS end,COUNT(IF(result='Pass',1,NULL))AS pass,COUNT(IF(result='Fail',1,NULL))AS fail,r.quantity FROM today_test_result r WHERE station='" + station + "' GROUP BY r.order,quantity ORDER BY datetime";
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Dashboard") ; ResultSet rs = pdb.getDb().querySQL(sql) )
        {
            JsonArray labels = new JsonArray();
            JsonArray timelineArr = new JsonArray();
            while ( rs.next() )
            {
                String label = rs.getString("order") + " (" + rs.getInt("pass") + "/" + rs.getString("quantity") + ")";
                labels.add(label);
                JsonArray timeline = new JsonArray();
                timeline.add(rs.getDouble("start"));
                timeline.add(rs.getDouble("end"));
                timelineArr.add(timeline);

            }
            JsonObject dataObj = new JsonObject();
            dataObj.add("data", timelineArr);

            JsonArray dataset = new JsonArray();
            dataset.add(dataObj);

            JsonObject jsonResult = new JsonObject();
            jsonResult.add("datasets", dataset);
            jsonResult.add("labels", labels);
            return jsonResult.toString();
        }
        catch ( Exception e )
        {
            e.printStackTrace();
            return "{}";
        }
    }

    @RequestMapping ( value = "/station/1r", method = RequestMethod.GET )
    public String station1r ( @RequestParam ( required = true ) String station, @RequestParam ( required = true ) String result )
    {
        //String sql = "SELECT COALESCE(test_fail_at,'Unknown')AS test_fail_at,COUNT(*)AS num FROM testdata WHERE test_station='" + station + "' AND DATE(test_datetime)=CURRENT_DATE GROUP BY test_fail_at ORDER BY test_fail_at='none' DESC,test_fail_at";
        String sql = "SELECT IF(ISNULL(test_type),'none',COALESCE(test_fail_at,'Unknown'))AS test_failed,COUNT(*)AS num FROM testdata WHERE test_station='" + station + "' AND DATE(test_datetime)=CURRENT_DATE GROUP BY test_failed ORDER BY test_failed='none' DESC,test_failed";
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Dashboard") ; ResultSet rs = pdb.getDb().querySQL(sql) )
        {
            JsonArray labelOrder = new JsonArray();
            JsonArray dataPass = new JsonArray();
            boolean checkFirst = false;
            while ( rs.next() )
            {
                JsonArray label = new JsonArray();
                String reason = rs.getString("test_failed");
                if ( !checkFirst && !"none".equals(reason) )
                {
                    //means no pass
                    label.add("Pass");
                    labelOrder.add(label);
                    dataPass.add(0);
                    label = new JsonArray();
                }
                checkFirst = true;
                if ( "none".equals(reason) )
                    label.add("Pass");
                else
                    label.add("Fail : " + rs.getString("test_failed"));
                labelOrder.add(label);
                dataPass.add(rs.getInt("num"));
            }
            JsonObject passObj = new JsonObject();
            passObj.add("data", dataPass);

            JsonArray dataArr = new JsonArray();
            dataArr.add(passObj);

            JsonObject jsonResult = new JsonObject();
            jsonResult.add("datasets", dataArr);
            jsonResult.add("labels", labelOrder);
            return jsonResult.toString();
        }
        catch ( Exception e )
        {
            e.printStackTrace();
            return "{}";
        }
    }

    @RequestMapping ( value = "/station/data", method = RequestMethod.GET )
    public String stationData ( @RequestParam ( required = true ) String station )
    {
        JsonObject result = new JsonObject();

        String sql1 = "SELECT MIN(r.datetime)AS start,MAX(r.datetime)AS end,IF(DATE(MAX(r.datetime))!=CURRENT_DATE OR(r.quantity=MAX(r.pass)OR MAX(r.pass)<=1),'-',DATE_ADD(MAX(r.datetime),INTERVAL (CEILING(TIME_TO_SEC(TIMEDIFF(MAX(r.datetime),MIN(r.datetime)))/(MAX(r.pass)-1)))*(r.quantity-MAX(r.pass)) SECOND))AS estend,r.order,COALESCE(GROUP_CONCAT(DISTINCT(IF(d.test_type='Unknown',NULL,d.test_type))),'Unknown')AS testtype,r.quantity,COUNT(IF(result='Pass',1,NULL))AS pass,COUNT(IF(result='Fail',1,NULL))AS fail,COALESCE(GROUP_CONCAT(DISTINCT(IF(d.test_fail_at='none',NULL,d.test_fail_at))),'None')AS failat FROM today_test_result r LEFT JOIN testdata d ON r.id=d.id WHERE r.station='" + station + "' GROUP BY r.order,r.quantity ORDER BY r.datetime";
        //String sql1 = "SELECT MIN(test_datetime)AS start,MAX(test_datetime)AS end,if(DATE(MAX(test_datetime))!=CURRENT_DATE OR(test_quantity=MAX(test_pass)OR MAX(test_pass)<=1),'-',DATE_ADD(MAX(test_datetime),INTERVAL (CEILING(TIME_TO_SEC(TIMEDIFF(MAX(test_datetime),MIN(test_datetime)))/(MAX(test_pass)-1)))*(test_quantity-MAX(test_pass)) SECOND))AS estend,test_order,COALESCE(GROUP_CONCAT(DISTINCT(IF(test_type='Unknown',NULL,test_type))),'Unknown')AS testtype,test_quantity,MAX(test_pass)AS pass,MAX(test_fail)AS fail,COALESCE(GROUP_CONCAT(DISTINCT(IF(test_fail_at='none',NULL,test_fail_at))),'None')AS failat FROM testdata WHERE test_station='" + station + "' AND DATE(test_datetime)=CURRENT_DATE GROUP BY test_order,test_quantity ORDER BY test_datetime";
        //String sql1 = "SELECT MIN(test_datetime)AS start,MAX(test_datetime)AS end,test_station,COALESCE(GROUP_CONCAT(DISTINCT(IF(test_type='Unknown',NULL,test_type))),'Unknown')AS testtype,test_quantity,MAX(test_pass)AS pass,MAX(test_fail)AS fail,COALESCE(GROUP_CONCAT(DISTINCT(IF(test_fail_at='none',NULL,test_fail_at))),'None')AS failat FROM testdata WHERE test_order='" + order + "' GROUP BY test_quantity,test_station ORDER BY test_datetime";
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Dashboard") ; ResultSet rs = pdb.getDb().querySQL(sql1) )
        {
            if ( rs instanceof DBResultSet )
            {
                JsonArray tests = ( (DBResultSet) rs ).getJson();

                for ( int i = 0 ; i < tests.size() ; i++ )
                {
                    JsonObject obj = tests.get(i).getAsJsonObject();
                    if ( obj.keySet().contains("start") && obj.get("start").getAsString().endsWith(".0") )
                    {
                        obj.addProperty("start", obj.get("start").getAsString().replace(".0", ""));
                    }
                    if ( obj.keySet().contains("end") && obj.get("end").getAsString().endsWith(".0") )
                    {
                        obj.addProperty("end", obj.get("end").getAsString().replace(".0", ""));
                    }
                }
                result.add("test", tests);
            }
        }
        catch ( Exception e )
        {
            e.printStackTrace();
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