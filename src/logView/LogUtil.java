package logView;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LogUtil {
    public static final String TYPE_ALL = "ALL";

    public static String getLogPath() {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

        try {
            String confFilePath = System.getProperty("user.dir") + "\\log.xml";
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new File(confFilePath));

            Element rootElement = doc.getDocumentElement();

            XPathFactory xf = XPathFactory.newInstance();
            XPath xpath = xf.newXPath();
            XPathExpression xexp = null;
            NodeList nodes = null;
            Element el = null;

            xexp = xpath.compile("/log");
            nodes = (NodeList) xexp.evaluate(rootElement, XPathConstants.NODESET);
            el = (Element) nodes.item(0);
            {
                String path = el.getAttribute("path");
                return path;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static List<String> getLogTypes() {
        List<String> types = new ArrayList<>();
        types.add(TYPE_ALL);
        types.add("ERROR");
        types.add("WARN");
        types.add("INFO");
        types.add("DEBUG");
        types.add("FATAL ");
        types.add("TRACE ");
        return types;
    }

    public static List<String> getPathNames() {
        String rootPath = getLogPath();
        File file = new File(rootPath);
        File[] files = file.listFiles();
        List<String> pathNames = Arrays.stream(files).filter( item -> item.isDirectory()).map(item -> item.getName()).collect(Collectors.toList());
        return pathNames;
    }

    public static  List<LogItem> getLogContent( String dir , int range, String type, String keyword, String exceptKeyword, String regex, LocalDate date) {
        String rootPath = getLogPath();
        String path = rootPath + "\\" + dir;
        File file = new File(path);
        List<File> logFiles = Arrays.stream(file.listFiles()).filter( item -> {
            if( !item.isFile()) {
                return false;
            }
            if( date != null) {
                return dateMatch(item.getName(), date);
            }
            if(  range > 0 ) {
                return inRange(item.getName(), range);
            }
            return true;
        } ).collect(Collectors.toList());
        CountDownLatch countDownLatch = new CountDownLatch(logFiles.size());
        ExecutorService executorService = Executors.newFixedThreadPool(9);
        List<LogItem> allLogItem = new ArrayList<>();

        for(File fileItem : logFiles) {
            executorService.execute( ()->{
                synchronized (allLogItem) {
                    allLogItem.addAll( LogItem.asList(fileItem, type,keyword,exceptKeyword, regex));
                }
                countDownLatch.countDown();
            });
        }
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        executorService.shutdown();

        allLogItem.sort( (b,a) -> {
            long diff =  a.date().getTime()  - b.date().getTime();
            return (int)diff;
        });

        return allLogItem;
    }

    private static boolean dateMatch(String fileName, LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();

        FileName2Date fileName2Date = new FileName2Date(fileName);
        if( !fileName2Date.isValid()) {
            return false;
        }

        int fYear = fileName2Date.getYear();
        int fMonth = fileName2Date.getMonth();
        int fDay = fileName2Date.getDay();

        return year == fYear && month == fMonth && fDay == day;
    }

    private static boolean inRange( String name, int range ) {
        FileName2Date fileName2Date = new FileName2Date(name);
        if( !fileName2Date.isValid()) {
            return false;
        }

        int year = fileName2Date.getYear();
        int month = fileName2Date.getMonth();
        int day = fileName2Date.getDay();

        Calendar calendar = new GregorianCalendar(year, month - 1, day,0,0,0);

        Calendar now = Calendar.getInstance();
        Calendar endCal = new GregorianCalendar(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH),23,59,59);
        Calendar beginCal = new GregorianCalendar(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH),23,59,59);
        beginCal.add(Calendar.DAY_OF_MONTH, -range);

        if( calendar.getTimeInMillis() <= endCal.getTimeInMillis() && calendar.getTimeInMillis() >= beginCal.getTimeInMillis() ) {
            return true;
        }
        return false;
    }
}