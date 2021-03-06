package logView;

import java.io.File;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class LogUtil {
    public static final String TYPE_ALL = "ALL";

    public static String getLogPath() {
        return Config.getPath();
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

    public static  List<LogItem> getLogContent( String dir , String type, String keyword, String exceptKeyword, String regex, LocalDate beginDate, LocalDate endDate) {
        String rootPath = getLogPath();
        String path = rootPath + "\\" + dir;
        File file = new File(path);
        List<File> logFiles = Arrays.stream(file.listFiles()).filter( item -> {
            if( !item.isFile()) {
                return false;
            }
            return inRange(item.getName(), beginDate, endDate);
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

    private static boolean inRange( String name, LocalDate beginDate, LocalDate endDate ) {
        FileName2Date2 fileName2Date = new FileName2Date2(name);
        if( !fileName2Date.isValid()) {
            return false;
        }

        int year = fileName2Date.getYear();
        int month = fileName2Date.getMonth();
        int day = fileName2Date.getDay();
        Calendar calendar = new GregorianCalendar(year, month - 1, day,0,0,0);

        Calendar beginCal = new GregorianCalendar(beginDate.getYear(), beginDate.getMonthValue() - 1, beginDate.getDayOfMonth(),0,0,0);
        Calendar endCal = new GregorianCalendar(endDate.getYear(), endDate.getMonthValue() - 1, endDate.getDayOfMonth(),23,59,59);

        if( calendar.getTimeInMillis() <= endCal.getTimeInMillis() && calendar.getTimeInMillis() >= beginCal.getTimeInMillis() ) {
            return true;
        }
        return false;
    }
}