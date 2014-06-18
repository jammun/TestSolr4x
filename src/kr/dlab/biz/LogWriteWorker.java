/**
 * 
 */
package kr.dlab.biz;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

import javax.servlet.http.HttpServletRequest;

import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author admin
 *
 */
public class LogWriteWorker extends Thread {
	
	private boolean isIndexed;
	private String contextPath;

	private SolrQueryRequest solrReq;
	private SolrQueryResponse solrRsp;
	private HttpServletRequest req;
	private boolean hasIpRange;
	private long lowIp;
	private long highIp;
	private String myIpaddress;
	private String mySubnet;
	
	private Logger myLogger;
	
	
	/**
	 * @return the contextPath
	 */
	public String getContextPath() {
		return contextPath;
	}

	/**
	 * @param contextPath the contextPath to set
	 */
	public void setContextPath(String contextPath) {
		this.contextPath = contextPath;
	}
	
	/**
	 * @return the req
	 */
	public HttpServletRequest getReq() {
		return req;
	}

	/**
	 * @param req the req to set
	 */
	public void setReq(HttpServletRequest req) {
		this.req = req;
	}

	/**
	 * @return the isIndexed
	 */
	public boolean isIndexed() {
		return isIndexed;
	}

	/**
	 * @param isIndexed the isIndexed to set
	 */
	public void setIndexed(boolean isIndexed) {
		this.isIndexed = isIndexed;
	}

	/**
	 * @return the solrReq
	 */
	public SolrQueryRequest getSolrReq() {
		return solrReq;
	}

	/**
	 * @param solrReq the solrReq to set
	 */
	public void setSolrReq(SolrQueryRequest solrReq) {
		this.solrReq = solrReq;
	}

	/**
	 * @return the solrRsp
	 */
	public SolrQueryResponse getSolrRsp() {
		return solrRsp;
	}

	/**
	 * @param solrRsp the solrRsp to set
	 */
	public void setSolrRsp(SolrQueryResponse solrRsp) {
		this.solrRsp = solrRsp;
	}

	public LogWriteWorker() {
		super();
		myLogger = LoggerFactory.getLogger(DBHandler.class);
		initMyNetwork();
	}
	
	@Override
	public void run() {
		
		if ( isIndexed() ) {
			writeIndexLog();
		} else {
			writeSearchLog();
		}
	}
	
	private void initMyNetwork() {
		try {
			myIpaddress = getMyIpaddress();
			mySubnet = myIpaddress + "/24";
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void writeIndexLog() {
	}
	
	private void writeSearchLog() {
		
		String userip = getClientIpAddr(req);
		
		myLogger.info("my ip : " + myIpaddress + " subnet : " + mySubnet + " client userIp : " + userip);
		
		if ( netMatch(mySubnet, userip)  ) { // called from solr cloud
			return; //do nothing
		}else { //this is correct call maybe.
			DBHandler dbh = new DBHandler();
			dbh.insertLogSearch(req, solrReq, solrRsp, contextPath, userip);
		}
	}
	
	public void testSelect() {
		DBHandler dbh = new DBHandler();
		dbh.selectTest();
	}
	
	private String getMyIpaddress() throws UnknownHostException {
		return InetAddress.getLocalHost().getHostAddress().toString();
	}
	
	//addr is subnet address and addr1 is ip address. 
	//Function will return true, if addr1 is within addr(subnet)
	private boolean netMatch(String addr, String addr1){ 

		if ( addr == null || addr1 == null ) {
			return false;
		}
		
		//called from localhost. (for test)
		if ( addr1.equals("127.0.0.1") || addr1.equals("localhost")) {
			return false;
		}
		
        String[] parts = addr.split("/");
        String ip = parts[0];
        int prefix;

        if (parts.length < 2) {
            prefix = 0;
        } else {
            prefix = Integer.parseInt(parts[1]);
        }

        Inet4Address a =null;
        Inet4Address a1 =null;
        try {
            a = (Inet4Address) InetAddress.getByName(ip);
            a1 = (Inet4Address) InetAddress.getByName(addr1);
        } catch (UnknownHostException e){}

        byte[] b = a.getAddress();
        int ipInt = ((b[0] & 0xFF) << 24) |
                         ((b[1] & 0xFF) << 16) |
                         ((b[2] & 0xFF) << 8)  |
                         ((b[3] & 0xFF) << 0);

        byte[] b1 = a1.getAddress();
        int ipInt1 = ((b1[0] & 0xFF) << 24) |
                         ((b1[1] & 0xFF) << 16) |
                         ((b1[2] & 0xFF) << 8)  |
                         ((b1[3] & 0xFF) << 0);

        int mask = ~((1 << (32 - prefix)) - 1);

        if ((ipInt & mask) == (ipInt1 & mask)) {
            return true;
        }
        else {
            return false;
        }
	}
	
	private String getClientIpAddr(HttpServletRequest request) {
		
		if ( request == null ) return "";
		
        String ip = request.getHeader("X-Forwarded-For");  
        
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {  
            ip = request.getHeader("Proxy-Client-IP");  
        }  
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {  
            ip = request.getHeader("WL-Proxy-Client-IP");  
        }  
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {  
            ip = request.getHeader("HTTP_CLIENT_IP");  
        }  
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {  
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");  
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {  
            ip = request.getHeader("HTTP_X_FORWARDED");  
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {  
            ip = request.getHeader("HTTP_X_CLUSTER_CLIENT_IP");  
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {  
            ip = request.getHeader("HTTP_FORWARDED_FOR");  
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {  
            ip = request.getHeader("HTTP_FORWARDED");  
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {  
            ip = request.getHeader("X-CLIENT-IP");  
        }  
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {  
            ip = request.getHeader("X-Real-IP");  
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {  
            ip = request.getHeader("X-REAL-IP");  
        }  
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {  
            ip = request.getRemoteAddr();  
        }  
        return ip;  
    }  

}
