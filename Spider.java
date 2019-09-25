// Based on Peter Strazdin's ClientTCP program, RSCS ANU, 03/18
// Ernest Kwan u6381103, 04/19

import java.io.*;
import java.net.Socket;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;


public class Spider {

    // set host and port here
    static String host = "comp3310.ddns.net";
    static int port = 7880;
    static String remoteAddr;

    // delay in milliseconds before making the next request
    static long delay = 2000;

    static int dist_urls = 0;  // distinct urls
    static ArrayList<String> urls = new ArrayList<String>();
    static int html_pages = 0, non_html_objects = 0;

    // smallest and largest html pages
    static int smallest_size = Integer.MAX_VALUE, largest_size = -1;
    static String smallest_page = "", largest_page = "";

    // oldest and most recently modified html pages
    static Date oldest_date = null, newest_date = null;
    static String oldest_page = "", newest_page = "";

    static ArrayList<String> invalid_urls = new ArrayList<String>();
    static ArrayList<String> redirected_urls = new ArrayList<String>();

    /**
     *
     * @param addr URL address
     * @return the URL address in absolute URL
     */
    private static String toAbsoluteURL(String addr){
        if(addr.contains("://")) return addr.substring(addr.indexOf("://") + 3);
        else if(addr.charAt(0) != '/') addr = "/" + addr;
        return host + ":" + port + addr;
    }

    /**
     *
     * @param addr URL address
     * @return the URL address in relative URL
     */
    private static String toRelativeURL(String addr){
        if(!addr.contains("://")){
            if(addr.charAt(0) != '/') return "/" + addr;
            else return addr;
        }
        if (!addr.contains(host))
            return addr.substring(addr.indexOf("://") + 3);
        else
            return addr.substring(addr.indexOf(host) + host.length());
    }

    /**
     *
     * @param addr URL address
     * @return true if it is an external url
     */
    private static boolean externalURL(String addr){
        if(!addr.contains("://")) return false;
        int beginIndex = addr.indexOf("://") + 3, endIndex = 0;
        if(addr.substring(beginIndex).contains("/"))
            endIndex = addr.substring(beginIndex).indexOf("/");
        else endIndex = addr.substring(beginIndex).length();
        String addrHost = addr.substring(beginIndex, beginIndex + endIndex);
        if(!remoteAddr.contains(addrHost)){
            System.out.println(remoteAddr + " doesnt contain " + addrHost);
        }
        return !remoteAddr.contains(addrHost);
    }
    /**
     * Recursively scans a page by making HTTP/1.0 requests and parsing the results to scan further links
     * @param addr the URL of the target
     * @param method the HTTP method (GET/HEAD)
     * @param redirects the number of redirects (in a row), used for determining infinite redirect
     */
    private static void scan(String addr, String method, int redirects) throws IOException, ParseException, InterruptedException {

        TimeUnit.MILLISECONDS.sleep(delay);
        int http = 0, size = 0;
        if(addr.isEmpty()) addr = "/";

        Socket sock = new Socket(host, port);

        remoteAddr = String.valueOf(sock.getInetAddress());

        // Writing to the socket for HTTP request

        DataOutputStream out = new DataOutputStream(sock.getOutputStream());
        PrintWriter writer = new PrintWriter(out, false);

        writer.print(method + " " + addr + " HTTP/1.0\r\n");
        writer.print("\r\n");
        writer.flush();

        // Reading the HTTP response from the socket

        DataInputStream in = new DataInputStream(sock.getInputStream());
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));

        // Check Status-Line first (e.g. HTTP/1.0 200)

        String message = reader.readLine();

        // Valid URLs (Full-Response and Simple-Response)
        if(message.substring(9,12).equals("200") || !message.substring(0,5).equals("HTTP/")){
            if(!urls.contains(toAbsoluteURL(addr))){
                urls.add(toAbsoluteURL(addr));
                dist_urls++;
                http = 2;
            }
        }
        // Invalid URLs (404)
        else if(message.substring(9,12).equals("404")){
            http = 4;
            invalid_urls.add(addr);
            dist_urls++;
        }
        // Redirection 3xx
        else if(message.charAt(9) == '3'){
            http = 3;
            redirected_urls.add(addr);
            dist_urls++;
        }
        // Service Unavailable 503
        else if(message.substring(9,12).equals("503")){
            TimeUnit.MILLISECONDS.sleep(delay);
            scan(addr, "GET", redirects + 1);
        }


        // Keep reading response from socket
        while (http != 0 && message != null) {

            // valid URLs
            if(http == 2) {

                // Look at HTTP Header Fields

                // Last modified
                if (message.length() > 15 && message.substring(0, 14).equals("Last-Modified:")) {
                    SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
                    Date d = format.parse(message.substring(15));
                    if (oldest_date == null || d.before(oldest_date)) {
                        oldest_page = addr;
                        oldest_date = d;
                    }
                    if (newest_date == null || d.after(newest_date)) {
                        newest_page = addr;
                        newest_date = d;
                    }
                }

                // Page size
                if (message.length() > 16 && message.substring(0, 15).equals("Content-Length:")) {
                    size = Integer.parseInt(message.substring(16));
                }

                // Content type (e.g. html page, image)
                if (message.length() > 14 && message.substring(0, 13).equals("Content-Type:")) {
                    if(message.contains("html")){
                        html_pages++;
                        if (size < smallest_size) {
                            smallest_page = addr;
                            smallest_size = size;
                        }
                        if (size > largest_size) {
                            largest_page = addr;
                            largest_size = size;
                        }
                    }
                    else{
                        non_html_objects++;
                        http = 0;
                    }

                }

                // Look for html tags in the body

                // hyperlinks
                if(message.contains("<a href")){
                    int beginIndex = message.indexOf("href=\"") + 6;
                    int endIndex = message.substring(beginIndex).indexOf("\"");
                    String newAddr = message.substring(beginIndex, beginIndex+endIndex);
                    if(!newAddr.equals("/")) {
                        if (!newAddr.contains("://")) {
                            if(newAddr.charAt(0) != '/') newAddr = "/" + newAddr;
                            newAddr = addr.substring(0, addr.lastIndexOf('/')) + newAddr;
                            scan(newAddr, "GET", 0);
                        } else{
                            if(!externalURL(newAddr)) scan(newAddr, "GET", 0);
                        }
                    }
                }

                // images
                if(message.contains("<img")){
                    int beginIndex = message.indexOf("src=\"") + 5;
                    int endIndex = message.substring(beginIndex).indexOf("\"");
                    String newAddr = message.substring(beginIndex, beginIndex+endIndex);
                    if (!newAddr.contains("://")) {
                        if(newAddr.charAt(0) != '/') newAddr = "/" + newAddr;
                        newAddr = addr.substring(0, addr.lastIndexOf('/')) + newAddr;
                        scan(newAddr, "HEAD", 0);

                    } else{
                        if(!externalURL(newAddr)) scan(newAddr, "HEAD", 0);

                    }
                }
            }

            // Look at Location field for redirection
            else if(http == 3){
                if (message.length() > 10 && message.substring(0, 9).equals("Location:")) {
                    String newAddr = message.substring(10);
                    redirected_urls.add(newAddr);
                    if (toAbsoluteURL(newAddr).equals(host + addr)
                            || toAbsoluteURL(newAddr).equals(remoteAddr.substring(remoteAddr.indexOf("/") + 1) + "/" + addr)
                            || toAbsoluteURL(addr).equals(toAbsoluteURL(newAddr))
                            || redirects > 14
                    ){
                        System.out.println("Infinite redirect");
                    } else {
                        if(!externalURL(newAddr)) scan(newAddr, "GET", redirects + 1);
                    }
                }
            }

            message = reader.readLine(); // read the next line
        }

        // close socket
        sock.close();

    }


    public static void main (String[] args) throws IOException, ParseException, InterruptedException {

        System.out.println("Crawling " + host + ":" + port + "...");
        // start with / (usually index.html)
        scan("/", "GET", 0);

        // Report (mostly in relative URL, except (5) and (6))
        System.out.println("(1) Number of distinct urls: " + dist_urls);
        System.out.println("(2) Number of html pages: " + html_pages);
        System.out.println("    Number of non-html objects: " + non_html_objects);
        if(html_pages == 0){
            System.out.println("(3) and (4) No valid html page is found");
        } else {
            System.out.println("(3) Smallest html page: " + toRelativeURL(smallest_page) + " (" + smallest_size + " bytes)");
            System.out.println("    Largest html page: " + toRelativeURL(largest_page) + " (" + largest_size + " bytes)");
            System.out.println("(4) Oldest modified page: " + toRelativeURL(oldest_page) + " (" + oldest_date + ")");
            System.out.println("    Most recently modified page: " + toRelativeURL(newest_page) + " (" + newest_date + ")");
        }
        System.out.println("(5) Invalid URLs (404):");
        for(String url : invalid_urls){
            System.out.println("    " + toAbsoluteURL(url));
        }
        System.out.println("(6) Redirected URLs (30x):");
        for (int i = 0; i < redirected_urls.size(); i+=2) {
            System.out.println("    " + toRelativeURL(redirected_urls.get(i)) + " -> " + redirected_urls.get(i+1));
        }

    }

}


