package proj.rengserver.controller;

import com.google.gson.*;
import danny.dbconn.DBConnectionType;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import proj.rengserver.background.PoolDBConnection;

import java.io.File;
import java.io.RandomAccessFile;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;

/**
 * response queries for reports
 *
 * @author Danny Wong
 */
@CrossOrigin ( origins = "*" )
@RestController
public class Report
{
    static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    //reply report type for report selection
    @RequestMapping ( value = "/report/selection", method = RequestMethod.GET )
    public String listAllTypes ()
    {
        JsonArray reportList = new JsonArray();

        JsonObject report3 = new JsonObject();
        report3.addProperty("type", "Productivity");
        report3.addProperty("link", "productive");
        reportList.add(report3);

        JsonObject report5 = new JsonObject();
        report5.addProperty("type", "Fail Counts");
        report5.addProperty("link", "failcount");
        reportList.add(report5);

        JsonObject report4 = new JsonObject();
        report4.addProperty("type", "Issue Report");
        report4.addProperty("link", "issue");
        reportList.add(report4);

        JsonObject report1 = new JsonObject();
        report1.addProperty("type", "Utilization");
        report1.addProperty("link", "utilization");
        reportList.add(report1);

        JsonObject report2 = new JsonObject();
        report2.addProperty("type", "Cycle Time");
        report2.addProperty("link", "cycle");
        reportList.add(report2);


        JsonArray stationList = new JsonArray();
        //get station list from database
        String sql = "SELECT DISTINCT(test_station) FROM testdata WHERE test_station LIKE 'CFL%' AND ISNULL(test_station)=0";
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Report") ; ResultSet rs = pdb.getDb().querySQL(sql) )
        {
            //need to filter station not in operation in the past x months?
            while ( rs.next() )
            {
                stationList.add(rs.getString("test_station"));
            }
        }
        catch ( Exception e )
        {
            e.printStackTrace();
        }

        JsonObject selection = new JsonObject();
        selection.add("report", reportList);
        selection.add("station", stationList);
        return selection.toString();
    }

    @RequestMapping ( value = "/report/productive", method = RequestMethod.GET )
    public String productive ( String from, String to, String station )
    {
        JsonObject result = new JsonObject();
        JsonObject graph = new JsonObject();
        station = station.replace(",", "','");
        String sql = "SELECT DATE(test_datetime)AS test_date,HOUR(test_datetime)AS test_hour,COUNT(*)pass FROM testdata WHERE test_station IN ('" + station + "') AND DATE(test_datetime)>='" + from + "' AND DATE(test_datetime)<='" + to + "' AND test_fail_at='none' GROUP BY DATE(test_datetime),HOUR(test_datetime)";
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Report") ; ResultSet rs = pdb.getDb().querySQL(sql) )
        {
            int minHour = 24;
            int maxHour = 0;

            while ( rs.next() )
            {
                //test_date, test_hour, pass
                int hour = rs.getInt("test_hour");
                if ( hour < minHour ) minHour = hour;
                if ( hour > maxHour ) maxHour = hour;
            }

            JsonArray labelHour = new JsonArray();
            for ( int i = minHour ; i <= maxHour ; i++ )
            {
                labelHour.add("" + i);
            }
            System.out.println("hour from " + minHour + " to " + maxHour);

            rs.beforeFirst();

            String lastDate = "";
            JsonArray datasets = new JsonArray();
            JsonObject dataset = new JsonObject();
            int array[] = new int[labelHour.size()];
            while ( rs.next() )
            {
                if ( !rs.getString("test_date").equals(lastDate) )
                {
                    if ( !"".equals(lastDate) )
                    {
                        //next
                        JsonArray data = new JsonArray();
                        for ( int i = 0 ; i < array.length ; i++ )
                        {
                            data.add(array[i]);
                        }
                        dataset.add("data", data);
                        datasets.add(dataset);
                    }
                    lastDate = rs.getString("test_date");
                    dataset = new JsonObject();
                    dataset.addProperty("label", lastDate);
                    array = new int[labelHour.size()];
                }
                int index = rs.getInt("test_hour") - minHour;
                int pass = rs.getInt("pass");
                for ( int i = index ; i < array.length ; i++ )
                {
                    array[i] += pass;
                }
            }
            //final
            JsonArray data = new JsonArray();
            for ( int i = 0 ; i < array.length ; i++ )
            {
                data.add(array[i]);
            }
            dataset.add("data", data);
            datasets.add(dataset);

            graph.add("labels", labelHour);
            graph.add("datasets", datasets);
            result.add("graph", graph);
        }
        catch ( Exception e )
        {
            e.printStackTrace();
        }
        sql = "SELECT DATE(test_datetime)test_date,COUNT(*)pass FROM testdata WHERE test_station IN ('" + station + "') AND DATE(test_datetime)>='" + from + "' AND DATE(test_datetime)<='" + to + "' AND test_fail_at='none' GROUP BY DATE(test_datetime)";
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Report") ; ResultSet rs = pdb.getDb().querySQL(sql) )
        {
            JsonArray table = new JsonArray();
            while ( rs.next() )
            {
                JsonObject row = new JsonObject();
                row.addProperty("date", rs.getString("test_date"));
                row.addProperty("pass", rs.getString("pass"));
                table.add(row);
            }
            result.add("table", table);
        }
        catch ( Exception e )
        {
            e.printStackTrace();
        }
        return result.toString();
    }

    @RequestMapping ( value = "/report/failcount", method = RequestMethod.GET )
    public String failCounts ( String from, String to, String station )
    {
        JsonObject result = new JsonObject();
        station = station.replace(",", "','");
        String sql = "SELECT test_fail_at,test_station,COUNT(*)num FROM testdata WHERE test_station IN ('" + station + "') AND DATE(test_datetime)>='" + from + "' AND DATE(test_datetime)<='" + to + "' AND test_fail_at <> 'none' GROUP BY test_station,test_fail_at ORDER BY test_fail_at,test_station";
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Report") ; ResultSet rs = pdb.getDb().querySQL(sql) )
        {
            ArrayList<String> reasons = new ArrayList<>();
            ArrayList<String> stations = new ArrayList<>();
            LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
            JsonArray table = new JsonArray();
            while ( rs.next() )
            {
                JsonObject row = new JsonObject();
                //test_fail_at, test_station, num
                String reason = rs.getString("test_fail_at");
                String stat = rs.getString("test_station");
                int num = rs.getInt("num");

                row.addProperty("reason", reason);
                row.addProperty("station", stat);
                row.addProperty("count", num);
                table.add(row);

                //build graph
                if ( !reasons.contains(reason) ) reasons.add(reason);
                if ( !stations.contains(stat) ) stations.add(stat);
                counts.put(reason + "@" + stat, num);
            }
            result.add("table", table);

            //build graph
            Collections.sort(stations);
            JsonObject graph = new JsonObject();
            JsonArray labels = new JsonArray();
            JsonArray datasets = new JsonArray();
            for ( String reason : reasons )
            {
                labels.add(reason);
            }
            for ( String stat : stations )
            {
                JsonObject dataset = new JsonObject();
                dataset.addProperty("label", stat);
                JsonArray data = new JsonArray();
                for ( String reason : reasons )
                {
                    if ( counts.containsKey(reason + "@" + stat) )
                        data.add(counts.get(reason + "@" + stat));
                    else data.add(0);
                }
                //data.add("label", stat);
                dataset.add("data", data);
                datasets.add(dataset);
            }
            graph.add("labels", labels);
            graph.add("datasets", datasets);
            result.add("graph", graph);
        }
        catch ( Exception e )
        {
            e.printStackTrace();
        }
        return result.toString();
    }

    @RequestMapping ( value = "/report/utilization", method = RequestMethod.GET )
    public String utilization ( String from, String to, String station )
    {
        JsonObject result = new JsonObject();
        if ( station.contains(",") )
        {
            result.addProperty("type", "multi");
            //more than one station
            station = station.replace(",", "','");
            String sql = "SELECT date,test_station,SUM(duration)duration,COUNT(test_order)AS worder,SUM(quantity)quantity,SUM(pass)pass FROM (SELECT test_station,date,test_order,SUM(duration)duration,MAX(quantity)quantity,SUM(pass)pass FROM (SELECT test_station,DATE(MIN(test_datetime))AS date,MIN(test_datetime)AS starttime,MAX(test_datetime)AS endtime,CEILING(TIMESTAMPDIFF(SECOND,MIN(test_datetime),MAX(test_datetime))/60) AS duration,test_order,MAX(test_quantity)AS quantity,MAX(test_pass)AS pass FROM testdata WHERE test_station IN('" + station + "') AND DATE(test_datetime) >= '" + from + "' AND DATE(test_datetime) <= '" + to + "' GROUP BY DATE(test_datetime),test_order,test_quantity,test_station ORDER BY test_station,test_datetime) a GROUP BY test_station,date,test_order) b GROUP BY test_station,date ORDER BY date,test_station";
            try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Report") ; ResultSet rs = pdb.getDb().querySQL(sql) )
            {
                //need to filter station not in operation in the past x months?
                JsonArray orderList = new JsonArray();
                while ( rs.next() )
                {
                    JsonObject order = new JsonObject();
                    //test_station, test_order, starttime, endtime, duration
                    order.addProperty("date", rs.getString("date"));
                    order.addProperty("station", rs.getString("test_station"));
                    order.addProperty("duration", rs.getString("duration"));
                    order.addProperty("order", rs.getString("worder"));
                    order.addProperty("quantity", rs.getString("quantity"));
                    order.addProperty("pass", rs.getString("pass"));
                    orderList.add(order);
                }
                result.add("order", orderList);
            }
            catch ( Exception e )
            {
                e.printStackTrace();
            }
        }
        else
        {
            result.addProperty("type", "single");
            //one station selected - show details of every order
            String sql = "SELECT test_station, date, SUM(duration)duration, test_order, MAX(quantity)quantity, SUM(pass)pass FROM (SELECT test_station,DATE(MIN(test_datetime))AS date,MIN(test_datetime)AS starttime,MAX(test_datetime)AS endtime,CEILING(TIMESTAMPDIFF(SECOND,MIN(test_datetime),MAX(test_datetime))/60) AS duration,test_order,MAX(test_quantity)AS quantity,MAX(test_pass)AS pass FROM testdata WHERE test_station IN('" + station + "') AND DATE(test_datetime) >= '" + from + "' AND DATE(test_datetime) <= '" + to + "' GROUP BY DATE(test_datetime),test_order,test_quantity,test_station ORDER BY test_station,test_datetime) a GROUP BY date,test_order";
            try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Report") ; ResultSet rs = pdb.getDb().querySQL(sql) )
            {
                //need to filter station not in operation in the past x months?
                JsonArray orderList = new JsonArray();
                while ( rs.next() )
                {
                    JsonObject order = new JsonObject();
                    //test_station, test_order, starttime, endtime, duration
                    order.addProperty("station", rs.getString("test_station"));
                    order.addProperty("date", rs.getString("date"));
                    order.addProperty("duration", rs.getInt("duration"));
                    order.addProperty("order", rs.getString("test_order"));
                    order.addProperty("quantity", rs.getInt("quantity"));
                    order.addProperty("pass", rs.getInt("pass"));
                    orderList.add(order);
                }
                result.add("order", orderList);
            }
            catch ( Exception e )
            {
                e.printStackTrace();
            }
        }
        return result.toString();
    }

    @RequestMapping ( value = "/report/cycle", method = RequestMethod.GET )
    public String cycleTime ( String from, String to, String station )
    {
        JsonObject result = new JsonObject();
//        if ( station.contains(",") )
//        {
        result.addProperty("type", "multi");
        //more than one station
        station = station.replace(",", "','");
        String sql = "SELECT DATE(test_datetime)AS date,test_station AS station,test_order AS worder,MAX(test_quantity)quantity,MAX(test_pass)pass,MAX(test_fail)fail,COUNT(*)AS testdone,IF(CEILING((MAX(test_pass)/COUNT(*))*100)>100,100,CEILING((MAX(test_pass)/COUNT(*))*100))AS passrate,ROUND(AVG(secToRunATest)/60,1)AS avgTime,ROUND(MAX(secToRunATest)/60,1)AS maxTime,ROUND(MIN(secToRunATest)/60,1)AS minTime FROM (SELECT test_datetime,test_station,test_order,test_quantity,test_todo,test_pass,test_fail,last_datetime,last_pass,last_fail,IF(ISNULL(last_pass),NULL,IF(test_pass<>last_pass,'Pass','Fail')) AS test_result,TIMESTAMPDIFF(SECOND,last_datetime,test_datetime)AS secToRunATest FROM testdata b LEFT JOIN (SELECT test_station AS last_station,test_datetime AS last_datetime,test_order AS last_order,test_quantity AS last_quantity,test_pass AS last_pass,test_fail AS last_fail FROM testdata WHERE test_station IN ('" + station + "') AND DATE(test_datetime)>='" + from + "' AND DATE(test_datetime)<='" + to + "')a ON a.last_station=b.test_station AND DATE(a.last_datetime)=DATE(b.test_datetime) AND a.last_datetime<b.test_datetime AND a.last_order=b.test_order AND a.last_quantity=b.test_quantity AND ( (a.last_pass+1=b.test_pass AND a.last_fail=b.test_fail) OR (a.last_pass=b.test_pass AND a.last_fail+1=b.test_fail) ) WHERE test_station IN ('" + station + "') AND DATE(test_datetime)>='" + from + "' AND DATE(test_datetime)<='" + to + "' ORDER BY test_station,test_datetime)c GROUP BY DATE(test_datetime),test_order,test_quantity,test_station ORDER BY DATE(test_datetime),test_station,test_datetime,test_order";
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Report") ; ResultSet rs = pdb.getDb().querySQL(sql) )
        {
            //need to filter station not in operation in the past x months?
            JsonArray orderList = new JsonArray();
            while ( rs.next() )
            {
                JsonObject order = new JsonObject();
                //date, station, worder, quantity, pass, fail, testdone, passrate, avgTime, maxTime, minTime
                order.addProperty("date", rs.getString("date"));
                order.addProperty("station", rs.getString("station"));
                order.addProperty("order", rs.getString("worder"));
                order.addProperty("quantity", rs.getInt("quantity"));
                order.addProperty("completed", rs.getInt("testdone"));
                order.addProperty("pass", rs.getInt("pass"));
                order.addProperty("passrate", rs.getInt("passrate"));
                order.addProperty("min", rs.getDouble("minTime"));
                order.addProperty("avg", rs.getDouble("avgTime"));
                order.addProperty("max", rs.getDouble("maxTime"));
                orderList.add(order);
            }
            result.add("order", orderList);
        }
        catch ( Exception e )
        {
            e.printStackTrace();
        }
//        }
//        else
//        {
//            result.addProperty("type", "single");
//            //one station selected - show details of every order
//            String sql = "SELECT test_station, date, SUM(duration)duration, test_order, MAX(quantity)quantity, SUM(pass)pass FROM (SELECT test_station,DATE(MIN(test_datetime))AS date,MIN(test_datetime)AS starttime,MAX(test_datetime)AS endtime,CEILING(TIMESTAMPDIFF(SECOND,MIN(test_datetime),MAX(test_datetime))/60) AS duration,test_order,MAX(test_quantity)AS quantity,MAX(test_pass)AS pass FROM testdata WHERE test_station IN('" + station + "') AND DATE(test_datetime) >= '" + from + "' AND DATE(test_datetime) <= '" + to + "' GROUP BY DATE(test_datetime),test_order,test_quantity,test_station ORDER BY test_station,test_datetime) a GROUP BY date,test_order";
//            try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Report") ; ResultSet rs = pdb.getDb().querySQL(sql) )
//            {
//                //need to filter station not in operation in the past x months?
//                JsonArray orderList = new JsonArray();
//                while ( rs.next() )
//                {
//                    JsonObject order = new JsonObject();
//                    //test_station, test_order, starttime, endtime, duration
//                    order.addProperty("station", rs.getString("test_station"));
//                    order.addProperty("date", rs.getString("date"));
//                    order.addProperty("duration", rs.getInt("duration"));
//                    order.addProperty("order", rs.getString("test_order"));
//                    order.addProperty("quantity", rs.getInt("quantity"));
//                    order.addProperty("pass", rs.getInt("pass"));
//                    orderList.add(order);
//                }
//                result.add("order", orderList);
//            }
//            catch ( Exception e )
//            {
//                e.printStackTrace();
//            }
//        }
        return result.toString();
    }

    @RequestMapping ( value = "/report/issue", method = RequestMethod.GET )
    public String issueReport ( String from, String to, String station )
    {
        station = station.replace(",", "','");
        JsonObject result = new JsonObject();
        //get this as left graph data
        String sql = "SELECT DATE(report_datetime)report_date,report_issue,COUNT(*)AS num FROM issuedata WHERE DATE(report_datetime) >= '" + from + "' AND DATE(report_datetime) <= '" + to + "' AND report_station IN('" + station + "') GROUP BY DATE(report_datetime),report_issue";
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Report") ; ResultSet rs = pdb.getDb().querySQL(sql) )
        {
            JsonArray issueList = new JsonArray();
            while ( rs.next() )
            {
                JsonObject issue = new JsonObject();
                //report_date, report_issue, num
                issue.addProperty("date", rs.getString("report_date"));
                issue.addProperty("issue", rs.getString("report_issue"));
                issue.addProperty("num", rs.getString("num"));
                issueList.add(issue);
            }
            result.add("count", issueList);
        }
        catch ( Exception e )
        {
            e.printStackTrace();
        }

        //get this as right graph data
        sql = "SELECT report_program,report_reason,COUNT(*)num FROM issuedata WHERE DATE(report_datetime) >= '" + from + "' AND DATE(report_datetime) <= '" + to + "' AND report_station IN('" + station + "') GROUP BY report_program,report_reason ORDER BY report_program";
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Report") ; ResultSet rs = pdb.getDb().querySQL(sql) )
        {
            JsonArray programList = new JsonArray();
            while ( rs.next() )
            {
                JsonObject program = new JsonObject();
                //report_program, report_reason, num
                program.addProperty("prono", rs.getString("report_program"));
                program.addProperty("reason", rs.getString("report_reason"));
                program.addProperty("num", rs.getString("num"));
                programList.add(program);
            }
            result.add("program", programList);
        }
        catch ( Exception e )
        {
            e.printStackTrace();
        }

        //list all issue as table
        sql = "SELECT * FROM issuedata WHERE DATE(report_datetime) >= '" + from + "' AND DATE(report_datetime) <= '" + to + "' AND report_station IN('" + station + "') ORDER BY report_datetime";
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Report") ; ResultSet rs = pdb.getDb().querySQL(sql) )
        {
            JsonArray issueList = new JsonArray();
            while ( rs.next() )
            {
                JsonObject issue = new JsonObject();
                //id, report_datetime, report_station, report_operator, report_issue, report_program, report_reason, report_screenshot
                issue.addProperty("date", rs.getString("report_datetime"));
                issue.addProperty("station", rs.getString("report_station"));
                issue.addProperty("operator", rs.getString("report_operator"));
                issue.addProperty("issue", rs.getString("report_issue"));
                issue.addProperty("program", rs.getString("report_program"));
                issue.addProperty("reason", rs.getString("report_reason"));
                issue.addProperty("screenshot", rs.getString("report_screenshot"));
                issueList.add(issue);
            }
            result.add("data", issueList);
        }
        catch ( Exception e )
        {
            e.printStackTrace();
        }
        return result.toString();
    }

    @RequestMapping ( value = "/uncimage", method = RequestMethod.GET )
    public ResponseEntity<byte[]> imageFromUNC ( @RequestParam ( required = true ) String name ) throws Exception
    {
        String path = "\\\\gilliam\\OPERATIONS\\Production Engineering\\Projects\\2021 New test equipment\\4. Data";
        if ( name.startsWith("Breakdown") ) path += "\\BreakdownScreenshots";
        else if ( name.startsWith("TestFail") ) path += "\\TestFailScreenshots";
        path += "\\" + name;
        //get the file and return as byte array
        try ( RandomAccessFile raf = new RandomAccessFile(new File(path), "r") )
        {
            byte[] b = new byte[(int) raf.length()];
            raf.readFully(b);
            return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(b);
        }
        catch ( Exception e )
        {
            throw e;
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
