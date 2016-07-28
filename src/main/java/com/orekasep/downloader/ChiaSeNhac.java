package com.orekasep.downloader;
import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.jsoup.Connection;
import org.jsoup.Connection.Method;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.helper.StringUtil;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.ExampleMode;
import org.kohsuke.args4j.Option;

public class ChiaSeNhac {
	private static final String LOGIN_URL = "http://chiasenhac.vn/login.php";
	
	private static final int DEFAULT_NETWORK_TIMEOUT = 30000;
	
	private static final String SYSTEM_SEPERATOR = FileSystems.getDefault().getSeparator();
	
	@Option(name = "-u", usage = "ChiaSeNhac - Tên đăng nhập", metaVar = "USERNAME")
	private String username;
	
	@Option(name = "-p", usage = "ChiaSeNhac - Mật khẩu đăng nhập", metaVar = "PASSWORD")
	private String password;
	
	@Option(name = "-o", usage = "Đường dẫn thư mục lưu nhạc", metaVar = "OUTPUT")
	private File outputFolder = new File(".");

	@Option(name = "-c", usage = "Sử dụng cookies (không đăng nhập)", metaVar = "COOKIES")
	private String cookies;
	
	@Option(name = "-q", usage = "Chất lượng tải (128|320|500)", metaVar = "QUALITY")
	private String quality = MusicQuality._500KBPS.value;
	
	/**
	 * In case there we don't specify the cookies so we will need to store 
	 * the return map after login somewhere
	 */
	private Map<String, String> cookieMap;
	
    @Argument
    private List<String> arguments = new ArrayList<String>();
    
    public enum MusicQuality {
        _128KBPS("128"), _192KBPS("192"), _320KBPS("320"), _500KBPS("500");

    	private String value;
    	private MusicQuality(String value) { 
    		this.value = value; 
		}
    }
    
    /**
     * The magical come here
     * 
     * @param args
     * @throws IOException
     */
	public static void main(String[] args) throws IOException {
		new ChiaSeNhac().doMain(args);
	}
	
	/**
	 * Parse the Arguments?
	 * 
	 * @param args
	 * @throws IOException
	 */
    public void doMain(String[] args) throws IOException {
        CmdLineParser parser = new CmdLineParser(this);
        try {
            // parse the arguments.
            parser.parseArgument(args);

            if( arguments.isEmpty() )
                throw new CmdLineException(parser, "Thiếu tham số đầu vào");
            
            if (StringUtils.isEmpty(cookies)) {
            	cookieMap = doLogin(username, password);
            }
            
            for (String part : arguments) {
            	if (part != null && part.startsWith("http") && part.contains("chiasenhac")) {
            		if (part.contains("album")) {
            			this.downloadAlbum(part);
            		} else {
            			this.downloadSong(part);
            		}
            	}
            }
        } catch( CmdLineException e ) {
            System.err.println(e.getMessage());
            System.err.println("java ChiaSeNhac [options...] arguments...");
            
            // print the list of available options
            parser.printUsage(System.err);
            System.err.println();

            // print option sample. This is useful some time
            System.err.println("  Ví dụ: java ChiaSeNhac" + parser.printExample(ExampleMode.ALL));

        }
    }
    
    /**
     * Process to login | www.chiasenhac.vn
     * 
     * @param username
     * @param password
     * @return
     * @throws CmdLineException
     * @throws IOException
     */
    private Map<String, String> doLogin(String username, String password) throws CmdLineException, IOException {
    	if (StringUtils.isEmpty(username) || StringUtils.isEmpty(password))
    		throw new CmdLineException("Thiếu Tên đăng nhập hoặc Mật khẩu");
    	
    	Response response = Jsoup.connect(LOGIN_URL)
    			.timeout(DEFAULT_NETWORK_TIMEOUT)
    			.method(Method.POST)
    			.header("Content-Type", "application/x-www-form-urlencoded")
    			.data("username", username)
    			.data("password", password)
    			.data("autologin", "on")
    			.execute();
    	
    	if (response != null) {
    		return response.cookies();
    	}
    	
    	return null;
    }
    
    /**
     * Helper to get the right connection with authentication cookies
     * 
     * @param url
     * @return
     * @throws IOException
     */
    private Connection doConnect(String url) throws IOException {
    	Connection connection = Jsoup.connect(url);
		if (StringUtils.isNotEmpty(cookies)) {
			connection.header("Cookie", cookies);
		} else {
			if (cookieMap == null) 
				throw new IOException("Đăng nhập lỗi");
			
			connection.cookies(cookieMap);
		}
    	return connection;
    }

    /**
     * Helper to get the correct URL to download song
     * 
     * @param url
     * @return
     * @throws IOException
     */
    private String getDownloadPage(String url) throws IOException {
		Document doc = doConnect(url).get();
		return doc.select("a[href$=download.html]").first().absUrl("href");
	}
	
    /**
     * Helper to find the best quality for this URL
     * 
     * @param downloadPage
     * @return
     * @throws IOException
     */
    private String getBestQualitySong(String downloadPage) throws IOException {
		Document doc = Jsoup.connect(downloadPage).timeout(30000).header("Cookie", cookies).get();
		
		Elements link500kbps = doc.select("#downloadlink a[href*=500kbps]");
		if (MusicQuality._500KBPS.value.equals(quality) && 
				link500kbps != null && !link500kbps.isEmpty()) {
			return link500kbps.first().absUrl("href");
		}
		
		Elements link320kbps = doc.select("#downloadlink a[href*=320kbps]");
		if (MusicQuality._320KBPS.value.equals(quality) && 
				link320kbps != null && !link320kbps.isEmpty()) {
			return link320kbps.first().absUrl("href");
		}
		
		Elements link192kbps = doc.select("#downloadlink a[href*=192kbps]");
		if (MusicQuality._192KBPS.value.equals(quality) && 
				link192kbps != null && !link192kbps.isEmpty()) {
			return link192kbps.first().absUrl("href");
		}
		
		Elements link128kbps = doc.select("#downloadlink a[href*=128kbps]");
		if (MusicQuality._128KBPS.value.equals(quality) && 
				link128kbps != null && !link128kbps.isEmpty()) {
			return link128kbps.first().absUrl("href");
		}
		
		return null;
	}
	
    /**
     * Download a particular song by URL
     * 
     * @param url
     * @throws IOException
     */
    private void downloadSong(String url) throws IOException {
		// fetch the link to download first
		String downloadPage = getDownloadPage(url);
		
		String link2download = getBestQualitySong(downloadPage);
		if (!StringUtil.isBlank(link2download)) {
			String filename = URLDecoder.decode(FilenameUtils.getName(link2download));
			System.out.println("Đang tải ...");
			System.out.println("___________________________________");
			System.out.println(filename);
			
			Connection connection = doConnect(link2download)
					.ignoreContentType(true)
					.maxBodySize(Integer.MAX_VALUE);
			
			Response response = connection.execute();
			
			File outputFile = new File(outputFolder.getAbsolutePath() + SYSTEM_SEPERATOR + filename);
			FileUtils.writeByteArrayToFile(outputFile, response.bodyAsBytes());
			
			System.out.println("Đã tải xong.");
			System.out.println("___________________________________");

		}		
	}
	
    /**
     * Download whole album by URL
     * 
     * @param url
     * @throws IOException
     */
    private void downloadAlbum(String url) throws IOException {
		Document doc = doConnect(url).get();
		
		Elements downloadLinks = doc.select(".playlist_prv span.gen a[href$=download.html]");
		for (Element link : downloadLinks) {
			downloadSong(link.absUrl("href"));
		}
	}
}
